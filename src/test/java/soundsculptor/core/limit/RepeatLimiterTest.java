package soundsculptor.core.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import soundsculptor.core.sets.Limit;

import org.junit.jupiter.api.Test;

/** PRD FR-6.1 to FR-6.6 and the FR-6 acceptance checks. */
class RepeatLimiterTest {
	private static final Limit THREE_PER_SECOND = new Limit(3, 1000, false);

	private final RepeatLimiter limiter = new RepeatLimiter();

	private boolean start(String ruleId, Limit limit, double x, long nowMs) {
		return limiter.tryStart(ruleId, limit, x, 64, 0, nowMs);
	}

	// --- Sliding window, shared counter (FR-6.2 to FR-6.4) ---

	@Test
	void allowsUpToTheCountThenDrops() {
		assertTrue(start("r", THREE_PER_SECOND, 0, 0));
		assertTrue(start("r", THREE_PER_SECOND, 0, 10));
		assertTrue(start("r", THREE_PER_SECOND, 0, 20));
		assertFalse(start("r", THREE_PER_SECOND, 0, 30));
	}

	@Test
	void windowSlidesWithRealTime() {
		start("r", THREE_PER_SECOND, 0, 0);
		start("r", THREE_PER_SECOND, 0, 400);
		start("r", THREE_PER_SECOND, 0, 800);
		assertFalse(start("r", THREE_PER_SECOND, 0, 999));
		assertTrue(start("r", THREE_PER_SECOND, 0, 1000), "the play at 0 ms left the window");
		assertFalse(start("r", THREE_PER_SECOND, 0, 1300));
		assertTrue(start("r", THREE_PER_SECOND, 0, 1400));
	}

	@Test
	void droppedPlaysAreNotCountedSoAStreamSettlesAtExactlyN() {
		// FR-6.3 and the 20-villager acceptance check: a steady stream never goes silent, and never exceeds N per window.
		List<Long> allowed = new ArrayList<>();
		for (long t = 0; t < 30_000; t += 7) {
			if (start("villagers", THREE_PER_SECOND, 0, t)) {
				allowed.add(t);
			}
		}
		assertTrue(allowed.size() >= 89 && allowed.size() <= 90, "about 90 in 30 s, got " + allowed.size());
		for (int i = 3; i < allowed.size(); i++) {
			assertTrue(allowed.get(i) - allowed.get(i - 3) >= 1000, "4 starts within one second at " + allowed.get(i));
		}
	}

	@Test
	void sharedCounterCoversEverySourceOfTheRule() {
		assertTrue(start("r", THREE_PER_SECOND, 0, 0));
		assertTrue(start("r", THREE_PER_SECOND, 10, 0));
		assertTrue(start("r", THREE_PER_SECOND, 20, 0));
		assertFalse(start("r", THREE_PER_SECOND, 30, 0), "different villager, same shared count");
	}

	@Test
	void eachRuleHasItsOwnCounter() {
		// FR-6.7: each rule counts only the sounds it decided.
		for (int i = 0; i < 3; i++) {
			start("global", THREE_PER_SECOND, 0, i);
		}
		assertFalse(start("global", THREE_PER_SECOND, 0, 5));
		assertTrue(start("zone", THREE_PER_SECOND, 0, 5));
	}

	// --- Per source (FR-6.5) ---

	@Test
	void perSourceCountsEachBlockSeparately() {
		Limit perSource = new Limit(1, 1000, true);
		assertTrue(start("r", perSource, 0.5, 0));
		assertFalse(start("r", perSource, 0.9, 10), "same block");
		assertTrue(start("r", perSource, 1.1, 10), "next block");
		assertTrue(start("r", perSource, 5.5, 10));
	}

	@Test
	void sourcePositionsRoundDownToTheBlock() {
		assertEquals(RepeatLimiter.blockKey(-1, 64, 3), RepeatLimiter.blockKey(-0.5, 64.9, 3.99));
		assertTrue(RepeatLimiter.blockKey(0, 64, 0) != RepeatLimiter.blockKey(-0.01, 64, 0));
		assertTrue(RepeatLimiter.blockKey(0, 64, 1) != RepeatLimiter.blockKey(0, 65, 0));
	}

