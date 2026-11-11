package soundsculptor.core.limit;

import soundsculptor.core.diag.MemoryEstimate;

/**
 * Every rule's repeat-limit counters in one fixed-size pool (PRD FR-6.4, FR-6.5 and the 9.1 memory budget): a rule's
 * shared counter, or one counter per block position for a per-source rule. A counter is found by its owner rule and a
 * {@code long} key (the block, or 0 for a shared counter).
 *
 * <p>When a per-source rule already has its {@link RepeatLimiter#MAX_SOURCES_PER_RULE} positions, its own least
 * recently used one is forgotten (FR-6.5). Otherwise, when the pool is full, the least recently used counter of any
 * rule is forgotten. A forgotten counter just starts afresh next time.
 *
 * <p>An open-addressing hash table over packed arrays, so lookups never box a key, and counters are reused, so steady
 * state allocates nothing even when positions keep changing. Entries {@code 0..size-1} are in use; removing one moves
 * the last entry into its place.
 */
final class CounterPool {
	/** What owns counters: one rule's state in {@link RepeatLimiter}. */
	static class Owner {
		/** Mixed into the hash so equal keys of different rules spread out. */
		final long serial;
		/** Counters this owner holds in the pool. */
		int entries;
		/** Marks owners being removed by {@link #removeMarked}. */
		boolean removed;

		Owner(long serial) {
			this.serial = serial;
		}
	}

	private final int capacity;
	private final int mask;
	/** Hash slots holding entry index + 1, or 0 when empty. */
	private final int[] table;
	private final Owner[] owners;
	private final long[] keys;
	private final long[] lastUsed;
	private final SlidingWindow[] windows;
	private int size;
	private long tick;

	CounterPool(int capacity) {
		this.capacity = capacity;
		int tableSize = Integer.highestOneBit(capacity * 2 - 1) << 1;
		this.mask = tableSize - 1;
		this.table = new int[tableSize];
		this.owners = new Owner[capacity];
		this.keys = new long[capacity];
		this.lastUsed = new long[capacity];
		this.windows = new SlidingWindow[capacity];
	}

	/**
	 * The counter for {@code owner} and {@code key}, set to {@code count} per {@code windowMs}; a new or recycled one
	 * if it is not in the pool.
	 *
	 * @param ownerCap most counters this owner may hold
	 */
	SlidingWindow get(Owner owner, long key, int count, long windowMs, int ownerCap) {
		tick++;
		int slot = home(owner, key);
		while (table[slot] != 0) {
			int entry = table[slot] - 1;
			if (owners[entry] == owner && keys[entry] == key) {
				lastUsed[entry] = tick;
				SlidingWindow w = windows[entry];
				w.configure(count, windowMs);
				return w;
			}
			slot = (slot + 1) & mask;
		}
		if (owner.entries >= ownerCap) {
			remove(leastRecentlyUsedOf(owner));
		} else if (size == capacity) {
			remove(leastRecentlyUsed());
		}
		int entry = size++;
		if (windows[entry] == null) {
			windows[entry] = new SlidingWindow();
		}
		owners[entry] = owner;
		keys[entry] = key;
		lastUsed[entry] = tick;
		owner.entries++;
		table[freeSlot(owner, key)] = entry + 1;
		SlidingWindow w = windows[entry];
		w.configure(count, windowMs);
		w.reset();
		return w;
	}

	/** Counters in use, across all owners. */
	int size() {
		return size;
	}

	/** Forgets every counter of {@code owner}. */
	void removeOwner(Owner owner) {
		for (int i = size - 1; i >= 0 && owner.entries > 0; i--) {
			if (owners[i] == owner) {
				remove(i);
			}
		}
	}

	/** Forgets every counter whose owner is marked {@link Owner#removed}, in one pass. */
	void removeMarked() {
		for (int i = size - 1; i >= 0; i--) {
			if (owners[i].removed) {
				remove(i);
			}
		}
	}

	/** The pool and every counter it holds, including recycled ones, for the Diagnostics memory readout. */
	long estimatedBytes() {
		long bytes = MemoryEstimate.object(6 * MemoryEstimate.REFERENCE + 4 * 4 + 8)
				+ MemoryEstimate.array(4, table.length) + 2 * MemoryEstimate.array(8, capacity)
				+ 2 * MemoryEstimate.array(MemoryEstimate.REFERENCE, capacity);
		for (SlidingWindow w : windows) {
			if (w != null) {
				bytes += w.estimatedBytes();
			}
		}
		return bytes;
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

	private int leastRecentlyUsedOf(Owner owner) {
		int best = -1;
		for (int i = 0; i < size; i++) {
			if (owners[i] == owner && (best < 0 || lastUsed[i] < lastUsed[best])) {
				best = i;
			}
		}
		return best;
	}

	/** Removes {@code entry} and moves the last entry into its place, keeping its counter object for reuse. */
	private void remove(int entry) {
		removeSlotOf(entry);
		owners[entry].entries--;
		int last = size - 1;
		if (entry != last) {
			table[slotOf(last)] = entry + 1;
			owners[entry] = owners[last];
			keys[entry] = keys[last];
			lastUsed[entry] = lastUsed[last];
			SlidingWindow freed = windows[entry];
			windows[entry] = windows[last];
			windows[last] = freed;
		}
		owners[last] = null;
		size = last;
	}

	private int slotOf(int entry) {
		int i = home(owners[entry], keys[entry]);
		while (table[i] != entry + 1) {
			i = (i + 1) & mask;
		}
		return i;
	}

	private int freeSlot(Owner owner, long key) {
		int i = home(owner, key);
		while (table[i] != 0) {
			i = (i + 1) & mask;
		}
		return i;
	}

	/** Removes {@code entry}'s hash slot with backward-shift deletion, so lookups never need tombstones. */
	private void removeSlotOf(int entry) {
		int i = slotOf(entry);
		int j = i;
		while (true) {
			j = (j + 1) & mask;
			if (table[j] == 0) {
				break;
			}
			int other = table[j] - 1;
			int k = home(owners[other], keys[other]);
			boolean stays = i <= j ? (i < k && k <= j) : (i < k || k <= j);
			if (!stays) {
				table[i] = table[j];
				i = j;
			}
		}
		table[i] = 0;
	}

	private int home(Owner owner, long key) {
		long h = (key + owner.serial * 0xC2B2AE3D27D4EB4FL) * 0x9E3779B97F4A7C15L;
		return (int) (h >>> 40) & mask;
	}
}
