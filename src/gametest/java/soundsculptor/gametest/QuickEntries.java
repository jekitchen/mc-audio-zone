package soundsculptor.gametest;

import java.util.ArrayList;
import java.util.List;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;

/** Test fixtures: the entries of a place's Quick changes (PRD FR-4.10), read and written straight on a profile. */
final class QuickEntries {
	private QuickEntries() {
	}

	/** Everywhere's Quick changes entries, in order; empty if it has none. */
	static List<SetEntry> of(Profile profile) {
		return of(profile, PlaceRef.EVERYWHERE);
	}

	static List<SetEntry> of(Profile profile, PlaceRef where) {
		PlaceSets place = profile.place(where);
		return place == null ? List.of() : place.quickEntries();
	}

	/** The entry with this ID in Everywhere's Quick changes, or null. */
	static SetEntry get(Profile profile, String id) {
		for (SetEntry entry : of(profile)) {
			if (entry.id().equals(id)) {
				return entry;
			}
		}
		return null;
	}

	/** Replaces the entry with the same ID in Everywhere's Quick changes, or adds it at the end. */
	static Profile put(Profile profile, SetEntry entry) {
		List<SetEntry> list = new ArrayList<>(of(profile));
		SetEntry existing = get(profile, entry.id());
		if (existing == null) {
			list.add(entry);
		} else {
			list.set(list.indexOf(existing), entry);
		}
		return profile.withEverywhere(profile.everywhere().withQuickEntries(list));
	}

	/** Removes the entry with this ID from Everywhere's Quick changes; unchanged if there is none. */
	static Profile remove(Profile profile, String id) {
		List<SetEntry> list = new ArrayList<>(of(profile));
		list.removeIf(entry -> entry.id().equals(id));
		return profile.withEverywhere(profile.everywhere().withQuickEntries(list));
	}

	/** The first free entry ID in Everywhere's Quick changes. */
	static String newId(Profile profile) {
		return SoundSet.quick(of(profile)).newEntryId();
	}
}
