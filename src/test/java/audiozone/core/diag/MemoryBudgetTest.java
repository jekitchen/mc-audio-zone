package audiozone.core.diag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import audiozone.core.Allocations;
import audiozone.core.inspect.EntitySounds;
import audiozone.core.limit.RepeatLimiter;
import audiozone.core.playing.PlayingSounds;
import audiozone.core.recent.PlayStatus;
import audiozone.core.recent.RecentSounds;
import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.rules.RuleResolver;
import audiozone.core.rules.SoundPattern;
import audiozone.core.trace.LastPlays;
import audiozone.core.zones.Point;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import org.junit.jupiter.api.Test;

/**
 * PRD 9.1: "Memory used by recent log, linker and limiters: under 8 MB", read from Diagnostics; PRD 11.2 soak test:
 * "memory readout stable (under 8 MB for Audio Zone data)".
 *
 * <p>Each structure is filled to its cap and its estimate is compared with the bytes actually allocated while
 * building and filling it, then everything at its cap together is checked against the budget.
 */
class MemoryBudgetTest {
	/** A typical sound ID length, e.g. {@code minecraft:entity.villager.ambient} is 33. */
	private static String id(int i) {
		return "minecraft:entity.test_sound.number_" + i;
	}

	// ---- filled to the cap ------------------------------------------------------------------------------------

	private static RecentSounds fullRecent() {
		RecentSounds recent = new RecentSounds();
		// More IDs than the session cap, so both session sets fill too; the last 200 stay listed.
		for (int i = 0; i < 4200; i++) {
			recent.record(id(i), "music", false, 0, true, PlayStatus.PLAYING, null, 1000);
			recent.record(id(i), "neutral", true, 4, true, PlayStatus.PLAYING, null, 1000 + i);
		}
		return recent;
	}

	/** Exactly as many IDs as the list keeps, so nothing is built and then thrown away. */
	private static RecentSounds fullList() {
		RecentSounds recent = new RecentSounds();
		for (int i = 0; i < RecentSounds.MAX_IDS; i++) {
			recent.record(id(i), "neutral", true, 4, true, PlayStatus.PLAYING, null, 1000 + i);
		}
		return recent;
	}

	private static EntitySounds fullLinker() {
		EntitySounds linker = new EntitySounds();
		for (int e = 0; e < EntitySounds.MAX_ENTITIES; e++) {
			for (int s = 0; s < EntitySounds.SOUNDS_PER_ENTITY; s++) {
				linker.recordLinked(e, id(e * 100 + s), true, 1000 + s);
			}
		}
		return linker;
	}

	private static LastPlays fullLastPlays() {
		LastPlays plays = new LastPlays();
		for (int i = 0; i < LastPlays.CAPACITY + 10; i++) {
			plays.record(id(i), true, "world", "minecraft:overworld", i, 64, 0, 1, 64, 1, 80F, false, false);
		}
		return plays;
	}

	private static PlayingSounds fullPlaying() {
		PlayingSounds playing = new PlayingSounds();
		for (int i = 0; i < PlayingSounds.CAPACITY; i++) {
			playing.start(i + 1, id(i), i, 64, 0, 1F, 1F);
		}
		return playing;
	}

