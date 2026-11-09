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
 *   <li>Collect zones in this world and dimension that contain the sound. Non-positional sounds skip zones.</li>
 *   <li>A zone takes part for a property only if one of its rules sets it.</li>
 *   <li>Among those, the zone whose anchor is closest to the sound wins.</li>
 *   <li>Anchors within 0.01 blocks of the same distance tie: the smaller shape wins, then the zone higher in the list.</li>
 *   <li>Volume blends {@code global + (zone - global) × w}; limit and cue use the zone when {@code w ≥ 0.5}.</li>
 *   <li>A property nothing sets keeps vanilla behavior (100%, no limit, cue off).</li>
 * </ol>
 *
 * <p>MVP zones have a hard edge, so {@code w} is always 1 inside. The blend is kept so fade (v1) only has to supply
 * a weight.
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

	public Precedence(List<Rule> globalRules, List<Zone> zones) {
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

	/**
	 * Decides one sound and writes the result into {@code out}.
	 *
	 * @param positional whether the sound has a world position (PRD section 5); zones never apply otherwise
	 * @param worldKey   the current server or world (FR-11.1)
	 * @param dimension  the current dimension, e.g. {@code minecraft:overworld}
	 */
	public void decide(String soundId, boolean positional, String worldKey, String dimension, double x, double y, double z,
			SoundDecision out) {
		out.reset();
		RuleResolution g = global.resolve(soundId);

		int loudIdx = -1;
		int limitIdx = -1;
		int cueIdx = -1;
		double loudDist = 0;
		double limitDist = 0;
		double cueDist = 0;
		RuleResolution loudRes = null;
		RuleResolution limitRes = null;
		RuleResolution cueRes = null;

		if (positional) {
			for (int i = 0; i < zones.length; i++) {
				Zone zone = zones[i];
				if (!zone.enabled() || !worldKey.equals(zone.worldKey()) || !dimension.equals(zone.dimension())) {
					continue;
				}
				Point a = zone.anchor();
				double dx = x - a.x();
				double dy = y - a.y();
				double dz = z - a.z();
				if (!zone.shape().contains(dx, dy, dz)) {
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
				}
				if (r.limit() != null && beats(i, dist, limitIdx, limitDist)) {
					limitIdx = i;
					limitDist = dist;
					limitRes = r;
				}
				if (r.cue() != null && beats(i, dist, cueIdx, cueDist)) {
					cueIdx = i;
					cueDist = dist;
					cueRes = r;
				}
			}
		}

		// Hard edge: every affecting zone has weight 1.
		float w = 1.0F;
		float globalVolume = g.effectiveVolumePercent();
		if (loudIdx >= 0) {
			out.volumePercent = globalVolume + (loudRes.effectiveVolumePercent() - globalVolume) * w;
			out.loudnessRule = loudRes.loudness();
			out.loudnessZone = zones[loudIdx];
		} else {
			out.volumePercent = globalVolume;
			out.loudnessRule = g.loudness();
		}

		if (limitIdx >= 0 && w >= DISCRETE_WEIGHT) {
			out.limitRule = limitRes.limit();
			out.limitZone = zones[limitIdx];
		} else {
			out.limitRule = g.limit();
		}
		out.limit = out.limitRule == null ? null : out.limitRule.limit();

		if (cueIdx >= 0 && w >= DISCRETE_WEIGHT) {
			out.cueRule = cueRes.cue();
			out.cueZone = zones[cueIdx];
			out.cueOn = cueRes.cueOn();
		} else {
			out.cueRule = g.cue();
			out.cueOn = g.cueOn();
		}
	}

	/** Whether zone {@code i} at {@code dist} beats the current best zone (FR-9 steps 4 and 5). */
	private boolean beats(int i, double dist, int best, double bestDist) {
		if (best < 0) {
			return true;
		}
		if (Math.abs(dist - bestDist) <= TIE_DISTANCE) {
			// Zones are visited in list order, so on equal volume the earlier (higher) zone keeps the win.
			return volumes[i] < volumes[best];
		}
		return dist < bestDist;
	}
}
