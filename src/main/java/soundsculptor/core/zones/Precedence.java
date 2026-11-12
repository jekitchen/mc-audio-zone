package soundsculptor.core.zones;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.sets.CacheBudget;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.EntryResolution;
import soundsculptor.core.sets.EntryResolver;

/**
 * Combines the global rule list and the zones into one decision per sound (PRD FR-9), separately for loudness,
 * limit and cue:
 * <ol>
 *   <li>Resolve the global list for the sound ID (FR-4.5).</li>
 *   <li>Collect zones whose weight {@code w} for the sound is above 0 ({@link Zone#weight}): fixed zones only in their
 *       own world and dimension, personal bubbles wherever the player is. Non-positional sounds skip zones.</li>
 *   <li>A zone takes part for a property only if one of its rules sets it.</li>
 *   <li>Among those, the zone whose anchor is closest to the sound wins. Outside zones are measured from their centre
 *       too; a bubble's anchor is the player.</li>
 *   <li>Anchors within 0.01 blocks of the same distance tie: the smaller shape wins, then the zone higher in the list.</li>
 *   <li>Volume blends {@code global + (zone - global) × w}; limit and cue use the zone when {@code w ≥ 0.5}.</li>
 *   <li>A property nothing sets keeps vanilla behavior (100%, no limit, cue off).</li>
 * </ol>
 *
 * <p>An immutable snapshot of one profile: build a new one when the player saves. {@link #decide} allocates nothing
 * once each sound ID has been seen.
 */
public final class Precedence {
	/** Most zones a profile can hold (FR-8.9). */
	public static final int MAX_ZONES = 64;
	/**
	 * Sound IDs all zones' rule lists cache together (PRD 9.1 memory budget): about 1 MB when full. Without it, 64 zones
	 * could each cache 4,096 IDs, about 32 MB. Past the cap a zone answers the same, just by matching patterns again.
	 */
	public static final int ZONE_CACHE_IDS = 8192;
	/**
	 * Sound IDs whose resolutions in every list are kept together in one row (see {@code rows}): at most about 0.3 MB
	 * with 64 zones. A real session hears a few hundred IDs; past the cap a sound is resolved list by list, as before.
	 */
	public static final int ROW_CACHE_IDS = 1024;

	private static final double TIE_DISTANCE = 0.01;
	private static final float DISCRETE_WEIGHT = 0.5F;

	private final EntryResolver global;
	private final Zone[] zones;
	private final EntryResolver[] zoneRules;
	private final double[] volumes;
	private final ZoneEdge profileEdge;
	/**
	 * Per zone, in flat arrays so the per-sound loop reads them together instead of following references (zone,
	 * anchor, shape): on/off, bubble, Inside mode, the fixed anchor and the shape's half-size on each axis. An Inside
	 * zone has weight 0 outside its shape, so a sound beyond the half-size on any axis is rejected without the exact
	 * test (PRD 7.4: "a cheap axis-aligned bounding-box test before the exact sphere or cylinder test").
	 */
	private final boolean[] enabled;
	private final boolean[] bubble;
	private final boolean[] inside;
	private final double[] ax;
	private final double[] ay;
	private final double[] az;
	private final double[] hx;
	private final double[] hy;
	private final double[] hz;

	/**
	 * Per sound ID, what every rule list resolves it to (global first, then each zone): one lookup per sound instead of
	 * one per list. Measured in game, the per-list lookups were most of a sound start's cost with 64 zones (M8). Capped
	 * like the resolvers' own caches, because servers can send arbitrary IDs.
	 */
	private final Map<String, EntryResolution[]> rows = new ConcurrentHashMap<>();
	/** Which zones are fixed in the last world and dimension decided (bubbles are always "here"). */
	private String hereWorldKey;
	private String hereDimension;
	private boolean[] here = new boolean[0];
	/** Sounds decided from {@link #rows}; each answers every list from the cache. Plain field: one thread. */
	private long rowHits;

	/** A profile whose default edge is hard (PRD 7.5). */
	public Precedence(List<SetEntry> globalRules, List<Zone> zones) {
		this(globalRules, zones, ZoneEdge.hard());
	}

