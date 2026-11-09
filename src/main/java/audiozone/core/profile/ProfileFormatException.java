package audiozone.core.profile;

/** A profile file that cannot be read; the message says what is wrong in plain words. */
public final class ProfileFormatException extends Exception {
	public ProfileFormatException(String message) {
		super(message);
	}

	public ProfileFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
