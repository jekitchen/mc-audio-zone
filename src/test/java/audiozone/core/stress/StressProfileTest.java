package audiozone.core.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import audiozone.core.rules.Rule;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD 11.2 step 2: the "stress" profile, 50 global rules and 64 zones overlapping the pen. */
class StressProfileTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";

	@Test
	void hasFiftyGlobalRulesMixingExactAndWildcard() {
		List<Rule> rules = StressProfile.globalRules();
		assertEquals(50, rules.size());
		long exact = rules.stream().filter(r -> r.pattern().isExact()).count();
		assertTrue(exact >= 15 && exact <= 35, "exact: " + exact);
	}

	@Test
	void hasTheMaximumSixtyFourZonesInBothShapes() {
		List<Zone> zones = StressProfile.zones(WORLD, OVERWORLD);
		assertEquals(Precedence.MAX_ZONES, zones.size());
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Sphere));
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Box));
		assertTrue(zones.stream().allMatch(z -> !z.rules().isEmpty()));
	}

	@Test
	void everyZoneOverlapsThePen() {
		// The pen is 9 x 9 blocks around (0, 64, 0); each zone contains at least its centre.
		for (Zone z : StressProfile.zones(WORLD, OVERWORLD)) {
			assertTrue(z.shape().contains(-z.anchor().x(), StressProfile.PEN_Y - z.anchor().y(), -z.anchor().z()), z.id());
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
