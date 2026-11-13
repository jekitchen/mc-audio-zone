package soundsculptor.core.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import soundsculptor.core.recent.DistanceBand;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;

import org.junit.jupiter.api.Test;

/** PRD FR-1.0, FR-1.3, FR-3.1: one browser, with or without the Recently played filter. */
class SoundBrowserTest {
	private static final SoundEntry AMBIENT = new SoundEntry("minecraft:entity.villager.ambient", "Villager mumbles");
	private static final SoundEntry HURT = new SoundEntry("minecraft:entity.villager.hurt", "Villager hurts");
	private static final SoundEntry ZOMBIE = new SoundEntry("minecraft:entity.zombie.ambient", "Zombie groans");
	private static final SoundEntry MUSIC = new SoundEntry("minecraft:music.menu", null);
	private static final List<SoundEntry> ALL = List.of(AMBIENT, HURT, ZOMBIE, MUSIC);

	private static RecentRow recent(String id, int count, long last) {
		return new RecentRow(id, "neutral", count, last, DistanceBand.NEAR, PlayStatus.PLAYING, null);
	}

	private static final RecentRow R_AMBIENT = recent(AMBIENT.id(), 2, 1000);
	private static final RecentRow R_ZOMBIE = recent(ZOMBIE.id(), 9, 2000);
	private static final List<RecentRow> RECENT = List.of(R_AMBIENT, R_ZOMBIE);

	private static List<String> ids(List<BrowserRow> rows) {
		return rows.stream().map(BrowserRow::soundId).toList();
	}

	private static List<BrowserRow> recentOnly(String query, SortOrder sort) {
		return SoundBrowser.rows(RECENT, ALL, SearchQuery.parse(query), true, sort, Set.of());
	}

	@Test
	void filterOnShowsOnlyRecentMatches() {
		assertEquals(List.of(ZOMBIE.id(), AMBIENT.id()), ids(recentOnly("ambient", SortOrder.MOST_RECENT)));
		assertEquals(List.of(ZOMBIE.id()), ids(recentOnly("zombie", SortOrder.MOST_RECENT)));
		assertEquals(List.of(AMBIENT.id()), ids(recentOnly("mumbles", SortOrder.MOST_RECENT)));
		assertEquals(List.of(), ids(recentOnly("hurt", SortOrder.MOST_RECENT)));
	}

	@Test
	void filterOnSortsByTheChosenOrder() {
		assertEquals(List.of(ZOMBIE.id(), AMBIENT.id()), ids(recentOnly("", SortOrder.PLAY_COUNT)));
		assertEquals(List.of(AMBIENT.id(), ZOMBIE.id()), ids(recentOnly("", SortOrder.NAME)));
	}

	@Test
	void filterOffListsEveryKnownMatch() {
		List<BrowserRow> rows = SoundBrowser.rows(RECENT, ALL, SearchQuery.parse("villager"), false, SortOrder.MOST_RECENT, Set.of());
		assertEquals(List.of(AMBIENT.id(), HURT.id()), ids(rows));
		assertEquals("Villager hurts", rows.get(1).subtitle());
	}

	@Test
	void filterOffHidesVanillaCoveredAndSeenExcluded() {
		List<BrowserRow> rows = SoundBrowser.rows(RECENT, ALL, SearchQuery.parse(""), false, SortOrder.MOST_RECENT, Set.of(HURT.id()));
		assertEquals(List.of(AMBIENT.id(), ZOMBIE.id()), ids(rows));
	}

	@Test
	void emptyQueryShowsAll() {
		SearchQuery empty = SearchQuery.parse("");
		assertEquals(2, SoundBrowser.rows(RECENT, ALL, empty, true, SortOrder.MOST_RECENT, Set.of()).size());
		assertEquals(3, SoundBrowser.rows(RECENT, ALL, empty, false, SortOrder.MOST_RECENT, Set.of()).size());
	}

	@Test
	void recentDataCarriedWhenFilterOff() {
		List<BrowserRow> rows = SoundBrowser.rows(RECENT, ALL, SearchQuery.parse(""), false, SortOrder.MOST_RECENT, Set.of());
		assertSame(R_AMBIENT, rows.get(0).recent());
		assertNull(rows.get(1).recent());
		assertSame(R_ZOMBIE, rows.get(2).recent());
	}

	@Test
	void recentSoundMissingFromKnownListIsStillShown() {
		RecentRow modded = recent("mod:thing.hum", 1, 3000);
		List<BrowserRow> rows = SoundBrowser.rows(List.of(modded), ALL, SearchQuery.parse("hum"), true, SortOrder.MOST_RECENT, Set.of());
		assertEquals(List.of("mod:thing.hum"), ids(rows));
		assertNull(rows.get(0).subtitle());
	}
}
