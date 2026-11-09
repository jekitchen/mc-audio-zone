package audiozone.core.start;

/**
 * What the sound hook knows about one sound start, as input to {@link SoundStartDecider#start}. Mutable, so the hook
 * fills one instance per sound and allocates nothing (PRD 7.4). Call {@link #reset} before filling it.
 */
public final class SoundStart {
	/** The sound's ID, e.g. {@code minecraft:entity.villager.ambient}; reuse one string per ID. */
	public String soundId;
	/** Whether the sound belongs to Music, UI or Voice/Speech, which Audio Zone never changes (section 5, Q8). */
	public boolean vanillaCovered;
	/** Whether the sound has a world position (not relative to the listener); zones apply only then. */
	public boolean positional;
	/** Vanilla hearing range from the sound's own, un-boosted volume ({@code SoundRange.vanillaRange}). */
	public float range;
	/** Squared distance from the listener to the sound; ignored for non-positional sounds. */
	public double distanceSq;
	/** The current server or world (FR-11.1). */
	public String worldKey;
	/** The current dimension, e.g. {@code minecraft:overworld}. */
	public String dimension;
	public double x;
	public double y;
	public double z;
	/** The local player's eye position, the anchor of personal bubbles; {@code NaN} skips bubbles. */
	public double px;
	public double py;
	public double pz;
	/** Whether the sound has a vanilla subtitle, the text of its cue (FR-7.2). */
	public boolean hasSubtitle;
	/** Whether Bypass Audio Zone is on (FR-13.1). */
	public boolean bypass;
	/** Real time in milliseconds, from a clock that never goes backwards; repeat limits count with it (FR-6.2). */
	public long nowMs;

	public SoundStart() {
		reset();
	}

	/** Back to a non-positional sound with no ID, no world and no player position. */
	public void reset() {
		soundId = null;
		vanillaCovered = false;
		positional = false;
		range = Float.POSITIVE_INFINITY;
		distanceSq = 0;
		worldKey = "";
		dimension = "";
		x = 0;
		y = 0;
		z = 0;
		px = Double.NaN;
		py = Double.NaN;
		pz = Double.NaN;
		hasSubtitle = false;
		bypass = false;
		nowMs = 0;
	}
}
