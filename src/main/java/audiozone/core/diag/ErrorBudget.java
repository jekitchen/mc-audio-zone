package audiozone.core.diag;

import java.util.HashSet;
import java.util.Set;

/**
 * Fail-open bookkeeping (PRD FR-13.2). When handling a sound throws, the sound plays as vanilla, the error is logged
 * once ({@link #firstTime}), and it is counted here ({@link #recordError}). More than {@link #MAX_ERRORS_PER_MINUTE}
 * errors within a minute trips the budget once: the caller turns on bypass and asks the player to report the bug.
 *
 * <p>Errors are rare, so this class is simple rather than allocation-free. Not thread-safe.
 */
public final class ErrorBudget {
	public static final int MAX_ERRORS_PER_MINUTE = 10;
	public static final long WINDOW_MS = 60_000;
	/** Distinct errors remembered for log-once; past this nothing new is logged. */
	static final int MAX_DISTINCT_ERRORS = 256;

	/** Times of the most recent errors, as a ring holding one more than the allowed count. */
	private final long[] times = new long[MAX_ERRORS_PER_MINUTE + 1];
	private final Set<String> logged = new HashSet<>();
	private int next;
	private int stored;
	private long total;
	private boolean tripped;

	/**
	 * Whether an error with this key (e.g. exception type plus location) should be written to the log. True only the
	 * first time each key is seen.
	 */
	public boolean firstTime(String key) {
		if (logged.size() >= MAX_DISTINCT_ERRORS) {
			return false;
		}
		return logged.add(key);
	}

	/**
	 * Counts one error.
	 *
	 * @return true exactly once, for the error that pushes the count past the limit; the caller then turns on bypass
	 */
	public boolean recordError(long nowMs) {
		total++;
		if (tripped) {
			return false;
		}
		times[next] = nowMs;
		next = (next + 1) % times.length;
		if (stored < times.length) {
			stored++;
		}
		// When the ring is full, "next" points at the oldest of the last MAX + 1 errors.
		if (stored == times.length && nowMs - times[next] < WINDOW_MS) {
			tripped = true;
			return true;
		}
		return false;
	}

	public boolean isTripped() {
		return tripped;
	}

	/** Errors counted this session, for the diagnostics report. */
	public long totalErrors() {
		return total;
	}

	/** Starts a fresh budget, e.g. after the player turns bypass back off. Log-once memory is kept. */
	public void reset() {
		tripped = false;
		stored = 0;
		next = 0;
	}
}
