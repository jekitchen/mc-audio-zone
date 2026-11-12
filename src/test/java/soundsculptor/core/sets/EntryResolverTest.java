package soundsculptor.core.sets;

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
class EntryResolverTest {
	private static final String VILLAGER_AMBIENT = "minecraft:entity.villager.ambient";
	private static final String VILLAGER_HURT = "minecraft:entity.villager.hurt";
	private static final String TRADER_AMBIENT = "minecraft:entity.wandering_trader.ambient";

	private static EntryResolver resolver(SetEntry... rules) {
		return EntryResolver.of(List.of(rules));
	}

	// --- FR-4 acceptance checks ---

	@Test
	void villagerWildcardMutesAllVillagerSoundsButNotTheTrader() {
		EntryResolver r = resolver(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true));
		assertTrue(r.resolve(VILLAGER_AMBIENT).muted());
		assertTrue(r.resolve(VILLAGER_HURT).muted());
		assertFalse(r.resolve(TRADER_AMBIENT).muted());
		assertEquals(100, r.resolve(TRADER_AMBIENT).volumePercent());
	}

	@Test
	void footstepPatternCoversVanillaAndModdedBlocks() {
		EntryResolver r = resolver(SetEntry.of("r1", "*:block.*.step").withVolume(50));
		assertEquals(50, r.resolve("minecraft:block.stone.step").volumePercent());
		assertEquals(50, r.resolve("minecraft:block.wood.step").volumePercent());
		assertEquals(50, r.resolve("somemod:block.marble.step").volumePercent());
		assertEquals(100, r.resolve("minecraft:block.stone.break").volumePercent());
	}

	@Test
	void exactRuleBelowWildcardStillWinsForItsSound() {
		SetEntry mute = SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true);
		SetEntry exact = SetEntry.of("r2", VILLAGER_AMBIENT).withVolume(100);
		EntryResolver r = resolver(mute, exact);

		EntryResolution ambient = r.resolve(VILLAGER_AMBIENT);
		assertFalse(ambient.muted());
		assertEquals(100, ambient.volumePercent());
		assertSame(exact, ambient.loudness());

		assertTrue(r.resolve(VILLAGER_HURT).muted(), "other villager sounds stay muted");
	}

	@Test
	void wildcardReportsThatItIsPartlyOverridden() {
		SetEntry mute = SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true);
		SetEntry exact = SetEntry.of("r2", VILLAGER_AMBIENT).withVolume(100);
		EntryResolver r = resolver(mute, exact);
		List<String> known = List.of(VILLAGER_AMBIENT, VILLAGER_HURT, TRADER_AMBIENT);

		assertEquals(Set.of(exact), r.overriddenBy(mute, known));
		assertEquals(Set.of(), r.overriddenBy(exact, known));
	}

	@Test
	void disabledRuleHasNoEffect() {
		SetEntry mute = SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true);
		assertTrue(resolver(mute).resolve(VILLAGER_AMBIENT).muted());
		assertFalse(resolver(mute.withEnabled(false)).resolve(VILLAGER_AMBIENT).muted());
	}

	// --- Specificity order (FR-4.5) ---

	@Test
	void moreLiteralCharactersBeatFewer() {
		SetEntry broad = SetEntry.of("broad", "*villager*").withVolume(20);
		SetEntry narrow = SetEntry.of("narrow", "minecraft:entity.villager.*").withVolume(70);
		assertEquals(70, resolver(broad, narrow).resolve(VILLAGER_AMBIENT).volumePercent());
		assertEquals(70, resolver(narrow, broad).resolve(VILLAGER_AMBIENT).volumePercent());
	}

	@Test
	void fewerStarsBeatMoreWhenLiteralsAreEqual() {
		SetEntry twoStars = SetEntry.of("two", "a:*b*c").withVolume(20);
		SetEntry oneStar = SetEntry.of("one", "a:b*c").withVolume(70);
		assertEquals(4, twoStars.pattern().literalCount());
		assertEquals(4, oneStar.pattern().literalCount());
		assertEquals(70, resolver(twoStars, oneStar).resolve("a:bxc").volumePercent());
	}

	@Test
	void listOrderBreaksRemainingTies() {
		SetEntry first = SetEntry.of("first", "minecraft:entity.villager.*").withVolume(30);
		SetEntry second = SetEntry.of("second", "minecraft:entity.villager.*").withVolume(80);
		assertEquals(30, resolver(first, second).resolve(VILLAGER_AMBIENT).volumePercent());
		assertEquals(80, resolver(second, first).resolve(VILLAGER_AMBIENT).volumePercent());
	}

	@Test
	void ofIsOneLayerWithoutASetId() {
		SetEntry mute = SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true);
		EntryResolver r = resolver(mute);
		assertEquals(List.of(new Layer("", List.of(mute))), r.layers());
		assertEquals("", r.resolve(VILLAGER_AMBIENT).loudnessSet());
		assertNull(r.resolve(VILLAGER_AMBIENT).limitSet(), "no winner, no set");
	}

	// --- Per-property resolution (FR-4.5) ---

	@Test
	void eachPropertyHasItsOwnWinner() {
		SetEntry mute = SetEntry.of("mute", "minecraft:entity.villager.*").withMute(true).withLimit(new Limit(3, 1000, false));
		SetEntry cueOnly = SetEntry.of("cue", VILLAGER_AMBIENT).withCue(false);
		EntryResolution res = resolver(mute, cueOnly).resolve(VILLAGER_AMBIENT);
		assertSame(mute, res.loudness());
		assertSame(mute, res.limit());
		assertSame(cueOnly, res.cue());
		assertTrue(res.muted());
		assertFalse(res.cueOn());
	}

	@Test
	void muteAndVolumeCompeteTogetherAsLoudness() {
		// A rule that sets only volume still beats a less specific rule that sets only mute.
		SetEntry mute = SetEntry.of("mute", "minecraft:entity.villager.*").withMute(true);
		SetEntry volume = SetEntry.of("vol", VILLAGER_AMBIENT).withVolume(60);
		EntryResolution res = resolver(mute, volume).resolve(VILLAGER_AMBIENT);
		assertFalse(res.muted());
		assertEquals(60, res.volumePercent());
	}

	@Test
	void mutedRuleKeepsItsVolumeForUnmute() {
		// FR-5.1: mute is separate from the volume value.
		EntryResolution res = resolver(SetEntry.of("r", VILLAGER_AMBIENT).withVolume(40).withMute(true)).resolve(VILLAGER_AMBIENT);
		assertTrue(res.muted());
		assertEquals(40, res.volumePercent());
		assertEquals(0, res.effectiveVolumePercent());
	}

	@Test
	void noMatchingRuleMeansVanillaDefaults() {
		EntryResolution res = resolver(SetEntry.of("r", "minecraft:entity.cow.*").withMute(true)).resolve(VILLAGER_AMBIENT);
		assertNull(res.loudness());
		assertNull(res.limit());
		assertNull(res.cue());
		assertFalse(res.muted());
		assertEquals(100, res.effectiveVolumePercent());
		assertFalse(res.cueOn());
	}

	@Test
	void ruleThatSetsNothingNeverWins() {
		SetEntry empty = SetEntry.of("empty", VILLAGER_AMBIENT);
		SetEntry mute = SetEntry.of("mute", "minecraft:entity.villager.*").withMute(true);
		assertSame(mute, resolver(empty, mute).resolve(VILLAGER_AMBIENT).loudness());
	}

	// --- Hot path (PRD 7.4) ---

	@Test
	void repeatedLookupsReuseTheCachedResult() {
		EntryResolver r = resolver(SetEntry.of("r", "minecraft:entity.villager.*").withMute(true));
		assertSame(r.resolve(VILLAGER_AMBIENT), r.resolve(VILLAGER_AMBIENT));
	}

	@Test
	void cacheCountsHitsAndMissesForDiagnostics() {
		// FR-13.3: pattern cache hit rate.
		EntryResolver r = resolver(SetEntry.of("r", "minecraft:entity.villager.*").withMute(true));
		r.resolve(VILLAGER_AMBIENT);
		r.resolve(VILLAGER_AMBIENT);
		r.resolve(VILLAGER_AMBIENT);
		r.resolve(VILLAGER_HURT);
		assertEquals(2, r.cacheHits());
		assertEquals(2, r.cacheMisses());
	}

	@Test
	void cacheStaysBoundedForUnboundedIds() {
		EntryResolver r = resolver(SetEntry.of("r", "x:*").withVolume(10));
		for (int i = 0; i < EntryResolver.MAX_CACHED_IDS + 500; i++) {
			assertEquals(10, r.resolve("x:sound" + i).volumePercent());
		}
		assertTrue(r.cachedIdCount() <= EntryResolver.MAX_CACHED_IDS);
	}

	// --- Value ranges ---

	@Test
	void volumeMustBeWithinZeroToTwoHundred() {
		SetEntry r = SetEntry.of("r", VILLAGER_AMBIENT);
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
