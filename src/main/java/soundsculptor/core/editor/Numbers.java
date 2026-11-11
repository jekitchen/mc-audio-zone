package soundsculptor.core.editor;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/** Reading and writing the numbers typed into editor fields. Plain decimal notation only, with a {@code .}. */
final class Numbers {
	private static final Pattern WHOLE = Pattern.compile("[-+]?\\d+");
	private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)");

	private Numbers() {
	}

	/** Null if {@code text} is a whole number from {@code min} to {@code max}; otherwise why not. */
	static FieldError checkWhole(String text, long min, long max) {
		String s = text.trim();
		if (s.isEmpty()) {
			return FieldError.of(FieldError.REQUIRED);
		}
		if (!WHOLE.matcher(s).matches()) {
			return FieldError.of(DECIMAL.matcher(s).matches() ? FieldError.NOT_WHOLE_NUMBER : FieldError.NOT_A_NUMBER);
		}
		return range(new BigDecimal(s), BigDecimal.valueOf(min), BigDecimal.valueOf(max));
	}

	/** Null if {@code text} is a number from {@code min} to {@code max}; otherwise why not. */
	static FieldError checkDecimal(String text, double min, double max) {
		FieldError error = checkNumber(text);
		return error != null ? error : range(new BigDecimal(text.trim()), BigDecimal.valueOf(min), BigDecimal.valueOf(max));
	}

	/** Null if {@code text} is any number. */
	static FieldError checkNumber(String text) {
		String s = text.trim();
		if (s.isEmpty()) {
			return FieldError.of(FieldError.REQUIRED);
		}
		return DECIMAL.matcher(s).matches() ? null : FieldError.of(FieldError.NOT_A_NUMBER);
	}

	private static FieldError range(BigDecimal value, BigDecimal min, BigDecimal max) {
		if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
			return new FieldError(FieldError.OUT_OF_RANGE, List.of(format(min), format(max)));
		}
		return null;
	}

	static boolean isNumber(String text) {
		return DECIMAL.matcher(text.trim()).matches();
	}

	/** Two texts that are the same number ("10", "10.0", "010"), or the same text. */
	static boolean same(String a, String b) {
		if (isNumber(a) && isNumber(b)) {
			return new BigDecimal(a.trim()).compareTo(new BigDecimal(b.trim())) == 0;
		}
		return a.trim().equals(b.trim());
	}

	static int whole(String text) {
		return Integer.parseInt(text.trim());
	}

	static double decimal(String text) {
		return Double.parseDouble(text.trim());
	}

	/** The shortest text that reads back as the same number: 10.0 is "10", 0.5 is "0.5". */
	static String format(double value) {
		return format(BigDecimal.valueOf(value));
	}

	private static String format(BigDecimal value) {
		String s = value.stripTrailingZeros().toPlainString();
		return s.equals("-0") ? "0" : s;
	}
}
