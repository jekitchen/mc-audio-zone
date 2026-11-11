package soundsculptor.core.rules;

/**
 * Why a rule pattern was refused (PRD FR-4.2). The UI shows the translated message under the field and disables Save.
 */
public enum PatternError {
	/** Nothing typed. */
	EMPTY("empty"),
	/** A character outside {@code a-z 0-9 _ . - / : *}. */
	INVALID_CHARACTER("invalid_character"),
	/** More than one {@code :}. Sound IDs have exactly one. */
	TOO_MANY_COLONS("too_many_colons"),
	/** Nothing before or after the {@code :}. */
	EMPTY_PART("empty_part");

	private final String key;

	PatternError(String key) {
		this.key = key;
	}

	/** Key in {@code assets/soundsculptor/lang/en_us.json}. */
	public String translationKey() {
		return "soundsculptor.pattern.error." + key;
	}
}
