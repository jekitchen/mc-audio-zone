package soundsculptor.core.sets;

import soundsculptor.core.Message;

/**
 * Save to library for a place's Quick changes (PRD FR-4.10): the set gets a name and becomes a normal library set,
 * still attached in the same slot, and the place no longer has Quick changes, so its next quick action starts a new
 * one pinned above. Not on the hot path.
 */
public final class QuickChanges {
	private QuickChanges() {
	}

	/**
	 * {@code null} if the place's Quick changes can be saved to the library, else the problem to show: there is no
	 * Quick changes (also for a zone that no longer exists), the place already has {@value PlaceSets#MAX_SETS} library
	 * sets, or the library is full.
	 */
	public static Message canSave(Workspace ws, PlaceRef where) {
		PlaceSets place = ws.profile().place(where);
		if (place == null || place.quickChanges() == null) {
			return Message.of("soundsculptor.sets.error.no_quick_changes");
		}
		Message problem = place.canAttachAnother();
		return problem != null ? problem : ws.library().canCreate();
	}

	/**
	 * Saves the place's Quick changes as a new library set with its entries, named {@code name} or, if another set has
	 * that name, the first free "name (2)", "name (3)" and so on; the result carries the new set's ID. Refused with
	 * {@link #canSave}'s problem.
	 */
	public static NewSet saveToLibrary(Workspace ws, PlaceRef where, String name) {
		Message problem = canSave(ws, where);
		if (problem != null) {
			return NewSet.refused(ws, problem);
		}
		PlaceSets place = ws.profile().place(where);
		String id = ws.library().newId();
		SoundSet saved = new SoundSet(id, ws.library().uniqueName(name, null), place.quickEntries());
		return NewSet.added(new Workspace(ws.profile().withPlace(where, place.quickSavedAs(id)), ws.library().put(saved)), id);
	}
}
