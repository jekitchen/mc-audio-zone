package audiozone.core.outline;

import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.core.zones.ZoneEdge;
import audiozone.core.zones.ZoneMode;

/**
 * Builds the line segments for a zone's outline (PRD FR-10.2, FR-10.3):
 * <ul>
 *   <li>box: its 12 edges;</li>
 *   <li>sphere: three great circles, one in each axis plane through the anchor;</li>
 *   <li>cylinder: top and bottom circles plus four vertical lines.</li>
 * </ul>
 * Outside zones are marked dashed. A fade zone gets an inner outline where the fade reaches full strength: the shape
 * shrunk by the fade distance for Inside zones, grown by it for Outside zones.
 *
 * <p>Build once per zone and cache: outlines only change when the zone is edited. A personal bubble's outline is
 * rebuilt around the player while its editor is open.
 */
public final class OutlineGeometry {
	/** Segments per circle for small shapes, so they still look round. */
	public static final int MIN_CIRCLE_SEGMENTS = 16;
	/** Segments per circle for big shapes, to bound drawing cost (FR-10.6: 64 zones under 0.3 ms a frame). */
	public static final int MAX_CIRCLE_SEGMENTS = 96;

	private OutlineGeometry() {
	}

	/** The outline at the zone's own anchor, with a hard profile default edge. */
	public static Outline of(Zone zone) {
		return of(zone, ZoneEdge.hard(), null);
	}

	/**
	 * @param profileEdge the profile's default edge, for zones whose edge is {@link ZoneEdge#DEFAULT}
	 * @param anchor      where to draw it: the player's eye position for a bubble; {@code null} for the zone's anchor
	 */
	public static Outline of(Zone zone, ZoneEdge profileEdge, Point anchor) {
		Point a = anchor != null ? anchor : zone.anchor();
		boolean dashed = zone.mode() == ZoneMode.OUTSIDE;
		ZoneEdge edge = zone.edge().mode() == ZoneEdge.Mode.DEFAULT ? profileEdge : zone.edge();
		Outline inner = null;
		if (edge.mode() == ZoneEdge.Mode.FADE) {
			double grow = zone.mode() == ZoneMode.INSIDE ? -edge.fadeDistance() : edge.fadeDistance();
			double[] coords = lines(zone.shape(), a, grow);
			if (coords != null) {
				inner = new Outline(coords, dashed, null);
			}
		}
		return new Outline(lines(zone.shape(), a, 0), dashed, inner);
	}

	/** The shape's lines with every size grown by {@code grow} blocks (negative shrinks); null if nothing is left. */
	private static double[] lines(Shape shape, Point a, double grow) {
		return switch (shape) {
			case Shape.Box b -> {
				double hx = b.width() / 2 + grow;
				double hy = b.height() / 2 + grow;
				double hz = b.depth() / 2 + grow;
				yield hx > 0 && hy > 0 && hz > 0 ? box(a, hx, hy, hz) : null;
			}
			case Shape.Sphere s -> s.radius() + grow > 0 ? sphere(a, s.radius() + grow) : null;
			case Shape.Cylinder c -> {
				double r = c.radius() + grow;
				double hh = c.height() / 2 + grow;
				yield r > 0 && hh > 0 ? cylinder(a, r, hh) : null;
			}
		};
	}

	private static double[] box(Point a, double hx, double hy, double hz) {
		double x0 = a.x() - hx;
		double x1 = a.x() + hx;
		double y0 = a.y() - hy;
		double y1 = a.y() + hy;
		double z0 = a.z() - hz;
		double z1 = a.z() + hz;
		return new double[] {
				// Bottom rectangle
				x0, y0, z0, x1, y0, z0,
				x1, y0, z0, x1, y0, z1,
				x1, y0, z1, x0, y0, z1,
				x0, y0, z1, x0, y0, z0,
				// Top rectangle
				x0, y1, z0, x1, y1, z0,
				x1, y1, z0, x1, y1, z1,
				x1, y1, z1, x0, y1, z1,
				x0, y1, z1, x0, y1, z0,
				// Vertical edges
				x0, y0, z0, x0, y1, z0,
				x1, y0, z0, x1, y1, z0,
				x1, y0, z1, x1, y1, z1,
				x0, y0, z1, x0, y1, z1,
		};
	}

	private static double[] sphere(Point a, double r) {
		int n = circleSegments(r);
		double[] coords = new double[3 * n * 6];
		int k = 0;
		for (int plane = 0; plane < 3; plane++) {
			k = circle(coords, k, a, plane, r, 0, n);
		}
		return coords;
	}

	private static double[] cylinder(Point a, double r, double halfHeight) {
		int n = circleSegments(r);
		double[] coords = new double[(2 * n + 4) * 6];
		int k = circle(coords, 0, a, 1, r, -halfHeight, n);
		k = circle(coords, k, a, 1, r, halfHeight, n);
		double[][] sides = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
		for (double[] s : sides) {
			coords[k++] = a.x() + s[0];
			coords[k++] = a.y() - halfHeight;
			coords[k++] = a.z() + s[1];
			coords[k++] = a.x() + s[0];
			coords[k++] = a.y() + halfHeight;
			coords[k++] = a.z() + s[1];
		}
		return coords;
	}

	private static int circleSegments(double r) {
		return Math.max(MIN_CIRCLE_SEGMENTS, Math.min(MAX_CIRCLE_SEGMENTS, (int) Math.ceil(r * 2)));
	}

	/**
	 * Writes a closed circle of {@code n} segments in plane 0 (XY), 1 (XZ) or 2 (YZ) around the anchor, shifted by
	 * {@code offset} along the plane's normal (used for cylinder tops and bottoms).
	 */
	private static int circle(double[] coords, int k, Point a, int plane, double r, double offset, int n) {
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n; // closes the circle on the exact first point
			k = put(coords, k, a, plane, r, i, n, offset);
			k = put(coords, k, a, plane, r, j, n, offset);
		}
		return k;
	}

	private static int put(double[] coords, int k, Point a, int plane, double r, int step, int n, double offset) {
		double angle = 2 * Math.PI * step / n;
		double u = Math.cos(angle) * r;
		double v = Math.sin(angle) * r;
		switch (plane) {
			case 0 -> {
				coords[k] = a.x() + u;
				coords[k + 1] = a.y() + v;
				coords[k + 2] = a.z() + offset;
			}
			case 1 -> {
				coords[k] = a.x() + u;
				coords[k + 1] = a.y() + offset;
				coords[k + 2] = a.z() + v;
			}
			default -> {
				coords[k] = a.x() + offset;
				coords[k + 1] = a.y() + u;
				coords[k + 2] = a.z() + v;
			}
		}
		return k + 3;
	}
}
