package audiozone.core.limit;

import java.util.HashMap;
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
 * <p>After the first sight of each rule and position, {@link #tryStart} allocates nothing. Not thread-safe: call it
 * from the thread that starts sounds.
 */
public final class RepeatLimiter {
	/** Per-source positions remembered per rule (FR-6.5). */
	public static final int MAX_SOURCES_PER_RULE = 1024;

	private final Map<String, RuleCounters> rules = new HashMap<>();

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
			counters = new RuleCounters();
			rules.put(ruleId, counters);
		}
		if (!limit.equals(counters.limit)) {
			counters.limit = limit;
			counters.shared.reset();
			if (counters.sources != null) {
				counters.sources.clear();
			}
		}
		SlidingWindow window;
		if (limit.perSource()) {
			if (counters.sources == null) {
				counters.sources = new SourceTable();
			}
			window = counters.sources.get(blockKey(x, y, z), limit.count(), limit.windowMs());
		} else {
			window = counters.shared;
			window.configure(limit.count(), limit.windowMs());
		}
		boolean allowed = window.tryStart(nowMs);
		if (!allowed) {
			counters.dropped++;
		}
		return allowed;
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
		return c == null || c.sources == null ? 0 : c.sources.size();
	}

	/** Rules that have counters. */
	public Set<String> ruleIds() {
		return Set.copyOf(rules.keySet());
	}

	/**
	 * Estimated bytes of all counters, for the Diagnostics memory readout (PRD 9.1). It walks every rule's counters,
	 * so call it on demand through {@code MemoryMeter}, not per sound. Limits belong to the profile and are not counted.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(MemoryEstimate.REFERENCE) + MemoryEstimate.HASH_MAP
				+ MemoryEstimate.hashTable(rules.size()) + rules.size() * MemoryEstimate.HASH_NODE;
		for (Map.Entry<String, RuleCounters> entry : rules.entrySet()) {
			RuleCounters c = entry.getValue();
			bytes += MemoryEstimate.string(entry.getKey()) + RuleCounters.BYTES + c.shared.estimatedBytes();
			if (c.sources != null) {
				bytes += c.sources.estimatedBytes();
			}
		}
		return bytes;
	}

	/** Forgets counters of rules that no longer exist, e.g. after the profile is saved. */
	public void retain(Set<String> ruleIds) {
		rules.keySet().retainAll(ruleIds);
	}

	private static final class RuleCounters {
		static final long BYTES = MemoryEstimate.object(3 * MemoryEstimate.REFERENCE + 8);

		Limit limit;
		final SlidingWindow shared = new SlidingWindow();
		SourceTable sources;
		long dropped;
	}
}
