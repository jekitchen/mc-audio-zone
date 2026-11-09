package audiozone.core.profile;

import audiozone.core.rules.Rule;
import audiozone.core.zones.Zone;
import java.time.Instant;
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
 * @param lastJoined when the player last joined this server or world (FR-11.3), or null if never
 * @param boostConfirmed whether the player has confirmed the boost dialog for this profile (FR-5.4: asked once per
 *                   profile, the first time a volume goes above 100%)
 */
public record Profile(String name, ProfileSettings settings, List<Rule> globalRules, List<Zone> zones,
		Map<String, Integer> zoneColors, Instant lastJoined, boolean boostConfirmed) {
	/** Bumped on every change to the file format, with a migration step (PRD 7.6). */
	public static final int SCHEMA_VERSION = 2;

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

	/** A profile never joined, with boost not yet confirmed. */
	public Profile(String name, ProfileSettings settings, List<Rule> globalRules, List<Zone> zones,
			Map<String, Integer> zoneColors) {
		this(name, settings, globalRules, zones, zoneColors, null, false);
	}

	/** A profile with no zones. */
	public Profile(String name, ProfileSettings settings, List<Rule> globalRules) {
		this(name, settings, globalRules, List.of(), Map.of());
	}

	public static Profile empty(String name) {
		return new Profile(name, ProfileSettings.DEFAULTS, List.of());
	}

	public Profile withSettings(ProfileSettings value) {
		return new Profile(name, value, globalRules, zones, zoneColors, lastJoined, boostConfirmed);
	}

	public Profile withName(String value) {
		return new Profile(value, settings, globalRules, zones, zoneColors, lastJoined, boostConfirmed);
	}

	public Profile withGlobalRules(List<Rule> value) {
		return new Profile(name, settings, value, zones, zoneColors, lastJoined, boostConfirmed);
	}

	/** Records a join (FR-11.3); called with the time the player joined this server or world. */
	public Profile withLastJoined(Instant value) {
		return new Profile(name, settings, globalRules, zones, zoneColors, value, boostConfirmed);
	}

	/** Set after the player confirms the boost dialog, so it is not shown again for this profile (FR-5.4). */
	public Profile withBoostConfirmed(boolean value) {
		return new Profile(name, settings, globalRules, zones, zoneColors, lastJoined, value);
	}

	/**
	 * Whether setting a rule to this volume must first show the boost dialog (FR-5.4): only above 100%, and only until
	 * the player has confirmed it once for this profile.
	 *
	 * @param volume the new volume in percent, or null if the rule does not set one
	 */
	public boolean needsBoostConfirm(Integer volume) {
		return !boostConfirmed && volume != null && volume > 100;
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
		return new Profile(name, settings, globalRules, value, colors, lastJoined, boostConfirmed);
	}

	/** Sets the outline color of a zone in this profile. */
	public Profile withZoneColor(String zoneId, int rgb) {
		Map<String, Integer> colors = new LinkedHashMap<>(zoneColors);
		colors.put(zoneId, rgb);
		return new Profile(name, settings, globalRules, zones, colors, lastJoined, boostConfirmed);
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

	/** The global rule with this ID, or null. */
	public Rule rule(String id) {
		for (Rule rule : globalRules) {
			if (rule.id().equals(id)) {
				return rule;
			}
		}
		return null;
	}

	/** Saves a rule from the editor: replaces the rule with the same ID in place, or adds it at the end. */
	public Profile putRule(Rule rule) {
		Rule existing = rule(rule.id());
		return existing == null ? withRules(append(rule)) : replace(existing, rule);
	}

	/** Deletes the rule with this ID; returns this profile if there is none. */
	public Profile removeRule(String id) {
		Rule existing = rule(id);
		if (existing == null) {
			return this;
		}
		List<Rule> rules = new ArrayList<>(globalRules);
		rules.remove(existing);
		return withRules(rules);
	}

	/**
	 * Moves a rule up ({@code delta < 0}) or down the list. List order is the last tie-break between rules (FR-4.5
	 * step 4). Returns this profile if the rule is missing or already at that end.
	 */
	public Profile moveRule(String id, int delta) {
		Rule existing = rule(id);
		if (existing == null) {
			return this;
		}
		int from = globalRules.indexOf(existing);
		int to = Math.max(0, Math.min(globalRules.size() - 1, from + delta));
		if (to == from) {
			return this;
		}
		List<Rule> rules = new ArrayList<>(globalRules);
		rules.remove(from);
		rules.add(to, existing);
		return withRules(rules);
	}

	/** An ID for a new rule from the editor: {@code r1}, {@code r2}, … the first one not in use. */
	public String newRuleId() {
		for (int n = 1; ; n++) {
			String id = "r" + n;
			if (rule(id) == null) {
				return id;
			}
		}
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
