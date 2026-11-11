package soundsculptor.core.outline;

/**
 * A zone's outline as straight line segments in world coordinates (PRD FR-10.2). Stored flat, six numbers per
 * segment, so a renderer can cache it per zone and emit lines without creating objects each frame.
 *
 * <p>Line style carries meaning without relying on color (FR-10.3): Inside zones are solid, Outside zones
 * {@link #dashed()}. A fade zone also has an {@link #inner()} outline where the fade reaches full strength, to be
 * drawn fainter in the same style.
 */
public final class Outline {
	private final double[] coords;
	private final boolean dashed;
	private final Outline inner;

	Outline(double[] coords) {
		this(coords, false, null);
	}

	Outline(double[] coords, boolean dashed, Outline inner) {
		this.coords = coords;
		this.dashed = dashed;
		this.inner = inner;
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

	/** True for Outside zones: draw these lines dashed. */
	public boolean dashed() {
		return dashed;
	}

	/** The fainter line where a fade reaches full strength, or {@code null} for a hard edge. */
	public Outline inner() {
		return inner;
	}
}
