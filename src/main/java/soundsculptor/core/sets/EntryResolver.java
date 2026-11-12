package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import soundsculptor.core.diag.MemoryEstimate;

/**
 * Resolves one rule list (the global list, or one zone's list) for a sound ID, per property (PRD FR-4.5).
 *
 * <p>For each property, the most specific enabled rule that sets it wins:
 * <ol>
 *   <li>an exact ID beats any pattern,</li>
 *   <li>more literal (non-{@code *}) characters beat fewer,</li>
 *   <li>fewer {@code *} beat more,</li>
 *   <li>a rule higher in the list beats a lower one.</li>
 * </ol>
 *
 * <p>A resolver is an immutable snapshot of the list: build a new one when the player saves (PRD 7.4). Results
 * are cached per sound ID, so after the first play of an ID a lookup is one map read with no allocation.
 */
public final class EntryResolver {
	/** Cache size cap. Servers can send arbitrary sound IDs, so the cache must not grow without bound. */
	public static final int MAX_CACHED_IDS = 4096;
	/**
	 * Distinct results (combinations of winning rules) kept per list. Every cached ID points at one of these, so a
	 * lookup the cache cannot hold allocates nothing once its combination has been seen. Past this cap, a result is
	 * neither kept nor cached.
	 */
	static final int MAX_RESULTS = 128;
	private static final long RESOLUTION_BYTES = MemoryEstimate.object(3 * MemoryEstimate.REFERENCE);
	private static final int NO_RULE = 0x1FFFFF;

	private static final Comparator<SetEntry> MOST_SPECIFIC_FIRST = Comparator
			.comparing((SetEntry r) -> !r.pattern().isExact())
			.thenComparingInt(r -> -r.pattern().literalCount())
			.thenComparingInt(r -> r.pattern().starCount());

	/** Enabled rules, most specific first. The sort is stable, so equal rules keep list order (step 4). */
	private final SetEntry[] ordered;
	private final Map<String, EntryResolution> cache = new ConcurrentHashMap<>();
	/** Shared with other resolvers, or null for only this resolver's own cap. */
	private final CacheBudget budget;
	// Diagnostics counters (FR-13.3). Plain fields: sounds are resolved on one thread, and a readout that is off by
	// a few counts would not matter.
	private long hits;
	private long misses;
	/** Results by their winners' positions in {@link #ordered}, packed by {@link #resultKey}; empty until needed. */
	private long[] resultKeys = new long[0];
	private EntryResolution[] results = new EntryResolution[0];
	private int resultCount;

	public EntryResolver(List<SetEntry> rules) {
		this(rules, null);
	}

	/**
	 * @param budget a cap shared with other resolvers (PRD 9.1), or null for only {@value #MAX_CACHED_IDS} IDs here
	 */
	public EntryResolver(List<SetEntry> rules, CacheBudget budget) {
		this.budget = budget;
		List<SetEntry> enabled = new ArrayList<>(rules.size());
		for (SetEntry rule : rules) {
			if (rule.enabled()) {
				enabled.add(rule);
			}
		}
		enabled.sort(MOST_SPECIFIC_FIRST);
		this.ordered = enabled.toArray(new SetEntry[0]);
	}

	/** The winning rule per property for {@code soundId}. */
	public EntryResolution resolve(String soundId) {
		if (ordered.length == 0) {
			hits++; // nothing to match, so nothing worth caching
			return EntryResolution.NONE;
		}
		EntryResolution cached = cache.get(soundId);
		if (cached != null) {
			hits++;
			return cached;
		}
		misses++;
		EntryResolution result = compute(soundId);
		boolean shared = result == EntryResolution.NONE || result == findResult(result);
		if (shared && cache.size() < MAX_CACHED_IDS && (budget == null || budget.take())) {
			cache.put(soundId, result);
		}
		return result;
	}

	private EntryResolution compute(String soundId) {
		int loudness = NO_RULE;
		int limit = NO_RULE;
		int cue = NO_RULE;
		for (int i = 0; i < ordered.length; i++) {
			SetEntry rule = ordered[i];
			if (!rule.pattern().matches(soundId)) {
				continue;
			}
			if (loudness == NO_RULE && rule.setsLoudness()) {
				loudness = i;
			}
			if (limit == NO_RULE && rule.setsLimit()) {
				limit = i;
			}
			if (cue == NO_RULE && rule.setsCue()) {
				cue = i;
			}
			if (loudness != NO_RULE && limit != NO_RULE && cue != NO_RULE) {
				break;
			}
		}
		if (loudness == NO_RULE && limit == NO_RULE && cue == NO_RULE) {
			return EntryResolution.NONE;
		}
		return result(resultKey(loudness, limit, cue));
	}

