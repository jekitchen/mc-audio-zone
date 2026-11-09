package audiozone.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-8.4 and FR-8.5: how strongly a zone applies at a point (its weight w, 0 to 1). */
class ZoneWeightTest {
	private static final ZoneEdge HARD = ZoneEdge.hard();
	private static final ZoneEdge FADE_4 = ZoneEdge.fade(4);

	private static Zone sphere(ZoneMode mode, ZoneEdge edge) {
		return new Zone("z", "Z", true, "w", "minecraft:overworld", new Point(0, 0, 0), new Shape.Sphere(20), List.of())
				.withMode(mode).withEdge(edge);
	}

	@Test
	void hardInsideZoneIsFullyOnInsideAndOffOutside() {
		Zone z = sphere(ZoneMode.INSIDE, HARD);
		assertEquals(1, z.weight(0, 0, 0, HARD), 1e-9);
		assertEquals(1, z.weight(20, 0, 0, HARD), 1e-9, "the boundary counts as inside");
		assertEquals(0, z.weight(21, 0, 0, HARD), 1e-9);
	}

	@Test
	void outsideZoneAffectsSoundsOutsideItsShape() {
		Zone z = sphere(ZoneMode.OUTSIDE, HARD);
		assertEquals(0, z.weight(0, 0, 0, HARD), 1e-9);
		assertEquals(1, z.weight(30, 0, 0, HARD), 1e-9);
	}

	@Test
	void insideFadeRisesInwardFromTheBoundary() {
		// FR-8 acceptance: 20-block sphere, fade 8: silent from 8 blocks inside the edge.
		Zone z = sphere(ZoneMode.INSIDE, ZoneEdge.fade(8));
		assertEquals(0, z.weight(20, 0, 0, HARD), 1e-9, "0 at the boundary");
		assertEquals(0.5, z.weight(16, 0, 0, HARD), 1e-9);
		assertEquals(1, z.weight(12, 0, 0, HARD), 1e-9, "full strength 8 blocks in");
		assertEquals(1, z.weight(0, 0, 0, HARD), 1e-9);
		assertEquals(0, z.weight(25, 0, 0, HARD), 1e-9);
	}

	@Test
	void outsideFadeRisesOutwardFromTheBoundary() {
		Zone z = sphere(ZoneMode.OUTSIDE, FADE_4);
		assertEquals(0, z.weight(20, 0, 0, HARD), 1e-9);
		assertEquals(0.5, z.weight(22, 0, 0, HARD), 1e-9);
		assertEquals(1, z.weight(30, 0, 0, HARD), 1e-9);
		assertEquals(0, z.weight(10, 0, 0, HARD), 1e-9);
	}

	@Test
	void defaultEdgeUsesTheProfileDefault() {
		Zone z = sphere(ZoneMode.INSIDE, ZoneEdge.DEFAULT);
		assertEquals(1, z.weight(18, 0, 0, HARD), 1e-9);
		assertEquals(0.5, z.weight(18, 0, 0, FADE_4), 1e-9);
	}

	@Test
	void fadeDistanceLimits() {
		assertThrows(IllegalArgumentException.class, () -> ZoneEdge.fade(0.4));
		assertThrows(IllegalArgumentException.class, () -> ZoneEdge.fade(32.5));
		assertEquals(4, ZoneEdge.DEFAULT_FADE, 1e-9);
	}

	@Test
	void zonesAreFixedInsideAndDefaultEdgeUnlessSetOtherwise() {
		Zone z = new Zone("z", "Z", true, "w", "d", new Point(0, 0, 0), new Shape.Sphere(5), List.of());
		assertEquals(ZoneMode.INSIDE, z.mode());
		assertEquals(ZoneEdge.DEFAULT, z.edge());
		assertEquals(AnchorType.FIXED, z.anchorType());
		assertEquals(AnchorType.BUBBLE, z.asBubble().anchorType());
	}
}
