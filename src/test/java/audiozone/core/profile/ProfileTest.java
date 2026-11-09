package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audiozone.core.rules.Rule;
import org.junit.jupiter.api.Test;

/** PRD FR-1.5 quick actions on exact-ID rules (M4: Mute). */
class ProfileTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	@Test
	void muteCreatesOneExactIdRule() {
		Profile p = Profile.empty("Default").muteExact(VILLAGER);
		assertEquals(1, p.globalRules().size());
		Rule rule = p.globalRules().get(0);
		assertEquals(VILLAGER, rule.pattern().text());
		assertEquals(Boolean.TRUE, rule.mute());
		assertTrue(p.isExactMuted(VILLAGER));
	}

	@Test
	void mutingTwiceDoesNotDuplicate() {
		Profile p = Profile.empty("Default").muteExact(VILLAGER).muteExact(VILLAGER);
		assertEquals(1, p.globalRules().size());
	}

	@Test
	void unmuteRemovesARuleThatSetsNothingElse() {
		Profile p = Profile.empty("Default").muteExact(VILLAGER).unmuteExact(VILLAGER);
		assertTrue(p.globalRules().isEmpty());
		assertFalse(p.isExactMuted(VILLAGER));
	}

	@Test
	void unmuteKeepsOtherPropertiesOfTheRule() {
		Rule withVolume = Rule.of("r1", VILLAGER).withMute(true).withVolume(50);
		Profile p = new Profile("Default", ProfileSettings.DEFAULTS, java.util.List.of(withVolume)).unmuteExact(VILLAGER);
		assertEquals(1, p.globalRules().size());
		assertNull(p.globalRules().get(0).mute());
		assertEquals(50, p.globalRules().get(0).volume());
	}

	@Test
	void muteReenablesADisabledExactRule() {
		Rule disabled = Rule.of("r1", VILLAGER).withMute(true).withEnabled(false);
		Profile p = new Profile("Default", ProfileSettings.DEFAULTS, java.util.List.of(disabled));
		assertFalse(p.isExactMuted(VILLAGER));
		assertTrue(p.muteExact(VILLAGER).isExactMuted(VILLAGER));
	}

	@Test
	void wildcardRulesAreNotExactRules() {
		Rule wildcard = Rule.of("r1", "minecraft:entity.villager.*").withMute(true);
		Profile p = new Profile("Default", ProfileSettings.DEFAULTS, java.util.List.of(wildcard));
		assertNull(p.exactRule(VILLAGER));
		assertEquals(2, p.muteExact(VILLAGER).globalRules().size());
	}

	@Test
	void unmuteWithoutRuleChangesNothing() {
		Profile p = Profile.empty("Default");
		assertSame(p, p.unmuteExact(VILLAGER));
	}
}
