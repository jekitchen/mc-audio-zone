package soundsculptor.core.zones;

/**
 * A zone's edge (PRD FR-8.5): hard, or a fade over {@code fadeDistance} blocks (0.5 to 32, default 4). Each zone uses
 * the profile's default edge unless set to Hard or Fade itself.
 */
public record ZoneEdge(Mode mode, double fadeDistance) {
	public enum Mode {
		/** Use the profile's default edge. */
		DEFAULT,
		HARD,
		FADE
	}

	public static final double MIN_FADE = 0.5;
	public static final double MAX_FADE = 32;
	public static final double DEFAULT_FADE = 4;

	/** "Use the profile default". */
	public static final ZoneEdge DEFAULT = new ZoneEdge(Mode.DEFAULT, DEFAULT_FADE);

	public ZoneEdge {
		if (mode == null) {
			throw new IllegalArgumentException("edge mode is required");
		}
		if (!(fadeDistance >= MIN_FADE && fadeDistance <= MAX_FADE)) {
			throw new IllegalArgumentException("fade distance " + fadeDistance + " is outside " + MIN_FADE + ".." + MAX_FADE);
		}
	}

	public static ZoneEdge hard() {
		return new ZoneEdge(Mode.HARD, DEFAULT_FADE);
	}

	public static ZoneEdge fade(double distance) {
		return new ZoneEdge(Mode.FADE, distance);
	}
}
