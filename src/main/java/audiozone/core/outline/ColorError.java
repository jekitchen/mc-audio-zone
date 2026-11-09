package audiozone.core.outline;

/** Why the color picker's hex field was refused (PRD FR-10.4, FR-12.7). */
public enum ColorError {
	/** Nothing typed. */
	EMPTY("empty"),
	/** Not six hex digits, with or without a leading {@code #}. */
	INVALID_HEX("invalid_hex");

	private final String key;

	ColorError(String key) {
		this.key = key;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json}. */
	public String translationKey() {
		return "audiozone.color.error." + key;
	}
}
