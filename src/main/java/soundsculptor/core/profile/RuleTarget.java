package soundsculptor.core.profile;

import soundsculptor.core.Message;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Zone;
import java.util.ArrayList;
import java.util.List;

// M8c shim: removed in Task 13
/**
 * Which list the old rule editors work on: Everywhere's Quick changes, or one zone's (PRD FR-4.10). Until the Sound
 * Sets UI replaces them, the Rules tab edits Everywhere's Quick changes and a zone's Rules screen edits that zone's.
 * Every operation goes through {@link PlaceSets#withQuickEntries}: the set is created on the first entry and removed
 * when its last entry goes. An entry's ID only has to be unique within its set.
 *
 * @param zoneId the zone, or {@code null} for Everywhere
 */
public record RuleTarget(String zoneId) {
	public static final RuleTarget GLOBAL = new RuleTarget(null);

	public static RuleTarget zone(String zoneId) {
		return new RuleTarget(zoneId);
	}

	public boolean isGlobal() {
		return zoneId == null;
	}

	/** The place this list belongs to. */
	public PlaceRef place() {
		return isGlobal() ? PlaceRef.EVERYWHERE : PlaceRef.zone(zoneId);
	}

	/** The entries of this place's Quick changes, in order; empty if it has none or the zone no longer exists. */
	public List<SetEntry> rules(Profile profile) {
		PlaceSets sets = profile.place(place());
		return sets == null ? List.of() : sets.quickEntries();
	}

	/** The entry with this ID in this list, or null. */
	public SetEntry rule(Profile profile, String id) {
		for (SetEntry rule : rules(profile)) {
			if (rule.id().equals(id)) {
				return rule;
			}
		}
		return null;
	}

	/**
	 * {@code null} if {@link #put} can save this entry, else the problem to show: a new entry does not fit once Quick
	 * changes holds {@value SoundSet#MAX_ENTRIES}.
	 */
	public Message canPut(Profile profile, SetEntry rule) {
		if (rule(profile, rule.id()) != null) {
			return null;
		}
		return SoundSet.quick(rules(profile)).canAdd(1);
	}

	/**
	 * Replaces the entry with the same ID in place, or adds it at the end. Returns the profile unchanged if
	 * {@link #canPut} reports a problem or the zone no longer exists.
	 */
	public Profile put(Profile profile, SetEntry rule) {
		if (canPut(profile, rule) != null) {
			return profile;
		}
		List<SetEntry> list = new ArrayList<>(rules(profile));
		SetEntry existing = rule(profile, rule.id());
		if (existing == null) {
			list.add(rule);
		} else {
			list.set(list.indexOf(existing), rule);
		}
		return with(profile, list);
	}

	/** Deletes the entry with this ID; returns the profile unchanged if there is none. */
	public Profile remove(Profile profile, String id) {
		SetEntry existing = rule(profile, id);
		if (existing == null) {
			return profile;
		}
		List<SetEntry> list = new ArrayList<>(rules(profile));
		list.remove(existing);
		return with(profile, list);
	}

	/** Moves an entry up ({@code delta < 0}) or down; unchanged at either end. List order is FR-4.5 step 5. */
	public Profile move(Profile profile, String id, int delta) {
		List<SetEntry> rules = rules(profile);
		SetEntry existing = rule(profile, id);
		if (existing == null) {
			return profile;
		}
		int from = rules.indexOf(existing);
		int to = Math.max(0, Math.min(rules.size() - 1, from + delta));
		if (to == from) {
			return profile;
		}
		List<SetEntry> list = new ArrayList<>(rules);
		list.remove(from);
		list.add(to, existing);
		return with(profile, list);
	}

	/** {@code r1}, {@code r2}, … the first ID not in use in this list. */
	public String newRuleId(Profile profile) {
		for (int n = 1; ; n++) {
			String id = "r" + n;
			if (rule(profile, id) == null) {
				return id;
			}
		}
	}

	/** An entry and the list it is in, for "Add existing rule". */
	public record Sourced(RuleTarget source, SetEntry rule) {
	}

	/**
	 * Every entry in the other places' Quick changes, to copy into this one: Everywhere first (unless this is it), then
	 * each other zone in zone order.
	 */
	public List<Sourced> otherRules(Profile profile) {
		List<Sourced> out = new ArrayList<>();
		if (!isGlobal()) {
			for (SetEntry rule : profile.everywhere().quickEntries()) {
				out.add(new Sourced(GLOBAL, rule));
			}
		}
		for (Zone zone : profile.zones()) {
			if (!zone.id().equals(zoneId)) {
				for (SetEntry rule : zone.sets().quickEntries()) {
					out.add(new Sourced(zone(zone.id()), rule));
				}
			}
		}
		return out;
	}

	/** Whether this list already has an entry with this pattern. */
	public boolean hasPattern(Profile profile, SoundPattern pattern) {
		for (SetEntry rule : rules(profile)) {
			if (rule.pattern().equals(pattern)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * "Add existing rule": a copy of {@code rule} at the end of this list, under a new ID. Entries are values, so the
	 * copy and the original are independent: changing one never changes the other. Unchanged if the list is full.
	 */
	public Profile copyIn(Profile profile, SetEntry rule) {
		return put(profile, rule.withId(newRuleId(profile)));
	}

	private Profile with(Profile profile, List<SetEntry> rules) {
		PlaceSets sets = profile.place(place());
		return sets == null ? profile : profile.withPlace(place(), sets.withQuickEntries(rules));
	}
}
