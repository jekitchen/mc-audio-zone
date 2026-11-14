package soundsculptor.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Places controls left to right and starts a new line when the next one does not fit, so a toolbar wraps instead of
 * cutting a control's text when a translation is longer (PRD 9.3, A-3).
 */
public final class RowFlow {
	private RowFlow() {
	}

	/** Where one control goes: its line (0 for the first) and its x offset from the line's start. */
	public record Spot(int line, int x) {
	}

	/**
	 * @param widths    each control's width, in order
	 * @param available the line's width; a control wider than this gets a line of its own
	 * @param gap       the space between two controls on a line
	 * @return one spot per control, in the same order
	 */
	public static List<Spot> place(int[] widths, int available, int gap) {
		List<Spot> spots = new ArrayList<>(widths.length);
		int line = 0;
		int x = 0;
		for (int width : widths) {
			if (width < 0) {
				throw new IllegalArgumentException("a width cannot be negative: " + width);
			}
			if (x > 0 && x + width > available) {
				line++;
				x = 0;
			}
			spots.add(new Spot(line, x));
			x += width + gap;
		}
		return spots;
	}

	/** How many lines {@code spots} use. */
	public static int lines(List<Spot> spots) {
		return spots.isEmpty() ? 0 : spots.get(spots.size() - 1).line() + 1;
	}
}
