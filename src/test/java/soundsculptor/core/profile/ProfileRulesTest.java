package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import soundsculptor.core.rules.Rule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Rules tab's list operations (PRD 6.1 Rules: add, edit, delete, move up/down; FR-4.7 disable). */
class ProfileRulesTest {
	private static final Rule A = Rule.of("r1", "minecraft:entity.villager.*").withMute(true);
	private static final Rule B = Rule.of("r2", "*:block.*.step").withVolume(50);
	private static final Rule C = Rule.of("r3", "minecraft:entity.villager.ambient").withVolume(100);

	private static Profile profile(Rule... rules) {
		return new Profile("Default", ProfileSettings.DEFAULTS, List.of(rules));
	}

	private static List<String> ids(Profile p) {
		return p.globalRules().stream().map(Rule::id).toList();
	}

	@Test
	void putAppendsANewRule() {
		assertEquals(List.of("r1", "r2"), ids(profile(A).putRule(B)));
	}

	@Test
	void putReplacesTheRuleWithTheSameIdInPlace() {
		Rule edited = B.withVolume(25);
		Profile p = profile(A, B, C).putRule(edited);
		assertEquals(List.of("r1", "r2", "r3"), ids(p));
		assertSame(edited, p.rule("r2"));
	}

	@Test
	void removeDeletesOnlyThatRule() {
		assertEquals(List.of("r1", "r3"), ids(profile(A, B, C).removeRule("r2")));
		Profile unchanged = profile(A);
		assertSame(unchanged, unchanged.removeRule("missing"));
	}

	@Test
	void moveChangesListOrderAndStopsAtTheEnds() {
		Profile p = profile(A, B, C);
		assertEquals(List.of("r1", "r3", "r2"), ids(p.moveRule("r3", -1)));
		assertEquals(List.of("r2", "r1", "r3"), ids(p.moveRule("r1", 1)));
		assertSame(p, p.moveRule("r1", -1), "the first rule cannot move up");
		assertSame(p, p.moveRule("r3", 1), "the last rule cannot move down");
		assertSame(p, p.moveRule("missing", 1));
	}

	@Test
	void ruleLooksUpById() {
		assertSame(B, profile(A, B).rule("r2"));
		assertNull(profile(A).rule("r2"));
	}

	@Test
	void newRuleIdIsNotInUse() {
		assertEquals("r1", Profile.empty("Default").newRuleId());
		assertEquals("r2", profile(A).newRuleId());
		assertEquals("r1", profile(B).newRuleId());
		assertEquals("r4", profile(A, B, C).newRuleId());
	}
}
