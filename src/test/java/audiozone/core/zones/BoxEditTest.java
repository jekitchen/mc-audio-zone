package audiozone.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The zone editor's corner marking and side resizing (M8 review). */
class BoxEditTest {
	@Test
	void cornersCoverBothBlocksInAnyOrder() {
		BoxEdit.Placed a = BoxEdit.fromCorners(0, 64, 0, 2, 64, 0);
		assertEquals(new Point(1.5, 64.5, 0.5), a.center());
		assertEquals(new Shape.Box(3, 1, 1), a.box());
		BoxEdit.Placed b = BoxEdit.fromCorners(10, 70, -5, 1, 60, 4);
		assertEquals(b, BoxEdit.fromCorners(1, 60, 4, 10, 70, -5), "the order of the corners does not matter");
		assertEquals(new Shape.Box(10, 11, 10), b.box());
		assertEquals(new Point(6, 65.5, 0), b.center());
	}

	@Test
	void theSameBlockTwiceIsAOneBlockBox() {
		BoxEdit.Placed p = BoxEdit.fromCorners(5, 64, 5, 5, 64, 5);
		assertEquals(new Shape.Box(1, 1, 1), p.box());
		assertEquals(new Point(5.5, 64.5, 5.5), p.center());
	}

	@Test
	void growingASideKeepsTheOppositeSide() {
		Shape.Box box = new Shape.Box(4, 4, 4);
		Point center = new Point(0, 64, 0);
		// North is -z: the north face moves from -2 to -3, the south face stays at +2.
		BoxEdit.Placed north = BoxEdit.moveSide(center, box, BoxEdit.Side.NORTH, 1);
		assertEquals(new Shape.Box(4, 4, 5), north.box());
		assertEquals(new Point(0, 64, -0.5), north.center());
		assertEquals(2, north.center().z() + north.box().depth() / 2, 1e-9, "south face unchanged");
		BoxEdit.Placed east = BoxEdit.moveSide(center, box, BoxEdit.Side.EAST, 5);
		assertEquals(new Shape.Box(9, 4, 4), east.box());
		assertEquals(-2, east.center().x() - east.box().width() / 2, 1e-9, "west face unchanged");
		BoxEdit.Placed up = BoxEdit.moveSide(center, box, BoxEdit.Side.UP, -1);
		assertEquals(new Shape.Box(4, 3, 4), up.box());
		assertEquals(62, up.center().y() - up.box().height() / 2, 1e-9, "the floor stays when the top shrinks");
	}

	@Test
	void snapPutsEveryEdgeOnABlockBoundary() {
		// "My position" at x 3.7: a 10-wide box from -1.3 to 8.7 becomes -1 to 9.
		BoxEdit.Placed p = BoxEdit.snap(new Point(3.7, -59.4, 0.5), new Shape.Box(10, 3, 5));
		assertEquals(new Point(4, -59.5, 0.5), p.center());
		for (double edge : new double[] {p.center().x() - 5, p.center().y() - 1.5, p.center().z() - 2.5}) {
			assertEquals(Math.rint(edge), edge, 1e-9, "edge " + edge + " should be on a block boundary");
		}
		BoxEdit.Placed aligned = BoxEdit.fromCorners(0, 64, 0, 2, 64, 0);
		assertEquals(aligned, BoxEdit.snap(aligned.center(), aligned.box()), "a marked box is already aligned");
	}

	@Test
	void sizeStaysWithinTheLimits() {
		Shape.Box box = new Shape.Box(2, 2, 2);
		BoxEdit.Placed shrunk = BoxEdit.moveSide(new Point(0, 0, 0), box, BoxEdit.Side.WEST, -5);
		assertEquals(1, shrunk.box().width(), "a box is at least 1 block wide");
		assertEquals(1, shrunk.center().x() + shrunk.box().width() / 2, 1e-9, "the east face stays put");
		BoxEdit.Placed grown = BoxEdit.moveSide(new Point(0, 0, 0), new Shape.Box(510, 2, 2), BoxEdit.Side.EAST, 5);
		assertEquals(512, grown.box().width(), "at most 512 blocks");
	}
}