	/** The kept result for these winners, keeping a new one if there is room; a new unkept one if not. */
	private synchronized EntryResolution result(long key) {
		for (int i = 0; i < resultCount; i++) {
			if (resultKeys[i] == key) {
				return results[i];
			}
		}
		EntryResolution r = new EntryResolution(rule(key, 42), rule(key, 21), rule(key, 0));
		if (resultCount < MAX_RESULTS) {
			if (resultCount == results.length) {
				int grown = Math.min(MAX_RESULTS, Math.max(4, resultCount * 2));
				resultKeys = Arrays.copyOf(resultKeys, grown);
				results = Arrays.copyOf(results, grown);
			}
			resultKeys[resultCount] = key;
			results[resultCount] = r;
			resultCount++;
		}
		return r;
	}

	/** {@code r} itself if it is a kept result, otherwise null. */
	private synchronized EntryResolution findResult(EntryResolution r) {
		for (int i = 0; i < resultCount; i++) {
			if (results[i] == r) {
				return r;
			}
		}
		return null;
	}

	private static long resultKey(int loudness, int limit, int cue) {
		return ((long) loudness << 42) | ((long) limit << 21) | cue;
	}

	private SetEntry rule(long key, int shift) {
		int i = (int) (key >>> shift) & NO_RULE;
		return i == NO_RULE ? null : ordered[i];
	}

	/** Enabled rules, most specific first: the order winners are picked in. For the sound trace (FR-14). */
	public List<SetEntry> rulesInOrder() {
		return List.of(ordered);
	}

	/**
	 * Rules that take over at least one of {@code rule}'s properties for at least one of {@code knownIds}, for the
	 * rule editor's "Partly overridden by" line (FR-4.6). Not for the hot path.
	 */
	public Set<SetEntry> overriddenBy(SetEntry rule, Iterable<String> knownIds) {
		Set<SetEntry> winners = new LinkedHashSet<>();
		if (!rule.enabled()) {
			return winners;
		}
		for (String id : knownIds) {
			if (!rule.pattern().matches(id)) {
				continue;
			}
			EntryResolution res = resolve(id);
			addIfOther(winners, rule, rule.setsLoudness(), res.loudness());
			addIfOther(winners, rule, rule.setsLimit(), res.limit());
			addIfOther(winners, rule, rule.setsCue(), res.cue());
		}
		return winners;
	}

	private static void addIfOther(Set<SetEntry> winners, SetEntry rule, boolean sets, SetEntry winner) {
		if (sets && winner != null && winner != rule) {
			winners.add(winner);
		}
	}

	/** Number of sound IDs currently cached (for diagnostics, FR-13.3). */
	public int cachedIdCount() {
		return cache.size();
	}

	/**
	 * Estimated bytes of the per-ID cache, for the Diagnostics memory readout (PRD 9.1). The rules themselves belong
	 * to the profile and are not counted. It walks the cache, so call it on demand through {@code MemoryMeter}.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(4 * MemoryEstimate.REFERENCE + 2 * 8 + 4) + MemoryEstimate.CONCURRENT_HASH_MAP
				+ MemoryEstimate.hashTable(cache.size()) + cache.size() * MemoryEstimate.HASH_NODE;
		for (String id : cache.keySet()) {
			bytes += MemoryEstimate.string(id);
		}
		if (results.length > 0) {
			bytes += MemoryEstimate.array(8, results.length) + MemoryEstimate.array(MemoryEstimate.REFERENCE, results.length)
					+ resultCount * RESOLUTION_BYTES;
		}
		return bytes;
	}

	/** Lookups answered from the cache (FR-13.3 hit rate). */
	public long cacheHits() {
		return hits;
	}

	/** Lookups that had to match patterns. */
	public long cacheMisses() {
		return misses;
	}

	/** Enabled rules in this list ("rules active", FR-13.3). */
	public int ruleCount() {
		return ordered.length;
	}
}
