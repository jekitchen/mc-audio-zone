package soundsculptor.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-3.5: the search syntax, one test per row of the table plus edge cases. */
class SearchQueryTest {
	private static final SoundEntry VILLAGER_AMBIENT = new SoundEntry("minecraft:entity.villager.ambient", "Villager mumbles");
	private static final SoundEntry VILLAGER_HURT = new SoundEntry("minecraft:entity.villager.hurt", "Villager hurts");
	private static final SoundEntry ZOMBIE_AMBIENT = new SoundEntry("minecraft:entity.zombie.ambient", "Zombie groans");
	private static final SoundEntry ZOMBIE_HURT = new SoundEntry("minecraft:entity.zombie.hurt", "Zombie hurts");
	private static final SoundEntry COW_AMBIENT = new SoundEntry("minecraft:entity.cow.ambient", "Cow moos");
	private static final SoundEntry GLASS_BREAK = new SoundEntry("minecraft:block.glass.break", "Glass breaks");
	private static final SoundEntry STONE_STEP = new SoundEntry("minecraft:block.stone.step", "Footsteps");
	private static final SoundEntry NO_SUBTITLE = new SoundEntry("somemod:machine.hum", null);

	private static final List<SoundEntry> ALL = List.of(
			VILLAGER_AMBIENT, VILLAGER_HURT, ZOMBIE_AMBIENT, ZOMBIE_HURT, COW_AMBIENT, GLASS_BREAK, STONE_STEP, NO_SUBTITLE);

	private static List<SoundEntry> search(String query) {
		SearchQuery q = SearchQuery.parse(query);
		return ALL.stream().filter(q::matches).toList();
	}

	// --- Table rows ---

	@Test
	void plainWordMeansContains() {
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("villager"));
	}

	@Test
	void spacesMeanAllWords() {
		assertEquals(List.of(VILLAGER_HURT), search("villager hurt"));
	}

	@Test
	void barMeansEither() {
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT, ZOMBIE_AMBIENT, ZOMBIE_HURT), search("villager|zombie"));
	}

	@Test
	void barSplitsGroupsBeforeSpacesMeanAnd() {
		assertEquals(List.of(VILLAGER_HURT, ZOMBIE_HURT), search("villager hurt | zombie hurt"));
	}

	@Test
	void minusExcludes() {
		assertEquals(List.of(VILLAGER_HURT), search("villager -ambient"));
	}

	@Test
	void minusAloneKeepsEverythingElse() {
		List<SoundEntry> result = search("-ambient");
		assertFalse(result.contains(VILLAGER_AMBIENT));
		assertFalse(result.contains(COW_AMBIENT));
		assertTrue(result.contains(GLASS_BREAK));
	}

	@Test
	void quotesMatchAnExactPhrase() {
		assertEquals(List.of(GLASS_BREAK), search("\"glass break\""), "subtitle 'Glass breaks' contains the phrase");
		SearchQuery reversed = SearchQuery.parse("\"break glass\"");
		assertFalse(reversed.matches(GLASS_BREAK));
	}

	@Test
	void starMatchesAnyCharacters() {
		assertEquals(List.of(STONE_STEP), search("block.*.step"));
	}

	// --- Acceptance checks (FR-3) ---

	@Test
	void wordsThatNoSingleSoundHasTogetherFindNothing() {
		assertEquals(List.of(), search("villager zombie"));
	}

	@Test
	void noResultContainsAnExcludedWord() {
		for (SoundEntry e : search("villager -ambient")) {
			assertFalse(e.id().contains("ambient"));
		}
	}

	// --- Matching details (FR-3.5 text) ---

	@Test
	void matchingIgnoresCase() {
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("VILLAGER"));
		assertEquals(List.of(VILLAGER_AMBIENT), search("Mumbles"));
	}

	@Test
	void wordsCanMatchTheSubtitleText() {
		assertEquals(List.of(COW_AMBIENT), search("moos"));
		assertEquals(List.of(STONE_STEP), search("footsteps"));
	}

	@Test
	void eachWordCanMatchIdOrSubtitle() {
		// "cow" is in the ID, "moos" only in the subtitle.
		assertEquals(List.of(COW_AMBIENT), search("cow moos"));
	}

	@Test
	void excludedWordInTheSubtitleAlsoExcludes() {
		assertEquals(List.of(VILLAGER_HURT), search("villager -mumbles"));
	}

	@Test
	void soundsWithoutSubtitlesMatchOnTheirId() {
		assertEquals(List.of(NO_SUBTITLE), search("hum"));
	}

	@Test
	void excludedPhraseWorks() {
		assertEquals(List.of(VILLAGER_AMBIENT), search("villager -\"villager hurts\""));
	}

	@Test
	void barInsideQuotesIsLiteral() {
		SoundEntry odd = new SoundEntry("x:a", "left|right");
		assertTrue(SearchQuery.parse("\"left|right\"").matches(odd));
		assertFalse(SearchQuery.parse("\"left|right\"").matches(VILLAGER_AMBIENT));
	}

	@Test
	void unclosedQuoteRunsToTheEnd() {
		assertEquals(List.of(GLASS_BREAK), search("\"glass break"));
	}

	@Test
	void starWorksInsideAWordAndAtItsEdges() {
		assertEquals(List.of(VILLAGER_AMBIENT, ZOMBIE_AMBIENT), search("entity.*e*.ambient"));
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("vill*"));
	}

	// --- Empty and odd input ---

	@Test
	void emptyQueryMatchesEverything() {
		assertEquals(ALL, search(""));
		assertEquals(ALL, search("   "));
		assertTrue(SearchQuery.parse("  ").isEmpty());
	}

	@Test
	void emptyGroupsAreIgnored() {
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("villager | "));
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("|villager||"));
	}

	@Test
	void loneMinusAndEmptyQuotesAreIgnored() {
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("villager -"));
		assertEquals(List.of(VILLAGER_AMBIENT, VILLAGER_HURT), search("villager \"\""));
	}

	@Test
	void loneStarMatchesEverything() {
		assertEquals(ALL, search("*"));
	}
}
