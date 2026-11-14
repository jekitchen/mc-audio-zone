package soundsculptor.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD FR-10.2 and FR-10.3 for v1 zones: cylinder, dashed Outside lines, inner fade line. */
class OutlineFullZonesTest {
	private static final String WORLD = "w";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final ZoneEdge HARD = ZoneEdge.hard();

	private static Zone zone(Shape shape) {
		return new Zone("z", "Z", true, WORLD, OVERWORLD, new Point(0, 64, 0), shape, PlaceSets.EMPTY);
	}

	@Test
	void cylinderIsTopAndBottomCirclesPlusFourVerticalLines() {
		Outline o = OutlineGeometry.of(zone(new Shape.Cylinder(5, 10)));
		int vertical = 0;
		for (int i = 0; i < o.segmentCount(); i++) {
			if (o.y1(i) != o.y2(i)) {
				vertical++;
				assertEquals(59, Math.min(o.y1(i), o.y2(i)), 1e-9);
				assertEquals(69, Math.max(o.y1(i), o.y2(i)), 1e-9);
			} else {
				assertTrue(o.y1(i) == 59 || o.y1(i) == 69, "circle points at top or bottom");
			}
			assertEquals(5, Math.hypot(o.x1(i), o.z1(i)), 1e-9, "on the curved surface");
			assertEquals(5, Math.hypot(o.x2(i), o.z2(i)), 1e-9);
		}
		assertEquals(4, vertical);
		assertEquals(0, (o.segmentCount() - 4) % 2, "two circles of equal size");
	}

	@Test
	void outsideZonesUseDashedLinesInsideZonesSolid() {
		assertFalse(OutlineGeometry.of(zone(new Shape.Cylinder(5, 10))).dashed());
		assertTrue(OutlineGeometry.of(zone(new Shape.Cylinder(5, 10)).withMode(ZoneMode.OUTSIDE)).dashed());
	}

	@Test
	void hardEdgedZonesHaveNoInnerLine() {
		assertNull(OutlineGeometry.of(zone(new Shape.Cylinder(10, 20)).withEdge(HARD), HARD).inner());
	}

	@Test
	void insideFadeDrawsAFainterLineWhereTheFadeReachesFullStrength() {
		Outline o = OutlineGeometry.of(zone(new Shape.Cylinder(10, 20)).withEdge(ZoneEdge.fade(4)), HARD);
		Outline inner = o.inner();
		assertNotNull(inner);
		// The cylinder shrunk by the fade distance: radius 6, from 6 below the anchor to 6 above it.
		for (int i = 0; i < inner.segmentCount(); i++) {
			assertEquals(6, Math.hypot(inner.x1(i), inner.z1(i)), 1e-9);
			assertEquals(6, Math.abs(inner.y1(i) - 64), 1e-9);
		}
	}

	@Test
	void outsideFadeLineIsOutsideTheShape() {
		Outline o = OutlineGeometry.of(zone(new Shape.Box(10, 10, 10)).withMode(ZoneMode.OUTSIDE).withEdge(ZoneEdge.fade(2)), HARD);
		double maxX = 0;
		for (int i = 0; i < o.inner().segmentCount(); i++) {
			maxX = Math.max(maxX, Math.abs(o.inner().x1(i)));
		}
		assertEquals(7, maxX, 1e-9, "half width 5 plus fade 2");
		assertTrue(o.inner().dashed(), "the fade line keeps the zone's line style");
	}

	@Test
	void defaultEdgeUsesTheProfileEdgeForTheInnerLine() {
		Zone z = zone(new Shape.Cylinder(10, 20));
		assertNull(OutlineGeometry.of(z, HARD).inner());
		assertNotNull(OutlineGeometry.of(z, ZoneEdge.fade(4)).inner());
	}

	@Test
	void fadeDeeperThanTheShapeHasNoInnerLine() {
		assertNull(OutlineGeometry.of(zone(new Shape.Cylinder(3, 6)).withEdge(ZoneEdge.fade(4)), HARD).inner());
	}

	@Test
	void cylinderDistanceCullingUsesItsNearestPoint() {
		Zone c = new Zone("c", "C", true, WORLD, OVERWORLD, new Point(200, 0, 0), new Shape.Cylinder(80, 10), PlaceSets.EMPTY);
		assertTrue(OutlineVisibility.shouldDraw(c, OutlineMode.ALWAYS, null, WORLD, OVERWORLD, 0, 0, 0), "rim is 120 away");
	}
}