	private static RuleResolver fullResolver() {
		List<Rule> rules = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			rules.add(new Rule("r" + i, true, SoundPattern.of("minecraft:entity.test_sound.number_" + i + "*"), null, 50,
					null, null));
		}
		RuleResolver resolver = new RuleResolver(rules);
		for (int i = 0; i < 4096; i++) { // the cache's cap
			resolver.resolve(id(i));
		}
		return resolver;
	}

	/** One rule counting per source, at the highest count, with every remembered position in use. */
	private static RepeatLimiter fullPerSourceRule() {
		RepeatLimiter limiter = new RepeatLimiter();
		Limit limit = new Limit(Limit.MAX_COUNT, 1000, true);
		for (int i = 0; i < RepeatLimiter.MAX_SOURCES_PER_RULE; i++) {
			for (int n = 0; n < Limit.MAX_COUNT; n++) {
				limiter.tryStart("r1", limit, i, 64, 0, n);
			}
		}
		return limiter;
	}

	/** The 11.2 stress profile's 50 global rules, each limited at the highest count, counted together. */
	private static RepeatLimiter fiftySharedRules() {
		RepeatLimiter limiter = new RepeatLimiter();
		Limit limit = new Limit(Limit.MAX_COUNT, 1000, false);
		for (int r = 0; r < 50; r++) {
			String ruleId = "r" + r;
			for (int n = 0; n < Limit.MAX_COUNT; n++) {
				limiter.tryStart(ruleId, limit, 0, 64, 0, n);
			}
		}
		return limiter;
	}

	/**
	 * The limiter's worst case: {@link RepeatLimiter#MAX_RULES} limited rules with long zone-rule keys, and every one of
	 * its {@link RepeatLimiter#MAX_COUNTERS} counters sized for the highest count.
	 */
	private static RepeatLimiter worstLimiter() {
		RepeatLimiter limiter = new RepeatLimiter();
		Limit limit = new Limit(Limit.MAX_COUNT, 1000, false);
		for (int r = 0; r < RepeatLimiter.MAX_RULES; r++) {
			limiter.tryStart("zone_" + (r % 64) + "\nexact:" + id(r), limit, 0, 64, 0, r);
		}
		return limiter;
	}

	/**
	 * A full profile's per-ID caches: {@link Precedence#MAX_ZONES} zones, each with a rule matching every sound, and
	 * enough sound IDs to fill the global list's cache and the zones' shared cap.
	 */
	private static Precedence worstPrecedence() {
		List<Zone> zones = new ArrayList<>();
		for (int z = 0; z < Precedence.MAX_ZONES; z++) {
			zones.add(new Zone("zone_" + z, "Zone " + z, true, "world", "minecraft:overworld", new Point(z * 100, 0, 0),
					new Shape.Sphere(10), List.of(Rule.of("r", "minecraft:*").withMute(true))));
		}
		Precedence precedence = new Precedence(List.of(Rule.of("g", "minecraft:*").withVolume(50)), zones);
		audiozone.core.rules.RuleResolution[] out = new audiozone.core.rules.RuleResolution[zones.size() + 1];
		for (int i = 0; i < 4096; i++) { // the global cache's cap; the zones' shared cap fills on the way
			precedence.resolve(id(i), out);
		}
		return precedence;
	}

	// ---- estimates agree with what the JVM allocates ----------------------------------------------------------

	/**
	 * The estimate is within 25% of the bytes allocated while building and filling. Allocation also counts what was
	 * thrown away (hash tables that grew, temporary strings), so the estimate may be a little lower, but not much.
	 */
	private static <T> void estimateMatchesAllocation(String name, Supplier<T> build, ToLongFunction<T> estimate) {
		build.get();
		build.get();
		Object[] keep = new Object[1];
		long allocated = Allocations.fewestBytes(round -> keep[0] = build.get());
		@SuppressWarnings("unchecked")
		long estimated = estimate.applyAsLong((T) keep[0]);
		assertTrue(estimated >= allocated * 0.75 && estimated <= allocated * 1.25,
				name + ": estimated " + estimated + " bytes, allocated " + allocated);
	}

	@Test
	void recentLogEstimateMatchesAllocation() {
		estimateMatchesAllocation("recent log", MemoryBudgetTest::fullList, RecentSounds::estimatedBytes);
	}

	@Test
	void linkerEstimateMatchesAllocation() {
		estimateMatchesAllocation("linker", MemoryBudgetTest::fullLinker, EntitySounds::estimatedBytes);
	}

	@Test
	void lastPlaysEstimateMatchesAllocation() {
		estimateMatchesAllocation("last plays", MemoryBudgetTest::fullLastPlays, LastPlays::estimatedBytes);
	}

	@Test
	void playingSoundsEstimateMatchesAllocation() {
		estimateMatchesAllocation("playing sounds", MemoryBudgetTest::fullPlaying, PlayingSounds::estimatedBytes);
	}

	@Test
	void resolverEstimateMatchesAllocation() {
		estimateMatchesAllocation("pattern cache", MemoryBudgetTest::fullResolver, RuleResolver::estimatedBytes);
	}

	@Test
	void perSourceLimiterEstimateMatchesAllocation() {
		estimateMatchesAllocation("per-source limiter", MemoryBudgetTest::fullPerSourceRule, RepeatLimiter::estimatedBytes);
	}

	@Test
	void sharedLimiterEstimateMatchesAllocation() {
		estimateMatchesAllocation("shared limiter", MemoryBudgetTest::fiftySharedRules, RepeatLimiter::estimatedBytes);
	}

	@Test
	void worstCaseLimiterEstimateMatchesAllocation() {
		estimateMatchesAllocation("limiter, worst case", MemoryBudgetTest::worstLimiter, RepeatLimiter::estimatedBytes);
	}

	/**
	 * Each sound ID string is one object shared by the global cache and all 64 zones' caches, but every cache counts
	 * it, so this estimate errs high on purpose. It must never be lower than what was allocated, and not far above.
	 */
	@Test
	void fullProfileCachesEstimateIsNeverLow() {
		worstPrecedence();
		Object[] keep = new Object[1];
		long allocated = Allocations.fewestBytes(round -> keep[0] = worstPrecedence());
		long estimated = ((Precedence) keep[0]).estimatedBytes();
		assertTrue(estimated >= allocated && estimated <= allocated * 1.5,
				"profile caches: estimated " + estimated + " bytes, allocated " + allocated);
	}

	// ---- the budget -------------------------------------------------------------------------------------------

	@Test
	void everythingAtItsCapStaysUnderEightMegabytes() {
		MemoryMeter meter = new MemoryMeter();
		RecentSounds recent = fullRecent();
		EntitySounds linker = fullLinker();
		LastPlays lastPlays = fullLastPlays();
		PlayingSounds playing = fullPlaying();
		RuleResolver global = fullResolver();
		RepeatLimiter limiter = worstLimiter();
		Precedence profile = worstPrecedence();
		meter.add("recent", recent::estimatedBytes);
		meter.add("linker", linker::estimatedBytes);
		meter.add("last plays", lastPlays::estimatedBytes);
		meter.add("playing", playing::estimatedBytes);
		meter.add("mc global pattern cache", global::estimatedBytes);
		meter.add("limits, worst case", limiter::estimatedBytes);
		meter.add("profile caches, 64 zones", profile::estimatedBytes);
		StringBuilder parts = new StringBuilder();
		for (int i = 0; i < meter.partCount(); i++) {
			parts.append(meter.name(i)).append(' ').append(MemoryEstimate.megabytes(meter.bytes(i, 0))).append(" MB; ");
		}
		assertTrue(!meter.overBudget(0), "total " + MemoryEstimate.megabytes(meter.totalBytes(0)) + " MB: " + parts);
	}

	@Test
	void emptyStructuresAreSmall() {
		IntFunction<String> fail = bytes -> "an empty structure should be well under 1 MB, was " + bytes;
		long empty = new RecentSounds().estimatedBytes() + new EntitySounds().estimatedBytes()
				+ new LastPlays().estimatedBytes() + new RepeatLimiter().estimatedBytes()
				+ new RuleResolver(List.of()).estimatedBytes();
		assertTrue(empty < 200_000, fail.apply((int) empty));
	}
}
