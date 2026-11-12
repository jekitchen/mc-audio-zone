package soundsculptor.core.lists;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;

import org.junit.jupiter.api.Test;

/** PRD 6.1: the Rules tab row summary, shown as separate chips so no sentence is built from fragments (9.3). */
class EntrySummaryTest {
	private static final TestEnglish EN = new TestEnglish();

	private static List<String> chips(SetEntry rule) {
		return EntrySummary.parts(rule).stream().map(p -> p.text(EN)).toList();
	}

	@Test
	void mutedWithCueOn() {
		SetEntry rule = SetEntry.of("r", "*villager*").withMute(true).withCue(true);
		assertEquals(List.of("Muted", "Cue on"), chips(rule));
		assertEquals("Muted, Cue on", EntrySummary.text(rule, EN));
	}

	@Test
	void everySettingInAFixedOrder() {
		SetEntry rule = SetEntry.of("r", "*").withCue(false).withLimit(new Limit(3, 1000, false)).withVolume(60).withMute(false);
		assertEquals(List.of("Not muted", "Volume 60%", "At most 3 per 1 s", "Cue off"), chips(rule));
	}

	@Test
	void perSourceLimitAndShortWindows() {
		assertEquals(List.of("At most 2 per 0.25 s, per source"),
				chips(SetEntry.of("r", "*").withLimit(new Limit(2, 250, true))));
		assertEquals(List.of("At most 1 per 0.05 s"), chips(SetEntry.of("r", "*").withLimit(new Limit(1, 50, false))));
	}

	@Test
	void disabledComesFirst() {
		assertEquals(List.of("Off", "Muted"), chips(SetEntry.of("r", "*").withMute(true).withEnabled(false)));
	}

	@Test
	void ruleThatSetsNothingSaysSo() {
		assertEquals(List.of("Sets nothing yet"), chips(SetEntry.of("r", "*")));
		assertEquals(List.of("Off", "Sets nothing yet"), chips(SetEntry.of("r", "*").withEnabled(false)));
	}

	@Test
	void matchCountCountsKnownIds() {
		SetEntry rule = SetEntry.of("r", "minecraft:entity.villager.*");
		List<String> ids = List.of("minecraft:entity.villager.ambient", "minecraft:entity.villager.trade",
				"minecraft:entity.cow.ambient");
		assertEquals(2, EntrySummary.matchCount(rule, ids));
		assertEquals("Matching sounds: 2", EntrySummary.matchesPart(2).text(EN));
	}
}
