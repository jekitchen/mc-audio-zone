package audiozone.core.profile;

import audiozone.core.rules.Rule;
import audiozone.core.zones.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A profile (PRD FR-11, section 7.5): its global rules, zones and profile settings. Immutable; every change returns a
 * new profile, which the sound path then reads as a snapshot (PRD 7.4).
 *
 * @param name     display name
 * @param settings profile settings
 * @param globalRules rules that apply everywhere, in the order the player sees them
 * @param zones    fixed zones and personal bubbles, in the order the player sees them
 * @param zoneColors outline color of each zone that has one, as {@code 0xRRGGBB}, keyed by zone ID
 */
public record Profile(String name, ProfileSettings settings, List<Rule> globalRules, List<Zone> zones,
		Map<String, Integer> zoneColors) {
	/** Bumped on every change to the file format, with a migration step (PRD 7.6). */
	public static final int SCHEMA_VERSION = 1;

	public Profile {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(settings, "settings");
		globalRules = List.copyOf(globalRules);
		zones = List.copyOf(zones);
		Set<String> ids = new HashSet<>();
		for (Zone zone : zones) {
			if (!ids.add(zone.id())) {
				throw new IllegalArgumentException("zone id " + zone.id() + " is used twice");
			}
		}
		for (String id : zoneColors.keySet()) {
			if (!ids.contains(id)) {
				throw new IllegalArgumentException("color for unknown zone " + id);
			}
		}
		zoneColors = Map.copyOf(zoneColors);
	}

	/** A profile with no zones. */
	public Profile(String name, ProfileSettings settings, List<Rule> globalRules) {
		this(name, settings, globalRules, List.of(), Map.of());
	}

	public static Profile empty(String name) {
		return new Profile(name, ProfileSettings.DEFAULTS, List.of());
	}

	public Profile withSettings(ProfileSettings value) {
		return new Profile(name, value, globalRules, zones, zoneColors);
	}

	public Profile withName(String value) {
		return new Profile(value, settings, globalRules, zones, zoneColors);
	}

	public Profile withGlobalRules(List<Rule> value) {
		return new Profile(name, settings, value, zones, zoneColors);
	}

	/** Replaces the zones; colors of zones no longer present are dropped. */
	public Profile withZones(List<Zone> value) {
		Map<String, Integer> colors = new LinkedHashMap<>();
		for (Zone zone : value) {
			Integer rgb = zoneColors.get(zone.id());
			if (rgb != null) {
				colors.put(zone.id(), rgb);
			}
		}
		return new Profile(name, settings, globalRules, value, colors);
	}

	/** Sets the outline color of a zone in this profile. */
	public Profile withZoneColor(String zoneId, int rgb) {
		Map<String, Integer> colors = new LinkedHashMap<>(zoneColors);
		colors.put(zoneId, rgb);
		return new Profile(name, settings, globalRules, zones, colors);
	}

	/** The zone's color as {@code 0xRRGGBB}, or null if it has none. */
	public Integer colorOf(String zoneId) {
		return zoneColors.get(zoneId);
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
		return withGlobalRules(rules);
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
