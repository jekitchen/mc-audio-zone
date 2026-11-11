package soundsculptor.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** PRD FR-4.4 to FR-4.7: resolving one rule list for a sound, per property. */
class RuleResolverTest {
	private static final String VILLAGER_AMBIENT = "minecraft:entity.villager.ambient";
	private static final String VILLAGER_HURT = "minecraft:entity.villager.hurt";
	private static final String TRADER_AMBIENT = "minecraft:entity.wandering_trader.ambient";

	private static RuleResolver resolver(Rule... rules) {
		return new RuleResolver(List.of(rules));
	}

	// --- FR-4 acceptance checks ---

	@Test
	void villagerWildcardMutesAllVillagerSoundsButNotTheTrader() {
		RuleResolver r = resolver(Rule.of("r1", "minecraft:entity.villager.*").withMute(true));
		assertTrue(r.resolve(VILLAGER_AMBIENT).muted());
		assertTrue(r.resolve(VILLAGER_HURT).muted());
		assertFalse(r.resolve(TRADER_AMBIENT).muted());
		assertEquals(100, r.resolve(TRADER_AMBIENT).volumePercent());
	}

	@Test
	void footstepPatternCoversVanillaAndModdedBlocks() {
		RuleResolver r = resolver(Rule.of("r1", "*:block.*.step").withVolume(50));
		assertEquals(50, r.resolve("minecraft:block.stone.step").volumePercent());
		assertEquals(50, r.resolve("minecraft:block.wood.step").volumePercent());
		assertEquals(50, r.resolve("somemod:block.marble.step").volumePercent());
		assertEquals(100, r.resolve("minecraft:block.stone.break").volumePercent());
	}

	@Test
	void exactRuleBelowWildcardStillWinsForItsSound() {
		Rule mute = Rule.of("r1", "minecraft:entity.villager.*").withMute(true);
		Rule exact = Rule.of("r2", VILLAGER_AMBIENT).withVolume(100);
		RuleResolver r = resolver(mute, exact);

		RuleResolution ambient = r.resolve(VILLAGER_AMBIENT);
		assertFalse(ambient.muted());
		assertEquals(100, ambient.volumePercent());
		assertSame(exact, ambient.loudness());

		assertTrue(r.resolve(VILLAGER_HURT).muted(), "other villager sounds stay muted");
	}

	@Test
	void wildcardReportsThatItIsPartlyOverridden() {
		Rule mute = Rule.of("r1", "minecraft:entity.villager.*").withMute(true);
		Rule exact = Rule.of("r2", VILLAGER_AMBIENT).withVolume(100);
		RuleResolver r = resolver(mute, exact);
		List<String> known = List.of(VILLAGER_AMBIENT, VILLAGER_HURT, TRADER_AMBIENT);

		assertEquals(Set.of(exact), r.overriddenBy(mute, known));
		assertEquals(Set.of(), r.overriddenBy(exact, known));
	}

	@Test
	void disabledRuleHasNoEffect() {
		Rule mute = Rule.of("r1", "minecraft:entity.villager.*").withMute(true);
		assertTrue(resolver(mute).resolve(VILLAGER_AMBIENT).muted());
		assertFalse(resolver(mute.withEnabled(false)).resolve(VILLAGER_AMBIENT).muted());
	}

	// --- Specificity order (FR-4.5) ---

	@Test
	void moreLiteralCharactersBeatFewer() {
		Rule broad = Rule.of("broad", "*villager*").withVolume(20);
		Rule narrow = Rule.of("narrow", "minecraft:entity.villager.*").withVolume(70);
		assertEquals(70, resolver(broad, narrow).resolve(VILLAGER_AMBIENT).volumePercent());
		assertEquals(70, resolver(narrow, broad).resolve(VILLAGER_AMBIENT).volumePercent());
	}

	@Test
	void fewerStarsBeatMoreWhenLiteralsAreEqual() {
		Rule twoStars = Rule.of("two", "a:*b*c").withVolume(20);
		Rule oneStar = Rule.of("one", "a:b*c").withVolume(70);
		assertEquals(4, twoStars.pattern().literalCount());
		assertEquals(4, oneStar.pattern().literalCount());
		assertEquals(70, resolver(twoStars, oneStar).resolve("a:bxc").volumePercent());
	}

	@Test
	void listOrderBreaksRemainingTies() {
		Rule first = Rule.of("first", "minecraft:entity.villager.*").withVolume(30);
		Rule second = Rule.of("second", "minecraft:entity.villager.*").withVolume(80);
		assertEquals(30, resolver(first, second).resolve(VILLAGER_AMBIENT).volumePercent());
		assertEquals(80, resolver(second, first).resolve(VILLAGER_AMBIENT).volumePercent());
	}

