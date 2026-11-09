package audiozone.core.zones;

/**
 * A zone's shape, centred on its anchor (PRD FR-8.3). Positions are given as offsets from the anchor. Points on the
 * boundary count as inside. The vertical cylinder arrives in v1 (M13).
 */
public sealed interface Shape {
	/** Whether the offset {@code (dx, dy, dz)} from the anchor is inside the shape. */
	boolean contains(double dx, double dy, double dz);

	/** Geometric volume in cubic blocks, used to break distance ties (FR-9 step 5). */
	double volume();

	/** Sphere with radius 1 to 256 blocks. */
	record Sphere(double radius) implements Shape {
		public static final double MIN_RADIUS = 1;
		public static final double MAX_RADIUS = 256;

		public Sphere {
			requireRange("radius", radius, MIN_RADIUS, MAX_RADIUS);
		}

		@Override
		public boolean contains(double dx, double dy, double dz) {
			// Cheap bounding-box test first (PRD 7.4), then the exact one.
			if (Math.abs(dx) > radius || Math.abs(dy) > radius || Math.abs(dz) > radius) {
				return false;
			}
			return dx * dx + dy * dy + dz * dz <= radius * radius;
		}

		@Override
		public double volume() {
			return 4.0 / 3.0 * Math.PI * radius * radius * radius;
		}
	}

	/** Axis-aligned box; width along x, height along y, depth along z, each 1 to 512 blocks. */
	record Box(double width, double height, double depth) implements Shape {
		public static final double MIN_SIDE = 1;
		public static final double MAX_SIDE = 512;

		public Box {
			requireRange("width", width, MIN_SIDE, MAX_SIDE);
			requireRange("height", height, MIN_SIDE, MAX_SIDE);
			requireRange("depth", depth, MIN_SIDE, MAX_SIDE);
		}

		@Override
		public boolean contains(double dx, double dy, double dz) {
			return Math.abs(dx) <= width / 2 && Math.abs(dy) <= height / 2 && Math.abs(dz) <= depth / 2;
		}

		@Override
		public double volume() {
			return width * height * depth;
		}
	}

	private static void requireRange(String name, double value, double min, double max) {
		if (!(value >= min && value <= max)) {
			throw new IllegalArgumentException(name + " " + value + " is outside " + min + ".." + max);
		}
	}
}
