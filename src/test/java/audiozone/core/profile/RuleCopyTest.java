package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audiozone.core.rules.Rule;
import audiozone.core.rules.SoundPattern;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

/** "Add existing rule" on a zone's rules: copies are independent of the original. */
class RuleCopyTest {
	private static Zone zone(String id, Rule... rules) {
		return new Zone(id, "Zone " + id, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(10), List.of(rules));
	}

	private static final Rule GLOBAL_MUTE = Rule.of("r1", "minecraft:entity.villager.*").withMute(true);
	private static final Rule A_RULE = Rule.of("r1", "*cow*").withVolume(40);

	private static Profile profile() {
		return Profile.empty("Default").putRule(GLOBAL_MUTE).withZones(List.of(zone("a", A_RULE), zone("b")));
	}

	@Test
	void otherRulesListTheGlobalListThenOtherZones() {
		List<RuleTarget.Sourced> others = RuleTarget.zone("b").otherRules(profile());
		assertEquals(2, others.size());
		assertEquals(RuleTarget.GLOBAL, others.get(0).source());
		assertEquals(GLOBAL_MUTE, others.get(0).rule());
		assertEquals(RuleTarget.zone("a"), others.get(1).source());
		assertTrue(RuleTarget.zone("a").otherRules(profile()).stream().noneMatch(s -> s.source().equals(RuleTarget.zone("a"))),
				"a zone's own rules are not offered");
	}

	@Test
	void aCopyGetsANewIdAndTheSameSettings() {
		RuleTarget b = RuleTarget.zone("b");
		Profile p = b.copyIn(b.copyIn(profile(), GLOBAL_MUTE), A_RULE);
		List<Rule> copies = b.rules(p);
		assertEquals(List.of("r1", "r2"), copies.stream().map(Rule::id).toList());
		assertEquals(GLOBAL_MUTE.pattern(), copies.get(0).pattern());
		assertEquals(Boolean.TRUE, copies.get(0).mute());
		assertEquals(40, copies.get(1).volume());
	}

	@Test
	void copiesAreNotLinked() {
		RuleTarget b = RuleTarget.zone("b");
		Profile p = b.copyIn(profile(), GLOBAL_MUTE);
		// Change the copy: the original stays.
		p = b.put(p, b.rule(p, "r1").withMute(null).withVolume(20));
		assertEquals(Boolean.TRUE, p.rule("r1").mute());
		assertEquals(null, p.rule("r1").volume());
		// Change the original: the copy stays.
		p = p.putRule(p.rule("r1").withEnabled(false));
		assertTrue(b.rule(p, "r1").enabled());
		assertEquals(20, b.rule(p, "r1").volume());
		// Delete the original: the copy stays.
		p = p.removeRule("r1");
		assertEquals(1, b.rules(p).size());
	}

	@Test
	void hasPatternFindsADuplicate() {
		RuleTarget b = RuleTarget.zone("b");
		Profile p = b.copyIn(profile(), GLOBAL_MUTE);
		assertTrue(b.hasPattern(p, SoundPattern.of("minecraft:entity.villager.*")));
		assertFalse(b.hasPattern(p, SoundPattern.of("*cow*")));
	}
}
