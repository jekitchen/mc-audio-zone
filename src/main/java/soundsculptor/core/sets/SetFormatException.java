package soundsculptor.core.sets;

import soundsculptor.core.Message;

/**
 * A Sound Set file that cannot be read. {@link #problem()} says what is wrong in plain words, with its place in the
 * file, as a translatable message (PRD 9.3, FR-11.6).
 */
public final class SetFormatException extends Exception {
	private final transient Message problem;

	public SetFormatException(Message problem) {
		super(problem.toString());
		this.problem = problem;
	}

	public SetFormatException(Message problem, Throwable cause) {
		super(problem.toString(), cause);
		this.problem = problem;
	}

	/** What is wrong, for the player. */
	public Message problem() {
		return problem;
	}
}
