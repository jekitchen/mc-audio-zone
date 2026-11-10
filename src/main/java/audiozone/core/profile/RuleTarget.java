package audiozone.core.profile;

import audiozone.core.rules.Rule;
import audiozone.core.rules.SoundPattern;
import audiozone.core.zones.Zone;
import java.util.ArrayList;
import java.util.List;

/**
 * Which rule list an editor works on: the global list, or one zone's own list (PRD FR-4, FR-8.6). Both have the same
 * format and the same operations; a zone rule's ID only has to be unique within its zone.
 *
 * @param zoneId the zone, or {@code null} for the global list
 */
public record RuleTarget(String zoneId) {
	public static final RuleTarget GLOBAL = new RuleTarget(null);

	public static RuleTarget zone(String zoneId) {
		return new RuleTarget(zoneId);
	}

	public boolean isGlobal() {
		return zoneId == null;
	}

	/** The rules of this list, in order; empty if the zone no longer exists. */
	public List<Rule> rules(Profile profile) {
		if (isGlobal()) {
			return profile.globalRules();
		}
		Zone zone = profile.zone(zoneId);
		return zone == null ? List.of() : zone.rules();
	}

	/** The rule with this ID in this list, or null. */
	public Rule rule(Profile profile, String id) {
		for (Rule rule : rules(profile)) {
			if (rule.id().equals(id)) {
				return rule;
			}
		}
		return null;
	}

	/** Replaces the rule with the same ID in place, or adds it at the end. */
	public Profile put(Profile profile, Rule rule) {
		List<Rule> list = new ArrayList<>(rules(profile));
		Rule existing = rule(profile, rule.id());
		if (existing == null) {
			list.add(rule);
		} else {
			list.set(list.indexOf(existing), rule);
		}
		return with(profile, list);
	}

	/** Deletes the rule with this ID; returns the profile unchanged if there is none. */
	public Profile remove(Profile profile, String id) {
		Rule existing = rule(profile, id);
		if (existing == null) {
			return profile;
		}
		List<Rule> list = new ArrayList<>(rules(profile));
		list.remove(existing);
		return with(profile, list);
	}

	/** Moves a rule up ({@code delta < 0}) or down; unchanged at either end. List order is FR-4.5 step 4. */
	public Profile move(Profile profile, String id, int delta) {
		List<Rule> rules = rules(profile);
		Rule existing = rule(profile, id);
		if (existing == null) {
			return profile;
		}
		int from = rules.indexOf(existing);
		int to = Math.max(0, Math.min(rules.size() - 1, from + delta));
		if (to == from) {
			return profile;
		}
		List<Rule> list = new ArrayList<>(rules);
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

	/** A rule and the list it is in, for "Add existing rule". */
	public record Sourced(RuleTarget source, Rule rule) {
	}

	/**
	 * Every rule in the profile's other lists, to copy into this one: the global list first (unless this is it), then
	 * each other zone in zone order.
	 */
	public List<Sourced> otherRules(Profile profile) {
		List<Sourced> out = new ArrayList<>();
		if (!isGlobal()) {
			for (Rule rule : profile.globalRules()) {
				out.add(new Sourced(GLOBAL, rule));
			}
		}
		for (Zone zone : profile.zones()) {
			if (!zone.id().equals(zoneId)) {
				for (Rule rule : zone.rules()) {
					out.add(new Sourced(zone(zone.id()), rule));
				}
			}
		}
		return out;
	}

	/** Whether this list already has a rule with this pattern. */
	public boolean hasPattern(Profile profile, SoundPattern pattern) {
		for (Rule rule : rules(profile)) {
			if (rule.pattern().equals(pattern)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * "Add existing rule": a copy of {@code rule} at the end of this list, under a new ID. Rules are values, so the
	 * copy and the original are independent: changing one never changes the other.
	 */
	public Profile copyIn(Profile profile, Rule rule) {
		return put(profile, rule.withId(newRuleId(profile)));
	}

	private Profile with(Profile profile, List<Rule> rules) {
		if (isGlobal()) {
			return profile.withGlobalRules(rules);
		}
		Zone zone = profile.zone(zoneId);
		return zone == null ? profile : profile.putZone(zone.withRules(rules));
	}
}
