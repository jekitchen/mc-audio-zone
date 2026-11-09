package audiozone.core.ui;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Zone colors (PRD A-5, FR-10): the Okabe–Ito palette, which stays distinguishable for the common forms of color
 * blindness, plus hex parsing and formatting for the color picker's text field. Pure Java.
 */
public final class Palette {
	/** A named color; {@code id} is used for the translation key ("audiozone.color.&lt;id&gt;"). */
	public record Swatch(String id, int rgb) {
	}

	/** Okabe–Ito, in the order the color picker shows them. */
	public static final List<Swatch> OKABE_ITO = List.of(
			new Swatch("orange", 0xE69F00),
			new Swatch("sky_blue", 0x56B4E9),
			new Swatch("bluish_green", 0x009E73),
			new Swatch("yellow", 0xF0E442),
			new Swatch("blue", 0x0072B2),
			new Swatch("vermillion", 0xD55E00),
			new Swatch("reddish_purple", 0xCC79A7),
			new Swatch("black", 0x000000));

	private Palette() {
	}

	/** Parses "#RRGGBB" or "RRGGBB" (any case, surrounding spaces ignored). Empty if it is not a 6-digit hex color. */
	public static OptionalInt parseHex(String text) {
		String s = text.strip();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (s.length() != 6) {
			return OptionalInt.empty();
		}
		for (int i = 0; i < 6; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return OptionalInt.empty();
			}
		}
		return OptionalInt.of(Integer.parseInt(s, 16));
	}

	/** Formats as "#RRGGBB" in upper case. */
	public static String formatHex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	/** The palette swatch with this color, or null if it is a custom color. */
	public static Swatch swatchFor(int rgb) {
		for (Swatch swatch : OKABE_ITO) {
			if (swatch.rgb() == (rgb & 0xFFFFFF)) {
				return swatch;
			}
		}
		return null;
	}
}
