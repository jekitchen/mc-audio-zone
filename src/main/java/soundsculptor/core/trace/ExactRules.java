package soundsculptor.core.trace;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Zone;

/**
 * Creating and editing the exact-ID rule for one sound, in the global list or in one zone: the rule both "Add rule for
 * this sound" in the trace (PRD FR-14.4) and the Recently Played quick actions (FR-1.5) edit. Not on the hot path.
 */
public final class ExactRules {
	/** What an edit did to the exact-ID rule. */
	public enum Outcome {
		/** A new exact-ID rule was added. */
		ADDED,
		/** The existing exact-ID rule was changed. */
		CHANGED,
		/** The exact-ID rule was removed because it no longer set anything. */
		REMOVED,
		/** Nothing changed. */
		UNCHANGED
	}

	/**
	 * The result of {@link #edit}.
	 *
	 * @param error   why the edit was refused, or {@code null}
	 * @param after   the new rules and zones; the same object as the input when refused or unchanged
	 * @param outcome what happened to the rule; {@link Outcome#UNCHANGED} when refused
	 */
	public record Change(TraceEditError error, EditableRules after, Outcome outcome) {
	}

	private ExactRules() {
	}

	/** The exact-ID rule for {@code soundId} in the global list ({@code zoneId} null) or in that zone, or null. */
	public static SetEntry find(EditableRules rules, String zoneId, String soundId) {
		List<SetEntry> list = list(rules, zoneId);
		if (list == null) {
			return null;
		}
		int i = indexOfExact(list, soundId);
		return i < 0 ? null : list.get(i);
	}

	/**
	 * Applies {@code edit} to the exact-ID rule for {@code soundId} in the global list ({@code zoneId} null) or in that
	 * zone, adding the rule if there is none.
	 *
	 * @param tidy {@code false} (trace panel): an existing rule is always turned on, and a new rule is added even if
	 *             the edit sets nothing. {@code true} (quick actions): an edit that only clears a value never adds a
	 *             rule nor turns one on, and a rule left setting nothing is removed, so muting and then unmuting leaves
	 *             the profile as it was.
	 */
	public static Change edit(EditableRules rules, String zoneId, String soundId, EntryEdit edit, boolean tidy) {
		TraceEditError invalid = edit.validate();
		if (invalid != null) {
			return refused(rules, invalid);
		}
		int z = -1;
		List<SetEntry> list = rules.globalRules();
		if (zoneId != null) {
			z = indexOfZone(rules.zones(), zoneId);
			if (z < 0) {
				return refused(rules, TraceEditError.ZONE_NOT_FOUND);
			}
			list = rules.zones().get(z).rules();
		}
		boolean setsSomething = setsAnything(edit.apply(SetEntry.of("probe", soundId)));
		int existing = indexOfExact(list, soundId);
		List<SetEntry> newList;
		Outcome outcome;
		if (existing >= 0) {
			SetEntry old = list.get(existing);
			SetEntry changed = tidy && !setsSomething ? edit.apply(old) : edit.apply(old.withEnabled(true));
			if (tidy && !setsAnything(changed)) {
				newList = new ArrayList<>(list);
				newList.remove(existing);
				outcome = Outcome.REMOVED;
			} else if (tidy && changed.equals(old)) {
				return new Change(null, rules, Outcome.UNCHANGED);
			} else {
				newList = TraceEditor.replace(list, existing, changed);
				outcome = Outcome.CHANGED;
			}
		} else {
			if (tidy && !setsSomething) {
				return new Change(null, rules, Outcome.UNCHANGED);
			}
			if (ruleCount(rules) >= TraceEditor.MAX_RULES) {
				return refused(rules, TraceEditError.TOO_MANY_RULES);
			}
			newList = new ArrayList<>(list);
			newList.add(edit.apply(SetEntry.of(freeId(rules, TraceEditor.exactRuleId(zoneId, soundId)), soundId)));
			outcome = Outcome.ADDED;
		}
		EditableRules after = z < 0
				? new EditableRules(newList, rules.zones())
				: new EditableRules(rules.globalRules(),
						TraceEditor.replace(rules.zones(), z, TraceEditor.withRules(rules.zones().get(z), newList)));
		return new Change(null, after, outcome);
	}

	private static Change refused(EditableRules rules, TraceEditError error) {
		return new Change(error, rules, Outcome.UNCHANGED);
	}

	private static boolean setsAnything(SetEntry rule) {
		return rule.setsLoudness() || rule.setsLimit() || rule.setsCue();
	}

	private static List<SetEntry> list(EditableRules rules, String zoneId) {
		if (zoneId == null) {
			return rules.globalRules();
		}
		int z = indexOfZone(rules.zones(), zoneId);
		return z < 0 ? null : rules.zones().get(z).rules();
	}

	static int indexOfZone(List<Zone> zones, String id) {
		for (int i = 0; i < zones.size(); i++) {
			if (zones.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	static int indexOfRule(List<SetEntry> rules, String id) {
		for (int i = 0; i < rules.size(); i++) {
			if (rules.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfExact(List<SetEntry> rules, String soundId) {
		for (int i = 0; i < rules.size(); i++) {
			SetEntry rule = rules.get(i);
			if (rule.pattern().isExact() && rule.pattern().text().equals(soundId)) {
				return i;
			}
		}
		return -1;
	}

	private static int ruleCount(EditableRules rules) {
		int count = rules.globalRules().size();
		for (Zone zone : rules.zones()) {
			count += zone.rules().size();
		}
		return count;
	}

	/** {@code id}, or {@code id#2}, {@code id#3}... if another rule already uses it. */
	private static String freeId(EditableRules rules, String id) {
		String candidate = id;
		for (int n = 2; isTaken(rules, candidate); n++) {
			candidate = id + "#" + n;
		}
		return candidate;
	}

	private static boolean isTaken(EditableRules rules, String id) {
		if (indexOfRule(rules.globalRules(), id) >= 0) {
			return true;
		}
		for (Zone zone : rules.zones()) {
			if (indexOfRule(zone.rules(), id) >= 0) {
				return true;
			}
		}
		return false;
	}
}