	@Test
	void perSourceRaisesTheTotal() {
		Limit shared = new Limit(1, 1000, false);
		Limit perSource = new Limit(1, 1000, true);
		int sharedAllowed = 0;
		int perSourceAllowed = 0;
		for (int villager = 0; villager < 20; villager++) {
			sharedAllowed += start("a", shared, villager * 2, 0) ? 1 : 0;
			perSourceAllowed += start("b", perSource, villager * 2, 0) ? 1 : 0;
		}
		assertEquals(1, sharedAllowed);
		assertEquals(20, perSourceAllowed);
	}

	@Test
	void atMost1024PositionsAndTheLeastRecentlyUsedIsForgotten() {
		Limit perSource = new Limit(1, 10_000, true);
		for (int i = 0; i < RepeatLimiter.MAX_SOURCES_PER_RULE; i++) {
			assertTrue(start("r", perSource, i, i));
		}
		assertFalse(start("r", perSource, 0, 2000), "position 0 is still remembered");
		assertTrue(start("r", perSource, 5000, 2001), "a new position evicts the least recently used one");
		assertEquals(RepeatLimiter.MAX_SOURCES_PER_RULE, limiter.sourceCount("r"));
		assertTrue(start("r", perSource, 1, 2002), "position 1 was the least recently used, so it starts fresh");
	}

	// --- Other acceptance checks and lifecycle ---

	@Test
	void minecartLimitOnePerTenSeconds() {
		// Two minecarts start 2 s apart; the second is dropped. (Already-playing sounds are never cut off: FR-6.6.)
		Limit oneIn10 = new Limit(1, 10_000, false);
		assertTrue(start("minecarts", oneIn10, 0, 0));
		assertFalse(start("minecarts", oneIn10, 30, 2000));
	}

	@Test
	void changingALimitStartsItsCounterAfresh() {
		start("r", new Limit(1, 1000, false), 0, 0);
		assertFalse(start("r", new Limit(1, 1000, false), 0, 10));
		assertTrue(start("r", new Limit(2, 1000, false), 0, 20), "new count: fresh window");
	}

	@Test
	void retainForgetsDeletedRules() {
		start("keep", THREE_PER_SECOND, 0, 0);
		start("gone", THREE_PER_SECOND, 0, 0);
		limiter.retain(Set.of("keep"));
		assertEquals(Set.of("keep"), limiter.ruleIds());
	}

	@Test
	void droppedPlaysAreCountedForDiagnostics() {
		for (int i = 0; i < 5; i++) {
			start("r", THREE_PER_SECOND, 0, i);
		}
		assertEquals(2, limiter.droppedCount("r"));
	}

	@Test
	void perSourceMemoryMatchesAReferenceLruUnderRandomChurn() {
		// One play per huge window: a start is dropped exactly when its block is still remembered. Compare with
		// LinkedHashMap's own LRU, to check the hand-written hash table (including its deletions) never loses a key.
		Limit oncePerever = new Limit(1, 10_000, true);
		java.util.LinkedHashMap<Long, Boolean> reference = new java.util.LinkedHashMap<>(16, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
				return size() > RepeatLimiter.MAX_SOURCES_PER_RULE;
			}
		};
		java.util.Random random = new java.util.Random(6);
		for (int i = 0; i < 200_000; i++) {
			int x = random.nextInt(3000) - 1500;
			int z = random.nextInt(3) - 1;
			long key = RepeatLimiter.blockKey(x, 64, z);
			boolean expected = !reference.containsKey(key);
			reference.put(key, Boolean.TRUE);
			assertEquals(expected, limiter.tryStart("r", oncePerever, x, 64, z, 0), "step " + i);
		}
		assertEquals(reference.size(), limiter.sourceCount("r"));
	}

	@Test
	void steadyStateAllocatesNothing() {
		Limit perSource = new Limit(3, 1000, true);
		for (int i = 0; i < 300_000; i++) {
			limiter.tryStart("shared", THREE_PER_SECOND, i % 50, 64, 0, i);
			limiter.tryStart("per", perSource, i % 500, 64, i % 7, i);
		}
		long allocated = soundsculptor.core.Allocations.fewestBytes(round -> {
			long start = 300_000L + round * 100_000L;
			for (int i = 0; i < 100_000; i++) {
				limiter.tryStart("shared", THREE_PER_SECOND, i % 50, 64, 0, start + i);
				limiter.tryStart("per", perSource, i % 500, 64, i % 7, start + i);
			}
		});
		assertEquals(0, allocated);
	}
}
