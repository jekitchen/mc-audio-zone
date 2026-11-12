package soundsculptor.core.trace;

import java.util.Objects;

import soundsculptor.core.sets.SetEntry;

/**
 * The entry that decided one setting at a place, the set it won in, and why.
 *
 * @param setId the set (layer) the entry won in, taken from its position in the place's resolver, never looked up
 *              from the entry: a set and its duplicate share entry instances
 */
public record EntryWinner(SetEntry entry, String setId, EntryWinReason reason) {
	public EntryWinner {
		Objects.requireNonNull(entry, "entry");
		Objects.requireNonNull(setId, "setId");
		Objects.requireNonNull(reason, "reason");
	}
}
