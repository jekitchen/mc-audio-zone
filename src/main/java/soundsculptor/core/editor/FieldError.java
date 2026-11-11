package soundsculptor.core.editor;

import java.util.List;

/**
 * Why an editor field was refused (PRD FR-12.7): a key in {@code assets/soundsculptor/lang/en_us.json} and the values
 * for its {@code %s} placeholders, already written as text (for example {@code ["1", "256"]}).
 */
public record FieldError(String translationKey, List<String> args) {
	/** Nothing typed in a field that needs a value. */
	public static final String REQUIRED = "soundsculptor.editor.error.required";
	/** Not a number. */
	public static final String NOT_A_NUMBER = "soundsculptor.editor.error.not_a_number";
	/** A decimal where only whole numbers are allowed. */
	public static final String NOT_WHOLE_NUMBER = "soundsculptor.editor.error.not_whole_number";
	/** Outside the allowed range; args are the minimum and maximum. */
	public static final String OUT_OF_RANGE = "soundsculptor.editor.error.out_of_range";

	public FieldError {
		args = List.copyOf(args);
	}

	static FieldError of(String translationKey) {
		return new FieldError(translationKey, List.of());
	}
}
