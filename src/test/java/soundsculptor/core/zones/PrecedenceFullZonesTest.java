package soundsculptor.core.zones;

import static soundsculptor.core.Fixtures.quick;
import static soundsculptor.core.Fixtures.precedence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;

import org.junit.jupiter.api.Test;

/** PRD FR-8.3 to FR-8.5 and FR-9 with v1 zones: matrix cases P6, P7 and the fade rules. */
class PrecedenceFullZonesTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	private static Zone cylinder(String id, double x, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Cylinder(radius, 2 * radius), quick(List.of(rules)));
	}

	private static SoundDecision decide(Precedence p, String id, double x, double y, double z) {
		SoundDecision out = new SoundDecision();
		p.decide(id, true, WORLD, OVERWORLD, x, y, z, out);
		return out;
	}

	private static SoundDecision decide(Precedence p, String id, double x) {
		return decide(p, id, x, 0, 0);
	}

	// --- Fade (FR-8.5, P6) ---

	@Test
	void p6FadeZoneBlendsVolumeByItsWeight() {
		Zone a = cylinder("a", 0, 10, SetEntry.of("r", "minecraft:entity.cow.*").withMute(true)).withEdge(ZoneEdge.fade(4));
		Precedence p = precedence(List.of(SetEntry.of("g", "minecraft:entity.cow.*").withVolume(100)), List.of(a));
		SoundDecision d = decide(p, COW, 8);
		assertEquals(50, d.volumePercent(), 1e-4, "2 blocks inside a 4-block fade: w = 0.5");
		assertEquals(0.5, d.loudnessWeight(), 1e-6);
		assertEquals(0, decide(p, COW, 0).volumePercent(), 1e-4, "fully muted deep inside");
		assertEquals(100, decide(p, COW, 10).volumePercent(), 1e-4, "w = 0 at the boundary");
	}

	@Test
	void walkingTowardTheCentreGetsSteadilyQuieter() {
		// FR-8 acceptance: fade 8 on a 20-block zone muting cows; silent from 8 blocks inside.
		Zone a = cylinder("a", 0, 20, SetEntry.of("r", "minecraft:entity.cow.*").withMute(true)).withEdge(ZoneEdge.fade(8));
		Precedence p = precedence(List.of(), List.of(a));
		float previous = 101;
		for (double x = 20; x >= 12; x -= 1) {
			float v = decide(p, COW, x).volumePercent();
			org.junit.jupiter.api.Assertions.assertTrue(v < previous, "at " + x);
			previous = v;
		}
		assertEquals(0, decide(p, COW, 12).volumePercent(), 1e-4);
	}

	@Test
	void fadeZoneLimitAndCueApplyOnlyFromHalfWeight() {
		Zone a = cylinder("a", 0, 10, SetEntry.of("r", "minecraft:entity.cow.*").withLimit(new Limit(2, 1000, false)).withCue(true))
				.withEdge(ZoneEdge.fade(4));
		Precedence p = precedence(List.of(), List.of(a));
		SoundDecision quarter = decide(p, COW, 9);
		assertNull(quarter.limit(), "w = 0.25");
		assertEquals(false, quarter.cueOn());
		SoundDecision half = decide(p, COW, 8);
		assertEquals(new Limit(2, 1000, false), half.limit());
		assertEquals(true, half.cueOn());
		assertSame(a, half.limitZone());
	}

	@Test
	void defaultEdgeZonesUseTheProfileEdge() {
		Zone a = cylinder("a", 0, 10, SetEntry.of("r", "minecraft:entity.cow.*").withMute(true));
		assertEquals(0, decide(precedence(List.of(), List.of(a)), COW, 8).volumePercent(), 1e-4, "profile default is hard");
		assertEquals(50, decide(precedence(List.of(), List.of(a), ZoneEdge.fade(4)), COW, 8).volumePercent(), 1e-4);
	}

	// --- Outside mode (FR-8.4, P7) ---

	@Test
	void outsideZoneAffectsOnlySoundsOutsideIt() {
		Zone base = cylinder("base", 0, 20, SetEntry.of("r", "*:entity.*").withMute(true)).withMode(ZoneMode.OUTSIDE);
		Precedence p = precedence(List.of(), List.of(base));
		assertEquals(100, decide(p, COW, 5).volumePercent(), 1e-4, "inside the base: audible");
		assertEquals(0, decide(p, COW, 30).volumePercent(), 1e-4, "outside: muted");
	}

	@Test
	void p7CloserInsideZoneBeatsABroadOutsideZone() {
		// Outside zone O around the base, centre 40 blocks from the sound; Inside zone A 6 blocks away.
		Zone o = cylinder("o", 40, 20, SetEntry.of("all", "*").withMute(true)).withMode(ZoneMode.OUTSIDE);
		Zone a = cylinder("a", 6, 10, SetEntry.of("r", "*villager*").withVolume(70));
		SoundDecision d = decide(precedence(List.of(), List.of(o, a)), VILLAGER, 0);
		assertEquals(70, d.volumePercent(), 1e-4);
		assertSame(a, d.loudnessZone());
	}

	@Test
	void outsideZoneDistanceIsMeasuredFromItsCentre() {
		// O's centre is 25 away, A's 30 away: O wins although the sound is far outside O.
		Zone o = cylinder("o", 25, 5, SetEntry.of("all", "*").withVolume(10)).withMode(ZoneMode.OUTSIDE);
		Zone a = cylinder("a", 30, 40, SetEntry.of("r", "*").withVolume(90));
		assertEquals(10, decide(precedence(List.of(), List.of(o, a)), COW, 0).volumePercent(), 1e-4);
	}

	@Test
	void outsideFadeRisesAwayFromTheShape() {
		Zone o = cylinder("o", 0, 10, SetEntry.of("all", "*").withMute(true)).withMode(ZoneMode.OUTSIDE).withEdge(ZoneEdge.fade(4));
		Precedence p = precedence(List.of(), List.of(o));
		assertEquals(50, decide(p, COW, 12).volumePercent(), 1e-4);
		assertEquals(0, decide(p, COW, 20).volumePercent(), 1e-4);
	}

	// --- Cylinder in decisions ---

	@Test
	void cylinderZonesWork() {
		Zone c = new Zone("c", "C", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Cylinder(5, 10),
				quick(List.of(SetEntry.of("r", "*").withMute(true))));
		Precedence p = precedence(List.of(), List.of(c));
		assertEquals(0, decide(p, COW, 4).volumePercent(), 1e-4);
		assertEquals(100, decide(p, COW, 0, 6, 0).volumePercent(), 1e-4, "above the top");
	}
}
