package audiozone.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PaletteTest {
	@Test
	void okabeItoHasEightNamedColors() {
		assertEquals(8, Palette.OKABE_ITO.size());
		assertEquals("orange", Palette.OKABE_ITO.get(0).id());
		assertEquals(0xE69F00, Palette.OKABE_ITO.get(0).rgb());
	}

	@Test
	void parsesHexWithOrWithoutHashInAnyCase() {
		assertEquals(OptionalInt.of(0xE69F00), Palette.parseHex("#E69F00"));
		assertEquals(OptionalInt.of(0xE69F00), Palette.parseHex("e69f00"));
		assertEquals(OptionalInt.of(0x56B4E9), Palette.parseHex("  #56b4E9 "));
	}

	@Test
	void rejectsAnythingElse() {
		assertTrue(Palette.parseHex("").isEmpty());
		assertTrue(Palette.parseHex("#E69F0").isEmpty());
		assertTrue(Palette.parseHex("#E69F000").isEmpty());
		assertTrue(Palette.parseHex("#GG9F00").isEmpty());
		assertTrue(Palette.parseHex("orange").isEmpty());
		assertTrue(Palette.parseHex("+E69F0").isEmpty());
	}

	@Test
	void formatsUpperCaseWithHash() {
		assertEquals("#E69F00", Palette.formatHex(0xE69F00));
		assertEquals("#000000", Palette.formatHex(0));
		assertEquals("#123456", Palette.formatHex(0xFF123456));
	}

	@Test
	void findsSwatchByColor() {
		assertEquals("sky_blue", Palette.swatchFor(0x56B4E9).id());
		assertNull(Palette.swatchFor(0x123456));
	}
}
