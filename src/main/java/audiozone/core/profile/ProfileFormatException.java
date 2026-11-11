package audiozone.core.profile;

import audiozone.core.Message;

/**
 * A profile file that cannot be read. {@link #problem()} says what is wrong in plain words, with its place in the
 * file, as a translatable message (PRD 9.3, FR-11.6).
 */
public final class ProfileFormatException extends Exception {
	private final transient Message problem;

	public ProfileFormatException(Message problem) {
		super(problem.toString());
		this.problem = problem;
	}

	public ProfileFormatException(Message problem, Throwable cause) {
		super(problem.toString(), cause);
		this.problem = problem;
	}

	/** What is wrong, for the player. */
	public Message problem() {
		return problem;
	}
}
