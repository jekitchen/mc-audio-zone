package soundsculptor.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.rules.SoundPattern;

import org.junit.jupiter.api.Test;

/** PRD FR-1.5: "Make pattern…" offers a suggested wildcard, e.g. minecraft:entity.villager.*. */
class PatternSuggestionsTest {
	@Test
	void firstSuggestionReplacesTheLastPartOfThePath() {
		assertEquals("minecraft:entity.villager.*", PatternSuggestions.suggest("minecraft:entity.villager.ambient").get(0));
	}

	@Test
	void suggestionsGoFromClosestFamilyToWholeNamespace() {
		assertEquals(List.of(
				"minecraft:entity.villager.*",
				"minecraft:entity.*.ambient",
				"minecraft:entity.*",
				"minecraft:*"), PatternSuggestions.suggest("minecraft:entity.villager.ambient"));
	}

	@Test
	void footstepsGetTheAnyMaterialPattern() {
		// The FR-4 acceptance example: one rule for footsteps on every block.
		assertTrue(PatternSuggestions.suggest("minecraft:block.stone.step").contains("minecraft:block.*.step"));
	}

	@Test
	void longerPathsOfferEveryShorterPrefix() {
		List<String> s = PatternSuggestions.suggest("minecraft:block.note_block.basedrum.loud");
		assertEquals("minecraft:block.note_block.basedrum.*", s.get(0));
		assertTrue(s.contains("minecraft:block.note_block.*"));
		assertTrue(s.contains("minecraft:block.*"));
		assertTrue(s.contains("minecraft:block.*.loud"));
		assertEquals("minecraft:*", s.get(s.size() - 1));
	}

	@Test
	void twoPartPathsOfferThePrefixAndTheNamespace() {
		assertEquals(List.of("minecraft:ambient.*", "minecraft:*"), PatternSuggestions.suggest("minecraft:ambient.cave"));
	}

	@Test
	void singlePartPathOffersOnlyTheNamespace() {
		assertEquals(List.of("somemod:*"), PatternSuggestions.suggest("somemod:hum"));
	}

	@Test
	void everySuggestionIsValidMatchesTheSoundAndIsNotTheExactId() {
		for (String id : List.of("minecraft:entity.villager.ambient", "minecraft:block.stone.step", "somemod:a.b/c.d", "x:y")) {
			for (String suggestion : PatternSuggestions.suggest(id)) {
				assertNull(SoundPattern.validate(suggestion), suggestion);
				assertTrue(SoundPattern.of(suggestion).matches(id), suggestion + " should match " + id);
				assertFalse(SoundPattern.of(suggestion).isExact(), suggestion);
			}
		}
	}

	@Test
	void noDuplicateSuggestions() {
		List<String> s = PatternSuggestions.suggest("minecraft:a.b.c");
		assertEquals(s.size(), s.stream().distinct().count(), s.toString());
	}

	@Test
	void idWithoutNamespaceIsTreatedAsMinecraft() {
		assertEquals("minecraft:entity.cow.*", PatternSuggestions.suggest("entity.cow.ambient").get(0));
	}
}
