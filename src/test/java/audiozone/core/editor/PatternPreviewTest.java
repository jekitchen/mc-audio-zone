package audiozone.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import audiozone.core.rules.PatternError;

import org.junit.jupiter.api.Test;

/** PRD FR-4.3: while editing, a live preview shows "Matches N sounds" with an expandable list. */
class PatternPreviewTest {
	private static final List<String> KNOWN = List.of(
			"minecraft:entity.villager.hurt",
			"minecraft:entity.villager.ambient",
			"minecraft:entity.wandering_trader.ambient",
			"minecraft:block.stone.step",
			"somemod:block.marble.step",
			"minecraft:music.game",
			"minecraft:ui.button.click",
			"somemod:theme");

	@Test
	void countsAndListsMatchingSoundsInOrder() {
		PatternPreview p = PatternPreview.of("minecraft:entity.villager.*", KNOWN, Set.of());
		assertNull(p.error());
		assertEquals(2, p.count());
		assertEquals(List.of("minecraft:entity.villager.ambient", "minecraft:entity.villager.hurt"), p.matches());
	}

	@Test
	void patternWithoutNamespaceCoversModdedSounds() {
		PatternPreview p = PatternPreview.of("block.*.step", KNOWN, Set.of());
		assertEquals(List.of("minecraft:block.stone.step", "somemod:block.marble.step"), p.matches());
	}

	@Test
	void vanillaCoveredSoundsAreNeverCounted() {
		// FR-4.4: not even * matches music, UI or IDs seen in excluded categories.
		PatternPreview p = PatternPreview.of("*", KNOWN, Set.of("somemod:theme"));
		assertEquals(5, p.count());
		assertTrue(p.matches().stream().noneMatch(id -> id.contains("music.") || id.contains("ui.") || id.equals("somemod:theme")));
	}

	@Test
	void invalidPatternReportsItsErrorAndMatchesNothing() {
		PatternPreview p = PatternPreview.of("entity.cow.#", KNOWN, Set.of());
		assertEquals(PatternError.INVALID_CHARACTER, p.error());
		assertEquals(0, p.count());
		assertEquals(PatternError.EMPTY, PatternPreview.of("  ", KNOWN, Set.of()).error());
	}

	@Test
	void inputIsNormalisedLikeTheSavedRule() {
		PatternPreview p = PatternPreview.of("Minecraft:Entity.Villager.*", KNOWN, Set.of());
		assertEquals(2, p.count());
		assertEquals("minecraft:entity.villager.*", p.normalizedPattern());
	}

	@Test
	void duplicateKnownIdsAreCountedOnce() {
		PatternPreview p = PatternPreview.of("minecraft:block.*", List.of("minecraft:block.stone.step", "minecraft:block.stone.step"), Set.of());
		assertEquals(1, p.count());
	}
}
