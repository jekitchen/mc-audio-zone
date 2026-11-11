package soundsculptor.core.recent;

/**
 * How far away a sound was, as a coarse band (PRD FR-1.4). Never a number, direction or position: the band must
 * stay coarser than what the player already hears (fair play, section 8.2).
 */
public enum DistanceBand {
	/** 4 blocks or less. */
	CLOSE("close"),
	/** More than 4, up to 16 blocks. */
	NEAR("near"),
	/** More than 16 blocks; only loud sounds reach this far. */
	FAR("far"),
	/** Non-positional sounds (the player's own sounds, most relative sounds). */
	AROUND_YOU("around_you");

	public static final double CLOSE_MAX = 4.0;
	public static final double NEAR_MAX = 16.0;

	private final String key;

	DistanceBand(String key) {
		this.key = key;
	}

	public static DistanceBand of(boolean positional, double distance) {
		if (!positional) {
			return AROUND_YOU;
		}
		if (distance <= CLOSE_MAX) {
			return CLOSE;
		}
		return distance <= NEAR_MAX ? NEAR : FAR;
	}

	/** Key in {@code assets/soundsculptor/lang/en_us.json}. */
	public String translationKey() {
		return "soundsculptor.recent.distance." + key;
	}
}
