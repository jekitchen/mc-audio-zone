package soundsculptor.core.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.EntryResolution;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/**
 * PRD 11.2 step 2: the "stress" profile, Everywhere with 2 sets of 25 entries and 64 zones overlapping the pen, each
 * with sets from a shared pool.
 */
class StressProfileTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";

	@Test
	void everywhereHasTwoSetsOfTwentyFiveMixingSinglesAndGroups() {
		SetLibrary library = StressProfile.library();
		List<Attachment> attached = StressProfile.everywhere().attached();
		assertEquals(2, attached.size());
		for (Attachment a : attached) {
			assertTrue(a.enabled());
			List<SetEntry> entries = library.get(a.setId()).entries();
			assertEquals(25, entries.size(), a.setId());
			long singles = entries.stream().filter(e -> e.pattern().isExact()).count();
			assertTrue(singles >= 5 && singles <= 20, a.setId() + " singles: " + singles);
		}
		assertEquals(50, StressProfile.everywhereEntries().size());
	}

	@Test
	void hasTheMaximumSixtyFourZonesOfEveryKind() {
		// PRD 11.2: boxes, cylinders, some fade, some Outside.
		List<Zone> zones = StressProfile.zones(WORLD, OVERWORLD);
		assertEquals(Precedence.MAX_ZONES, zones.size());
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Box));
		assertTrue(zones.stream().anyMatch(z -> z.shape() instanceof Shape.Cylinder));
		assertTrue(zones.stream().anyMatch(z -> z.mode() == ZoneMode.OUTSIDE));
		assertTrue(zones.stream().anyMatch(z -> z.edge().mode() == ZoneEdge.Mode.FADE));
		assertTrue(zones.stream().anyMatch(z -> z.anchor().equals(StressProfile.PLAYER)), "zones centred on the player");
	}

	@Test
	void zonesAttachOneToThreeSetsFromASharedPool() {
		SetLibrary library = StressProfile.library();
		Map<String, Integer> uses = new HashMap<>();
		for (Zone z : StressProfile.zones(WORLD, OVERWORLD)) {
			int count = z.sets().librarySetCount();
			assertTrue(count >= 1 && count <= 3, z.id() + " attaches " + count);
			for (Attachment a : z.sets().attached()) {
				assertTrue(library.contains(a.setId()), a.setId());
				assertTrue(!StressProfile.EVERYWHERE_SET_IDS.contains(a.setId()), "zones use the pool");
				uses.merge(a.setId(), 1, Integer::sum);
			}
		}
		assertTrue(uses.size() <= StressProfile.POOL_SIZE);
		assertTrue(uses.values().stream().filter(n -> n > 1).count() >= 2, "sets are shared by several zones: " + uses);
		assertEquals(2 + StressProfile.POOL_SIZE + 1 + Precedence.MAX_ZONES, library.size(), "stress, pool, typical and zone sets");
	}

	@Test
	void typicalProfileKeepsItsPreSetsShape() {
		// PRD 9.1 typical profile: 20 single sounds on Everywhere, and zones with 1 to 3 entries each, as before Sound Sets.
		SetLibrary library = StressProfile.library();
		List<Attachment> everywhere = StressProfile.typicalEverywhere().attached();
		assertEquals(List.of(new Attachment(StressProfile.TYPICAL_SET_ID, true)), everywhere);
		List<SetEntry> entries = library.get(StressProfile.TYPICAL_SET_ID).entries();
		assertEquals(StressProfile.TYPICAL_ENTRIES, entries.size());
		assertTrue(entries.stream().allMatch(e -> e.pattern().isExact()), "single sounds only");
		assertEquals(StressProfile.everywhereEntries().stream().filter(e -> e.pattern().isExact()).limit(20).toList(), entries);

		List<Zone> typical = StressProfile.typicalZones(WORLD, OVERWORLD);
		List<Zone> stress = StressProfile.zones(WORLD, OVERWORLD);
		assertEquals(stress.size(), typical.size());
		for (int i = 0; i < typical.size(); i++) {
			Zone z = typical.get(i);
			assertEquals(1, z.sets().attached().size(), z.id());
			int count = library.get(z.sets().attached().get(0).setId()).entries().size();
			assertTrue(count >= 1 && count <= 3, z.id() + " has " + count + " entries");
			assertEquals(stress.get(i), z.withSets(stress.get(i).sets()), "the same zone, only its sets differ");
		}
	}

	@Test
	void stressZonesWeighWhatTheZonesBeforeSoundSetsDid() {
		// PRD 9.1: the worst case stays comparable with the one it replaced. The typical zones hold the old 1 to 3 rules
		// each, so they are the yardstick: per farm sound, zones matched (each costs a geometry test every tick) and zone
		// limits must stay within 15% of theirs.
		double[] stress = zoneLoad(StressProfile.zones(WORLD, OVERWORLD));
		double[] before = zoneLoad(StressProfile.typicalZones(WORLD, OVERWORLD));
		assertTrue(Math.abs(stress[0] - before[0]) <= 0.15 * before[0], "zones matched per sound: " + stress[0] + " against " + before[0]);
		assertTrue(Math.abs(stress[1] - before[1]) <= 0.15 * before[1], "zone limits per sound: " + stress[1] + " against " + before[1]);
		for (int s = 1; s <= StressProfile.POOL_SIZE; s++) {
			for (SetEntry e : StressProfile.library().get("stress-pool-" + s).entries()) {
				assertTrue(!e.pattern().matches("anymod:any.sound"), "no shared match-all entry: " + e.pattern());
			}
		}
	}

	/** Average zones whose sets match each farm sound, and average zones that set a limit for it. */
	private static double[] zoneLoad(List<Zone> zones) {
		Precedence precedence = new Precedence(StressProfile.everywhere(), zones, StressProfile.library());
		List<String> ids = StressProfile.soundIds();
		EntryResolution[] row = new EntryResolution[zones.size() + 1];
		int matched = 0;
		int limits = 0;
		for (String id : ids) {
			precedence.resolve(id, row);
			for (int i = 1; i < row.length; i++) {
				matched += row[i] == EntryResolution.NONE ? 0 : 1;
				limits += row[i].limit() == null ? 0 : 1;
			}
		}
		return new double[] {matched / (double) ids.size(), limits / (double) ids.size()};
	}

	@Test
	void everyZoneAffectsThePenCentre() {
		// The pen is 9 x 9 blocks around (0, 64, 0), with the player standing in it.
		for (Zone z : StressProfile.zones(WORLD, OVERWORLD)) {
			Point a = z.anchor();
			double w = z.weight(-a.x(), StressProfile.PEN_Y - a.y(), -a.z(), ZoneEdge.hard());
			assertTrue(w > 0, z.id() + " weight " + w);
		}
	}

	@Test
	void profileIsTheSameEveryTime() {
		assertEquals(StressProfile.everywhereEntries(), StressProfile.everywhereEntries());
		assertEquals(StressProfile.zones(WORLD, OVERWORLD), StressProfile.zones(WORLD, OVERWORLD));
		List<SoundSet> a = StressProfile.library().sets();
		List<SoundSet> b = StressProfile.library().sets();
		assertEquals(a, b);
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
