package audiozone.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** PRD FR-3.1 and the "Vanilla-covered sounds are out of scope" rule in section 5. */
class SoundSearchTest {
	private static final SoundEntry MUSIC = new SoundEntry("minecraft:music.game", null);
	private static final SoundEntry UI_CLICK = new SoundEntry("minecraft:ui.button.click", null);
	private static final SoundEntry DISC = new SoundEntry("minecraft:music_disc.cat", "Music Disc - cat");
	private static final SoundEntry COW = new SoundEntry("minecraft:entity.cow.ambient", "Cow moos");
	private static final SoundEntry MODDED_MUSIC = new SoundEntry("somemod:theme", null);

	@Test
	void vanillaMusicAndUiIdsAreHidden() {
		assertTrue(SoundSearch.isVanillaCovered("minecraft:music.game"));
		assertTrue(SoundSearch.isVanillaCovered("minecraft:music.menu"));
		assertTrue(SoundSearch.isVanillaCovered("minecraft:ui.button.click"));
	}

	@Test
	void jukeboxDiscsStayInScope() {
		// Jukebox records share a broad slider, so they stay in scope (section 5).
		assertFalse(SoundSearch.isVanillaCovered("minecraft:music_disc.cat"));
	}

	@Test
	void otherNamespacesAreNotHiddenByPrefix() {
		assertFalse(SoundSearch.isVanillaCovered("somemod:music.theme"));
		assertFalse(SoundSearch.isVanillaCovered("minecraft:entity.cow.ambient"));
	}

	@Test
	void searchHidesVanillaCoveredAndSeenExcludedIds() {
		List<SoundEntry> all = List.of(MUSIC, UI_CLICK, DISC, COW, MODDED_MUSIC);
		// "somemod:theme" has played in the Music category this session, so it is hidden too.
		List<SoundEntry> result = SoundSearch.search(all, SearchQuery.parse(""), Set.of("somemod:theme"));
		assertEquals(List.of(DISC, COW), result);
	}

	@Test
	void searchAppliesTheQueryAndKeepsInputOrder() {
		List<SoundEntry> all = List.of(COW, DISC);
		assertEquals(List.of(DISC), SoundSearch.search(all, SearchQuery.parse("disc"), Set.of()));
		assertEquals(List.of(COW, DISC), SoundSearch.search(all, SearchQuery.parse("minecraft"), Set.of()));
	}
}
