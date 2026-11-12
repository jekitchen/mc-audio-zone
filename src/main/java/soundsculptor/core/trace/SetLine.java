package soundsculptor.core.trace;

import java.util.List;
import java.util.Objects;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;

/**
 * One set attached to a place that has entries matching the sound (PRD FR-14.2 item 1).
 *
 * @param setId       the library set's ID, or {@link SoundSet#QUICK_ID}
 * @param setName     the library set's name; {@code ""} for Quick changes, which has none
 * @param quick       whether this is the place's Quick changes
 * @param matched     the set's enabled entries that match the sound, most specific first
 * @param placesUsing how many places use the set, in this profile and others ("used in 3 places", FR-14.4); 1 for
 *                    Quick changes, 0 when not known
 */
public record SetLine(String setId, String setName, boolean quick, List<SetEntry> matched, int placesUsing) {
	public SetLine {
		Objects.requireNonNull(setId, "setId");
		Objects.requireNonNull(setName, "setName");
		matched = List.copyOf(matched);
	}
}
