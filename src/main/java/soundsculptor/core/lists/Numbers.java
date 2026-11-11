package soundsculptor.core.lists;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numbers for display text: no trailing zeros, a dot as the decimal mark, and no "-0". */
public final class Numbers {
	private Numbers() {
	}

	/** {@code value} rounded to at most {@code maxDecimals} places: 9.0 → "9", 30.5 → "30.5", 0.25 → "0.25". */
	public static String plain(double value, int maxDecimals) {
		BigDecimal rounded = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP);
		if (rounded.signum() == 0) {
			return "0";
		}
		return rounded.stripTrailingZeros().toPlainString();
	}

	/** At most one decimal place, for block sizes, distances and percentages. */
	public static String plain(double value) {
		return plain(value, 1);
	}
}
