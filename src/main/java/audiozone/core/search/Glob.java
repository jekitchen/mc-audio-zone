package audiozone.core.search;

/** Whole-string match where {@code *} matches any run of characters. No other special characters. */
final class Glob {
	private Glob() {
	}

	static boolean matches(String pattern, String text) {
		// Greedy match with backtracking to the last star; no allocation.
		int p = 0;
		int s = 0;
		int starP = -1;
		int starS = 0;
		while (s < text.length()) {
			if (p < pattern.length() && pattern.charAt(p) == '*') {
				starP = p++;
				starS = s;
			} else if (p < pattern.length() && pattern.charAt(p) == text.charAt(s)) {
				p++;
				s++;
			} else if (starP >= 0) {
				p = starP + 1;
				s = ++starS;
			} else {
				return false;
			}
		}
		while (p < pattern.length() && pattern.charAt(p) == '*') {
			p++;
		}
		return p == pattern.length();
	}
}
