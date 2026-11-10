package audiozone.core.zones;

/**
 * Box geometry for the zone editor's drawing tools (M8 review): a box from two marked corner blocks, and growing or
 * shrinking one side. A zone's anchor is its box's center (FR-8.3), so both return a new center and size.
 */
public final class BoxEdit {
	/** A box zone's center and size. */
	public record Placed(Point center, Shape.Box box) {
	}

	/** A side of a box. North is -z, south +z, east +x, west -x (Minecraft's compass). */
	public enum Side {
		NORTH(2, -1), SOUTH(2, 1), EAST(0, 1), WEST(0, -1), UP(1, 1), DOWN(1, -1);

		/** 0 = x, 1 = y, 2 = z. */
		final int axis;
		/** +1 if the side faces the positive direction of its axis. */
		final int sign;

		Side(int axis, int sign) {
			this.axis = axis;
			this.sign = sign;
		}
	}

	private BoxEdit() {
	}

	/**
	 * The box that covers both corner blocks and every block between them: corners (0, 64, 0) and (2, 64, 0) give a 3 ×
	 * 1 × 1 box centered on (1.5, 64.5, 0.5).
	 */
	public static Placed fromCorners(int x1, int y1, int z1, int x2, int y2, int z2) {
		double[] min = {Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)};
		double[] max = {Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1};
		double[] size = new double[3];
		for (int i = 0; i < 3; i++) {
			size[i] = Math.min(Shape.Box.MAX_SIDE, max[i] - min[i]);
		}
		Point center = new Point(min[0] + size[0] / 2, min[1] + size[1] / 2, min[2] + size[2] / 2);
		return new Placed(center, new Shape.Box(size[0], size[1], size[2]));
	}

	/**
	 * Moves one side out ({@code blocks > 0}) or in ({@code blocks < 0}); the opposite side stays where it is. The size
	 * stays within the box limits (1 to 512); a change that would leave them is cut short.
	 */
	public static Placed moveSide(Point center, Shape.Box box, Side side, double blocks) {
		double[] c = {center.x(), center.y(), center.z()};
		double[] size = {box.width(), box.height(), box.depth()};
		int a = side.axis;
		double newSize = Math.max(Shape.Box.MIN_SIDE, Math.min(Shape.Box.MAX_SIDE, size[a] + blocks));
		double change = newSize - size[a];
		// The fixed side stays put, so the center moves half the change towards the moving side.
		c[a] += side.sign * change / 2;
		size[a] = newSize;
		return new Placed(new Point(c[0], c[1], c[2]), new Shape.Box(size[0], size[1], size[2]));
	}
}