	/**
	 * @param profileEdge the profile's default edge, used by zones whose edge is {@link ZoneEdge#DEFAULT}
	 */
	public Precedence(List<SetEntry> globalRules, List<Zone> zones, ZoneEdge profileEdge) {
		this.profileEdge = profileEdge.mode() == ZoneEdge.Mode.DEFAULT ? ZoneEdge.hard() : profileEdge;
		if (zones.size() > MAX_ZONES) {
			throw new IllegalArgumentException(zones.size() + " zones is more than the maximum " + MAX_ZONES);
		}
		this.global = new EntryResolver(globalRules);
		this.zones = zones.toArray(new Zone[0]);
		this.zoneRules = new EntryResolver[this.zones.length];
		int n = this.zones.length;
		this.volumes = new double[n];
		this.enabled = new boolean[n];
		this.bubble = new boolean[n];
		this.inside = new boolean[n];
		this.ax = new double[n];
		this.ay = new double[n];
		this.az = new double[n];
		this.hx = new double[n];
		this.hy = new double[n];
		this.hz = new double[n];
		CacheBudget zoneCaches = new CacheBudget(ZONE_CACHE_IDS);
		for (int i = 0; i < n; i++) {
			Zone zone = this.zones[i];
			zoneRules[i] = new EntryResolver(zone.rules(), zoneCaches);
			volumes[i] = zone.shape().volume();
			enabled[i] = zone.enabled();
			bubble[i] = zone.isBubble();
			inside[i] = zone.mode() == ZoneMode.INSIDE;
			ax[i] = zone.anchor().x();
			ay[i] = zone.anchor().y();
			az[i] = zone.anchor().z();
			switch (zone.shape()) {
				case Shape.Sphere sphere -> {
					hx[i] = sphere.radius();
					hy[i] = sphere.radius();
					hz[i] = sphere.radius();
				}
				case Shape.Box box -> {
					hx[i] = box.width() / 2;
					hy[i] = box.height() / 2;
					hz[i] = box.depth() / 2;
				}
				case Shape.Cylinder cylinder -> {
					hx[i] = cylinder.radius();
					hy[i] = cylinder.height() / 2;
					hz[i] = cylinder.radius();
				}
			}
		}
	}

	/** {@link #decide(String, boolean, String, String, double, double, double, double, double, double, SoundDecision)}
	 * without a player position: personal bubbles are skipped. */
	public void decide(String soundId, boolean positional, String worldKey, String dimension, double x, double y, double z,
			SoundDecision out) {
		decide(soundId, positional, worldKey, dimension, x, y, z, Double.NaN, Double.NaN, Double.NaN, out);
	}

	/**
	 * Decides one sound and writes the result into {@code out}.
	 *
	 * @param positional whether the sound has a world position (PRD section 5); zones never apply otherwise
	 * @param worldKey   the current server or world (FR-11.1)
	 * @param dimension  the current dimension, e.g. {@code minecraft:overworld}
	 * @param px         the local player's eye position, the anchor of personal bubbles; {@code NaN} skips bubbles
	 */
	public void decide(String soundId, boolean positional, String worldKey, String dimension, double x, double y, double z,
			double px, double py, double pz, SoundDecision out) {
		decide(soundId, null, positional, worldKey, dimension, x, y, z, px, py, pz, out);
	}

	/** The cached resolutions of every list for this sound ID; computed on first sight. */
	private EntryResolution[] row(String soundId) {
		EntryResolution[] row = rows.get(soundId);
		if (row != null) {
			rowHits++;
		} else {
			row = new EntryResolution[zones.length + 1];
			resolve(soundId, row);
			if (rows.size() < ROW_CACHE_IDS) {
				rows.put(soundId, row);
			}
		}
		return row;
	}

	/**
	 * Which zones are fixed in this world and dimension. Recomputed only when they change, so the per-sound loop does
	 * not compare strings for every zone. Not thread-safe beyond the game thread, like the rest of the hot path.
	 */
	private boolean[] here(String worldKey, String dimension) {
		if (!worldKey.equals(hereWorldKey) || !dimension.equals(hereDimension)) {
			boolean[] flags = new boolean[zones.length];
			for (int i = 0; i < zones.length; i++) {
				flags[i] = worldKey.equals(zones[i].worldKey()) && dimension.equals(zones[i].dimension());
			}
			here = flags;
			hereWorldKey = worldKey;
			hereDimension = dimension;
		}
		return here;
	}

