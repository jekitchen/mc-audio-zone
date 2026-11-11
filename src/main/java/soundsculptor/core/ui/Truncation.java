package soundsculptor.core.ui;

import java.util.function.ToIntFunction;

/** Shortens text to a pixel width with an ellipsis (PRD 6.2, Row: "text columns with truncation"). */
public final class Truncation {
	public static final String ELLIPSIS = "…";

	private Truncation() {
	}

	/**
	 * @param width measures text in pixels (the game font in production, a stub in tests)
	 * @return {@code text} unchanged if it fits, otherwise the longest prefix that fits with "…" appended, or ""
	 *         if not even the ellipsis fits
	 */
	public static String truncate(String text, int maxWidth, ToIntFunction<String> width) {
		if (width.applyAsInt(text) <= maxWidth) {
			return text;
		}
		int ellipsisWidth = width.applyAsInt(ELLIPSIS);
		if (ellipsisWidth > maxWidth) {
			return "";
		}
		int lo = 0;
		int hi = text.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (width.applyAsInt(text.substring(0, mid)) + ellipsisWidth <= maxWidth) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		// Never split a surrogate pair.
		if (lo > 0 && Character.isHighSurrogate(text.charAt(lo - 1))) {
			lo--;
		}
		return text.substring(0, lo).stripTrailing() + ELLIPSIS;
	}

	public static boolean isTruncated(String original, String shown) {
		return !original.equals(shown);
	}
}
