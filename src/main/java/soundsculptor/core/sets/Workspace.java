package soundsculptor.core.sets;

import java.util.Objects;

import soundsculptor.core.profile.Profile;

/**
 * Everything the editor works on: the profile and the shared Sound Set library.
 */
public record Workspace(Profile profile, SetLibrary library) {
	public Workspace {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(library, "library");
	}
}
