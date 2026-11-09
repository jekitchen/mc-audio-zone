package audiozone.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-10.2: box edges; a sphere as three great circles. Coordinates are world coordinates. */
class OutlineGeometryTest {
	private static Zone zone(Point anchor, Shape shape) {
		return new Zone("z", "Z", true, "w", "minecraft:overworld", anchor, shape, List.of());
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
	void sphereIsThreeClosedGreatCirclesOnTheSurface() {
		Point c = new Point(0, 70, 0);
		double r = 10;
		Outline o = OutlineGeometry.of(zone(c, new Shape.Sphere(r)));
		assertEquals(0, o.segmentCount() % 3);
		for (int i = 0; i < o.segmentCount(); i++) {
			assertEquals(r, dist(o.x1(i), o.y1(i), o.z1(i), c), 1e-9, "every point is on the sphere");
			assertEquals(r, dist(o.x2(i), o.y2(i), o.z2(i), c), 1e-9);
		}
		int perCircle = o.segmentCount() / 3;
		// Each circle closes: its last segment ends where its first begins.
		for (int k = 0; k < 3; k++) {
			int first = k * perCircle;
			int last = first + perCircle - 1;
			assertEquals(o.x1(first), o.x2(last), 1e-9);
			assertEquals(o.y1(first), o.y2(last), 1e-9);
			assertEquals(o.z1(first), o.z2(last), 1e-9);
		}
	}

	@Test
	void eachSphereCircleLiesInOneAxisPlane() {
		Outline o = OutlineGeometry.of(zone(new Point(0, 0, 0), new Shape.Sphere(5)));
		int perCircle = o.segmentCount() / 3;
		for (int k = 0; k < 3; k++) {
			boolean flatX = true;
			boolean flatY = true;
			boolean flatZ = true;
			for (int i = k * perCircle; i < (k + 1) * perCircle; i++) {
				flatX &= Math.abs(o.x1(i)) < 1e-9;
				flatY &= Math.abs(o.y1(i)) < 1e-9;
				flatZ &= Math.abs(o.z1(i)) < 1e-9;
			}
			assertTrue(flatX || flatY || flatZ, "circle " + k);
		}
	}

	@Test
	void biggerSpheresGetSmootherCirclesWithinLimits() {
		int small = OutlineGeometry.of(zone(new Point(0, 0, 0), new Shape.Sphere(1))).segmentCount() / 3;
		int big = OutlineGeometry.of(zone(new Point(0, 0, 0), new Shape.Sphere(256))).segmentCount() / 3;
		assertEquals(OutlineGeometry.MIN_CIRCLE_SEGMENTS, small);
		assertEquals(OutlineGeometry.MAX_CIRCLE_SEGMENTS, big);
	}

	private static double dist(double x, double y, double z, Point c) {
		double dx = x - c.x();
		double dy = y - c.y();
		double dz = z - c.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
