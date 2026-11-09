package audiozone.core.recent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** PRD FR-1.1 to FR-1.3: what the recently played list records, groups, keeps and sorts. */
class RecentSoundsTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String EAT = "minecraft:entity.generic.eat";

	private final RecentSounds log = new RecentSounds();

	/** A positional, audible sound 8 blocks away that played normally. */
	private void heard(String id, long nowMs) {
		log.record(id, "neutral", true, 8.0, true, PlayStatus.PLAYING, null, nowMs);
	}

	private List<RecentRow> rows(long nowMs) {
		return log.snapshot(nowMs, SortOrder.MOST_RECENT, null);
	}

	// --- What gets recorded (FR-1.1) ---

	@Test
	void audiblePositionalSoundIsRecorded() {
		heard(VILLAGER, 1000);
		List<RecentRow> rows = rows(1000);
		assertEquals(1, rows.size());
		RecentRow row = rows.get(0);
		assertEquals(VILLAGER, row.id());
		assertEquals("neutral", row.category());
		assertEquals(1, row.count());
		assertEquals(1000, row.lastPlayedMs());
		assertEquals(DistanceBand.NEAR, row.band());
		assertEquals(PlayStatus.PLAYING, row.status());
	}

	@Test
	void soundOutOfVanillaRangeIsNotRecorded() {
		log.record(VILLAGER, "neutral", true, 30.0, false, PlayStatus.PLAYING, null, 1000);
		assertTrue(rows(1000).isEmpty());
	}

	@Test
	void nonPositionalSoundIsRecordedWhateverTheDistance() {
		log.record(EAT, "player", false, 0.0, true, PlayStatus.PLAYING, null, 1000);
		assertEquals(DistanceBand.AROUND_YOU, rows(1000).get(0).band());
	}

	@Test
	void mutedAndLimitedPlaysAreRecordedWithTheirStatus() {
		log.record(VILLAGER, "neutral", true, 3.0, true, PlayStatus.MUTED, "Trading hall", 1000);
		log.record(COW, "neutral", true, 3.0, true, PlayStatus.LIMITED, null, 1100);
		List<RecentRow> rows = rows(1100);
		RecentRow villager = rows.get(1);
		assertEquals(PlayStatus.MUTED, villager.status());
		assertEquals("Trading hall", villager.zoneName());
		assertEquals(PlayStatus.LIMITED, rows.get(0).status());
		assertNull(rows.get(0).zoneName());
	}

	@Test
	void vanillaCoveredCategoriesAreNeverRecorded() {
		log.record("minecraft:music.game", "music", false, 0, true, PlayStatus.PLAYING, null, 1000);
		log.record("minecraft:ui.button.click", "ui", false, 0, true, PlayStatus.PLAYING, null, 1000);
		log.record("somemod:radio", "voice", false, 0, true, PlayStatus.PLAYING, null, 1000);
		assertTrue(rows(1000).isEmpty());
	}

	@Test
	void idsSeenInExcludedCategoriesAreRememberedForSearch() {
		log.record("somemod:theme", "music", false, 0, true, PlayStatus.PLAYING, null, 1000);
		heard(VILLAGER, 1000);
		assertEquals(java.util.Set.of("somemod:theme"), log.seenExcludedIds());
	}

	@Test
	void categorySeenThisSessionIsKeptAfterTheRowExpires() {
		// FR-3.2: search shows "Seen as: <category>" for any sound that played this session.
		heard(COW, 1000);
		rows(1000 + RecentSounds.WINDOW_MS + 1);
		assertEquals("neutral", log.seenCategory(COW));
		assertNull(log.seenCategory(VILLAGER));
	}

	// --- Grouping and counting (FR-1.2, FR-1.3) ---

	@Test
	void playsOfOneIdShareARowWithACount() {
		heard(VILLAGER, 1000);
		heard(VILLAGER, 2000);
		log.record(VILLAGER, "neutral", true, 2.0, true, PlayStatus.MUTED, null, 3000);
		List<RecentRow> rows = rows(3000);
		assertEquals(1, rows.size());
		RecentRow row = rows.get(0);
		assertEquals(3, row.count());
		assertEquals(3000, row.lastPlayedMs());
		assertEquals(DistanceBand.CLOSE, row.band(), "band and status come from the latest play");
		assertEquals(PlayStatus.MUTED, row.status());
	}

	@Test
	void countOnlyIncludesTheLastThreeMinutes() {
		heard(VILLAGER, 0);
		heard(VILLAGER, 100_000);
		heard(VILLAGER, 170_000);
		assertEquals(3, rows(170_000).get(0).count());
		assertEquals(2, rows(RecentSounds.WINDOW_MS + 1).get(0).count(), "the play at 0 s has aged out");
	}

	@Test
	void rowDisappearsThreeMinutesAfterItsLastPlay() {
		heard(VILLAGER, 1000);
		assertEquals(1, rows(1000 + RecentSounds.WINDOW_MS - 1).size());
		assertTrue(rows(1000 + RecentSounds.WINDOW_MS + 1000).isEmpty());
	}

	@Test
	void atMostTwoHundredIdsAreKeptAndTheOldestGoesFirst() {
		for (int i = 0; i <= RecentSounds.MAX_IDS; i++) {
			heard("x:sound" + i, 1000 + i);
		}
		List<RecentRow> rows = rows(2000);
		assertEquals(RecentSounds.MAX_IDS, rows.size());
		assertFalse(rows.stream().anyMatch(r -> r.id().equals("x:sound0")), "least recently played is dropped");
		assertTrue(rows.stream().anyMatch(r -> r.id().equals("x:sound" + RecentSounds.MAX_IDS)));
	}

	// --- Sorting (FR-1.3) ---

	@Test
	void defaultSortIsMostRecentFirst() {
		heard(COW, 1000);
		heard(VILLAGER, 2000);
		assertEquals(List.of(VILLAGER, COW), ids(log.snapshot(2000, SortOrder.MOST_RECENT, null)));
	}

	@Test
	void sortByPlayCountPutsTheBusiestFirst() {
		heard(VILLAGER, 1000);
		heard(COW, 1100);
		heard(COW, 1200);
		assertEquals(List.of(COW, VILLAGER), ids(log.snapshot(2000, SortOrder.PLAY_COUNT, null)));
	}

	@Test
	void sortByNameUsesTheDisplayNameIgnoringCase() {
		heard(VILLAGER, 1000);
		heard(COW, 1100);
		Map<String, String> names = Map.of(VILLAGER, "Villager mumbles", COW, "cow moos");
		assertEquals(List.of(COW, VILLAGER), ids(log.snapshot(2000, SortOrder.NAME, names::get)));
	}

	@Test
	void sortByNameFallsBackToTheIdWithoutADisplayName() {
		heard(VILLAGER, 1000);
		heard(COW, 1100);
		assertEquals(List.of(COW, VILLAGER), ids(log.snapshot(2000, SortOrder.NAME, null)));
	}

	@Test
	void clearEmptiesTheList() {
		heard(VILLAGER, 1000);
		log.clear();
		assertTrue(rows(1000).isEmpty());
	}

	private static List<String> ids(List<RecentRow> rows) {
		return rows.stream().map(RecentRow::id).toList();
	}
}
