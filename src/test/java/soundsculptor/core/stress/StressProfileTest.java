package soundsculptor.core.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD 11.2 step 2: the "stress" profile, 50 global rules and 64 zones overlapping the pen. */
class StressProfileTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";

	@Test
	void hasFiftyGlobalRulesMixingExactAndWildcard() {
		List<SetEntry> rules = StressProfile.globalRules();
		assertEquals(50, rules.size());
		long exact = rules.stream().filter(r -> r.pattern().isExact()).count();
		assertTrue(exact >= 15 && exact <= 35, "exact: " + exact);
	}

	@Test
	void hasTheMaximumSixtyFourZonesOfEveryKind() {
		// PRD 11.2: spheres, boxes, cylinders, some fade, some Outside; plus personal bubbles.
		List<Zone> zones = StressProfile.zones(WORLD, OVERWORLD);
		assertEquals(Precedence.MAX_ZONES, zones.size());
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Sphere));
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Box));
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Cylinder));
		assertTrue(zones.stream().anyMatch(z -> z.mode() == ZoneMode.OUTSIDE));
		assertTrue(zones.stream().anyMatch(z -> z.edge().mode() == ZoneEdge.Mode.FADE));
		assertTrue(zones.stream().anyMatch(Zone::isBubble));
		assertTrue(zones.stream().allMatch(z -> !z.rules().isEmpty()));
	}

	@Test
	void everyZoneAffectsThePenCentre() {
		// The pen is 9 x 9 blocks around (0, 64, 0), with the player standing in it.
		for (Zone z : StressProfile.zones(WORLD, OVERWORLD)) {
			Point a = z.isBubble() ? StressProfile.PLAYER : z.anchor();
			double w = z.weight(-a.x(), StressProfile.PEN_Y - a.y(), -a.z(), ZoneEdge.hard());
			assertTrue(w > 0, z.id() + " weight " + w);
		}
	}

	@Test
	void profileIsTheSameEveryTime() {
		assertEquals(StressProfile.globalRules(), StressProfile.globalRules());
		assertEquals(StressProfile.zones(WORLD, OVERWORLD), StressProfile.zones(WORLD, OVERWORLD));
	}

	@Test
	void soundMixCoversTheFarmMobsAndUnmatchedSounds() {
		List<String> ids = StressProfile.soundIds();
		assertTrue(ids.size() >= 100, "ids: " + ids.size());
		assertTrue(ids.stream().anyMatch(id -> id.startsWith("minecraft:entity.villager.")));
		assertTrue(ids.stream().anyMatch(id -> id.startsWith("minecraft:entity.chicken.")));
		assertTrue(ids.stream().anyMatch(id -> id.startsWith("minecraft:entity.iron_golem.")));
		assertTrue(ids.stream().anyMatch(id -> id.startsWith("minecraft:block.hopper.") || id.contains("comparator")));
		assertTrue(ids.stream().anyMatch(id -> id.startsWith("othermod:")), "modded sounds too");
	}
}
