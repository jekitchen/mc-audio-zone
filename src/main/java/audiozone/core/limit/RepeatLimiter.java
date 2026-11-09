package audiozone.core.limit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import audiozone.core.diag.MemoryEstimate;
import audiozone.core.rules.Limit;

/**
 * Repeat limiting (PRD FR-6): caps how often the sounds a rule decides may start.
 *
 * <p>Call {@link #tryStart} once when a sound starts, with the rule that won the limit setting (from
 * {@code SoundDecision.limitRule()}). It returns false when the play should be dropped. Looping and long sounds count
 * once, when they start, and a sound already playing is never cut off (FR-6.6): both are the caller's side. Each rule
 * has its own counters, which only count sounds that rule decided (FR-6.7). Time is real time, not game ticks, so lag
 * does not change the cap.
 *
 * <p><b>Memory caps (PRD 9.1).</b> A per-source rule keeps up to {@value #MAX_SOURCES_PER_RULE} positions (FR-6.5).
 * All rules together keep at most {@value #MAX_COUNTERS} counters, shared and per-source alike, in one
 * {@link CounterPool}; when it is full, the least recently used counter of any rule is forgotten. At most
 * {@value #MAX_RULES} rules are remembered, least recently used forgotten first. Forgetting only means that counter
 * starts afresh, so in the worst case a few more sounds play than the limit allows, never fewer.
 *
 * <p>After the first sight of each rule, {@link #tryStart} allocates nothing. Not thread-safe: call it from the
 * thread that starts sounds.
 */
public final class RepeatLimiter {
	/** Per-source positions remembered per rule (FR-6.5). */
	public static final int MAX_SOURCES_PER_RULE = 1024;
	/** Counters remembered across all rules together: about 1.7 MB when every one counts 100 plays. */
	public static final int MAX_COUNTERS = 2048;
	/** Rules whose counters and dropped counts are remembered. */
	public static final int MAX_RULES = 4096;

	private final Map<String, RuleCounters> rules = new HashMap<>();
	private final CounterPool pool = new CounterPool(MAX_COUNTERS);
	private long serials;
	private long tick;

	/**
	 * @param ruleId the deciding rule's ID (stable across profile saves)
	 * @param limit  that rule's limit
	 * @param x      sound position, used for per-source counting
	 * @param nowMs  real time in milliseconds from a clock that never goes backwards
	 * @return true if the sound may start; false if it is dropped
	 */
	public boolean tryStart(String ruleId, Limit limit, double x, double y, double z, long nowMs) {
		RuleCounters counters = rules.get(ruleId);
		if (counters == null) {
			counters = add(ruleId);
		}
		counters.lastUsed = ++tick;
		if (!limit.equals(counters.limit)) {
			counters.limit = limit;
			pool.removeOwner(counters);
		}
		SlidingWindow window = limit.perSource()
				? pool.get(counters, blockKey(x, y, z), limit.count(), limit.windowMs(), MAX_SOURCES_PER_RULE)
				: pool.get(counters, 0, limit.count(), limit.windowMs(), 1);
		boolean allowed = window.tryStart(nowMs);
		if (!allowed) {
			counters.dropped++;
		}
		return allowed;
	}

	/** A new rule's counters, forgetting the least recently used rule first if {@value #MAX_RULES} are remembered. */
	private RuleCounters add(String ruleId) {
		if (rules.size() >= MAX_RULES) {
			RuleCounters oldest = null;
			for (RuleCounters c : rules.values()) {
				if (oldest == null || c.lastUsed < oldest.lastUsed) {
					oldest = c;
				}
			}
			rules.remove(oldest.ruleId);
			pool.removeOwner(oldest);
		}
		RuleCounters counters = new RuleCounters(ruleId, ++serials);
		rules.put(ruleId, counters);
		return counters;
	}

	/**
	 * The block a position is in, rounded down (FR-6.5), packed into one {@code long} the same way as Minecraft's
	 * {@code BlockPos.asLong}: 26 bits x, 26 bits z, 12 bits y.
	 */
	public static long blockKey(double x, double y, double z) {
		long bx = (long) Math.floor(x);
		long by = (long) Math.floor(y);
		long bz = (long) Math.floor(z);
		return ((bx & 0x3FFFFFFL) << 38) | ((bz & 0x3FFFFFFL) << 12) | (by & 0xFFFL);
	}

	/** Plays this rule has dropped since it was first used, for diagnostics and the "Limited" count (FR-6.8). */
	public long droppedCount(String ruleId) {
		RuleCounters c = rules.get(ruleId);
		return c == null ? 0 : c.dropped;
	}

	/** Positions currently remembered for a per-source rule. */
	public int sourceCount(String ruleId) {
		RuleCounters c = rules.get(ruleId);
		return c == null || c.limit == null || !c.limit.perSource() ? 0 : c.entries;
	}

	/** Counters remembered across all rules, at most {@value #MAX_COUNTERS}. */
	public int counterCount() {
		return pool.size();
	}

	/** Rules that have counters. */
	public Set<String> ruleIds() {
		return Set.copyOf(rules.keySet());
	}

	/**
	 * Estimated bytes of all counters, for the Diagnostics memory readout (PRD 9.1). It walks every rule, so call it on
	 * demand through {@code MemoryMeter}, not per sound. Limits belong to the profile and are not counted.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(2 * MemoryEstimate.REFERENCE + 2 * 8) + MemoryEstimate.HASH_MAP
				+ MemoryEstimate.hashTable(rules.size()) + rules.size() * MemoryEstimate.HASH_NODE + pool.estimatedBytes();
		for (Map.Entry<String, RuleCounters> entry : rules.entrySet()) {
			bytes += MemoryEstimate.string(entry.getKey()) + RuleCounters.BYTES;
		}
		return bytes;
	}

	/** Forgets counters of rules that no longer exist, e.g. after the profile is saved. */
	public void retain(Set<String> ruleIds) {
		boolean any = false;
		for (Iterator<RuleCounters> it = rules.values().iterator(); it.hasNext();) {
			RuleCounters c = it.next();
			if (!ruleIds.contains(c.ruleId)) {
				c.removed = true;
				any = true;
				it.remove();
			}
		}
		if (any) {
			pool.removeMarked();
		}
	}

	private static final class RuleCounters extends CounterPool.Owner {
		static final long BYTES = MemoryEstimate.object(2 * MemoryEstimate.REFERENCE + 3 * 8 + 4 + 1);

		final String ruleId;
		Limit limit;
		long dropped;
		long lastUsed;

		RuleCounters(String ruleId, long serial) {
			super(serial);
			this.ruleId = ruleId;
		}
	}
}
