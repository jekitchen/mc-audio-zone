package soundsculptor.core.quick;

import java.util.List;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.trace.TraceEditError;

/**
 * The outcome of one Recently Played quick action (PRD FR-1.5).
 *
 * @param error     why the action was refused, or {@code null}; its message is shown and nothing changes
 * @param before    the profile before the action, for Undo ({@link soundsculptor.core.undo.UndoSlot})
 * @param after     the profile to save; the same object as {@code before} when refused or when nothing changed
 * @param toastKey  the Undo toast's translation key, or {@code null} when refused or when nothing changed
 * @param toastArgs arguments for the toast text: the sound ID
 */
public record QuickActionResult(TraceEditError error, Profile before, Profile after, String toastKey,
		List<String> toastArgs) {
	public QuickActionResult {
		toastArgs = List.copyOf(toastArgs);
	}

	public boolean ok() {
		return error == null;
	}

	/** Whether there is anything to save and offer Undo for. */
	public boolean changed() {
		return after != before;
	}
}
