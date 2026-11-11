package soundsculptor.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The shared {@code *} matcher used by rule patterns (FR-4.1) and search (FR-3.5). */
class GlobTest {
	@Test
	void withoutStarsTheWholeTextMustBeEqual() {
		assertTrue(Glob.matches("abc", "abc"));
		assertFalse(Glob.matches("abc", "abcd"));
		assertFalse(Glob.matches("abc", "ab"));
	}

	@Test
	void starMatchesAnyRunIncludingNothing() {
		assertTrue(Glob.matches("a*c", "ac"));
		assertTrue(Glob.matches("a*c", "a.b.c"));
		assertTrue(Glob.matches("*", ""));
		assertTrue(Glob.matches("*", "anything"));
	}

	@Test
	void backtrackingFindsALaterMatch() {
		assertTrue(Glob.matches("*a*b", "aaab"));
		assertFalse(Glob.matches("*a*b", "aaaba"));
		assertTrue(Glob.matches("*ab*ab", "abxabab"));
	}

	@Test
	void trailingStarsMatchTheEnd() {
		assertTrue(Glob.matches("ab**", "ab"));
		assertFalse(Glob.matches("ab*c", "ab"));
	}

	@Test
	void emptyPatternMatchesOnlyEmptyText() {
		assertTrue(Glob.matches("", ""));
		assertFalse(Glob.matches("", "a"));
	}
}
