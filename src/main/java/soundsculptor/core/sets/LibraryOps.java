package soundsculptor.core.sets;

import java.util.List;

import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileOps;

/**
 * The library operations behind the Sound Sets tab: New, Duplicate, Rename and Delete (FR-4.7, FR-4.11).
 *
 * <p>None of them throws for a player-facing limit. Each returns the workspace unchanged when it cannot go ahead, and
 * a matching {@code can...} method returns the problem to show ({@code null} when the operation can go ahead).
 */
public final class LibraryOps {
	private static final String COPY_SUFFIX = " (copy)";
	/** The place is a zone this profile no longer has. */
	static final String ZONE_GONE = "soundsculptor.place_sets.zone_gone";

	private LibraryOps() {
	}

	/** {@code null} if {@link #create} will add a set named {@code name}, else why not: the name is not usable or the library is full. */
	public static Message canCreate(Workspace ws, String name) {
		Message problem = nameProblem(name);
		return problem != null ? problem : ws.library().canCreate();
	}

	/**
	 * An empty set named {@code name} (made unique), in the library and attached nowhere, with its new ID; refused with
	 * {@link #canCreate}'s problem.
	 */
	public static NewSet create(Workspace ws, String name) {
		Message problem = canCreate(ws, name);
		if (problem != null) {
			return NewSet.refused(ws, problem);
		}
		SetLibrary lib = ws.library();
		SoundSet set = new SoundSet(lib.newId(), lib.uniqueName(name.strip(), null), List.of());
		return NewSet.added(new Workspace(ws.profile(), lib.put(set)), set.id());
	}

	/**
	 * {@code null} if {@link #createAttached} will go ahead, else why not: the name is not usable, the library is full,
	 * the zone no longer exists, or the place already has {@value PlaceSets#MAX_SETS} library sets.
	 */
	public static Message canCreateAttached(Workspace ws, PlaceRef where, String name) {
		Message problem = canCreate(ws, name);
		if (problem != null) {
			return problem;
		}
		PlaceSets place = ws.profile().place(where);
		return place == null ? Message.of(ZONE_GONE) : place.canAttachAnother();
	}

	/**
	 * New set from a place's Attach set… (FR-8.6): an empty set named {@code name} (made unique), attached at the bottom
	 * of the place, in one workspace; refused with {@link #canCreateAttached}'s problem.
	 */
	public static NewSet createAttached(Workspace ws, PlaceRef where, String name) {
		Message problem = canCreateAttached(ws, where, name);
		if (problem != null) {
			return NewSet.refused(ws, problem);
		}
		NewSet made = create(ws, name);
		Workspace after = made.workspace();
		PlaceSets place = after.profile().place(where);
		return NewSet.added(new Workspace(after.profile().withPlace(where, place.attach(made.setId())), after.library()), made.setId());
	}

	/** {@code null} if the set can be duplicated, else the problem to show (the library is full). */
	public static Message canDuplicate(Workspace ws, String setId) {
		return ws.library().canCreate();
	}

	/**
	 * A copy named "name (copy)" (made unique, and cut to fit the name limit), with a new set ID, the same entries and
	 * attached nowhere, with its new ID. Refused if the set is not in the library or {@link #canDuplicate} reports a
	 * problem.
	 *
	 * <p>Entry IDs are only unique within a set, so the copy keeps them: entries are immutable, so the copy is
	 * independent of the original.
	 */
	public static NewSet duplicate(Workspace ws, String setId) {
		SoundSet source = ws.library().get(setId);
		if (source == null) {
			return NewSet.refused(ws, Message.of("soundsculptor.sets.error.not_found"));
		}
		Message problem = canDuplicate(ws, setId);
		if (problem != null) {
			return NewSet.refused(ws, problem);
		}
		SetLibrary lib = ws.library();
		SoundSet copy = new SoundSet(lib.newId(), lib.uniqueName(source.name(), null, COPY_SUFFIX), source.entries());
		return NewSet.added(new Workspace(ws.profile(), lib.put(copy)), copy.id());
	}

	/** {@code null} if the set can be renamed to {@code name}, else why not: the name is empty or too long. */
	public static Message canRename(Workspace ws, String setId, String name) {
		return nameProblem(name);
	}

	/**
	 * Renames the set; the ID stays. Spaces at either end are ignored, and a name another set already has (ignoring
	 * case) gets "(2)" and so on. Unchanged if the set is not in the library or {@link #canRename} reports a problem.
	 */
	public static Workspace rename(Workspace ws, String setId, String name) {
		SoundSet set = ws.library().get(setId);
		if (set == null || canRename(ws, setId, name) != null) {
			return ws;
		}
		String unique = ws.library().uniqueName(name.strip(), setId);
		if (unique.equals(set.name())) {
			return ws;
		}
		return new Workspace(ws.profile(), ws.library().put(set.withName(unique)));
	}

	/**
	 * What the delete warning shows (FR-4.11).
	 *
	 * @param placesHere    the places of the current profile that use the set: Everywhere, then zones in list order
	 * @param otherProfiles the display names of the other profiles that use it, never their zones
	 */
	public record DeletePlan(String setId, String name, List<PlaceRef> placesHere, List<String> otherProfiles) {
		public DeletePlan {
			placesHere = List.copyOf(placesHere);
			otherProfiles = List.copyOf(otherProfiles);
		}
	}

	/** The places here come from the live profile in {@code ws}; other profiles come from {@code usage}. */
	public static DeletePlan planDelete(Workspace ws, SetUsage usage, String currentKey, String setId) {
		SoundSet set = ws.library().get(setId);
		return new DeletePlan(setId, set == null ? "" : set.name(), ws.profile().placesUsing(setId),
				usage.otherProfileNames(setId, currentKey));
	}

	/** Removes the set from the library and detaches it everywhere in the loaded profile. Other profiles lose it when next joined. */
	public static Workspace delete(Workspace ws, String setId) {
		Profile profile = ws.profile();
		for (PlaceRef where : profile.placesUsing(setId)) {
			profile = profile.withPlace(where, profile.place(where).detach(setId));
		}
		return new Workspace(profile, ws.library().remove(setId));
	}

	private static Message nameProblem(String name) {
		Message problem = ProfileOps.nameProblem(name);
		if (problem == null && name.strip().length() > SoundSet.MAX_NAME_LENGTH) {
			// The set file limits characters, not code points.
			return Message.of("soundsculptor.profile.problem.name_too_long", SoundSet.MAX_NAME_LENGTH);
		}
		return problem;
	}
}
