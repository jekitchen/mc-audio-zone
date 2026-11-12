package soundsculptor.core.trace;

import java.util.Objects;

import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SoundSet;

/**
 * Where an entry shown in the trace lives (PRD FR-14.4): a set, and the place the line was shown for.
 *
 * <p>For a library set the place only says which line was edited: the entry belongs to the library set, so editing it
 * changes every place that uses the set (FR-4.9). For {@link SoundSet#QUICK_ID} it is the place whose Quick changes
 * holds the entry.
 *
 * @param place   Everywhere or the zone the line was shown for
 * @param setId   the library set's ID, or {@link SoundSet#QUICK_ID} for the place's Quick changes
 * @param entryId the entry's ID within the set
 */
public record EntryLocation(PlaceRef place, String setId, String entryId) {
	public EntryLocation {
		Objects.requireNonNull(place, "place");
		Objects.requireNonNull(setId, "setId");
		Objects.requireNonNull(entryId, "entryId");
	}

	/** Whether the entry is in the place's Quick changes, not in a library set. */
	public boolean isQuick() {
		return SoundSet.QUICK_ID.equals(setId);
	}
}
