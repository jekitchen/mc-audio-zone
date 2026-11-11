package soundsculptor.core.lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import soundsculptor.core.Translator;
import soundsculptor.core.rules.Limit;
import soundsculptor.core.rules.Rule;

/**
 * The Rules tab row summary (PRD 6.1), such as "Muted" and "Cue on", as separate chips in a fixed order: off,
 * mute, volume, limit, cue. Also used for rule lines in the copied trace.
 */
public final class RuleSummary {
	public static final String SEPARATOR = "soundsculptor.summary.separator";
	public static final String DISABLED = "soundsculptor.summary.rule.disabled";
	public static final String MUTED = "soundsculptor.summary.rule.muted";
	public static final String NOT_MUTED = "soundsculptor.summary.rule.not_muted";
	public static final String VOLUME = "soundsculptor.summary.rule.volume";
	public static final String LIMIT = "soundsculptor.summary.rule.limit";
	public static final String LIMIT_PER_SOURCE = "soundsculptor.summary.rule.limit_per_source";
	public static final String CUE_ON = "soundsculptor.summary.rule.cue_on";
	public static final String CUE_OFF = "soundsculptor.summary.rule.cue_off";
	public static final String NOTHING = "soundsculptor.summary.rule.nothing";
	public static final String MATCHES = "soundsculptor.summary.rule.matches";

	private RuleSummary() {
	}

	public static List<SummaryPart> parts(Rule rule) {
		List<SummaryPart> parts = new ArrayList<>();
		if (!rule.enabled()) {
			parts.add(SummaryPart.of(DISABLED));
		}
		if (rule.mute() != null) {
			parts.add(SummaryPart.of(rule.mute() ? MUTED : NOT_MUTED));
		}
		if (rule.volume() != null) {
			parts.add(SummaryPart.of(VOLUME, Integer.toString(rule.volume())));
		}
		if (rule.limit() != null) {
			parts.add(limitPart(rule.limit()));
		}
		if (rule.cue() != null) {
			parts.add(SummaryPart.of(rule.cue() ? CUE_ON : CUE_OFF));
		}
		if (!rule.setsLoudness() && !rule.setsLimit() && !rule.setsCue()) {
			parts.add(SummaryPart.of(NOTHING));
		}
		return List.copyOf(parts);
	}

	/** "At most 3 per 1 s", with "per source" as its own phrase so the word order can change. */
	public static SummaryPart limitPart(Limit limit) {
		String seconds = Numbers.plain(limit.windowMs() / 1000.0, 2);
		return SummaryPart.of(limit.perSource() ? LIMIT_PER_SOURCE : LIMIT, Integer.toString(limit.count()), seconds);
	}

	/** The chips joined with the translated separator, for one-line places such as the copied trace. */
	public static String text(Rule rule, Translator translator) {
		return join(parts(rule), translator);
	}

	public static String join(List<SummaryPart> parts, Translator translator) {
		String separator = translator.translate(SEPARATOR);
		StringBuilder out = new StringBuilder();
		for (SummaryPart part : parts) {
			if (!out.isEmpty()) {
				out.append(separator);
			}
			out.append(part.text(translator));
		}
		return out.toString();
	}

	/** How many of {@code knownIds} the rule's pattern matches, for the row's match count. */
	public static int matchCount(Rule rule, Collection<String> knownIds) {
		int count = 0;
		for (String id : knownIds) {
			if (rule.pattern().matches(id)) {
				count++;
			}
		}
		return count;
	}

	public static SummaryPart matchesPart(int count) {
		return SummaryPart.of(MATCHES, Integer.toString(count));
	}
}
