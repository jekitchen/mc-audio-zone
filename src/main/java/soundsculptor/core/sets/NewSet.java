package soundsculptor.core.sets;

import java.util.Objects;

import soundsculptor.core.Message;

/**
 * The result of an operation that adds a set to the library (New set, Duplicate, Save to library): the new workspace
 * and the new set's ID, or the problem that stopped it, in which case the workspace is the one passed in, unchanged.
 * Nothing that returns one throws for a player-facing limit.
 *
 * @param workspace the workspace after the operation, or unchanged when refused
 * @param setId     the new library set's ID, or {@code null} when refused
 * @param problem   why it was refused, or {@code null}
 */
public record NewSet(Workspace workspace, String setId, Message problem) {
	public NewSet {
		Objects.requireNonNull(workspace, "workspace");
		if ((setId == null) == (problem == null)) {
			throw new IllegalArgumentException("exactly one of setId and problem must be given");
		}
	}

	static NewSet refused(Workspace unchanged, Message problem) {
		return new NewSet(unchanged, null, Objects.requireNonNull(problem, "problem"));
	}

	static NewSet added(Workspace after, String setId) {
		return new NewSet(after, Objects.requireNonNull(setId, "setId"), null);
	}

	public boolean ok() {
		return problem == null;
	}

	/** The new set's name, made unique; {@code null} when refused. */
	public String name() {
		return ok() ? workspace.library().get(setId).name() : null;
	}
}
