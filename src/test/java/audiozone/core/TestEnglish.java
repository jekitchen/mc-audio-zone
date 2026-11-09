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
			Map.entry("audiozone.trace.notice.bypass", "Bypass is on: nothing is changed"),
			Map.entry("audiozone.trace.notice.vanilla_covered", "This sound has its own vanilla slider"),
			Map.entry("audiozone.trace.notice.not_heard_yet", "Not heard yet; zones depend on where it plays"),
			Map.entry("audiozone.trace.notice.non_positional", "Non-positional: zones never apply"),
			Map.entry("audiozone.trace.notice.boosted", "Above 100%%: boost applied"),
			Map.entry("audiozone.trace.notice.changed_since_play", "Rules changed since this sound last played"),
			Map.entry("audiozone.trace.rule_reason.only_match", "only match"),
			Map.entry("audiozone.trace.rule_reason.exact_id", "exact ID"),
			Map.entry("audiozone.trace.rule_reason.more_literal", "more specific pattern"),
			Map.entry("audiozone.trace.rule_reason.fewer_stars", "fewer *"),
			Map.entry("audiozone.trace.rule_reason.list_order", "higher in the list"),
			Map.entry("audiozone.trace.zone_reason.only_zone", "only zone"),
			Map.entry("audiozone.trace.zone_reason.closest", "closest anchor"),
			Map.entry("audiozone.trace.zone_reason.tie_smaller_zone", "tie on distance; smaller zone wins"),
			Map.entry("audiozone.trace.zone_reason.tie_list_order", "tie on distance and size; higher in the list")));

	@Override
	public String translate(String key, Object... args) {
		String format = TEXT.get(key);
		if (format == null) {
			throw new AssertionError("no English text for key " + key);
		}
		return String.format(Locale.ROOT, format, args);
	}
}
