package audiozone.core;

import java.lang.management.ManagementFactory;
import java.util.function.IntConsumer;

/**
 * Measures heap allocation on the current thread, for the "no allocation on the hot path" tests (PRD 7.4, 9.1).
 *
 * <p>A single measurement can pick up a few stray bytes when the JIT recompiles the code under test mid-loop. So the
 * work is measured in several rounds and the lowest count is returned: code that allocates on every call still
 * shows it in every round, while a one-off blip does not.
 */
public final class Allocations {
	private static final int ROUNDS = 5;

	private Allocations() {
	}

	/**
	 * Runs {@code work} {@value #ROUNDS} times, passing the round number (0, 1, 2...), and returns the fewest bytes
	 * allocated in any one round. Warm the code up before calling this.
	 */
	public static long fewestBytes(IntConsumer work) {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long fewest = Long.MAX_VALUE;
		for (int round = 0; round < ROUNDS; round++) {
			long before = threads.getCurrentThreadAllocatedBytes();
			work.accept(round);
			fewest = Math.min(fewest, threads.getCurrentThreadAllocatedBytes() - before);
		}
		return fewest;
	}
}
