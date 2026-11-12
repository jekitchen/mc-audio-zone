package soundsculptor.core.quick;

import soundsculptor.core.Message;
import soundsculptor.core.sets.Workspace;

/**
 * The outcome of one quick action on a sound row (PRD FR-1.5, FR-4.10).
 *
 * @param error  why the action was refused, or {@code null}; it is shown and nothing changes
 * @param before the workspace before the action, for Undo ({@link soundsculptor.core.undo.UndoSlot})
 * @param after  the workspace to save; the same object as {@code before} when refused or when nothing changed
 */
public record QuickActionResult(Message error, Workspace before, Workspace after) {
	public boolean ok() {
		return error == null;
	}

	/** Whether there is anything to save and offer Undo for. */
	public boolean changed() {
		return after != before;
	}
}
