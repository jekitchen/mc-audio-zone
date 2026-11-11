package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import soundsculptor.core.rules.Limit;
import soundsculptor.core.rules.Rule;

import org.junit.jupiter.api.Test;

/** PRD FR-8.2 to FR-8.5 and FR-9 with v1 zones: matrix cases P6, P7, P9 and the fade and bubble rules. */
class PrecedenceFullZonesTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final double NO_PLAYER = Double.NaN;

	private static Zone sphere(String id, double x, double radius, Rule... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), List.of(rules));
	}

	private static SoundDecision decide(Precedence p, String id, double x, double y, double z, double px, double py, double pz) {
		SoundDecision out = new SoundDecision();
		p.decide(id, true, WORLD, OVERWORLD, x, y, z, px, py, pz, out);
		return out;
	}

	private static SoundDecision decide(Precedence p, String id, double x) {
		return decide(p, id, x, 0, 0, NO_PLAYER, NO_PLAYER, NO_PLAYER);
	}

	// --- Fade (FR-8.5, P6) ---

	@Test
	void p6FadeZoneBlendsVolumeByItsWeight() {
		Zone a = sphere("a", 0, 10, Rule.of("r", "minecraft:entity.cow.*").withMute(true)).withEdge(ZoneEdge.fade(4));
		Precedence p = new Precedence(List.of(Rule.of("g", "minecraft:entity.cow.*").withVolume(100)), List.of(a));
		SoundDecision d = decide(p, COW, 8);
		assertEquals(50, d.volumePercent(), 1e-4, "2 blocks inside a 4-block fade: w = 0.5");
		assertEquals(0.5, d.loudnessWeight(), 1e-6);
		assertEquals(0, decide(p, COW, 0).volumePercent(), 1e-4, "fully muted deep inside");
		assertEquals(100, decide(p, COW, 10).volumePercent(), 1e-4, "w = 0 at the boundary");
	}

	@Test
	void walkingTowardTheCentreGetsSteadilyQuieter() {
		// FR-8 acceptance: fade 8 on a 20-block sphere muting cows; silent from 8 blocks inside.
		Zone a = sphere("a", 0, 20, Rule.of("r", "minecraft:entity.cow.*").withMute(true)).withEdge(ZoneEdge.fade(8));
		Precedence p = new Precedence(List.of(), List.of(a));
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
		Zone a = sphere("a", 0, 10, Rule.of("r", "minecraft:entity.cow.*").withLimit(new Limit(2, 1000, false)).withCue(true))
				.withEdge(ZoneEdge.fade(4));
		Precedence p = new Precedence(List.of(), List.of(a));
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
		Zone a = sphere("a", 0, 10, Rule.of("r", "minecraft:entity.cow.*").withMute(true));
		assertEquals(0, decide(new Precedence(List.of(), List.of(a)), COW, 8).volumePercent(), 1e-4, "profile default is hard");
		assertEquals(50, decide(new Precedence(List.of(), List.of(a), ZoneEdge.fade(4)), COW, 8).volumePercent(), 1e-4);
	}

	// --- Outside mode (FR-8.4, P7) ---

	@Test
	void outsideZoneAffectsOnlySoundsOutsideIt() {
		Zone base = sphere("base", 0, 20, Rule.of("r", "*:entity.*").withMute(true)).withMode(ZoneMode.OUTSIDE);
		Precedence p = new Precedence(List.of(), List.of(base));
		assertEquals(100, decide(p, COW, 5).volumePercent(), 1e-4, "inside the base: audible");
		assertEquals(0, decide(p, COW, 30).volumePercent(), 1e-4, "outside: muted");
	}

	@Test
	void p7CloserInsideZoneBeatsABroadOutsideZone() {
		// Outside zone O around the base, centre 40 blocks from the sound; Inside zone A 6 blocks away.
		Zone o = sphere("o", 40, 20, Rule.of("all", "*").withMute(true)).withMode(ZoneMode.OUTSIDE);
		Zone a = sphere("a", 6, 10, Rule.of("r", "*villager*").withVolume(70));
		SoundDecision d = decide(new Precedence(List.of(), List.of(o, a)), VILLAGER, 0);
		assertEquals(70, d.volumePercent(), 1e-4);
		assertSame(a, d.loudnessZone());
	}

	@Test
	void outsideZoneDistanceIsMeasuredFromItsCentre() {
		// O's centre is 25 away, A's 30 away: O wins although the sound is far outside O.
		Zone o = sphere("o", 25, 5, Rule.of("all", "*").withVolume(10)).withMode(ZoneMode.OUTSIDE);
		Zone a = sphere("a", 30, 40, Rule.of("r", "*").withVolume(90));
		assertEquals(10, decide(new Precedence(List.of(), List.of(o, a)), COW, 0).volumePercent(), 1e-4);
	}

	@Test
	void outsideFadeRisesAwayFromTheShape() {
		Zone o = sphere("o", 0, 10, Rule.of("all", "*").withMute(true)).withMode(ZoneMode.OUTSIDE).withEdge(ZoneEdge.fade(4));
		Precedence p = new Precedence(List.of(), List.of(o));
		assertEquals(50, decide(p, COW, 12).volumePercent(), 1e-4);
		assertEquals(0, decide(p, COW, 20).volumePercent(), 1e-4);
	}

	// --- Personal bubble (FR-8.2, P9) ---

	@Test
	void bubbleFollowsThePlayer() {
		Zone bubble = sphere("bubble", 0, 8, Rule.of("all", "*").withVolume(30)).asBubble();
		Precedence p = new Precedence(List.of(), List.of(bubble));
		assertEquals(30, decide(p, COW, 105, 0, 0, 100, 0, 0).volumePercent(), 1e-4, "5 blocks from the player");
		assertEquals(100, decide(p, COW, 120, 0, 0, 100, 0, 0).volumePercent(), 1e-4, "20 blocks from the player");
	}

	@Test
	void bubbleAppliesInAnyWorldAndDimension() {
		Zone bubble = sphere("bubble", 0, 8, Rule.of("all", "*").withVolume(30)).asBubble();
		SoundDecision out = new SoundDecision();
		new Precedence(List.of(), List.of(bubble)).decide(COW, true, "other.server", "minecraft:the_nether", 1, 0, 0, 0, 0, 0, out);
		assertEquals(30, out.volumePercent(), 1e-4);
	}

	@Test
	void bubbleIsSkippedWithoutAPlayerPosition() {
		Zone bubble = sphere("bubble", 0, 8, Rule.of("all", "*").withVolume(30)).asBubble();
		assertEquals(100, decide(new Precedence(List.of(), List.of(bubble)), COW, 1).volumePercent(), 1e-4);
	}

	@Test
	void p9BubbleAndFixedZoneCompeteByAnchorDistance() {
		Zone bubble = sphere("bubble", 0, 50, Rule.of("all", "*").withVolume(50)).asBubble();
		Zone fixed = sphere("fixed", 2, 20, Rule.of("r", "*").withVolume(20));
		Precedence p = new Precedence(List.of(), List.of(bubble, fixed));
		// Source at the origin; the fixed anchor is 2 away, the player 10 away.
		assertEquals(20, decide(p, COW, 0, 0, 0, 10, 0, 0).volumePercent(), 1e-4);
		// Walk closer than the fixed anchor: the bubble wins.
		assertEquals(50, decide(p, COW, 0, 0, 0, 1, 0, 0).volumePercent(), 1e-4);
	}

	// --- Cylinder in decisions ---

	@Test
	void cylinderZonesWork() {
		Zone c = new Zone("c", "C", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Cylinder(5, 10),
				List.of(Rule.of("r", "*").withMute(true)));
		Precedence p = new Precedence(List.of(), List.of(c));
		assertEquals(0, decide(p, COW, 4).volumePercent(), 1e-4);
		assertEquals(100, decide(p, COW, 0, 6, 0, NO_PLAYER, NO_PLAYER, NO_PLAYER).volumePercent(), 1e-4, "above the top");
	}
}
