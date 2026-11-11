package soundsculptor.core.ui;

/**
 * Value logic for the slider component (PRD 6.2): range, step, fine step, typed values and a warning threshold.
 * Pure Java so it can be unit-tested without Minecraft.
 *
 * <p>Values from the keyboard or mouse snap to {@code step} (or {@code fineStep} with Shift). Typed values snap to
 * {@code fineStep}, the finest resolution the slider supports. Values are always clamped to {@code [min, max]}.
 */
public final class SliderModel {
	private final double min;
	private final double max;
	private final double step;
	private final double fineStep;
	private final double warnAbove;
	private double value;

	/**
	 * @param warnAbove values strictly above this are in the warning zone; {@link Double#NaN} for no warning zone
	 */
	public SliderModel(double min, double max, double step, double fineStep, double warnAbove, double initial) {
		if (!(min < max)) {
			throw new IllegalArgumentException("min must be less than max");
		}
		if (!(step > 0) || !(fineStep > 0) || fineStep > step) {
			throw new IllegalArgumentException("need 0 < fineStep <= step");
		}
		this.min = min;
		this.max = max;
		this.step = step;
		this.fineStep = fineStep;
		this.warnAbove = warnAbove;
		this.value = snap(initial, fineStep);
	}

	public double value() {
		return value;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	/** Sets a typed or programmatic value; snaps to the fine step and clamps. */
	public void set(double newValue) {
		value = snap(newValue, fineStep);
	}

	/** Arrow keys: {@code direction} is +1 or -1; {@code fine} uses the fine step (Shift). */
	public void stepBy(int direction, boolean fine) {
		double size = fine ? fineStep : step;
		// Move to the next grid point in that direction, so an off-grid value lands on the grid first.
		double grid = (value - min) / size;
		double target = direction > 0 ? Math.floor(grid + 1e-9) + 1 : Math.ceil(grid - 1e-9) - 1;
		value = snap(min + target * size, fineStep);
	}

	/** Position of the handle, 0 to 1. */
	public double fraction() {
		return (value - min) / (max - min);
	}

	/** Mouse drag or click: maps a 0–1 position to a value on the step grid. */
	public void setFraction(double fraction) {
		double clamped = Math.max(0.0, Math.min(1.0, fraction));
		value = snap(min + clamped * (max - min), step);
	}

	public boolean hasWarningZone() {
		return !Double.isNaN(warnAbove);
	}

	public boolean isWarning() {
		return hasWarningZone() && value > warnAbove + 1e-9;
	}

	public double warnAbove() {
		return warnAbove;
	}

	private double snap(double raw, double grid) {
		if (Double.isNaN(raw)) {
			return value;
		}
		double snapped = min + Math.round((raw - min) / grid) * grid;
		// Round away floating-point noise (e.g. 0.30000000000000004) at the fine-step resolution.
		double scale = 1.0 / fineStep;
		snapped = Math.round(snapped * scale) / scale;
		return Math.max(min, Math.min(max, snapped));
	}
}
