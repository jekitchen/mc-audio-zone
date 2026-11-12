package soundsculptor.core.sets;

/**
 * A cap on cached sound IDs shared by several {@link EntryResolver}s, such as all of a profile's zones (PRD 9.1 memory
 * budget). Each resolver also keeps its own cap; this one bounds them together. Once used up, resolvers still answer
 * correctly but match patterns again for IDs they did not cache.
 *
 * <p>Not thread-safe, like the resolvers' diagnostics counters: sounds are resolved on one thread, and a cap off by
 * a few IDs would not matter.
 */
public final class CacheBudget {
	private int remaining;

	public CacheBudget(int ids) {
		if (ids < 0) {
			throw new IllegalArgumentException("cache budget " + ids + " is negative");
		}
		this.remaining = ids;
	}

	/** Takes room for one more cached ID; false when the budget is used up. */
	boolean take() {
		if (remaining <= 0) {
			return false;
		}
		remaining--;
		return true;
	}

	/** IDs that may still be cached. */
	public int remaining() {
		return remaining;
	}
}
