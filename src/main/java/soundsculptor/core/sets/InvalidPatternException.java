package soundsculptor.core.sets;

/** Thrown by {@link SoundPattern#of} for text that is not a valid pattern. */
public final class InvalidPatternException extends IllegalArgumentException {
	private final PatternError error;

	public InvalidPatternException(String pattern, PatternError error) {
		super("Invalid sound pattern '" + pattern + "': " + error);
		this.error = error;
	}

	public PatternError error() {
		return error;
	}
}