	/**
	 * Fills {@code out} with what each rule list resolves {@code soundId} to: index 0 is the global list, index
	 * {@code i + 1} is zone {@code i}. For callers that decide the same sound again and again, such as the per-tick
	 * re-evaluation of playing sounds (FR-8.8): resolve once per profile snapshot, then use {@link #decideResolved}.
	 *
	 * @param out at least {@link #zoneCount()} + 1 long
	 */
	public void resolve(String soundId, EntryResolution[] out) {
		out[0] = global.resolve(soundId);
		for (int i = 0; i < zones.length; i++) {
			out[i + 1] = zoneRules[i].resolve(soundId);
		}
	}

	/**
	 * The same decision as {@link #decide(String, boolean, String, String, double, double, double, double, double,
	 * double, SoundDecision)}, from resolutions {@link #resolve} filled for this snapshot. Skips the per-ID lookups.
	 */
	public void decideResolved(EntryResolution[] resolved, boolean positional, String worldKey, String dimension,
			double x, double y, double z, double px, double py, double pz, SoundDecision out) {
		decide(null, resolved, positional, worldKey, dimension, x, y, z, px, py, pz, out);
	}

	private void decide(String soundId, EntryResolution[] resolved, boolean positional, String worldKey, String dimension,
			double x, double y, double z, double px, double py, double pz, SoundDecision out) {
		out.reset();
		if (resolved == null) {
			resolved = row(soundId);
		}
		EntryResolution g = resolved[0];
		boolean[] inThisWorld = positional ? here(worldKey, dimension) : null;

		int loudIdx = -1;
		int limitIdx = -1;
		int cueIdx = -1;
		double loudDist = 0;
		double limitDist = 0;
		double cueDist = 0;
		double loudW = 0;
		double limitW = 0;
		double cueW = 0;
		EntryResolution loudRes = null;
		EntryResolution limitRes = null;
		EntryResolution cueRes = null;

		if (positional) {
			for (int i = 0; i < zones.length; i++) {
				if (!enabled[i]) {
					continue;
				}
				if (resolved[i + 1] == EntryResolution.NONE) {
					continue; // nothing in this zone matches the sound: skip the geometry (same result, it is skipped below)
				}
				double dx;
				double dy;
				double dz;
				if (bubble[i]) {
					if (Double.isNaN(px)) {
						continue;
					}
					dx = x - px;
					dy = y - py;
					dz = z - pz;
				} else {
					if (!inThisWorld[i]) {
						continue;
					}
					dx = x - ax[i];
					dy = y - ay[i];
					dz = z - az[i];
				}
				if (inside[i] && (Math.abs(dx) > hx[i] || Math.abs(dy) > hy[i] || Math.abs(dz) > hz[i])) {
					continue; // outside the shape's bounding box: an Inside zone's weight is 0 there
				}
				double w = zones[i].weight(dx, dy, dz, profileEdge);
				if (w <= 0) {
					continue;
				}
				EntryResolution r = resolved[i + 1];
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (r.loudness() != null && beats(i, dist, loudIdx, loudDist)) {
					loudIdx = i;
					loudDist = dist;
					loudRes = r;
					loudW = w;
				}
				if (r.limit() != null && beats(i, dist, limitIdx, limitDist)) {
					limitIdx = i;
					limitDist = dist;
					limitRes = r;
					limitW = w;
				}
				if (r.cue() != null && beats(i, dist, cueIdx, cueDist)) {
					cueIdx = i;
					cueDist = dist;
					cueRes = r;
					cueW = w;
				}
			}
		}

		float globalVolume = g.effectiveVolumePercent();
		if (loudIdx >= 0) {
			out.volumePercent = (float) (globalVolume + (loudRes.effectiveVolumePercent() - globalVolume) * loudW);
			out.loudnessRule = loudRes.loudness();
			out.loudnessZone = zones[loudIdx];
			out.loudnessWeight = (float) loudW;
		} else {
			out.volumePercent = globalVolume;
			out.loudnessRule = g.loudness();
		}

		if (limitIdx >= 0 && limitW >= DISCRETE_WEIGHT) {
			out.limitRule = limitRes.limit();
			out.limitZone = zones[limitIdx];
		} else {
			out.limitRule = g.limit();
		}
		out.limit = out.limitRule == null ? null : out.limitRule.limit();

		if (cueIdx >= 0 && cueW >= DISCRETE_WEIGHT) {
			out.cueRule = cueRes.cue();
			out.cueZone = zones[cueIdx];
			out.cueOn = cueRes.cueOn();
		} else {
			out.cueRule = g.cue();
			out.cueOn = g.cueOn();
		}
	}

