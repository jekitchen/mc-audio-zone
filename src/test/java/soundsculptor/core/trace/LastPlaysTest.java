package soundsculptor.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-14.6: the hot path keeps only the last play per sound ID, in a fixed-size table. */
class LastPlaysTest {
	private final LastPlays table = new LastPlays();

	@Test
	void keepsTheLatestPlayPerId() {
		table.record("x:a", true, "w", "minecraft:overworld", 1, 2, 3, 50, false, false);
		table.record("x:a", true, "w", "minecraft:overworld", 4, 5, 6, 0, true, true);
		LastPlay play = table.get("x:a");
		assertEquals(new LastPlay(true, "w", "minecraft:overworld", 4, 5, 6, 0, true, true), play);
	}

	@Test
	void unknownIdHasNoPlay() {
		assertNull(table.get("x:never"));
	}

	@Test
	void tableIsBoundedAndForgetsTheLeastRecentlyPlayed() {
		for (int i = 0; i < LastPlays.CAPACITY; i++) {
			table.record("x:" + i, false, "w", "d", 0, 0, 0, 100, false, false);
		}
		table.record("x:0", false, "w", "d", 0, 0, 0, 100, false, false); // x:0 is now the most recent
		table.record("x:new", false, "w", "d", 0, 0, 0, 100, false, false);
		assertEquals(LastPlays.CAPACITY, table.size());
		assertTrue(table.get("x:0") != null);
		assertNull(table.get("x:1"), "least recently played is dropped");
		assertFalse(table.get("x:new") == null);
	}
}
