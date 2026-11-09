package audiozone.core.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-10.1 to FR-10.3: which outlines and labels to draw. The camera is at the origin unless noted. */
class OutlineVisibilityTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";

	private static Zone sphereAt(String id, double x, double radius) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), List.of());
	}

	private static boolean draws(Zone zone, OutlineMode mode, String editingId) {
		return OutlineVisibility.shouldDraw(zone, mode, editingId, WORLD, OVERWORLD, 0, 0, 0);
	}

	@Test
	void defaultModeIsOnlyWhileEditing() {
		assertEquals(OutlineMode.EDITING, OutlineMode.DEFAULT);
	}

	@Test
	void alwaysDrawsNearbyZones() {
		assertTrue(draws(sphereAt("a", 20, 5), OutlineMode.ALWAYS, null));
	}

	@Test
	void neverDrawsNothingEvenWhileEditing() {
		assertFalse(draws(sphereAt("a", 20, 5), OutlineMode.NEVER, "a"));
	}

	@Test
	void editingModeDrawsOnlyTheZoneBeingEdited() {
		assertTrue(draws(sphereAt("a", 20, 5), OutlineMode.EDITING, "a"));
		assertFalse(draws(sphereAt("b", 20, 5), OutlineMode.EDITING, "a"));
		assertFalse(draws(sphereAt("a", 20, 5), OutlineMode.EDITING, null));
	}

	@Test
	void onlyZonesWithin128BlocksAreDrawn() {
		// Distance is to the nearest part of the zone, so a big zone whose edge is close still shows.
		assertTrue(draws(sphereAt("a", 128 + 10, 10), OutlineMode.ALWAYS, null));
		assertFalse(draws(sphereAt("a", 128 + 10.5, 10), OutlineMode.ALWAYS, null));
		assertTrue(draws(sphereAt("a", 0, 10), OutlineMode.ALWAYS, null), "camera inside the zone");
	}

	@Test
	void otherDimensionsAndServersAreNotDrawn() {
		Zone a = sphereAt("a", 10, 5);
		assertFalse(OutlineVisibility.shouldDraw(a, OutlineMode.ALWAYS, null, WORLD, "minecraft:the_nether", 0, 0, 0));
		assertFalse(OutlineVisibility.shouldDraw(a, OutlineMode.ALWAYS, null, "other.net", OVERWORLD, 0, 0, 0));
	}

	@Test
	void disabledZonesAreDrawnOnlyWhileEdited() {
		Zone off = new Zone("a", "A", false, WORLD, OVERWORLD, new Point(10, 0, 0), new Shape.Sphere(5), List.of());
		assertFalse(draws(off, OutlineMode.ALWAYS, null));
		assertTrue(draws(off, OutlineMode.ALWAYS, "a"));
		assertTrue(draws(off, OutlineMode.EDITING, "a"));
	}

	@Test
	void labelShowsWithin32BlocksOfTheAnchor() {
		assertTrue(OutlineVisibility.showLabel(sphereAt("a", 32, 5), 0, 0, 0));
		assertFalse(OutlineVisibility.showLabel(sphereAt("a", 32.5, 5), 0, 0, 0));
	}

	@Test
	void boxDistanceUsesItsNearestFace() {
		Zone box = new Zone("b", "B", true, WORLD, OVERWORLD, new Point(200, 0, 0), new Shape.Box(150, 4, 4), List.of());
		// Nearest face is at x = 125, 125 blocks from the camera.
		assertTrue(draws(box, OutlineMode.ALWAYS, null));
	}
}
