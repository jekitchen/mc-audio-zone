package audiozone.core.trace;

import audiozone.core.Translator;
import audiozone.core.lists.Numbers;
import audiozone.core.lists.RuleSummary;
import audiozone.core.lists.ZoneSummary;
import audiozone.core.rules.Rule;
import audiozone.core.zones.Zone;

/**
 * The "Copy trace" text (PRD FR-14.5): the trace panel as plain text, for asking for help or filing a bug. Sections
 * follow FR-14.2: notices, global rules (winner and reason per setting, "overridden by" for losers), zones considered
 * (mode, weight, distance to the anchor, their own rules), the deciding zone per setting, then the final result.
 * Every phrase comes from one translation key; only line breaks and indentation are added here.
 */
public final class TraceText {
	public static final String HEADER = "audiozone.trace.text.header";
	public static final String GLOBAL = "audiozone.trace.text.global";
	public static final String NO_RULES = "audiozone.trace.text.no_rules";
	public static final String RULE = "audiozone.trace.text.rule";
	public static final String WINS = "audiozone.trace.text.wins";
	public static final String OVERRIDDEN = "audiozone.trace.text.overridden";
	public static final String ZONES = "audiozone.trace.text.zones";
	public static final String NO_ZONES = "audiozone.trace.text.no_zones";
	public static final String ZONE = "audiozone.trace.text.zone";
	public static final String ZONE_WINNERS = "audiozone.trace.text.zone_winners";
	public static final String ZONE_WINNER = "audiozone.trace.text.zone_winner";
	public static final String RESULT = "audiozone.trace.text.result";
	public static final String VOLUME = "audiozone.trace.text.volume";
	public static final String NO_LIMIT = "audiozone.trace.text.no_limit";
	public static final String PLAY_ALLOWED = "audiozone.trace.text.play_allowed";
	public static final String PLAY_DROPPED = "audiozone.trace.text.play_dropped";
	public static final String CUE_SHOWN = "audiozone.trace.text.cue_shown";
	public static final String CUE_NOT_SHOWN = "audiozone.trace.text.cue_not_shown";

	private static final String INDENT = "  ";

	private TraceText() {
	}

	public static String of(SoundTrace trace, Translator tr) {
		StringBuilder out = new StringBuilder();
		line(out, 0, tr.translate(HEADER, trace.soundId()));
		for (TraceNotice notice : trace.notices()) {
			line(out, 1, tr.translate(notice.translationKey()));
		}

		line(out, 0, tr.translate(GLOBAL));
		rules(out, 1, trace.global(), tr);

		line(out, 0, tr.translate(ZONES));
		if (trace.zones().isEmpty()) {
			line(out, 1, tr.translate(NO_ZONES));
		}
		for (ZoneLine zone : trace.zones()) {
			line(out, 1, tr.translate(ZONE, name(zone.zone()), ZoneSummary.modePart(zone.zone().mode()).text(tr),
					Numbers.plain(zone.weight() * 100), Numbers.plain(zone.distance())));
			rules(out, 2, zone.rules(), tr);
		}

		if (!trace.zoneWinners().isEmpty()) {
			line(out, 0, tr.translate(ZONE_WINNERS));
			for (TraceProperty property : TraceProperty.values()) {
				ZoneWinner winner = trace.zoneWinners().get(property);
				if (winner != null) {
					line(out, 1, tr.translate(ZONE_WINNER, property(property, tr), name(winner.zone()),
							tr.translate(winner.reason().translationKey())));
				}
			}
		}

		line(out, 0, tr.translate(RESULT));
		line(out, 1, tr.translate(VOLUME, Numbers.plain(trace.volumePercent())));
		line(out, 1, trace.limit() == null ? tr.translate(NO_LIMIT) : RuleSummary.limitPart(trace.limit()).text(tr));
		if (trace.playAllowed() != null) {
			line(out, 1, tr.translate(trace.playAllowed() ? PLAY_ALLOWED : PLAY_DROPPED));
		}
		line(out, 1, tr.translate(trace.cueOn() ? RuleSummary.CUE_ON : RuleSummary.CUE_OFF));
		if (trace.cueShown() != null) {
			line(out, 1, tr.translate(trace.cueShown() ? CUE_SHOWN : CUE_NOT_SHOWN));
		}
		return out.toString();
	}

	/** Each matched rule with its settings, then which settings it decided and which it lost, and to whom. */
	private static void rules(StringBuilder out, int level, ListTrace list, Translator tr) {
		if (list.matched().isEmpty()) {
			line(out, level, tr.translate(NO_RULES));
			return;
		}
		for (Rule rule : list.matched()) {
			line(out, level, tr.translate(RULE, rule.pattern().text(), RuleSummary.text(rule, tr)));
			for (TraceProperty property : TraceProperty.values()) {
				RuleWinner winner = list.winners().get(property);
				if (winner != null && winner.rule() == rule) {
					line(out, level + 1, tr.translate(WINS, property(property, tr), tr.translate(winner.reason().translationKey())));
					continue;
				}
				Rule by = list.overriddenBy(rule, property);
				if (by != null) {
					line(out, level + 1, tr.translate(OVERRIDDEN, property(property, tr), by.pattern().text()));
				}
			}
		}
	}

	private static String property(TraceProperty property, Translator tr) {
		return tr.translate(switch (property) {
			case LOUDNESS -> "audiozone.trace.property.loudness";
			case LIMIT -> "audiozone.trace.property.limit";
			case CUE -> "audiozone.trace.property.cue";
		});
	}

	/** The zone's name, or its ID if it has none. */
	private static String name(Zone zone) {
		return zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
	}

	private static void line(StringBuilder out, int level, String text) {
		out.append(INDENT.repeat(level)).append(text).append('\n');
	}
}
