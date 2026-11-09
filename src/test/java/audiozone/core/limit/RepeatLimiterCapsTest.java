package audiozone.core.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import audiozone.core.Allocations;
import audiozone.core.rules.Limit;

import org.junit.jupiter.api.Test;

/**
 * PRD 9.1 ("Memory used by recent log, linker and limiters: under 8 MB"): the limiter's caps across all rules
 * together. Each rule still keeps up to 1,024 positions (FR-6.5); all rules together keep at most
 * {@link RepeatLimiter#MAX_COUNTERS} counters, and the least recently used counter of any rule is forgotten first. A
 * forgotten counter only starts afresh, the same as FR-6.5's per-rule forgetting.
 */
class RepeatLimiterCapsTest {
	/** One play per long window: a start is dropped exactly when its counter is still remembered. */
	private static final Limit ONCE_PER_SOURCE = new Limit(1, 10_000, true);
	private static final Limit ONCE_SHARED = new Limit(1, 10_000, false);

	private final RepeatLimiter limiter = new RepeatLimiter();

	private boolean start(String ruleId, Limit limit, double x, long nowMs) {
		return limiter.tryStart(ruleId, limit, x, 64, 0, nowMs);
	}

	@Test
	void allRulesTogetherKeepAtMostMaxCounters() {
		for (String rule : List.of("a", "b", "c")) {
			for (int i = 0; i < RepeatLimiter.MAX_SOURCES_PER_RULE; i++) {
				start(rule, ONCE_PER_SOURCE, i, 0);
			}
		}
		assertEquals(RepeatLimiter.MAX_COUNTERS, limiter.counterCount());
		int sources = limiter.sourceCount("a") + limiter.sourceCount("b") + limiter.sourceCount("c");
		assertEquals(RepeatLimiter.MAX_COUNTERS, sources);
	}

	@Test
	void whenFullTheLeastRecentlyUsedCounterOfAnyRuleIsForgotten() {
		long t = 0;
		for (int i = 0; i < RepeatLimiter.MAX_SOURCES_PER_RULE; i++) {
			start("a", ONCE_PER_SOURCE, i, t++);
		}
		for (int i = 0; i < RepeatLimiter.MAX_SOURCES_PER_RULE; i++) {
			start("b", ONCE_PER_SOURCE, i, t++);
		}
		assertEquals(RepeatLimiter.MAX_COUNTERS, limiter.counterCount(), "two full rules fill the shared counters");
		assertTrue(start("c", ONCE_PER_SOURCE, 0, t++), "a third rule's first position");
		assertEquals(RepeatLimiter.MAX_SOURCES_PER_RULE - 1, limiter.sourceCount("a"), "rule a lost its oldest position");
		assertTrue(start("a", ONCE_PER_SOURCE, 0, t++), "a's position 0 was forgotten, so it starts afresh");
		assertFalse(start("b", ONCE_PER_SOURCE, 0, t++), "b's position 0 is still remembered");
	}

	@Test
	void sharedCountersUseTheSameCounters() {
		for (int r = 0; r <= RepeatLimiter.MAX_COUNTERS; r++) {
			assertTrue(start("rule" + r, ONCE_SHARED, 0, r));
		}
		assertEquals(RepeatLimiter.MAX_COUNTERS, limiter.counterCount());
		assertTrue(start("rule0", ONCE_SHARED, 0, 5000), "rule0's counter was the least recently used, so it was forgotten");
		assertFalse(start("rule" + RepeatLimiter.MAX_COUNTERS, ONCE_SHARED, 0, 5001), "the newest counter is kept");
	}

	@Test
	void atMostMaxRulesAreRemembered() {
		for (int r = 0; r <= RepeatLimiter.MAX_RULES; r++) {
			start("rule" + r, ONCE_SHARED, 0, r);
		}
		Set<String> ids = limiter.ruleIds();
		assertEquals(RepeatLimiter.MAX_RULES, ids.size());
		assertFalse(ids.contains("rule0"), "the least recently used rule is forgotten");
		assertTrue(ids.contains("rule" + RepeatLimiter.MAX_RULES));
		assertTrue(limiter.counterCount() <= RepeatLimiter.MAX_COUNTERS);
	}

