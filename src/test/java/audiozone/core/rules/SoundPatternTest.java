package audiozone.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-4.1 (format and matching) and FR-4.2 (allowed characters). */
class SoundPatternTest {
	private static SoundPattern p(String raw) {
		return SoundPattern.of(raw);
	}

	// --- Matching (FR-4.1) ---

	@Test
	void exactIdMatchesOnlyItself() {
		SoundPattern pattern = p("minecraft:entity.cow.ambient");
		assertTrue(pattern.matches("minecraft:entity.cow.ambient"));
		assertFalse(pattern.matches("minecraft:entity.cow.hurt"));
		assertFalse(pattern.matches("minecraft:entity.cow.ambient.extra"));
	}

	@Test
	void starMatchesAnyRunIncludingDotsAndNothing() {
		SoundPattern pattern = p("minecraft:entity.villager.*");
		assertTrue(pattern.matches("minecraft:entity.villager.ambient"));
		assertTrue(pattern.matches("minecraft:entity.villager.work_cartographer"));
		assertTrue(pattern.matches("minecraft:entity.villager."), "* also matches nothing");
		assertFalse(pattern.matches("minecraft:entity.wandering_trader.ambient"));
	}

	@Test
	void starInTheMiddleSpansDots() {
		SoundPattern pattern = p("*:block.*.step");
		assertTrue(pattern.matches("minecraft:block.stone.step"));
		assertTrue(pattern.matches("minecraft:block.wood.step"));
		assertTrue(pattern.matches("somemod:block.fancy.glass.step"));
		assertFalse(pattern.matches("minecraft:block.stone.break"));
	}

	@Test
	void patternWithoutNamespaceMatchesAnyNamespace() {
		SoundPattern bare = p("block.*.step");
		assertEquals("*:block.*.step", bare.text());
		assertTrue(bare.matches("minecraft:block.stone.step"));
		assertTrue(bare.matches("somemod:block.stone.step"));
	}

	@Test
	void patternMustMatchTheWholeId() {
		assertFalse(p("villager").matches("minecraft:entity.villager.ambient"));
		assertTrue(p("*villager*").matches("minecraft:entity.villager.ambient"));
	}

	@Test
	void loneStarMatchesEverything() {
		assertTrue(p("*").matches("minecraft:entity.cow.ambient"));
		assertTrue(p("*").matches("othermod:x"));
	}

	@Test
	void backtrackingCasesMatchCorrectly() {
		assertTrue(p("*a*b").matches("x:aaab"));
		assertFalse(p("*a*b").matches("x:aaaba"));
		assertTrue(p("x:*.*.*").matches("x:a.b.c.d"));
	}

	// --- Normalising and validating (FR-4.2) ---

	@Test
	void uppercaseBecomesLowercase() {
		SoundPattern pattern = p("Minecraft:Entity.Cow.*");
		assertEquals("minecraft:entity.cow.*", pattern.text());
		assertTrue(pattern.matches("minecraft:entity.cow.ambient"));
	}

	@Test
	void surroundingSpacesAreTrimmed() {
		assertEquals("minecraft:entity.cow.*", p("  minecraft:entity.cow.*  ").text());
	}

	@Test
	void repeatedStarsCollapse() {
		assertEquals("*:a*b", p("a**b").text());
	}

	@Test
	void allAllowedCharactersAreAccepted() {
		assertNull(SoundPattern.validate("my_mod-2:path/to/sound.x_1-*"));
	}

	@Test
	void disallowedCharacterIsRejected() {
		assertEquals(PatternError.INVALID_CHARACTER, SoundPattern.validate("entity.cow.#"));
		assertEquals(PatternError.INVALID_CHARACTER, SoundPattern.validate("entity cow"));
		assertEquals(PatternError.INVALID_CHARACTER, SoundPattern.validate("entity.cow?"));
	}

	@Test
	void emptyPatternIsRejected() {
		assertEquals(PatternError.EMPTY, SoundPattern.validate(""));
		assertEquals(PatternError.EMPTY, SoundPattern.validate("   "));
	}

	@Test
	void moreThanOneColonIsRejected() {
		assertEquals(PatternError.TOO_MANY_COLONS, SoundPattern.validate("a:b:c"));
	}

	@Test
	void emptyNamespaceOrPathIsRejected() {
		assertEquals(PatternError.EMPTY_PART, SoundPattern.validate(":entity.cow"));
		assertEquals(PatternError.EMPTY_PART, SoundPattern.validate("minecraft:"));
	}

	@Test
	void ofThrowsWithTheErrorForInvalidInput() {
		InvalidPatternException e = assertThrows(InvalidPatternException.class, () -> SoundPattern.of("entity.cow.#"));
		assertEquals(PatternError.INVALID_CHARACTER, e.error());
	}

	@Test
	void everyErrorHasATranslationKey() {
		for (PatternError error : PatternError.values()) {
			assertTrue(error.translationKey().startsWith("audiozone.pattern.error."), error.name());
		}
	}

	// --- Specificity inputs (FR-4.5) ---

	@Test
	void exactnessAndCountsDescribeSpecificity() {
		SoundPattern exact = p("minecraft:entity.villager.ambient");
		assertTrue(exact.isExact());
		assertEquals(0, exact.starCount());
		assertEquals("minecraft:entity.villager.ambient".length(), exact.literalCount());

		SoundPattern wild = p("minecraft:entity.villager.*");
		assertFalse(wild.isExact());
		assertEquals(1, wild.starCount());
		assertEquals("minecraft:entity.villager.".length(), wild.literalCount());
	}

	@Test
	void impliedNamespaceStarCounts() {
		// "block.*.step" is the same pattern as "*:block.*.step", so it scores the same.
		SoundPattern bare = p("block.*.step");
		assertEquals(2, bare.starCount());
		assertEquals(":block..step".length(), bare.literalCount());
	}

	@Test
	void equalPatternsAreEqual() {
		assertEquals(p("block.*.step"), p("*:BLOCK.*.step"));
		assertEquals(p("block.*.step").hashCode(), p("*:BLOCK.*.step").hashCode());
	}
}
