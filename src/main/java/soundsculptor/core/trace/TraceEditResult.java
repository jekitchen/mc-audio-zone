package soundsculptor.core.trace;

import java.util.List;

/**
 * The outcome of one edit from the trace panel (PRD FR-14.4).
 *
 * @param error                why the edit was refused, or {@code null} if it was applied
 * @param before               the rules and zones before the edit, for Undo
 * @param after                the rules and zones to save; the same object as {@code before} when refused
 * @param predicted            the trace recomputed at once for {@code after}
 * @param predictedForNextPlay whether the panel labels the result "Predicted for next play": true when the sound has
 *                             played, because the result is worked out from its last play position
 * @param toastKey             the Undo toast's translation key, or {@code null} when refused
 * @param toastArgs            arguments for the toast text: the rule's pattern, or the zone's name
 */
public record TraceEditResult(TraceEditError error, EditableRules before, EditableRules after, SoundTrace predicted,
		boolean predictedForNextPlay, String toastKey, List<String> toastArgs) {
	/** Translation key for the "Predicted for next play" label. */
	public static final String PREDICTED_KEY = "soundsculptor.trace.edit.predicted";

	public TraceEditResult {
		toastArgs = List.copyOf(toastArgs);
	}

	public boolean ok() {
		return error == null;
	}
}
