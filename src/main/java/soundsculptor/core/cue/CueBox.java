package soundsculptor.core.cue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The lines in the muted-sound cue box (PRD FR-7.3 to FR-7.5): at most {@link #MAX_LINES}, a repeat of the same text
 * merges into its line with a count, and lines disappear {@link #DISPLAY_MS} after their latest repeat. When a new
 * line arrives and the box is full, the line repeated longest ago is dropped.
 *
 * <p>{@link #add} runs for every muted sound with a cue, which can be hundreds a second in a farm, so it allocates
 * nothing. Not thread-safe: add and read on the render thread.
 */
public final class CueBox {
	public static final int MAX_LINES = 5;
	/** Like vanilla subtitles, a line fades out after 3 seconds. */
	public static final long DISPLAY_MS = 3000;

	private final Slot[] slots = new Slot[MAX_LINES];

	public CueBox() {
		for (int i = 0; i < MAX_LINES; i++) {
			slots[i] = new Slot();
		}
	}

	/** Adds one cue: merges with a visible line with the same text, or takes a free or the oldest slot. */
	public void add(String text, CueDirection direction, long nowMs) {
		for (Slot slot : slots) {
			if (slot.visible(nowMs) && slot.text.equals(text)) {
				slot.count++;
				slot.direction = direction;
				slot.lastMs = nowMs;
				return;
			}
		}
		// A free (or faded) slot if there is one, otherwise the line repeated longest ago.
		Slot target = null;
		for (Slot slot : slots) {
			if (!slot.visible(nowMs)) {
				target = slot;
				break;
			}
			if (target == null || slot.lastMs < target.lastMs) {
				target = slot;
			}
		}
		target.text = text;
		target.count = 1;
		target.direction = direction;
		target.lastMs = nowMs;
	}

	/** Visible lines, newest first. Called once per frame by the HUD; may allocate. */
	public List<CueLine> visibleLines(long nowMs) {
		List<CueLine> lines = new ArrayList<>(MAX_LINES);
		for (Slot slot : slots) {
			if (slot.visible(nowMs)) {
				lines.add(new CueLine(slot.text, slot.count, slot.direction, nowMs - slot.lastMs));
			}
		}
		lines.sort(Comparator.comparingLong(CueLine::ageMs));
		return lines;
	}

	/** Removes every line, e.g. when the master switch is turned off or the world changes. */
	public void clear() {
		for (Slot slot : slots) {
			slot.text = null;
		}
	}

	private static final class Slot {
		String text;
		int count;
		CueDirection direction = CueDirection.NONE;
		long lastMs;

		boolean visible(long nowMs) {
			return text != null && nowMs - lastMs < DISPLAY_MS;
		}
	}
}
