package soundsculptor.core.cue;

/**
 * The left/right arrow on a cue line, worked out exactly like vanilla subtitles (PRD FR-7.4): no arrow when the
 * source is clearly in front of the listener, otherwise {@code <} or {@code >} by side. Never a distance or anything
 * finer than vanilla shows.
 *
 * <p>Vanilla's rule (subtitle overlay): with {@code d} the normalised direction from the listener to the sound,
 * {@code forward = d · listenerForward} and {@code right = d · listenerRight}; no arrow if {@code forward > 0.5},
 * else {@code >} if {@code right > 0} and {@code <} if {@code right < 0}. Check against the 26.x subtitle overlay when
 * wiring it up.
 */
public enum CueDirection {
	NONE,
	LEFT,
	RIGHT;

	/** Sounds without a world position never get an arrow. */
	public static final CueDirection NON_POSITIONAL = NONE;

	private static final double IN_FRONT = 0.5;

	public static CueDirection of(double rightDot, double forwardDot) {
		if (forwardDot > IN_FRONT) {
			return NONE;
		}
		if (rightDot > 0) {
			return RIGHT;
		}
		return rightDot < 0 ? LEFT : NONE;
	}
}
