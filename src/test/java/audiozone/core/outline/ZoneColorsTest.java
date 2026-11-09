package audiozone.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** PRD FR-10.4 and A-5: Okabe–Ito palette, next color, hex field. */
class ZoneColorsTest {
	@Test
	void paletteIsTheEightOkabeItoColors() {
		assertEquals(8, ZoneColor.values().length);
		assertEquals(0xE69F00, ZoneColor.ORANGE.rgb());
		assertEquals(0x56B4E9, ZoneColor.SKY_BLUE.rgb());
		assertEquals(0x009E73, ZoneColor.BLUISH_GREEN.rgb());
		assertEquals(0xF0E442, ZoneColor.YELLOW.rgb());
		assertEquals(0x0072B2, ZoneColor.BLUE.rgb());
		assertEquals(0xD55E00, ZoneColor.VERMILLION.rgb());
		assertEquals(0xCC79A7, ZoneColor.REDDISH_PURPLE.rgb());
		assertEquals(0x000000, ZoneColor.BLACK.rgb());
	}

	@Test
	void everySwatchHasANarratedName() {
		for (ZoneColor color : ZoneColor.values()) {
			assertTrue(color.translationKey().startsWith("audiozone.color."), color.name());
		}
		assertEquals("audiozone.color.sky_blue", ZoneColor.SKY_BLUE.translationKey());
	}

	@Test
	void newZonesTakeTheFirstUnusedColorInPaletteOrder() {
		assertEquals(ZoneColor.ORANGE.rgb(), ZoneColors.next(List.of()));
		assertEquals(ZoneColor.SKY_BLUE.rgb(), ZoneColors.next(List.of(ZoneColor.ORANGE.rgb())));
		assertEquals(ZoneColor.ORANGE.rgb(), ZoneColors.next(List.of(ZoneColor.SKY_BLUE.rgb())), "a gap is reused first");
	}

	@Test
	void whenEveryColorIsUsedTheyCycle() {
		List<Integer> used = new java.util.ArrayList<>();
		for (ZoneColor c : ZoneColor.values()) {
			used.add(c.rgb());
		}
		assertEquals(ZoneColor.ORANGE.rgb(), ZoneColors.next(used));
		used.add(ZoneColor.ORANGE.rgb());
		assertEquals(ZoneColor.SKY_BLUE.rgb(), ZoneColors.next(used));
	}

	@Test
	void customColorsDoNotUseUpPaletteSlots() {
		assertEquals(ZoneColor.ORANGE.rgb(), ZoneColors.next(List.of(0x123456)));
	}

	@Test
	void hexFieldAcceptsSixDigitsWithOrWithoutHash() {
		assertEquals(0x56B4E9, ZoneColors.parseHex("#56B4E9").rgb());
		assertEquals(0x56B4E9, ZoneColors.parseHex("56b4e9").rgb());
		assertEquals(0x56B4E9, ZoneColors.parseHex("  #56b4E9 ").rgb());
		assertNull(ZoneColors.parseHex("#56B4E9").error());
	}

	@Test
	void hexFieldRejectsAnythingElse() {
		assertEquals(ColorError.INVALID_HEX, ZoneColors.parseHex("#XYZ").error());
		assertEquals(ColorError.INVALID_HEX, ZoneColors.parseHex("#FFF").error());
		assertEquals(ColorError.INVALID_HEX, ZoneColors.parseHex("#1234567").error());
		assertEquals(ColorError.INVALID_HEX, ZoneColors.parseHex("#12345G").error());
		assertEquals(ColorError.EMPTY, ZoneColors.parseHex("  ").error());
		assertTrue(ColorError.INVALID_HEX.translationKey().startsWith("audiozone.color.error."));
	}

	@Test
	void formatIsUppercaseWithHash() {
		assertEquals("#E69F00", ZoneColors.format(0xE69F00));
		assertEquals("#000000", ZoneColors.format(0));
	}

	@Test
	void paletteColorsCanBeNamedFromTheirValue() {
		assertEquals(ZoneColor.VERMILLION, ZoneColor.of(0xD55E00));
		assertNull(ZoneColor.of(0x123456));
	}

	@Test
	void paletteValuesAreDistinct() {
		Set<Integer> seen = new HashSet<>();
		for (ZoneColor c : ZoneColor.values()) {
			assertTrue(seen.add(c.rgb()), c.name());
		}
	}
}