	// --- Per-property resolution (FR-4.5) ---

	@Test
	void eachPropertyHasItsOwnWinner() {
		Rule mute = Rule.of("mute", "minecraft:entity.villager.*").withMute(true).withLimit(new Limit(3, 1000, false));
		Rule cueOnly = Rule.of("cue", VILLAGER_AMBIENT).withCue(false);
		RuleResolution res = resolver(mute, cueOnly).resolve(VILLAGER_AMBIENT);
		assertSame(mute, res.loudness());
		assertSame(mute, res.limit());
		assertSame(cueOnly, res.cue());
		assertTrue(res.muted());
		assertFalse(res.cueOn());
	}

	@Test
	void muteAndVolumeCompeteTogetherAsLoudness() {
		// A rule that sets only volume still beats a less specific rule that sets only mute.
		Rule mute = Rule.of("mute", "minecraft:entity.villager.*").withMute(true);
		Rule volume = Rule.of("vol", VILLAGER_AMBIENT).withVolume(60);
		RuleResolution res = resolver(mute, volume).resolve(VILLAGER_AMBIENT);
		assertFalse(res.muted());
		assertEquals(60, res.volumePercent());
	}

	@Test
	void mutedRuleKeepsItsVolumeForUnmute() {
		// FR-5.1: mute is separate from the volume value.
		RuleResolution res = resolver(Rule.of("r", VILLAGER_AMBIENT).withVolume(40).withMute(true)).resolve(VILLAGER_AMBIENT);
		assertTrue(res.muted());
		assertEquals(40, res.volumePercent());
		assertEquals(0, res.effectiveVolumePercent());
	}

	@Test
	void noMatchingRuleMeansVanillaDefaults() {
		RuleResolution res = resolver(Rule.of("r", "minecraft:entity.cow.*").withMute(true)).resolve(VILLAGER_AMBIENT);
		assertNull(res.loudness());
		assertNull(res.limit());
		assertNull(res.cue());
		assertFalse(res.muted());
		assertEquals(100, res.effectiveVolumePercent());
		assertFalse(res.cueOn());
	}

	@Test
	void ruleThatSetsNothingNeverWins() {
		Rule empty = Rule.of("empty", VILLAGER_AMBIENT);
		Rule mute = Rule.of("mute", "minecraft:entity.villager.*").withMute(true);
		assertSame(mute, resolver(empty, mute).resolve(VILLAGER_AMBIENT).loudness());
	}

	// --- Hot path (PRD 7.4) ---

	@Test
	void repeatedLookupsReuseTheCachedResult() {
		RuleResolver r = resolver(Rule.of("r", "minecraft:entity.villager.*").withMute(true));
		assertSame(r.resolve(VILLAGER_AMBIENT), r.resolve(VILLAGER_AMBIENT));
	}

	@Test
	void cacheCountsHitsAndMissesForDiagnostics() {
		// FR-13.3: pattern cache hit rate.
		RuleResolver r = resolver(Rule.of("r", "minecraft:entity.villager.*").withMute(true));
		r.resolve(VILLAGER_AMBIENT);
		r.resolve(VILLAGER_AMBIENT);
		r.resolve(VILLAGER_AMBIENT);
		r.resolve(VILLAGER_HURT);
		assertEquals(2, r.cacheHits());
		assertEquals(2, r.cacheMisses());
	}

	@Test
	void cacheStaysBoundedForUnboundedIds() {
		RuleResolver r = resolver(Rule.of("r", "x:*").withVolume(10));
		for (int i = 0; i < RuleResolver.MAX_CACHED_IDS + 500; i++) {
			assertEquals(10, r.resolve("x:sound" + i).volumePercent());
		}
		assertTrue(r.cachedIdCount() <= RuleResolver.MAX_CACHED_IDS);
	}

	// --- Value ranges ---

	@Test
	void volumeMustBeWithinZeroToTwoHundred() {
		Rule r = Rule.of("r", VILLAGER_AMBIENT);
		assertThrows(IllegalArgumentException.class, () -> r.withVolume(-1));
		assertThrows(IllegalArgumentException.class, () -> r.withVolume(201));
		assertEquals(200, r.withVolume(200).volume());
	}

	@Test
	void limitValuesMustBeInRange() {
		// FR-6.1: count 1 to 100, window 50 ms to 10 s.
		assertThrows(IllegalArgumentException.class, () -> new Limit(0, 1000, false));
		assertThrows(IllegalArgumentException.class, () -> new Limit(101, 1000, false));
		assertThrows(IllegalArgumentException.class, () -> new Limit(3, 49, false));
		assertThrows(IllegalArgumentException.class, () -> new Limit(3, 10_001, false));
		new Limit(1, 50, true);
		new Limit(100, 10_000, false);
	}
}
