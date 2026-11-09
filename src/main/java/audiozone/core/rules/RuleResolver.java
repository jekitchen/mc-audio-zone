package audiozone.core.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import audiozone.core.diag.MemoryEstimate;

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
public final class RuleResolver {
	/** Cache size cap. Servers can send arbitrary sound IDs, so the cache must not grow without bound. */
	static final int MAX_CACHED_IDS = 4096;
	private static final long RESOLUTION_BYTES = MemoryEstimate.object(3 * MemoryEstimate.REFERENCE);

	private static final Comparator<Rule> MOST_SPECIFIC_FIRST = Comparator
			.comparing((Rule r) -> !r.pattern().isExact())
			.thenComparingInt(r -> -r.pattern().literalCount())
			.thenComparingInt(r -> r.pattern().starCount());

	/** Enabled rules, most specific first. The sort is stable, so equal rules keep list order (step 4). */
	private final Rule[] ordered;
	private final Map<String, RuleResolution> cache = new ConcurrentHashMap<>();
	// Diagnostics counters (FR-13.3). Plain fields: sounds are resolved on one thread, and a readout that is off by
	// a few counts would not matter.
	private long hits;
	private long misses;

	public RuleResolver(List<Rule> rules) {
		List<Rule> enabled = new ArrayList<>(rules.size());
		for (Rule rule : rules) {
			if (rule.enabled()) {
				enabled.add(rule);
			}
		}
		enabled.sort(MOST_SPECIFIC_FIRST);
		this.ordered = enabled.toArray(new Rule[0]);
	}

	/** The winning rule per property for {@code soundId}. */
	public RuleResolution resolve(String soundId) {
		RuleResolution cached = cache.get(soundId);
		if (cached != null) {
			hits++;
			return cached;
		}
		misses++;
		RuleResolution result = compute(soundId);
		if (cache.size() < MAX_CACHED_IDS) {
			cache.put(soundId, result);
		}
		return result;
	}

	private RuleResolution compute(String soundId) {
		Rule loudness = null;
		Rule limit = null;
		Rule cue = null;
		for (Rule rule : ordered) {
			if (!rule.pattern().matches(soundId)) {
				continue;
			}
			if (loudness == null && rule.setsLoudness()) {
				loudness = rule;
			}
			if (limit == null && rule.setsLimit()) {
				limit = rule;
			}
			if (cue == null && rule.setsCue()) {
				cue = rule;
			}
			if (loudness != null && limit != null && cue != null) {
				break;
			}
		}
		if (loudness == null && limit == null && cue == null) {
			return RuleResolution.NONE;
		}
		return new RuleResolution(loudness, limit, cue);
	}

	/** Enabled rules, most specific first: the order winners are picked in. For the sound trace (FR-14). */
	public List<Rule> rulesInOrder() {
		return List.of(ordered);
	}

	/**
	 * Rules that take over at least one of {@code rule}'s properties for at least one of {@code knownIds}, for the
	 * rule editor's "Partly overridden by" line (FR-4.6). Not for the hot path.
	 */
	public Set<Rule> overriddenBy(Rule rule, Iterable<String> knownIds) {
		Set<Rule> winners = new LinkedHashSet<>();
		if (!rule.enabled()) {
			return winners;
		}
		for (String id : knownIds) {
			if (!rule.pattern().matches(id)) {
				continue;
			}
			RuleResolution res = resolve(id);
			addIfOther(winners, rule, rule.setsLoudness(), res.loudness());
			addIfOther(winners, rule, rule.setsLimit(), res.limit());
			addIfOther(winners, rule, rule.setsCue(), res.cue());
		}
		return winners;
	}

	private static void addIfOther(Set<Rule> winners, Rule rule, boolean sets, Rule winner) {
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
		long bytes = MemoryEstimate.object(2 * MemoryEstimate.REFERENCE + 2 * 8) + MemoryEstimate.CONCURRENT_HASH_MAP
				+ MemoryEstimate.hashTable(cache.size()) + cache.size() * MemoryEstimate.HASH_NODE;
		for (Map.Entry<String, RuleResolution> entry : cache.entrySet()) {
			bytes += MemoryEstimate.string(entry.getKey());
			if (entry.getValue() != RuleResolution.NONE) {
				bytes += RESOLUTION_BYTES;
			}
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
