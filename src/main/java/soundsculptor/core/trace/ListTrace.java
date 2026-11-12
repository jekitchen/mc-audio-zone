package soundsculptor.core.trace;

import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.SetEntry;

/**
 * How one rule list (global, or one zone's) treated a sound.
 *
 * @param matched enabled rules whose pattern matches the sound, most specific first
 * @param winners the deciding rule per setting; a setting no matched rule sets is absent
 */
public record ListTrace(List<SetEntry> matched, Map<TraceProperty, EntryWinner> winners) {
	public static final ListTrace EMPTY = new ListTrace(List.of(), Map.of());

	public ListTrace {
		matched = List.copyOf(matched);
		winners = Map.copyOf(winners);
	}

	/**
	 * The rule that took over {@code property} from {@code rule}, for the grayed-out "overridden by" line, or
	 * {@code null} if {@code rule} won it or does not set it.
	 */
	public SetEntry overriddenBy(SetEntry rule, TraceProperty property) {
		EntryWinner winner = winners.get(property);
		if (winner == null || winner.rule() == rule || !property.setBy(rule)) {
			return null;
		}
		return winner.rule();
	}
}
