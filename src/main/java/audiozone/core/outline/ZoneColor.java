package audiozone.core.outline;

/**
 * The default zone colors: the 8 Okabe–Ito colors, chosen to stay distinct for the common forms of color blindness
 * (PRD FR-10.4, A-5). Declared in the order new zones take them. Black is last because it is hard to see at night.
 */
public enum ZoneColor {
	ORANGE(0xE69F00, "orange"),
	SKY_BLUE(0x56B4E9, "sky_blue"),
	BLUISH_GREEN(0x009E73, "bluish_green"),
	YELLOW(0xF0E442, "yellow"),
	BLUE(0x0072B2, "blue"),
	VERMILLION(0xD55E00, "vermillion"),
	REDDISH_PURPLE(0xCC79A7, "reddish_purple"),
	BLACK(0x000000, "black");

	private final int rgb;
	private final String key;

	ZoneColor(int rgb, String key) {
		this.rgb = rgb;
		this.key = key;
	}

	/** Color as {@code 0xRRGGBB}. */
	public int rgb() {
		return rgb;
	}

	/** Name the narrator reads for the swatch ("Sky blue"); key in {@code en_us.json}. */
	public String translationKey() {
		return "audiozone.color." + key;
	}

	/** The palette color with this value, or {@code null} for a custom color. */
	public static ZoneColor of(int rgb) {
		for (ZoneColor c : values()) {
			if (c.rgb == rgb) {
				return c;
			}
		}
		return null;
	}
}
