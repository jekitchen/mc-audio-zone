package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.rules.Rule;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Zones tab's list operations and zone rule lists (PRD 6.1 Zones, FR-8.6, FR-9 step 5). */
class ProfileZoneListTest {
	private static Zone zone(String id) {
		return new Zone(id, "Zone " + id, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(10), List.of());
	}

	private static List<String> ids(Profile p) {
		return p.zones().stream().map(Zone::id).toList();
	}

	private static Profile profile(Zone... zones) {
		return Profile.empty("Default").withZones(List.of(zones));
	}

	@Test
	void putAddsOrReplacesInPlace() {
		Profile p = profile(zone("z1"), zone("z2")).putZone(zone("z3"));
		assertEquals(List.of("z1", "z2", "z3"), ids(p));
		Zone renamed = new Zone("z2", "Hall", true, "world:1", "minecraft:overworld", new Point(1, 2, 3), new Shape.Box(4, 5, 6), List.of());
		p = p.putZone(renamed);
		assertEquals(List.of("z1", "z2", "z3"), ids(p));
		assertSame(renamed, p.zone("z2"));
	}

	@Test
	void removeDeletesTheZoneAndItsColor() {
		Profile p = profile(zone("z1"), zone("z2")).withZoneColor("z2", 0xE69F00);
		p = p.removeZone("z2");
		assertEquals(List.of("z1"), ids(p));
		assertNull(p.colorOf("z2"));
		assertSame(p, p.removeZone("missing"));
	}

	@Test
	void moveChangesOrderAndStopsAtTheEnds() {
		Profile p = profile(zone("z1"), zone("z2"), zone("z3"));
		assertEquals(List.of("z1", "z3", "z2"), ids(p.moveZone("z3", -1)));
		assertSame(p, p.moveZone("z1", -1));
		assertSame(p, p.moveZone("z3", 1));
	}

	@Test
	void newZoneIdIsNotInUse() {
		assertEquals("z1", Profile.empty("Default").newZoneId());
		assertEquals("z3", profile(zone("z1"), zone("z2")).newZoneId());
		assertEquals("z1", profile(zone("z2")).newZoneId());
	}

	@Test
	void zoneRuleListsWorkLikeTheGlobalList() {
		RuleTarget target = RuleTarget.zone("z1");
		Profile p = profile(zone("z1"), zone("z2")).putRule(Rule.of("r1", "*cow*").withMute(true));
		assertEquals("r1", target.newRuleId(p), "IDs are unique per list, so the zone starts at r1");
		p = target.put(p, Rule.of(target.newRuleId(p), "*villager*").withVolume(50));
		p = target.put(p, Rule.of(target.newRuleId(p), "*bell*").withMute(true));
		assertEquals(List.of("r1", "r2"), p.zone("z1").rules().stream().map(Rule::id).toList());
		assertTrue(p.zone("z2").rules().isEmpty(), "other zones are untouched");
		assertEquals(1, p.globalRules().size(), "the global list is untouched");
		p = target.move(p, "r2", -1);
		assertEquals("*:*bell*", p.zone("z1").rules().get(0).pattern().text());
		p = target.put(p, target.rule(p, "r1").withVolume(20));
		assertEquals(20, target.rule(p, "r1").volume());
		p = target.remove(p, "r2");
		assertEquals(List.of("r1"), p.zone("z1").rules().stream().map(Rule::id).toList());
	}

	@Test
	void globalTargetUsesTheGlobalList() {
		Profile p = RuleTarget.GLOBAL.put(Profile.empty("Default"), Rule.of("r1", "*cow*").withMute(true));
		assertEquals(1, p.globalRules().size());
		assertEquals(List.of(), RuleTarget.zone("missing").rules(p));
		assertSame(p, RuleTarget.zone("missing").put(p, Rule.of("r1", "*")), "a missing zone changes nothing");
	}
}
