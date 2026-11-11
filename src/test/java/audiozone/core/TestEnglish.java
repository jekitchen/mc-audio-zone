package audiozone.core;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A translator for tests: the proposed English text for every key the text builders use. An unknown key fails the
 * test, so a builder cannot use a key this map (and so en_us.json) does not list.
 */
public final class TestEnglish implements Translator {
	public static final Map<String, String> TEXT = new TreeMap<>(Map.ofEntries(
			// Profile files, import, copy and rename (PRD FR-11, 9.3)
			Map.entry("audiozone.pattern.error.empty", "Type a sound ID or a pattern"),
			Map.entry("audiozone.pattern.error.invalid_character", "Only a-z, 0-9 and _ . - / : * are allowed"),
			Map.entry("audiozone.pattern.error.too_many_colons", "Use at most one :"),
			Map.entry("audiozone.pattern.error.empty_part", "Type something before and after the :"),
			Map.entry("audiozone.profile.place.settings", "Settings"),
			Map.entry("audiozone.profile.place.rule", "Rule %s"),
			Map.entry("audiozone.profile.place.zone", "Zone %s"),
			Map.entry("audiozone.profile.place.zone_rule", "%1$s, rule %2$s"),
			Map.entry("audiozone.profile.problem.field", "%1$s, field %2$s: %3$s."),
			Map.entry("audiozone.profile.problem.top_field", "Field %1$s: %2$s."),
			Map.entry("audiozone.profile.problem.item_not_object", "%1$s: %2$s is not an object."),
			Map.entry("audiozone.profile.problem.value.missing", "missing"),
			Map.entry("audiozone.profile.problem.value.not_text", "%s is not text"),
			Map.entry("audiozone.profile.problem.value.not_date", "%s is not a date and time like 2026-11-09T13:37:46Z"),
			Map.entry("audiozone.profile.problem.value.not_boolean", "%s is not true or false"),
			Map.entry("audiozone.profile.problem.value.not_number", "%s is not a number"),
			Map.entry("audiozone.profile.problem.value.above_max", "%1$s is above the maximum %2$s"),
			Map.entry("audiozone.profile.problem.value.below_min", "%1$s is below the minimum %2$s"),
			Map.entry("audiozone.profile.problem.value.not_whole", "%s is not a whole number"),
			Map.entry("audiozone.profile.problem.value.not_choice", "%1$s is not one of: %2$s"),
			Map.entry("audiozone.profile.problem.value.not_color", "%s is not a color like #E69F00"),
			Map.entry("audiozone.profile.problem.value.not_object", "%s is not an object"),
			Map.entry("audiozone.profile.problem.value.not_list", "%s is not a list"),
			Map.entry("audiozone.profile.problem.value.id_used", "%1$s is already used by zone %2$s"),
			Map.entry("audiozone.profile.problem.not_json", "The file is not valid JSON."),
			Map.entry("audiozone.profile.problem.not_json_at", "The file is not valid JSON (line %1$s, column %2$s)."),
			Map.entry("audiozone.profile.problem.not_profile", "The file is not a profile (expected a JSON object)."),
			Map.entry("audiozone.profile.problem.not_allowed", "A value in the file is not allowed."),
			Map.entry("audiozone.profile.problem.too_many_zones", "The file has %1$s zones; the maximum is %2$s."),
			Map.entry("audiozone.profile.problem.too_many_rules", "The file has %1$s rules; the maximum is %2$s."),
			Map.entry("audiozone.profile.problem.too_large", "The file is larger than 1 MB."),
			Map.entry("audiozone.profile.problem.unreadable", "The file could not be read: %s"),
			Map.entry("audiozone.profile.problem.not_utf8", "The file is not UTF-8 text."),
			Map.entry("audiozone.profile.problem.empty", "The file is empty."),
			Map.entry("audiozone.profile.problem.newer", "This file was made with a newer Audio Zone (schema %s). Update the mod to use it."),
			Map.entry("audiozone.profile.problem.newer_import", "This file was made with a newer Audio Zone. Update the mod to import it."),
			Map.entry("audiozone.profile.problem.backup_failed", "The file could not be backed up before upgrading it: %s"),
			Map.entry("audiozone.profile.problem.no_schema", "The file has no schemaVersion."),
			Map.entry("audiozone.profile.problem.unknown_schema", "Unknown schemaVersion %s."),
			Map.entry("audiozone.profile.warning.unknown_field", "Unknown field %s was ignored."),
			Map.entry("audiozone.profile.warning.unknown_field_at", "%1$s: unknown field %2$s was ignored."),
			Map.entry("audiozone.profile.problem.import_too_many_zones", "After the import this profile would have %1$s zones; the maximum is %2$s."),
			Map.entry("audiozone.profile.problem.copy_too_many_zones", "After copying this profile would have %1$s zones; the maximum is %2$s."),
			Map.entry("audiozone.profile.problem.name_empty", "Enter a name."),
			Map.entry("audiozone.profile.problem.name_too_long", "The name can be at most %s characters."),
			// Rule summaries (Rules tab chips, PRD 6.1)
			Map.entry("audiozone.summary.separator", ", "),
			Map.entry("audiozone.summary.rule.disabled", "Off"),
			Map.entry("audiozone.summary.rule.muted", "Muted"),
			Map.entry("audiozone.summary.rule.not_muted", "Not muted"),
			Map.entry("audiozone.summary.rule.volume", "Volume %s%%"),
			Map.entry("audiozone.summary.rule.limit", "At most %1$s per %2$s s"),
			Map.entry("audiozone.summary.rule.limit_per_source", "At most %1$s per %2$s s, per source"),
			Map.entry("audiozone.summary.rule.cue_on", "Cue on"),
			Map.entry("audiozone.summary.rule.cue_off", "Cue off"),
			Map.entry("audiozone.summary.rule.nothing", "Sets nothing yet"),
			Map.entry("audiozone.summary.rule.matches", "Matching sounds: %s"),
			// Zone summaries (Zones tab row, PRD 6.1)
			Map.entry("audiozone.summary.shape.sphere", "Sphere, radius %s"),
			Map.entry("audiozone.summary.shape.box", "Box %1$s × %2$s × %3$s"),
			Map.entry("audiozone.summary.shape.cylinder", "Cylinder, radius %1$s, height %2$s"),
			Map.entry("audiozone.summary.mode.inside", "Inside"),
			Map.entry("audiozone.summary.mode.outside", "Outside"),
			Map.entry("audiozone.summary.zone.bubble", "Personal bubble"),
			Map.entry("audiozone.summary.zone.here", "Here"),
			Map.entry("audiozone.summary.zone.away", "%s blocks away"),
			Map.entry("audiozone.summary.zone.other_world", "Other world"),
			Map.entry("audiozone.summary.zone.other_dimension", "Other dimension"),
			// Copy trace (PRD FR-14.5)
			Map.entry("audiozone.trace.text.header", "Audio Zone trace for %s"),
			Map.entry("audiozone.trace.text.global", "Global rules"),
			Map.entry("audiozone.trace.text.no_rules", "No rule matches this sound"),
			Map.entry("audiozone.trace.text.rule", "%1$s: %2$s"),
			Map.entry("audiozone.trace.text.wins", "Decides %1$s (%2$s)"),
			Map.entry("audiozone.trace.text.overridden", "%1$s overridden by %2$s"),
			Map.entry("audiozone.trace.text.zones", "Zones"),
			Map.entry("audiozone.trace.text.no_zones", "No zone affects this sound"),
			Map.entry("audiozone.trace.text.zone", "%1$s (%2$s): weight %3$s%%, %4$s blocks from its anchor"),
			Map.entry("audiozone.trace.text.zone_winners", "Deciding zones"),
			Map.entry("audiozone.trace.text.zone_winner", "%1$s: %2$s (%3$s)"),
			Map.entry("audiozone.trace.text.result", "Result"),
			Map.entry("audiozone.trace.text.volume", "Volume %s%%"),
			Map.entry("audiozone.trace.text.no_limit", "No repeat limit"),
			Map.entry("audiozone.trace.text.play_allowed", "Last play was allowed"),
			Map.entry("audiozone.trace.text.play_dropped", "Last play was dropped by the repeat limit"),
			Map.entry("audiozone.trace.text.cue_shown", "A cue was shown"),
			Map.entry("audiozone.trace.text.cue_not_shown", "No cue was shown"),
			Map.entry("audiozone.trace.property.loudness", "loudness"),
			Map.entry("audiozone.trace.property.limit", "limit"),
			Map.entry("audiozone.trace.property.cue", "cue"),
			// Existing keys (their en_us.json text comes with #44)
			Map.entry("audiozone.trace.notice.bypass", "Bypass is on: nothing is changed."),
			Map.entry("audiozone.trace.notice.vanilla_covered", "This sound has its own vanilla slider, so Audio Zone does not handle it."),
			Map.entry("audiozone.trace.notice.not_heard_yet", "Not heard yet; zones depend on where it plays."),
			Map.entry("audiozone.trace.notice.non_positional", "Non-positional: zones never apply."),
			Map.entry("audiozone.trace.notice.boosted", "Above 100%%: boost applied."),
			Map.entry("audiozone.trace.notice.changed_since_play", "Rules or zones changed since this sound last played, so what you heard may differ."),
			Map.entry("audiozone.trace.rule_reason.only_match", "Only matching rule"),
			Map.entry("audiozone.trace.rule_reason.exact_id", "Exact sound ID"),
			Map.entry("audiozone.trace.rule_reason.more_literal", "More specific pattern"),
			Map.entry("audiozone.trace.rule_reason.fewer_stars", "Fewer *"),
			Map.entry("audiozone.trace.rule_reason.list_order", "Higher in the list"),
			Map.entry("audiozone.trace.zone_reason.only_zone", "Only zone"),
			Map.entry("audiozone.trace.zone_reason.closest", "Closest anchor"),
			Map.entry("audiozone.trace.zone_reason.tie_smaller_zone", "Tie on distance; smaller zone wins"),
			Map.entry("audiozone.trace.zone_reason.tie_list_order", "Tie on distance and size; higher in the list")));

	@Override
	public String translate(String key, Object... args) {
		String format = TEXT.get(key);
		if (format == null) {
			throw new AssertionError("no English text for key " + key);
		}
		return String.format(Locale.ROOT, format, args);
	}
}
