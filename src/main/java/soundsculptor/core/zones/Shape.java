package soundsculptor.core.zones;

/**
 * A zone's shape, centred on its anchor (PRD FR-8.3): a box (MVP) or a vertical cylinder (v1). Positions are given as
 * offsets from the anchor. Points on the boundary count as inside.
 */
public sealed interface Shape {
	/** Whether the offset {@code (dx, dy, dz)} from the anchor is inside the shape. */
	boolean contains(double dx, double dy, double dz);

	/** Geometric volume in cubic blocks, used to break distance ties (FR-9 step 5). */
	double volume();

	/** Distance from an inside point to the nearest boundary; 0 for points outside (FR-8.5 Inside fade). */
	double depthInside(double dx, double dy, double dz);

	/** Distance from an outside point to the nearest point of the shape; 0 for points inside (Outside fade). */
	double distanceOutside(double dx, double dy, double dz);

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

		@Override
		public double depthInside(double dx, double dy, double dz) {
			double d = Math.min(width / 2 - Math.abs(dx), Math.min(height / 2 - Math.abs(dy), depth / 2 - Math.abs(dz)));
			return Math.max(0, d);
		}

		@Override
		public double distanceOutside(double dx, double dy, double dz) {
			double ox = Math.max(0, Math.abs(dx) - width / 2);
			double oy = Math.max(0, Math.abs(dy) - height / 2);
			double oz = Math.max(0, Math.abs(dz) - depth / 2);
			return Math.sqrt(ox * ox + oy * oy + oz * oz);
		}
	}

	/** Vertical cylinder (v1): radius 1 to 256 blocks, height 1 to 512, centred on the anchor. */
	record Cylinder(double radius, double height) implements Shape {
		public static final double MIN_RADIUS = 1;
		public static final double MAX_RADIUS = 256;
		public static final double MIN_HEIGHT = 1;
		public static final double MAX_HEIGHT = 512;

		public Cylinder {
			requireRange("radius", radius, MIN_RADIUS, MAX_RADIUS);
			requireRange("height", height, MIN_HEIGHT, MAX_HEIGHT);
		}

		@Override
		public boolean contains(double dx, double dy, double dz) {
			return Math.abs(dy) <= height / 2 && dx * dx + dz * dz <= radius * radius;
		}

		@Override
		public double volume() {
			return Math.PI * radius * radius * height;
		}

		@Override
		public double depthInside(double dx, double dy, double dz) {
			double side = radius - Math.sqrt(dx * dx + dz * dz);
			double ends = height / 2 - Math.abs(dy);
			return Math.max(0, Math.min(side, ends));
		}

		@Override
		public double distanceOutside(double dx, double dy, double dz) {
			double radial = Math.max(0, Math.sqrt(dx * dx + dz * dz) - radius);
			double vertical = Math.max(0, Math.abs(dy) - height / 2);
			return Math.sqrt(radial * radial + vertical * vertical);
		}
	}

	private static void requireRange(String name, double value, double min, double max) {
		if (!(value >= min && value <= max)) {
			throw new IllegalArgumentException(name + " " + value + " is outside " + min + ".." + max);
		}
	}
}
