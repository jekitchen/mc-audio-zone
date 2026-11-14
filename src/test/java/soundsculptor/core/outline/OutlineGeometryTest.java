package soundsculptor.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-10.2: box edges; a cylinder's top and bottom circles. Coordinates are world coordinates. */
class OutlineGeometryTest {
	private static Zone zone(Point anchor, Shape shape) {
		return new Zone("z", "Z", true, "w", "minecraft:overworld", anchor, shape, PlaceSets.EMPTY);
	}

	@Test
	void boxHasItsTwelveEdgesAroundTheAnchor() {
		Outline o = OutlineGeometry.of(zone(new Point(10, 64, -5), new Shape.Box(4, 2, 6)));
		assertEquals(12, o.segmentCount());
		Set<String> corners = new HashSet<>();
		for (int i = 0; i < o.segmentCount(); i++) {
			corners.add(o.x1(i) + "," + o.y1(i) + "," + o.z1(i));
			corners.add(o.x2(i) + "," + o.y2(i) + "," + o.z2(i));
			// Each edge runs along exactly one axis.
			int changed = (o.x1(i) != o.x2(i) ? 1 : 0) + (o.y1(i) != o.y2(i) ? 1 : 0) + (o.z1(i) != o.z2(i) ? 1 : 0);
			assertEquals(1, changed, "edge " + i);
		}
		assertEquals(8, corners.size());
		assertTrue(corners.contains("8.0,63.0,-8.0"));
		assertTrue(corners.contains("12.0,65.0,-2.0"));
	}

	@Test
	void cylinderCirclesAreClosedAndLevel() {
		Point c = new Point(3, 70, -4);
		Outline o = OutlineGeometry.of(zone(c, new Shape.Cylinder(10, 6)));
		int perCircle = (o.segmentCount() - 4) / 2;
		// The bottom circle, then the top one; each closes: its last segment ends where its first begins.
		for (int k = 0; k < 2; k++) {
			int first = k * perCircle;
			int last = first + perCircle - 1;
			assertEquals(o.x1(first), o.x2(last), 1e-9);
			assertEquals(o.y1(first), o.y2(last), 1e-9);
			assertEquals(o.z1(first), o.z2(last), 1e-9);
			for (int i = first; i <= last; i++) {
				assertEquals(k == 0 ? 67 : 73, o.y1(i), 1e-9, "circle " + k + " is level");
				assertEquals(10, Math.hypot(o.x1(i) - c.x(), o.z1(i) - c.z()), 1e-9, "on the rim");
			}
		}
	}

	@Test
	void biggerCylindersGetSmootherCirclesWithinLimits() {
		int small = (OutlineGeometry.of(zone(new Point(0, 0, 0), new Shape.Cylinder(1, 2))).segmentCount() - 4) / 2;
		int big = (OutlineGeometry.of(zone(new Point(0, 0, 0), new Shape.Cylinder(256, 512))).segmentCount() - 4) / 2;
		assertEquals(OutlineGeometry.MIN_CIRCLE_SEGMENTS, small);
		assertEquals(OutlineGeometry.MAX_CIRCLE_SEGMENTS, big);
	}
}
