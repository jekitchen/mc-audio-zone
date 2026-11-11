package soundsculptor.core.zones;

import soundsculptor.core.rules.Limit;
import soundsculptor.core.rules.Rule;

/**
 * The result of {@link Precedence#decide} for one sound: the final value of each property and which rule and zone
 * decided it. A {@code null} zone means the global list decided; a {@code null} rule means no rule set the property.
 *
 * <p>Mutable so the sound hook can reuse one instance on the game thread and allocate nothing per sound (PRD 7.4).
 * The rule and zone references are what the recently played status ("Zone: name") and the sound trace (FR-14) show.
 */
public final class SoundDecision {
	float volumePercent;
	Rule loudnessRule;
	Zone loudnessZone;
	float loudnessWeight;
	Limit limit;
	Rule limitRule;
	Zone limitZone;
	boolean cueOn;
	Rule cueRule;
	Zone cueZone;

	void reset() {
		volumePercent = 100;
		loudnessRule = null;
		loudnessZone = null;
		loudnessWeight = 0;
		limit = null;
		limitRule = null;
		limitZone = null;
		cueOn = false;
		cueRule = null;
		cueZone = null;
	}

	public SoundDecision() {
		reset();
	}

	/** Final volume in percent of vanilla loudness; 0 when muted. */
	public float volumePercent() {
		return volumePercent;
	}

	public Rule loudnessRule() {
		return loudnessRule;
	}

	public Zone loudnessZone() {
		return loudnessZone;
	}

	/** The deciding zone's fade weight for loudness, 0 to 1; 0 when no zone decided (FR-14 trace). */
	public float loudnessWeight() {
		return loudnessWeight;
	}

	/** The repeat limit to apply, or {@code null} for none. */
	public Limit limit() {
		return limit;
	}

	public Rule limitRule() {
		return limitRule;
	}

	public Zone limitZone() {
		return limitZone;
	}

	public boolean cueOn() {
		return cueOn;
	}

	public Rule cueRule() {
		return cueRule;
	}

	public Zone cueZone() {
		return cueZone;
	}
}
