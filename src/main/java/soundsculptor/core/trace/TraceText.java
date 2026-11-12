package soundsculptor.core.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import soundsculptor.core.Translator;
import soundsculptor.core.lists.Numbers;
import soundsculptor.core.lists.EntrySummary;
import soundsculptor.core.lists.ZoneSummary;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Zone;

/**
 * The "Copy trace" text (PRD FR-14.5): the trace panel as plain text, for asking for help or filing a bug. Sections
 * follow FR-14.2: notices; Everywhere, with each set that has a matching entry ("used in N places" when it is linked
 * to more than one) and under it its entries, the winner and reason per setting, and for losers "overridden by" the
 * winning set and entry; zones considered (mode, weight, distance to the anchor, their sets as for Everywhere); the
 * deciding zone per setting; then the final result. Every phrase comes from one translation key; only line breaks and
 * indentation are added here.
 *
 * <p>{@link #lines} gives the same text line by line, each with what it is about, so the panel can put inline edit
 * controls on entry, zone and "no entry" lines (FR-14.4).
 */
public final class TraceText {
	public static final String HEADER = "soundsculptor.trace.text.header";
	public static final String EVERYWHERE = "soundsculptor.trace.text.everywhere";
	public static final String NO_ENTRIES = "soundsculptor.trace.text.no_entries";
	/** A set's line: Quick changes. */
	public static final String QUICK = "soundsculptor.trace.text.quick";
	/** A set's line when the set is linked: "Farm animals · used in 3 places". */
	public static final String USED_IN = "soundsculptor.trace.text.used_in";
	public static final String ENTRY = "soundsculptor.trace.text.entry";
	public static final String WINS = "soundsculptor.trace.text.wins";
	public static final String OVERRIDDEN = "soundsculptor.trace.text.overridden";
	public static final String ZONES = "soundsculptor.trace.text.zones";
	public static final String NO_ZONES = "soundsculptor.trace.text.no_zones";
	public static final String ZONE = "soundsculptor.trace.text.zone";
	public static final String ZONE_WINNERS = "soundsculptor.trace.text.zone_winners";
	public static final String ZONE_WINNER = "soundsculptor.trace.text.zone_winner";
	public static final String RESULT = "soundsculptor.trace.text.result";
	public static final String VOLUME = "soundsculptor.trace.text.volume";
	public static final String NO_LIMIT = "soundsculptor.trace.text.no_limit";
	public static final String PLAY_ALLOWED = "soundsculptor.trace.text.play_allowed";
	public static final String PLAY_DROPPED = "soundsculptor.trace.text.play_dropped";
	public static final String CUE_SHOWN = "soundsculptor.trace.text.cue_shown";
	public static final String CUE_NOT_SHOWN = "soundsculptor.trace.text.cue_not_shown";

	private static final String INDENT = "  ";

	/**
	 * One line of the trace.
	 *
	 * @param text   the line as "Copy trace" shows it, indentation included, without the line break
	 * @param target what the line is about, or {@code null} for a line with nothing to edit
	 */
	public record Line(String text, Target target) {
		public Line {
			Objects.requireNonNull(text, "text");
		}
	}

	/** What a trace line is about, for the panel's inline controls. */
	public sealed interface Target {
	}

	/** A matched entry, in the set and at the place it is listed under. */
	public record EntryTarget(EntryLocation where, SetEntry entry) implements Target {
	}

	/** A zone that contains the sound. */
	public record ZoneTarget(Zone zone) implements Target {
	}

	/** "No entry matches this sound" at Everywhere or a zone: where Add to Quick changes goes. */
	public record NoEntryTarget(PlaceRef place) implements Target {
	}

	private TraceText() {
	}

	public static String of(SoundTrace trace, Translator tr) {
		StringBuilder out = new StringBuilder();
		for (Line line : lines(trace, tr)) {
			out.append(line.text()).append('\n');
		}
		return out.toString();
	}

