package soundsculptor.core.trace;

import soundsculptor.core.sets.SetEntry;

/** The rule that decided one setting within a list, and why. */
public record EntryWinner(SetEntry rule, EntryWinReason reason) {
}
