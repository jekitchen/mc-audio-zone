package audiozone.core.limit;

import java.util.Arrays;

/**
 * Per-source counters for one rule (PRD FR-6.5): one {@link SlidingWindow} per block position, at most
 * {@link RepeatLimiter#MAX_SOURCES_PER_RULE}, least recently used forgotten first.
 *
 * <p>A small open-addressing hash table with primitive {@code long} keys, so lookups never box a key, and evicted
 * counters are reused, so steady state allocates nothing even when positions keep changing.
 */
final class SourceTable {
	private static final int CAPACITY = RepeatLimiter.MAX_SOURCES_PER_RULE;
	private static final int TABLE_SIZE = Integer.highestOneBit(CAPACITY * 2 - 1) << 1;
	private static final int MASK = TABLE_SIZE - 1;

	/** Hash slots holding entry index + 1, or 0 when empty. */
	private final int[] table = new int[TABLE_SIZE];
	private final long[] keys = new long[CAPACITY];
	private final long[] lastUsed = new long[CAPACITY];
	private final SlidingWindow[] windows = new SlidingWindow[CAPACITY];
	private int size;
	private long tick;

	/** The counter for {@code key}, creating (or recycling) one if needed. */
	SlidingWindow get(long key, int count, long windowMs) {
		tick++;
		int slot = home(key);
		while (table[slot] != 0) {
			int entry = table[slot] - 1;
			if (keys[entry] == key) {
				lastUsed[entry] = tick;
				SlidingWindow w = windows[entry];
				w.configure(count, windowMs);
				return w;
			}
			slot = (slot + 1) & MASK;
		}
		int entry;
		if (size < CAPACITY) {
			entry = size++;
			if (windows[entry] == null) {
				windows[entry] = new SlidingWindow();
			}
		} else {
			entry = leastRecentlyUsed();
			removeSlotOf(entry);
			slot = home(key);
			while (table[slot] != 0) {
				slot = (slot + 1) & MASK;
			}
		}
		keys[entry] = key;
		lastUsed[entry] = tick;
		table[slot] = entry + 1;
		SlidingWindow w = windows[entry];
		w.configure(count, windowMs);
		w.reset();
		return w;
	}

	int size() {
		return size;
	}

	void clear() {
		Arrays.fill(table, 0);
		size = 0;
	}

	private int leastRecentlyUsed() {
		int best = 0;
		for (int i = 1; i < size; i++) {
			if (lastUsed[i] < lastUsed[best]) {
				best = i;
			}
		}
		return best;
	}

	/** Removes {@code entry}'s hash slot with backward-shift deletion, so lookups never need tombstones. */
	private void removeSlotOf(int entry) {
		int i = home(keys[entry]);
		while (table[i] != entry + 1) {
			i = (i + 1) & MASK;
		}
		int j = i;
		while (true) {
			j = (j + 1) & MASK;
			if (table[j] == 0) {
				break;
			}
			int k = home(keys[table[j] - 1]);
			boolean stays = i <= j ? (i < k && k <= j) : (i < k || k <= j);
			if (!stays) {
				table[i] = table[j];
				i = j;
			}
		}
		table[i] = 0;
	}

	private static int home(long key) {
		long h = key * 0x9E3779B97F4A7C15L;
		return (int) (h >>> 40) & MASK;
	}
}
