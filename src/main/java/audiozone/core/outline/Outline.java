package audiozone.core.outline;

/**
 * A zone's outline as straight line segments in world coordinates (PRD FR-10.2). Stored flat, six numbers per
 * segment, so a renderer can cache it per zone and emit lines without creating objects each frame.
 */
public final class Outline {
	private final double[] coords;

	Outline(double[] coords) {
		this.coords = coords;
	}

	public int segmentCount() {
		return coords.length / 6;
	}

	public double x1(int i) {
		return coords[i * 6];
	}

	public double y1(int i) {
		return coords[i * 6 + 1];
	}

	public double z1(int i) {
		return coords[i * 6 + 2];
	}

	public double x2(int i) {
		return coords[i * 6 + 3];
	}

	public double y2(int i) {
		return coords[i * 6 + 4];
	}

	public double z2(int i) {
		return coords[i * 6 + 5];
	}
}
