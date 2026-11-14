package soundsculptor.core.ui;

import java.util.List;

/**
 * A wrapped label's lines when its text is live (PRD 9.3, FR-13.3): the label reserves as many lines as the text
 * needed when the screen was laid out, and each frame shows the text's current lines in that room.
 */
public final class WrappedLines {
	private WrappedLines() {
	}

	/** The lines to reserve for text wrapped into {@code lines}: at least one, so an empty text keeps its line. */
	public static int count(List<String> lines) {
		return Math.max(1, lines.size());
	}

	/**
	 * Line {@code index} of {@code reserved}: the text's line, or "" past its end. When the text now needs more lines
	 * than reserved, the last line holds the rest, joined with spaces, to be cut with "…" when drawn.
	 */
	public static String line(List<String> lines, int reserved, int index) {
		if (index >= lines.size()) {
			return "";
		}
		if (index < reserved - 1 || lines.size() <= reserved) {
			return lines.get(index);
		}
		return String.join(" ", lines.subList(index, lines.size()));
	}
}
