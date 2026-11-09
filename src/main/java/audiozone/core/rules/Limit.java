package audiozone.core.rules;

/**
 * A rule's repeat limit (PRD FR-6.1): at most {@code count} plays per {@code windowMs}, counted per rule or, with
 * {@code perSource}, per block the sound starts in. The limiter itself is a separate module (issue #9).
 */
public record Limit(int count, int windowMs, boolean perSource) {
	public static final int MIN_COUNT = 1;
	public static final int MAX_COUNT = 100;
	public static final int MIN_WINDOW_MS = 50;
	public static final int MAX_WINDOW_MS = 10_000;

	public Limit {
		if (count < MIN_COUNT || count > MAX_COUNT) {
			throw new IllegalArgumentException("limit count " + count + " is outside " + MIN_COUNT + ".." + MAX_COUNT);
		}
		if (windowMs < MIN_WINDOW_MS || windowMs > MAX_WINDOW_MS) {
			throw new IllegalArgumentException("limit window " + windowMs + " ms is outside " + MIN_WINDOW_MS + ".." + MAX_WINDOW_MS);
		}
	}
}
