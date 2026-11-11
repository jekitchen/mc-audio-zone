package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-8.3 (cylinder) and FR-8.5 (fade needs the distance to the boundary). Offsets are from the anchor. */
class ShapeDistanceTest {
	// --- Cylinder ---

	@Test
	void cylinderIsVerticalAndCentredOnTheAnchor() {
		Shape c = new Shape.Cylinder(5, 10);
		assertTrue(c.contains(3, 4, 4), "radius 5 at the edge, 4 up of a half-height 5");
		assertTrue(c.contains(0, -5, 0));
		assertFalse(c.contains(0, 5.01, 0));
		assertFalse(c.contains(4, 0, 3.01));
		assertEquals(Math.PI * 25 * 10, c.volume(), 1e-9);
	}

	@Test
	void cylinderSizeLimits() {
		assertThrows(IllegalArgumentException.class, () -> new Shape.Cylinder(0.5, 10));
		assertThrows(IllegalArgumentException.class, () -> new Shape.Cylinder(257, 10));
		assertThrows(IllegalArgumentException.class, () -> new Shape.Cylinder(5, 513));
		new Shape.Cylinder(256, 512);
		new Shape.Cylinder(1, 1);
	}

	// --- Depth inside (for Inside-mode fade) ---

	@Test
	void sphereDepthIsDistanceToTheSurface() {
		Shape s = new Shape.Sphere(10);
		assertEquals(10, s.depthInside(0, 0, 0), 1e-9);
		assertEquals(2, s.depthInside(8, 0, 0), 1e-9);
		assertEquals(0, s.depthInside(12, 0, 0), 1e-9, "outside has no depth");
	}

	@Test
	void boxDepthIsDistanceToTheNearestFace() {
		Shape b = new Shape.Box(20, 8, 30);
		assertEquals(4, b.depthInside(0, 0, 0), 1e-9, "the 8-high box is nearest its top and bottom");
		assertEquals(1, b.depthInside(9, 0, 0), 1e-9);
		assertEquals(0, b.depthInside(11, 0, 0), 1e-9);
	}

	@Test
	void cylinderDepthIsTheNearerOfSideAndTopOrBottom() {
		Shape c = new Shape.Cylinder(10, 40);
		assertEquals(10, c.depthInside(0, 0, 0), 1e-9);
		assertEquals(2, c.depthInside(0, 18, 0), 1e-9);
		assertEquals(4, c.depthInside(6, 0, 0), 1e-9, "nearer the side than the top");
		assertEquals(0, c.depthInside(11, 0, 0), 1e-9);
	}

	// --- Distance outside (for Outside-mode fade and outline culling) ---

	@Test
	void sphereDistanceOutside() {
		Shape s = new Shape.Sphere(10);
		assertEquals(5, s.distanceOutside(15, 0, 0), 1e-9);
		assertEquals(0, s.distanceOutside(3, 0, 0), 1e-9, "inside is 0");
	}

	@Test
	void boxDistanceOutsideMeasuresToTheNearestPoint() {
		Shape b = new Shape.Box(2, 2, 2);
		assertEquals(3, b.distanceOutside(4, 0, 0), 1e-9);
		assertEquals(Math.sqrt(2), b.distanceOutside(2, 2, 0), 1e-9, "diagonal from a corner edge");
		assertEquals(0, b.distanceOutside(0.5, 0.5, 0.5), 1e-9);
	}

	@Test
	void cylinderDistanceOutside() {
		Shape c = new Shape.Cylinder(5, 10);
		assertEquals(3, c.distanceOutside(8, 0, 0), 1e-9);
		assertEquals(2, c.distanceOutside(0, 7, 0), 1e-9);
		assertEquals(5, c.distanceOutside(9, 8, 0), 1e-9, "3-4-5 from the rim");
	}
}
