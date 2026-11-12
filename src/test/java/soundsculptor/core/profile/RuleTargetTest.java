package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;

import org.junit.jupiter.api.Test;

/**
 * The Rules tab's list operations (PRD 6.1 Rules: add, edit, delete, move up/down; FR-4.7 disable), which until the
 * Sound Sets UI edit Everywhere's Quick changes (FR-4.10).
 */
class RuleTargetTest {
	private static final RuleTarget G = RuleTarget.GLOBAL;
	private static final SetEntry A = SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true);
	private static final SetEntry B = SetEntry.of("r2", "*:block.*.step").withVolume(50);
	private static final SetEntry C = SetEntry.of("r3", "minecraft:entity.villager.ambient").withVolume(100);

	private static Profile profile(SetEntry... rules) {
		return new Profile("Default", ProfileSettings.DEFAULTS, quick(List.of(rules)));
	}

	private static List<String> ids(Profile p) {
		return G.rules(p).stream().map(SetEntry::id).toList();
	}

	@Test
	void putAppendsANewRule() {
		assertEquals(List.of("r1", "r2"), ids(G.put(profile(A), B)));
	}

	@Test
	void putReplacesTheRuleWithTheSameIdInPlace() {
		SetEntry edited = B.withVolume(25);
		Profile p = G.put(profile(A, B, C), edited);
		assertEquals(List.of("r1", "r2", "r3"), ids(p));
		assertSame(edited, G.rule(p, "r2"));
	}

	@Test
	void removeDeletesOnlyThatRule() {
		assertEquals(List.of("r1", "r3"), ids(G.remove(profile(A, B, C), "r2")));
		Profile unchanged = profile(A);
		assertSame(unchanged, G.remove(unchanged, "missing"));
	}

	@Test
	void moveChangesListOrderAndStopsAtTheEnds() {
		Profile p = profile(A, B, C);
		assertEquals(List.of("r1", "r3", "r2"), ids(G.move(p, "r3", -1)));
		assertEquals(List.of("r2", "r1", "r3"), ids(G.move(p, "r1", 1)));
		assertSame(p, G.move(p, "r1", -1), "the first rule cannot move up");
		assertSame(p, G.move(p, "r3", 1), "the last rule cannot move down");
		assertSame(p, G.move(p, "missing", 1));
	}

	@Test
	void ruleLooksUpById() {
		assertSame(B, G.rule(profile(A, B), "r2"));
		assertNull(G.rule(profile(A), "r2"));
	}

	@Test
	void newRuleIdIsNotInUse() {
		assertEquals("r1", G.newRuleId(Profile.empty("Default")));
		assertEquals("r2", G.newRuleId(profile(A)));
		assertEquals("r1", G.newRuleId(profile(B)));
		assertEquals("r4", G.newRuleId(profile(A, B, C)));
	}

	@Test
	void theFirstRuleCreatesQuickChangesAndTheLastOneRemovesIt() {
		Profile one = G.put(Profile.empty("Default"), A);
		assertEquals(List.of(new Attachment(SoundSet.QUICK_ID, true)), one.everywhere().attached());
		assertEquals(List.of(A), one.everywhere().quickChanges().entries());
		assertEquals(PlaceSets.EMPTY, G.remove(one, "r1").everywhere(), "no empty Quick changes is left behind");
	}

	@Test
	void libraryAttachmentsAreKept() {
		Profile p = Profile.empty("Default").withEverywhere(PlaceSets.EMPTY.attach("set-farm"));
		p = G.remove(G.put(p, A), "r1");
		assertEquals(List.of(new Attachment("set-farm", true)), p.everywhere().attached());
	}

	@Test
	void aFullQuickChangesRefusesANewRuleWithoutThrowing() {
		List<SetEntry> full = new ArrayList<>();
		for (int i = 0; i < SoundSet.MAX_ENTRIES; i++) {
			full.add(SetEntry.of("f" + i, "x:s" + i).withMute(true));
		}
		Profile p = new Profile("Default", ProfileSettings.DEFAULTS, quick(full));
		SetEntry extra = SetEntry.of("new", "x:new").withMute(true);
		assertNotNull(G.canPut(p, extra));
		assertSame(p, G.put(p, extra), "refused: the profile is unchanged");
		assertSame(p, G.copyIn(p, extra));
		assertNull(G.canPut(p, full.get(0).withVolume(10)), "editing an entry that is there still works");
		assertEquals(10, G.rule(G.put(p, full.get(0).withVolume(10)), "f0").volume());
	}

	@Test
	void aZoneThatNoLongerExistsHasNoRules() {
		RuleTarget gone = RuleTarget.zone("gone");
		Profile p = profile(A);
		assertTrue(gone.rules(p).isEmpty());
		assertSame(p, gone.put(p, B));
	}
}
