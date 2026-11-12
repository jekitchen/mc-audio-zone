package soundsculptor.core.trace;

import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.SetEntry;

/**
 * How one place (Everywhere or a zone) treated a sound: its sets with matching entries, and the winner per setting.
 *
 * <p>Entries are told apart by set ID and entry ID, never by instance: a set and its duplicate share entry instances
 * but are listed, and win, separately.
 *
 * @param sets    the place's sets that have a matching entry, in the place's order (Quick changes first)
 * @param winners the deciding entry per setting; a setting no matched entry sets is absent
 */
public record PlaceTrace(List<SetLine> sets, Map<TraceProperty, EntryWinner> winners) {
	public static final PlaceTrace EMPTY = new PlaceTrace(List.of(), Map.of());

	public PlaceTrace {
		sets = List.copyOf(sets);
		winners = Map.copyOf(winners);
	}

	/** The line of this set, or {@code null} if it has no matching entry here. */
	public SetLine set(String setId) {
		for (SetLine line : sets) {
			if (line.setId().equals(setId)) {
				return line;
			}
		}
		return null;
	}

	/** Whether {@code entry}, listed under set {@code setId}, decides {@code property} here. */
	public boolean wins(String setId, SetEntry entry, TraceProperty property) {
		EntryWinner winner = winners.get(property);
		return winner != null && winner.setId().equals(setId) && winner.entry().id().equals(entry.id());
	}

	/**
	 * The winner that took over {@code property} from {@code entry} in set {@code setId}, for the "overridden by" line,
	 * or {@code null} if that entry won it or does not set it.
	 */
	public EntryWinner overriddenBy(String setId, SetEntry entry, TraceProperty property) {
		EntryWinner winner = winners.get(property);
		if (winner == null || !property.setBy(entry) || wins(setId, entry, property)) {
			return null;
		}
		return winner;
	}
}
