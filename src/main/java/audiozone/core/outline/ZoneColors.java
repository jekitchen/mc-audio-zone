package audiozone.core.outline;

import java.util.Collection;
import java.util.Locale;

/** Color choices for zones (PRD FR-10.4): the next default color, and the color picker's hex field. */
public final class ZoneColors {
	/** Result of reading the hex field: a color, or why it was refused. */
	public record Parsed(int rgb, ColorError error) {
	}

	private ZoneColors() {
	}

	/**
	 * The color for a new zone: the first palette color no zone uses yet; when every color is taken, the least used
	 * one, earliest in the palette. Custom colors do not take up a palette slot.
	 *
	 * @param usedRgb colors of the profile's existing zones, as {@code 0xRRGGBB}
	 */
	public static int next(Collection<Integer> usedRgb) {
		ZoneColor[] palette = ZoneColor.values();
		int[] uses = new int[palette.length];
		for (int rgb : usedRgb) {
			ZoneColor c = ZoneColor.of(rgb);
			if (c != null) {
				uses[c.ordinal()]++;
			}
		}
		int best = 0;
		for (int i = 1; i < palette.length; i++) {
			if (uses[i] < uses[best]) {
				best = i;
			}
		}
		return palette[best].rgb();
	}

	/** Reads the hex field: six hex digits, with or without {@code #}, any case, surrounding spaces ignored. */
	public static Parsed parseHex(String text) {
		String s = text.trim();
		if (s.isEmpty()) {
			return new Parsed(0, ColorError.EMPTY);
		}
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (s.length() != 6) {
			return new Parsed(0, ColorError.INVALID_HEX);
		}
		for (int i = 0; i < 6; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return new Parsed(0, ColorError.INVALID_HEX);
			}
		}
		return new Parsed(Integer.parseInt(s, 16), null);
	}

	/** {@code #RRGGBB}, uppercase, as shown in the hex field and saved in the profile. */
	public static String format(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}
}
