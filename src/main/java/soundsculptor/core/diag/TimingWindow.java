package soundsculptor.core.diag;

/**
 * Live timing for one measured operation (per sound start, per tick re-check, outlines per frame), for the
 * Diagnostics page (PRD FR-13.3, section 9.1).
 *
 * <p>Samples accumulate into the current second; readouts show the last complete second, so the numbers are stable
 * enough to read. {@link #add} allocates nothing. Not thread-safe: measure and read on the same thread.
 */
public final class TimingWindow {
	/** One second of samples. */
	public record Stats(long count, double averageMicros, double maxMicros) {
		public static final Stats EMPTY = new Stats(0, 0, 0);
	}

	private long currentSecond = Long.MIN_VALUE;
	private long currentCount;
	private long currentSumNanos;
	private long currentMaxNanos;

	private long lastSecond = Long.MIN_VALUE;
	private long lastCount;
	private long lastSumNanos;
	private long lastMaxNanos;

	private long peakMaxNanos;

	/**
	 * @param nanos time the operation took, e.g. from {@code System.nanoTime()} differences
	 * @param nowMs current time in milliseconds
	 */
	public void add(long nanos, long nowMs) {
		roll(Math.floorDiv(nowMs, 1000));
		currentCount++;
		currentSumNanos += nanos;
		if (nanos > currentMaxNanos) {
			currentMaxNanos = nanos;
		}
		if (nanos > peakMaxNanos) {
			peakMaxNanos = nanos;
		}
	}

	private void roll(long second) {
		if (second <= currentSecond) {
			return;
		}
		lastSecond = currentSecond;
		lastCount = currentCount;
		lastSumNanos = currentSumNanos;
		lastMaxNanos = currentMaxNanos;
		currentSecond = second;
		currentCount = 0;
		currentSumNanos = 0;
		currentMaxNanos = 0;
	}

	/** Count, average and maximum for the last complete second; zeros if nothing happened in it. */
	public Stats stats(long nowMs) {
		long second = Math.floorDiv(nowMs, 1000);
		roll(second);
		if (lastSecond != second - 1 || lastCount == 0) {
			return Stats.EMPTY;
		}
		return new Stats(lastCount, lastSumNanos / 1000.0 / lastCount, lastMaxNanos / 1000.0);
	}

	/** Largest single sample since the last {@link #resetPeak()}; budgets the worst case (section 9.1). */
	public double peakMaxMicros() {
		return peakMaxNanos / 1000.0;
	}

	public void resetPeak() {
		peakMaxNanos = 0;
	}
}
