package audiozone.core.profile;

import audiozone.core.rules.Rule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A profile (PRD FR-11, section 7.5): its global rules and profile settings. Immutable; every change returns a new
 * profile, which the sound path then reads as a snapshot (PRD 7.4). Zones arrive in M7.
 *
 * @param name     display name
 * @param settings profile settings
 * @param globalRules rules that apply everywhere, in the order the player sees them
 */
public record Profile(String name, ProfileSettings settings, List<Rule> globalRules) {
	/** Bumped on every change to the file format, with a migration step (PRD 7.6). */
	public static final int SCHEMA_VERSION = 1;

	public Profile {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(settings, "settings");
		globalRules = List.copyOf(globalRules);
	}

	public static Profile empty(String name) {
		return new Profile(name, ProfileSettings.DEFAULTS, List.of());
	}

	public Profile withSettings(ProfileSettings value) {
		return new Profile(name, value, globalRules);
	}

	/**
	 * The exact-ID rule for this sound ID (the rule quick actions create and edit, FR-1.5), or null. An exact-ID rule
	 * is one whose pattern is the ID itself, with no wildcard.
	 */
	public Rule exactRule(String soundId) {
		for (Rule rule : globalRules) {
			if (rule.pattern().text().equals(soundId)) {
				return rule;
			}
		}
		return null;
	}

	/** Whether this sound ID has an exact-ID rule that mutes it. */
	public boolean isExactMuted(String soundId) {
		Rule rule = exactRule(soundId);
		return rule != null && rule.enabled() && Boolean.TRUE.equals(rule.mute());
	}

	/** Quick action Mute: sets mute on the sound's exact-ID rule, creating the rule if needed (FR-1.5). */
	public Profile muteExact(String soundId) {
		Rule existing = exactRule(soundId);
		if (existing == null) {
			return withRules(append(Rule.of(exactRuleId(soundId), soundId).withMute(true)));
		}
		return replace(existing, existing.withEnabled(true).withMute(true));
	}

	/**
	 * Quick action Unmute: clears mute on the sound's exact-ID rule. A rule left setting nothing is removed, so muting
	 * and unmuting leaves the profile as it was.
	 */
	public Profile unmuteExact(String soundId) {
		Rule existing = exactRule(soundId);
		if (existing == null) {
			return this;
		}
		Rule cleared = existing.withMute(null);
		if (!cleared.setsLoudness() && !cleared.setsLimit() && !cleared.setsCue()) {
			List<Rule> rules = new ArrayList<>(globalRules);
			rules.remove(existing);
			return withRules(rules);
		}
		return replace(existing, cleared);
	}

	/** Stable ID for a quick-action rule, so muting the same sound twice cannot create two rules. */
	static String exactRuleId(String soundId) {
		return "exact:" + soundId;
	}

	private Profile withRules(List<Rule> rules) {
		return new Profile(name, settings, rules);
	}

	private List<Rule> append(Rule rule) {
		List<Rule> rules = new ArrayList<>(globalRules);
		rules.add(rule);
		return rules;
	}

	private Profile replace(Rule old, Rule replacement) {
		List<Rule> rules = new ArrayList<>(globalRules);
		rules.set(rules.indexOf(old), replacement);
		return withRules(rules);
	}
}