	@Test
	void forgettingARuleFreesItsCounters() {
		for (int i = 0; i < 100; i++) {
			start("gone", ONCE_PER_SOURCE, i, i);
		}
		start("keep", ONCE_SHARED, 0, 0);
		assertEquals(101, limiter.counterCount());
		limiter.retain(Set.of("keep"));
		assertEquals(1, limiter.counterCount());
		assertFalse(start("keep", ONCE_SHARED, 0, 1), "the kept rule still counts");
		assertTrue(start("gone", ONCE_PER_SOURCE, 0, 2), "a deleted rule that comes back starts afresh");
	}

	@Test
	void switchingToASharedCountFreesThePositions() {
		for (int i = 0; i < 100; i++) {
			start("r", ONCE_PER_SOURCE, i, i);
		}
		assertTrue(start("r", ONCE_SHARED, 0, 200), "a new limit starts afresh");
		assertEquals(1, limiter.counterCount());
		assertEquals(0, limiter.sourceCount("r"));
	}

	/**
	 * Random plays of three per-source rules against a simple reference: per-rule LRU at 1,024, then global LRU at
	 * {@link RepeatLimiter#MAX_COUNTERS}. Checks the hash table never loses or keeps a counter it should not, including
	 * the moves that keep counters packed.
	 */
	@Test
	void matchesAReferenceLruUnderRandomChurn() {
		Map<String, Boolean> reference = new LinkedHashMap<>(16, 0.75F, true);
		Random random = new Random(9);
		String[] rules = {"a", "b", "c"};
		for (int step = 0; step < 200_000; step++) {
			String rule = rules[random.nextInt(rules.length)];
			int x = random.nextInt(1600);
			String key = rule + "@" + x;
			boolean expected = !reference.containsKey(key);
			if (expected) {
				evictForNew(reference, rule);
			}
			reference.put(key, Boolean.TRUE);
			assertEquals(expected, start(rule, ONCE_PER_SOURCE, x, 0), "step " + step);
		}
		assertEquals(reference.size(), limiter.counterCount());
		for (String rule : rules) {
			assertEquals(countOf(reference, rule), limiter.sourceCount(rule), rule);
		}
	}

	private static void evictForNew(Map<String, Boolean> reference, String rule) {
		if (countOf(reference, rule) >= RepeatLimiter.MAX_SOURCES_PER_RULE) {
			String eldest = null;
			for (String key : reference.keySet()) {
				if (key.startsWith(rule + "@")) {
					eldest = key;
					break;
				}
			}
			reference.remove(eldest);
		} else if (reference.size() >= RepeatLimiter.MAX_COUNTERS) {
			reference.remove(reference.keySet().iterator().next());
		}
	}

	private static int countOf(Map<String, Boolean> reference, String rule) {
		int n = 0;
		for (String key : reference.keySet()) {
			if (key.startsWith(rule + "@")) {
				n++;
			}
		}
		return n;
	}

	@Test
	void churnAcrossRulesAllocatesNothing() {
		Limit perSource = new Limit(3, 1000, true);
		List<String> rules = new ArrayList<>();
		for (int r = 0; r < 4; r++) {
			rules.add("per" + r);
		}
		// 4 rules x 900 positions: more than MAX_COUNTERS, so counters are forgotten and reused all the time.
		for (int i = 0; i < 200_000; i++) {
			limiter.tryStart(rules.get(i & 3), perSource, i % 900, 64, 0, i);
		}
		long allocated = Allocations.fewestBytes(round -> {
			long base = 200_000L + round * 50_000L;
			for (int i = 0; i < 50_000; i++) {
				limiter.tryStart(rules.get(i & 3), perSource, i % 900, 64, 0, base + i);
			}
		});
		assertEquals(0, allocated);
	}
}
