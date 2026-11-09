package audiozone.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TruncationTest {
	/** Stub font: every character (including the ellipsis) is 6 pixels wide. */
	private static final ToIntFunction<String> SIX_PX = s -> s.codePointCount(0, s.length()) * 6;

	@Test
	void textThatFitsIsUnchanged() {
		assertEquals("villager", Truncation.truncate("villager", 48, SIX_PX));
	}

	@Test
	void longTextGetsEllipsis() {
		// 30 px: 4 characters (24 px) + ellipsis (6 px).
		assertEquals("mine…", Truncation.truncate("minecraft:entity.villager.ambient", 30, SIX_PX));
	}

	@Test
	void trailingSpaceBeforeEllipsisIsRemoved() {
		assertEquals("ab…", Truncation.truncate("ab cdef", 24, SIX_PX));
	}

	@Test
	void tooNarrowForEllipsisGivesEmpty() {
		assertEquals("", Truncation.truncate("abc", 5, SIX_PX));
	}

	@Test
	void neverSplitsSurrogatePairs() {
		String withEmoji = "a🔊bcdef"; // a, 🔊, b, c...
		// Width stub counts code points; cutting after "a" + high surrogate would be invalid.
		ToIntFunction<String> perChar = s -> s.length() * 6; // treats each UTF-16 unit as 6 px
		String result = Truncation.truncate(withEmoji, 18, perChar);
		assertEquals("a…", result);
	}
}
