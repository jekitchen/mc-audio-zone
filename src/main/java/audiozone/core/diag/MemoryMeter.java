package audiozone.core.diag;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The Diagnostics memory readout (PRD 9.1, 11.2): Audio Zone's main data structures, each with a size estimate, and
 * their total against {@link MemoryEstimate#BUDGET_BYTES}.
 *
 * <p>Estimating walks the structures, so the meter recomputes at most once every {@value #REFRESH_MS} ms and returns
 * the stored numbers in between. Reading between refreshes allocates nothing, so a screen may read it every frame.
 * Not thread-safe: register and read on the render thread, where the structures are updated.
 */
public final class MemoryMeter {
	public static final long REFRESH_MS = 1000;
	/**
	 * The Diagnostics line: {@code %1$s} is {@link MemoryEstimate#megabytes} of {@link #totalBytes}, {@code %2$s} of
	 * {@link MemoryEstimate#BUDGET_BYTES}.
	 */
	public static final String READOUT_KEY = "audiozone.diag.memory";

	private String[] names = new String[0];
	private LongSupplier[] estimates = new LongSupplier[0];
	private long[] bytes = new long[0];
	private long total;
	private long refreshedAt;
	private boolean stale = true;

	/** Adds a structure, e.g. {@code add("Recently played", recent::estimatedBytes)}. */
	public void add(String name, LongSupplier estimate) {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(estimate, "estimate");
		int n = names.length;
		names = Arrays.copyOf(names, n + 1);
		estimates = Arrays.copyOf(estimates, n + 1);
		bytes = Arrays.copyOf(bytes, n + 1);
		names[n] = name;
		estimates[n] = estimate;
		stale = true;
	}

	public int partCount() {
		return names.length;
	}

	public String name(int part) {
		return names[part];
	}

	/** One structure's estimated bytes. */
	public long bytes(int part, long nowMs) {
		refresh(nowMs);
		return bytes[part];
	}

	/** All structures' estimated bytes together. */
	public long totalBytes(long nowMs) {
		refresh(nowMs);
		return total;
	}

	/** Whether the total is over the 8 MB budget. */
	public boolean overBudget(long nowMs) {
		return totalBytes(nowMs) > MemoryEstimate.BUDGET_BYTES;
	}

	/**
	 * @param nowMs time from a clock that never goes backwards, such as Minecraft's {@code Util.getMillis()}
	 */
	private void refresh(long nowMs) {
		if (!stale && nowMs - refreshedAt < REFRESH_MS) {
			return;
		}
		long sum = 0;
		for (int i = 0; i < estimates.length; i++) {
			bytes[i] = estimates[i].getAsLong();
			sum += bytes[i];
		}
		total = sum;
		refreshedAt = nowMs;
		stale = false;
	}
}