	/**
	 * Whether any zone could set this sound's loudness while it plays (FR-8.8): an enabled zone with a rule that sets
	 * mute or volume for {@code soundId}, that is either a personal bubble or a fixed zone in this world and dimension.
	 * A sound muted at start must still get a channel if so, so the per-tick loop can make it audible when the source
	 * or the player moves (matrix case P12). Allocates nothing once each sound ID has been seen.
	 */
	public boolean anyZoneCouldSetLoudness(String soundId, String worldKey, String dimension) {
		for (int i = 0; i < zones.length; i++) {
			Zone zone = zones[i];
			if (!zone.enabled()) {
				continue;
			}
			if (!zone.isBubble() && (!worldKey.equals(zone.worldKey()) || !dimension.equals(zone.dimension()))) {
				continue;
			}
			if (zoneRules[i].resolve(soundId).loudness() != null) {
				return true;
			}
		}
		return false;
	}

	/** Cache hits across the global list and every zone's list (FR-13.3). */
	public long cacheHits() {
		// A row hit answers the global list and every zone's list at once.
		long total = global.cacheHits() + rowHits * (zones.length + 1);
		for (EntryResolver r : zoneRules) {
			total += r.cacheHits();
		}
		return total;
	}

	/** Cache misses across the global list and every zone's list. */
	public long cacheMisses() {
		long total = global.cacheMisses();
		for (EntryResolver r : zoneRules) {
			total += r.cacheMisses();
		}
		return total;
	}

	/**
	 * Estimated bytes of the global list's and every zone's per-ID caches, the per-ID rows and the flat zone arrays, for
	 * the Diagnostics memory readout (PRD 9.1).
	 * The rules and zones belong to the profile and are not counted. Walks the caches: call it through
	 * {@code MemoryMeter}, not per sound.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(5 * MemoryEstimate.REFERENCE)
				+ MemoryEstimate.array(MemoryEstimate.REFERENCE, zoneRules.length) + MemoryEstimate.array(8, volumes.length)
				+ 6 * MemoryEstimate.array(8, ax.length) + 4 * MemoryEstimate.array(1, enabled.length)
				+ MemoryEstimate.CONCURRENT_HASH_MAP + MemoryEstimate.hashTable(rows.size())
				+ rows.size() * (MemoryEstimate.HASH_NODE + MemoryEstimate.array(MemoryEstimate.REFERENCE, zones.length + 1))
				+ global.estimatedBytes();
		for (EntryResolver r : zoneRules) {
			bytes += r.estimatedBytes();
		}
		return bytes;
	}

	/** Enabled global rules. */
	public int globalRuleCount() {
		return global.ruleCount();
	}

	/** Zones in the profile, enabled or not. */
	public int zoneCount() {
		return zones.length;
	}

	/** Whether zone {@code i} at {@code dist} beats the current best zone (FR-9 steps 4 and 5). */
	private boolean beats(int i, double dist, int best, double bestDist) {
		return best < 0 || beats(dist, volumes[i], bestDist, volumes[best]);
	}

	/**
	 * The FR-9 tie-break between two zones that both set a property, for a zone visited after the current best:
	 * the closer anchor wins; anchors within 0.01 blocks tie, and then the smaller shape wins. On a full tie the
	 * earlier zone (higher in the list) keeps the win, so this returns false.
	 */
	public static boolean beats(double dist, double volume, double bestDist, double bestVolume) {
		if (isDistanceTie(dist, bestDist)) {
			return volume < bestVolume;
		}
		return dist < bestDist;
	}

	/** Whether two anchor distances count as equal (FR-9 step 5). */
	public static boolean isDistanceTie(double a, double b) {
		return Math.abs(a - b) <= TIE_DISTANCE;
	}

	/** The global list's resolver, for the sound trace (FR-14). */
	public EntryResolver globalRules() {
		return global;
	}

	/** Zones in list order, for the sound trace. */
	public List<Zone> zones() {
		return List.of(zones);
	}

	/** Resolver for zone {@code i}'s own rule list. */
	public EntryResolver zoneRules(int i) {
		return zoneRules[i];
	}

	/** The profile's default edge, applied to zones whose edge is {@link ZoneEdge#DEFAULT}. */
	public ZoneEdge profileEdge() {
		return profileEdge;
	}

	/** Shape volume of zone {@code i}, as used for tie-breaks. */
	public double zoneVolume(int i) {
		return volumes[i];
	}
}
