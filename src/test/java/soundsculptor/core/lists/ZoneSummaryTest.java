package soundsculptor.core.lists;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.TestEnglish;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD 6.1: the Zones tab row: shape, mode, and "Here / 120 blocks away / Other world". */
class ZoneSummaryTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final ZoneEdge HARD = ZoneEdge.hard();

	private static Zone sphere(double radius) {
		return new Zone("z", "Hall", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Sphere(radius), PlaceSets.EMPTY);
	}

	private static String where(Zone zone, String world, String dimension, double x) {
		return ZoneSummary.location(zone, world, dimension, new Point(x, 64, 0), HARD).text(EN);
	}

	@Test
	void hereWhenThePlayerIsInside() {
		assertEquals("Here", where(sphere(10), WORLD, OVERWORLD, 3));
	}

	@Test
	void roundedBlocksFromTheAnchorWhenOutside() {
		assertEquals("120 blocks away", where(sphere(10), WORLD, OVERWORLD, 120.4));
		assertEquals("121 blocks away", where(sphere(10), WORLD, OVERWORLD, 120.6));
		assertEquals(LocationKind.AWAY, ZoneSummary.location(sphere(10), WORLD, OVERWORLD, new Point(50, 64, 0), HARD).kind());
	}

	@Test
	void outsideZoneIsHereWhenThePlayerIsOutsideItsShape() {
		Zone base = sphere(20).withMode(ZoneMode.OUTSIDE);
		assertEquals("Here", where(base, WORLD, OVERWORLD, 50));
		assertEquals("5 blocks away", where(base, WORLD, OVERWORLD, 5));
	}

	@Test
	void otherWorldBeforeOtherDimension() {
		assertEquals("Other world", where(sphere(10), "other.example.net", "minecraft:the_nether", 0));
		assertEquals("Other dimension", where(sphere(10), WORLD, "minecraft:the_nether", 0));
	}

	@Test
	void bubbleIsAlwaysHere() {
		Zone bubble = sphere(8).asBubble();
		assertEquals("Here", where(bubble, "other.example.net", "minecraft:the_nether", 500));
	}

	@Test
	void fadeEdgeCountsAsHereOnlyOnceTheWeightIsAboveZero() {
		Zone fade = sphere(20).withEdge(ZoneEdge.fade(8));
		assertEquals("Here", where(fade, WORLD, OVERWORLD, 15));
		assertEquals("20 blocks away", where(fade, WORLD, OVERWORLD, 20));
	}

	@Test
	void shapeModeAndBubbleChips() {
		assertEquals(List.of("Sphere, radius 10", "Inside", "Here"), chips(sphere(10)));
		Zone box = new Zone("b", "Farm", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Box(20, 8, 30.5), PlaceSets.EMPTY)
				.withMode(ZoneMode.OUTSIDE);
		assertEquals(List.of("Box 20 × 8 × 30.5", "Outside", "5 blocks away"), chipsAt(box, 5));
		Zone cylinder = new Zone("c", "Tower", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Cylinder(6, 40), PlaceSets.EMPTY)
				.asBubble();
		assertEquals(List.of("Cylinder, radius 6, height 40", "Inside", "Personal bubble", "Here"), chips(cylinder));
	}

	private static List<String> chips(Zone zone) {
		return chipsAt(zone, 0);
	}

	private static List<String> chipsAt(Zone zone, double x) {
		return ZoneSummary.parts(zone, WORLD, OVERWORLD, new Point(x, 64, 0), HARD).stream().map(p -> p.text(EN)).toList();
	}
}