	/** The trace line by line, in the same order and wording as {@link #of}. */
	public static List<Line> lines(SoundTrace trace, Translator tr) {
		List<Line> out = new ArrayList<>();
		line(out, 0, tr.translate(HEADER, trace.soundId()));
		for (TraceNotice notice : trace.notices()) {
			line(out, 1, tr.translate(notice.translationKey()));
		}

		line(out, 0, tr.translate(EVERYWHERE));
		place(out, 1, trace.everywhere(), PlaceRef.EVERYWHERE, tr);

		line(out, 0, tr.translate(ZONES));
		if (trace.zones().isEmpty()) {
			line(out, 1, tr.translate(NO_ZONES));
		}
		for (ZoneLine zone : trace.zones()) {
			line(out, 1, tr.translate(ZONE, name(zone.zone()), ZoneSummary.modePart(zone.zone().mode()).text(tr),
					Numbers.plain(zone.weight() * 100), Numbers.plain(zone.distance())), new ZoneTarget(zone.zone()));
			place(out, 2, zone.sets(), PlaceRef.zone(zone.zone().id()), tr);
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
		line(out, 1, trace.limit() == null ? tr.translate(NO_LIMIT) : EntrySummary.limitPart(trace.limit()).text(tr));
		if (trace.playAllowed() != null) {
			line(out, 1, tr.translate(trace.playAllowed() ? PLAY_ALLOWED : PLAY_DROPPED));
		}
		line(out, 1, tr.translate(trace.cueOn() ? EntrySummary.CUE_ON : EntrySummary.CUE_OFF));
		if (trace.cueShown() != null) {
			line(out, 1, tr.translate(trace.cueShown() ? CUE_SHOWN : CUE_NOT_SHOWN));
		}
		return out;
	}

	/** Each set with a matching entry; under it each entry, which settings it decided and which it lost, and to whom. */
	private static void place(List<Line> out, int level, PlaceTrace trace, PlaceRef place, Translator tr) {
		if (trace.sets().isEmpty()) {
			line(out, level, tr.translate(NO_ENTRIES), new NoEntryTarget(place));
			return;
		}
		for (SetLine set : trace.sets()) {
			String name = setName(set, tr);
			line(out, level, set.placesUsing() > 1 ? tr.translate(USED_IN, name, set.placesUsing()) : name);
			for (SetEntry entry : set.matched()) {
				EntryLocation where = new EntryLocation(place, set.setId(), entry.id());
				line(out, level + 1, tr.translate(ENTRY, entry.pattern().text(), EntrySummary.text(entry, tr)), new EntryTarget(where, entry));
				for (TraceProperty property : TraceProperty.values()) {
					EntryWinner winner = trace.winners().get(property);
					if (trace.wins(set.setId(), entry, property)) {
						line(out, level + 2, tr.translate(WINS, property(property, tr), tr.translate(winner.reason().translationKey())));
						continue;
					}
					EntryWinner by = trace.overriddenBy(set.setId(), entry, property);
					if (by != null) {
						line(out, level + 2, tr.translate(OVERRIDDEN, property(property, tr), setName(trace.set(by.setId()), tr),
								by.entry().pattern().text()));
					}
				}
			}
		}
	}

	/** The set's name, or "Quick changes". */
	private static String setName(SetLine set, Translator tr) {
		return set.quick() ? tr.translate(QUICK) : set.setName();
	}

	private static String property(TraceProperty property, Translator tr) {
		return tr.translate(switch (property) {
			case LOUDNESS -> "soundsculptor.trace.property.loudness";
			case LIMIT -> "soundsculptor.trace.property.limit";
			case CUE -> "soundsculptor.trace.property.cue";
		});
	}

	/** The zone's name, or its ID if it has none. */
	private static String name(Zone zone) {
		return zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
	}

	private static void line(List<Line> out, int level, String text) {
		line(out, level, text, null);
	}

	private static void line(List<Line> out, int level, String text, Target target) {
		out.add(new Line(INDENT.repeat(level) + text, target));
	}
}
