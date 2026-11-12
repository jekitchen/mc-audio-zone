package soundsculptor.core.zones;

import static soundsculptor.core.Fixtures.quick;
import static soundsculptor.core.Fixtures.precedence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-9 and the zone precedence matrix (section 11.4). The sound source is at the origin unless a test says
 * otherwise. Cases that need fade, Outside mode or bubbles (P6, P7, P9) arrive with v1 (M13).
 */
class PrecedenceTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	private static SetEntry villagers(String id) {
		return SetEntry.of(id, "minecraft:entity.villager.*");
	}

	private static Zone sphere(String id, double x, double y, double z, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, y, z), new Shape.Sphere(radius), quick(List.of(rules)));
	}

	private static SoundDecision decideAt(Precedence p, double x, double y, double z) {
		SoundDecision out = new SoundDecision();
		p.decide(VILLAGER, true, WORLD, OVERWORLD, x, y, z, out);
		return out;
	}

	private static SoundDecision decide(Precedence p) {
		return decideAt(p, 0, 0, 0);
	}

	// --- Matrix cases ---

	@Test
	void p1ZoneOverridesGlobalInsideOnly() {
		Zone a = new Zone("a", "A", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(20, 10, 20),
				quick(List.of(villagers("z1").withVolume(50))));
		Precedence p = precedence(List.of(villagers("g1").withMute(true)), List.of(a));

		SoundDecision inside = decide(p);
		assertEquals(50, inside.volumePercent(), 1e-6);
		assertSame(a, inside.loudnessZone());

		SoundDecision outside = decideAt(p, 100, 0, 0);
		assertEquals(0, outside.volumePercent(), 1e-6);
		assertNull(outside.loudnessZone());
	}

	@Test
	void p2GlobalMuteWithNoZones() {
		Precedence p = precedence(List.of(villagers("g1").withMute(true)), List.of());
		assertEquals(0, decide(p).volumePercent(), 1e-6);
	}

	@Test
	void p3ClosestAnchorWins() {
		Zone a = sphere("a", 3, 0, 0, 10, villagers("ra").withVolume(20));
		Zone b = sphere("b", 9, 0, 0, 20, villagers("rb").withVolume(80));
		assertEquals(20, decide(precedence(List.of(), List.of(a, b))).volumePercent(), 1e-6);
		assertEquals(20, decide(precedence(List.of(), List.of(b, a))).volumePercent(), 1e-6, "list order does not matter");
	}

	@Test
	void p4PropertiesComeFromDifferentZones() {
		Zone a = sphere("a", 2, 0, 0, 10, villagers("ra").withMute(true));
		Zone b = sphere("b", 6, 0, 0, 10, villagers("rb").withLimit(new Limit(2, 1000, false)));
		SoundDecision d = decide(precedence(List.of(), List.of(a, b)));
		assertEquals(0, d.volumePercent(), 1e-6);
		assertSame(a, d.loudnessZone());
		assertEquals(new Limit(2, 1000, false), d.limit());
		assertSame(b, d.limitZone());
	}

	@Test
	void p4bVolumeAndLimitApplyTogether() {
		Zone a = sphere("a", 2, 0, 0, 10, villagers("ra").withVolume(40));
		Zone b = sphere("b", 6, 0, 0, 10, villagers("rb").withLimit(new Limit(2, 1000, false)));
		SoundDecision d = decide(precedence(List.of(), List.of(a, b)));
		assertEquals(40, d.volumePercent(), 1e-6);
		assertEquals(new Limit(2, 1000, false), d.limit());
	}

	@Test
	void p5TieOnDistanceGoesToTheSmallerZone() {
		Zone small = sphere("small", 5, 0, 0, 6, villagers("rs").withVolume(20));
		Zone big = sphere("big", 0, 5, 0, 10, villagers("rb").withVolume(80));
		assertEquals(20, decide(precedence(List.of(), List.of(big, small))).volumePercent(), 1e-6);
		assertEquals(20, decide(precedence(List.of(), List.of(small, big))).volumePercent(), 1e-6);
	}

	@Test
	void p5DistancesWithinOneHundredthCountAsATie() {
		Zone small = sphere("small", 5, 0, 0, 6, villagers("rs").withVolume(20));
		Zone big = sphere("big", 0, 4.995, 0, 10, villagers("rb").withVolume(80));
		assertEquals(20, decide(precedence(List.of(), List.of(big, small))).volumePercent(), 1e-6);
	}

	@Test
	void p5ClearlyCloserAnchorStillBeatsASmallerZone() {
		Zone small = sphere("small", 5, 0, 0, 6, villagers("rs").withVolume(20));
		Zone big = sphere("big", 0, 4.9, 0, 10, villagers("rb").withVolume(80));
		assertEquals(80, decide(precedence(List.of(), List.of(small, big))).volumePercent(), 1e-6);
	}

	@Test
	void p5bEqualSizesGoToTheZoneHigherInTheList() {
		Zone a = sphere("a", 5, 0, 0, 10, villagers("ra").withVolume(20));
		Zone b = sphere("b", 0, 5, 0, 10, villagers("rb").withVolume(80));
		assertEquals(20, decide(precedence(List.of(), List.of(a, b))).volumePercent(), 1e-6);
		assertEquals(80, decide(precedence(List.of(), List.of(b, a))).volumePercent(), 1e-6);
	}

	@Test
	void p8CueFromZoneKeepsGlobalLoudness() {
		Zone a = sphere("a", 0, 0, 0, 10, villagers("ra").withCue(true));
		SoundDecision d = decide(precedence(List.of(villagers("g").withVolume(150)), List.of(a)));
		assertEquals(150, d.volumePercent(), 1e-6);
		assertNull(d.loudnessZone());
		assertTrue(d.cueOn());
		assertSame(a, d.cueZone());
	}

	@Test
	void p10ZoneHasNoEffectInAnotherDimension() {
		Zone a = sphere("a", 0, 0, 0, 10, villagers("ra").withMute(true));
		SoundDecision d = new SoundDecision();
		precedence(List.of(), List.of(a)).decide(VILLAGER, true, WORLD, NETHER, 0, 0, 0, d);
		assertEquals(100, d.volumePercent(), 1e-6);
		assertNull(d.loudnessZone());
	}

	@Test
	void p11NonPositionalSoundsIgnoreZones() {
		Zone a = sphere("a", 0, 0, 0, 10, SetEntry.of("all", "*").withMute(true));
		SoundDecision d = new SoundDecision();
		precedence(List.of(), List.of(a)).decide(VILLAGER, false, WORLD, OVERWORLD, 0, 0, 0, d);
		assertEquals(100, d.volumePercent(), 1e-6);
	}

	// --- Other FR-8 and FR-9 rules ---

	@Test
	void zoneFromAnotherServerHasNoEffect() {
		Zone a = sphere("a", 0, 0, 0, 10, villagers("ra").withMute(true));
		SoundDecision d = new SoundDecision();
		precedence(List.of(), List.of(a)).decide(VILLAGER, true, "other.server.net", OVERWORLD, 0, 0, 0, d);
		assertEquals(100, d.volumePercent(), 1e-6);
	}

	@Test
	void disabledZoneHasNoEffect() {
		Zone a = new Zone("a", "A", false, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(10),
				quick(List.of(villagers("ra").withMute(true))));
		assertEquals(100, decide(precedence(List.of(), List.of(a))).volumePercent(), 1e-6);
	}

	@Test
	void zoneWhoseRulesDoNotMatchTakesNoPart() {
		// A closer zone that only has cow rules must not hide a farther zone's villager rule.
		Zone cows = sphere("cows", 1, 0, 0, 10, SetEntry.of("rc", "minecraft:entity.cow.*").withMute(true));
		Zone vill = sphere("vill", 8, 0, 0, 10, villagers("rv").withVolume(30));
		SoundDecision d = decide(precedence(List.of(), List.of(cows, vill)));
		assertEquals(30, d.volumePercent(), 1e-6);
		assertSame(vill, d.loudnessZone());
	}

	@Test
	void noRulesAnywhereMeansVanilla() {
		SoundDecision d = decide(precedence(List.of(), List.of()));
		assertEquals(100, d.volumePercent(), 1e-6);
		assertNull(d.limit());
		assertFalse(d.cueOn());
		assertNull(d.loudnessRule());
	}

	@Test
	void decisionReportsTheDecidingRules() {
		SetEntry global = villagers("g").withMute(true).withCue(true);
		SetEntry zoneRule = villagers("z").withVolume(50);
		Zone a = sphere("a", 0, 0, 0, 10, zoneRule);
		SoundDecision d = decide(precedence(List.of(global), List.of(a)));
		assertSame(zoneRule, d.loudnessRule());
		assertSame(global, d.cueRule());
		assertNull(d.cueZone());
	}

	@Test
	void reusingTheDecisionObjectClearsOldResults() {
		Zone a = sphere("a", 0, 0, 0, 10, villagers("ra").withMute(true).withLimit(new Limit(2, 1000, false)).withCue(true));
		Precedence p = precedence(List.of(), List.of(a));
		SoundDecision d = new SoundDecision();
		p.decide(VILLAGER, true, WORLD, OVERWORLD, 0, 0, 0, d);
		assertSame(a, d.loudnessZone());
		p.decide(VILLAGER, true, WORLD, OVERWORLD, 50, 0, 0, d);
		assertNull(d.loudnessZone());
		assertNull(d.limit());
		assertFalse(d.cueOn());
		assertEquals(100, d.volumePercent(), 1e-6);
	}

	@Test
	void cacheStatsAddUpGlobalAndZoneLists() {
		Zone a = sphere("a", 0, 0, 0, 10, villagers("ra").withVolume(50));
		Precedence p = precedence(List.of(villagers("g").withMute(true)), List.of(a));
		decide(p);
		decide(p);
		// Each decide looks up the global list and zone a: 2 misses the first time, 2 hits the second.
		assertEquals(2, p.cacheHits());
		assertEquals(2, p.cacheMisses());
		assertEquals(1, p.zoneCount());
		assertEquals(1, p.everywhere().ruleCount());
	}

	@Test
	void zoneAtTheProfileLimitStillWorks() {
		// FR-8.9: up to 64 zones per profile.
		Zone[] zones = new Zone[Precedence.MAX_ZONES];
		for (int i = 0; i < zones.length; i++) {
			zones[i] = sphere("z" + i, 10 + i, 0, 0, 100, villagers("r" + i).withVolume(i));
		}
		assertEquals(0, decide(precedence(List.of(), List.of(zones))).volumePercent(), 1e-6, "z0 is closest");
	}
}
