package audiozone.core.limit;

/**
 * One repeat-limit counter (PRD FR-6.2, FR-6.3): the start times of the last {@code count} allowed plays. A new play
 * is allowed if fewer than {@code count} allowed plays started in the last {@code windowMs}; dropped plays are not
 * recorded, so a steady stream settles at exactly {@code count} per window instead of going silent.
 */
final class SlidingWindow {
	private long[] times = new long[0];
	private int count;
	private long windowMs;
	private int size;
	private int head;

	/** Sets the limit; clears the counter only if it actually changed. Allocates only when the count grows. */
	void configure(int count, long windowMs) {
		if (count == this.count && windowMs == this.windowMs) {
			return;
		}
		if (times.length < count) {
			times = new long[count];
		}
		this.count = count;
		this.windowMs = windowMs;
		reset();
	}

	void reset() {
		size = 0;
		head = 0;
	}

	boolean tryStart(long nowMs) {
		if (size < count) {
			times[(head + size) % count] = nowMs;
			size++;
			return true;
		}
		if (nowMs - times[head] >= windowMs) {
			times[head] = nowMs;
			head = (head + 1) % count;
			return true;
		}
		return false;
	}
}
