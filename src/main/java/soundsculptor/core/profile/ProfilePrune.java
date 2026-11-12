package soundsculptor.core.profile;

import soundsculptor.core.Message;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Removes attachments to Sound Sets the library no longer has, when a profile is loaded (PRD FR-4.3, FR-11.5a): a set
 * deleted while another world's profile was not loaded is still listed there. Each removal is reported once, so the
 * player knows why a place changed.
 */
public final class ProfilePrune {
	/** "'%s' was deleted, so it was removed from %s": the set's last name and the place. */
	public static final String NOTE_REMOVED = "soundsculptor.sets.note.removed";
	/** "A Sound Set that no longer exists was removed from %s": the place. */
	public static final String NOTE_REMOVED_UNNAMED = "soundsculptor.sets.note.removed_unnamed";

	private ProfilePrune() {
	}

	/**
	 * The profile without the missing attachments, and one note per removal.
	 *
	 * @param profile the same object as the input when nothing is missing
	 */
	public record Pruned(Profile profile, List<Message> notes) {
		public Pruned {
			Objects.requireNonNull(profile, "profile");
			notes = List.copyOf(notes);
		}
	}

	/**
	 * Drops attachments to sets not in {@code lib}, from Everywhere and then each zone in list order. Quick changes is
	 * never dropped.
	 *
	 * @param deletedNames the last name of each deleted set by ID ({@code SetStore.deletedNames()}); a set not listed
	 *                     there gets the unnamed note
	 */
	public static Pruned dropMissingSets(Profile p, SetLibrary lib, Map<String, String> deletedNames) {
		List<Message> notes = new ArrayList<>();
		Profile out = p;
		out = prune(out, PlaceRef.EVERYWHERE, p.everywhere(), Message.of(ProfileText.PLACE_EVERYWHERE), lib, deletedNames,
				notes);
		for (Zone zone : p.zones()) {
			String name = zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
			out = prune(out, PlaceRef.zone(zone.id()), zone.sets(), name, lib, deletedNames, notes);
		}
		return new Pruned(out, notes);
	}

	/**
	 * {@link #dropMissingSets} when the library loaded completely, otherwise nothing (PRD 9.2). When a set file or the
	 * folder could not be read ({@code SetStore.LoadAll.complete()} is false), a set the library lacks may still be on
	 * disk and load at the next start, so its attachments are kept; every reader skips a set the library lacks.
	 */
	public static Pruned dropMissingSetsIfComplete(Profile p, SetLibrary lib, boolean libraryComplete,
			Map<String, String> deletedNames) {
		return libraryComplete ? dropMissingSets(p, lib, deletedNames) : new Pruned(p, List.of());
	}

	private static Profile prune(Profile profile, PlaceRef where, PlaceSets sets, Object placeName, SetLibrary lib,
			Map<String, String> deletedNames, List<Message> notes) {
		boolean missing = false;
		for (Attachment attachment : sets.attached()) {
			String id = attachment.setId();
			if (id.equals(SoundSet.QUICK_ID) || lib.contains(id)) {
				continue;
			}
			missing = true;
			String name = deletedNames.get(id);
			notes.add(name == null ? Message.of(NOTE_REMOVED_UNNAMED, placeName) : Message.of(NOTE_REMOVED, name, placeName));
		}
		return missing ? profile.withPlace(where, sets.dropMissing(lib.ids())) : profile;
	}
}
