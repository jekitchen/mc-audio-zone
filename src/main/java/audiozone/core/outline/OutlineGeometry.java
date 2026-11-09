package audiozone.core.outline;

import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

/**
 * Builds the line segments for a zone's outline (PRD FR-10.2): a box as its 12 edges, a sphere as three great
 * circles (one in each axis plane through the anchor). The cylinder (top and bottom circles plus four vertical
 * lines), dashed Outside lines and the inner fade line arrive with v1 (M13).
 *
 * <p>Build once per zone and cache: outlines only change when the zone is edited.
 */
public final class OutlineGeometry {
	/** Segments per circle for small spheres, so they still look round. */
	public static final int MIN_CIRCLE_SEGMENTS = 16;
	/** Segments per circle for big spheres, to bound drawing cost (FR-10.6: 64 zones under 0.3 ms a frame). */
	public static final int MAX_CIRCLE_SEGMENTS = 96;

	private OutlineGeometry() {
	}

	public static Outline of(Zone zone) {
		Point a = zone.anchor();
		return switch (zone.shape()) {
			case Shape.Box box -> box(a, box.width() / 2, box.height() / 2, box.depth() / 2);
			case Shape.Sphere sphere -> sphere(a, sphere.radius());
		};
	}

	private static Outline box(Point a, double hx, double hy, double hz) {
		double x0 = a.x() - hx;
		double x1 = a.x() + hx;
		double y0 = a.y() - hy;
		double y1 = a.y() + hy;
		double z0 = a.z() - hz;
		double z1 = a.z() + hz;
		return new Outline(new double[] {
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
		});
	}

	private static Outline sphere(Point a, double r) {
		int n = Math.max(MIN_CIRCLE_SEGMENTS, Math.min(MAX_CIRCLE_SEGMENTS, (int) Math.ceil(r * 2)));
		double[] cos = new double[n];
		double[] sin = new double[n];
		for (int i = 0; i < n; i++) {
			double angle = 2 * Math.PI * i / n;
			cos[i] = Math.cos(angle) * r;
			sin[i] = Math.sin(angle) * r;
		}
		double[] coords = new double[3 * n * 6];
		int k = 0;
		for (int plane = 0; plane < 3; plane++) {
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n; // closes the circle on the exact first point
				k = put(coords, k, a, plane, cos[i], sin[i]);
				k = put(coords, k, a, plane, cos[j], sin[j]);
			}
		}
		return new Outline(coords);
	}

	/** Writes one point of a circle in plane 0 (XY), 1 (XZ) or 2 (YZ) around the anchor. */
	private static int put(double[] coords, int k, Point a, int plane, double u, double v) {
		switch (plane) {
			case 0 -> {
				coords[k] = a.x() + u;
				coords[k + 1] = a.y() + v;
				coords[k + 2] = a.z();
			}
			case 1 -> {
				coords[k] = a.x() + u;
				coords[k + 1] = a.y();
				coords[k + 2] = a.z() + v;
			}
			default -> {
				coords[k] = a.x();
				coords[k + 1] = a.y() + u;
				coords[k + 2] = a.z() + v;
			}
		}
		return k + 3;
	}
}
