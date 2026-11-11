package soundsculptor.core.start;

import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;

/**
 * What to do with one sound start, from {@link SoundStartDecider#start}. Mutable and reused, so the hook allocates
 * nothing per sound (PRD 7.4). Valid until the next call.
 */
public final class SoundStartResult {
	boolean handled;
	boolean audible;
	PlayStatus status;
	float volumePercent;
	float factor;
	boolean cancel;
	Zone zone;
	boolean cue;
	boolean cueUsesSoundId;
	SoundDecision decision;

	public SoundStartResult() {
		reset();
	}

	void reset() {
		handled = false;
		audible = true;
		status = PlayStatus.PLAYING;
		volumePercent = 100;
		factor = 1;
		cancel = false;
		zone = null;
		cue = false;
		cueUsesSoundId = false;
		decision = null;
	}

	/**
	 * Whether Sound Sculptor decided this sound. False with Bypass on, for vanilla-covered sounds and before a profile is
	 * loaded: the sound then plays exactly as in vanilla.
	 */
	public boolean handled() {
		return handled;
	}

	/** Whether the listener is in vanilla hearing range (strictly closer); always true for non-positional sounds. */
	public boolean audible() {
		return audible;
	}

	/** The status for the recently played list (FR-1.2). */
	public PlayStatus status() {
		return status;
	}

	/** The decided volume in percent, 0 to 200, as the trace shows it; 0 when muted. */
	public float volumePercent() {
		return volumePercent;
	}

	/**
	 * Multiply vanilla's computed volume by this (FR-5.3): 0 to 2, and at most 1 when boost is not available. 0 when
	 * muted or dropped.
	 */
	public float factor() {
		return factor;
	}

	/**
	 * True when the sound must not start at all: dropped by a repeat limit, or muted where nothing can make it audible
	 * while it plays. A sound muted by a zone, or one a zone could unmute, starts silently instead ({@link #factor} 0)
	 * so the per-tick loop can bring it back (FR-8.8, matrix case P12).
	 */
	public boolean cancel() {
		return cancel;
	}

	/**
	 * The zone that decided, shown as "Zone: name" in the recently played list: the zone whose limit dropped the play,
	 * otherwise the zone that set its loudness. {@code null} when the global list or nothing decided.
	 */
	public Zone zone() {
		return zone;
	}

	/** {@link #zone}'s name, or {@code null}. */
	public String zoneName() {
		return zone == null ? null : zone.name();
	}

	/** Whether to show a muted-sound cue (FR-7.1, FR-7.2, FR-7.7). */
	public boolean cue() {
		return cue;
	}

	/** When {@link #cue} is true: the cue text is the sound ID, because the sound has no subtitle (FR-7.2). */
	public boolean cueUsesSoundId() {
		return cueUsesSoundId;
	}

	/** The full precedence result, which rule and zone decided each property; {@code null} when not handled. */
	public SoundDecision decision() {
		return decision;
	}
}
