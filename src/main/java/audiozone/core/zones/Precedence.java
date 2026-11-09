package audiozone.core.zones;

import java.util.List;

import audiozone.core.rules.Rule;
import audiozone.core.rules.RuleResolution;
import audiozone.core.rules.RuleResolver;

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

	private static final double TIE_DISTANCE = 0.01;
	private static final float DISCRETE_WEIGHT = 0.5F;

	private final RuleResolver global;
	private final Zone[] zones;
	private final RuleResolver[] zoneRules;
	private final double[] volumes;
	private final ZoneEdge profileEdge;

	/** A profile whose default edge is hard (PRD 7.5). */
	public Precedence(List<Rule> globalRules, List<Zone> zones) {
		this(globalRules, zones, ZoneEdge.hard());
	}

	/**
	 * @param profileEdge the profile's default edge, used by zones whose edge is {@link ZoneEdge#DEFAULT}
	 */
	public Precedence(List<Rule> globalRules, List<Zone> zones, ZoneEdge profileEdge) {
		this.profileEdge = profileEdge.mode() == ZoneEdge.Mode.DEFAULT ? ZoneEdge.hard() : profileEdge;
		if (zones.size() > MAX_ZONES) {
			throw new IllegalArgumentException(zones.size() + " zones is more than the maximum " + MAX_ZONES);
		}
		this.global = new RuleResolver(globalRules);
		this.zones = zones.toArray(new Zone[0]);
		this.zoneRules = new RuleResolver[this.zones.length];
		this.volumes = new double[this.zones.length];
		for (int i = 0; i < this.zones.length; i++) {
			zoneRules[i] = new RuleResolver(this.zones[i].rules());
			volumes[i] = this.zones[i].shape().volume();
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
		out.reset();
		RuleResolution g = global.resolve(soundId);

		int loudIdx = -1;
		int limitIdx = -1;
		int cueIdx = -1;
		double loudDist = 0;
		double limitDist = 0;
		double cueDist = 0;
		double loudW = 0;
		double limitW = 0;
		double cueW = 0;
		RuleResolution loudRes = null;
		RuleResolution limitRes = null;
		RuleResolution cueRes = null;

		if (positional) {
			for (int i = 0; i < zones.length; i++) {
				Zone zone = zones[i];
				if (!zone.enabled()) {
					continue;
				}
				double ax;
				double ay;
				double az;
				if (zone.isBubble()) {
					if (Double.isNaN(px)) {
						continue;
					}
					ax = px;
					ay = py;
					az = pz;
				} else {
					if (!worldKey.equals(zone.worldKey()) || !dimension.equals(zone.dimension())) {
						continue;
					}
					Point a = zone.anchor();
					ax = a.x();
					ay = a.y();
					az = a.z();
				}
				double dx = x - ax;
				double dy = y - ay;
				double dz = z - az;
				double w = zone.weight(dx, dy, dz, profileEdge);
				if (w <= 0) {
					continue;
				}
				RuleResolution r = zoneRules[i].resolve(soundId);
				if (r == RuleResolution.NONE) {
					continue;
				}
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

	/** Cache hits across the global list and every zone's list (FR-13.3). */
	public long cacheHits() {
		long total = global.cacheHits();
		for (RuleResolver r : zoneRules) {
			total += r.cacheHits();
		}
		return total;
	}

	/** Cache misses across the global list and every zone's list. */
	public long cacheMisses() {
		long total = global.cacheMisses();
		for (RuleResolver r : zoneRules) {
			total += r.cacheMisses();
		}
		return total;
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
	public RuleResolver globalRules() {
		return global;
	}

	/** Zones in list order, for the sound trace. */
	public List<Zone> zones() {
		return List.of(zones);
	}

	/** Resolver for zone {@code i}'s own rule list. */
	public RuleResolver zoneRules(int i) {
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
