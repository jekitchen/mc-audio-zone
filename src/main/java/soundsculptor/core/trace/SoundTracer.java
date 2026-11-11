package soundsculptor.core.trace;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soundsculptor.core.rules.Rule;
import soundsculptor.core.rules.RuleResolver;
import soundsculptor.core.rules.SoundPattern;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;

/**
 * Builds the sound trace (PRD FR-14, read-only MVP) when the "Why?" panel opens. Not on the hot path: it allocates
 * freely. The final result comes from {@link Precedence#decide} and the zone tie-break from
 * {@link Precedence#beats(double, double, double, double)}, so the trace can never disagree with what was played.
 */
public final class SoundTracer {
	private static final float SAME_VOLUME = 0.01F;

	private SoundTracer() {
	}

	/**
	 * @param play           the sound's last play, or {@code null} if it has not played this session
	 * @param bypass         whether Bypass Sound Sculptor is on
	 * @param vanillaCovered whether the sound belongs to Music, UI or Voice/Speech
	 */
	public static SoundTrace trace(Precedence p, String soundId, LastPlay play, boolean bypass, boolean vanillaCovered) {
		Set<TraceNotice> notices = EnumSet.noneOf(TraceNotice.class);
		Boolean playAllowed = play == null ? null : !play.dropped();
		Boolean cueShown = play == null ? null : play.cueShown();
		if (vanillaCovered) {
			notices.add(TraceNotice.VANILLA_COVERED);
			return new SoundTrace(soundId, notices, ListTrace.EMPTY, List.of(), Map.of(), 100, null, false, playAllowed, cueShown);
		}
		if (bypass) {
			notices.add(TraceNotice.BYPASS);
		}
		if (play == null) {
			notices.add(TraceNotice.NOT_HEARD_YET);
		} else if (!play.positional()) {
			notices.add(TraceNotice.NON_POSITIONAL);
		}

		ListTrace global = explain(p.globalRules(), soundId);
		List<ZoneLine> lines = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		if (play != null && play.positional()) {
			List<Zone> zones = p.zones();
			for (int i = 0; i < zones.size(); i++) {
				Zone zone = zones.get(i);
				if (!zone.enabled()) {
					continue;
				}
				Point a;
				if (zone.isBubble()) {
					if (Double.isNaN(play.px())) {
						continue;
					}
					a = new Point(play.px(), play.py(), play.pz());
				} else {
					if (!play.worldKey().equals(zone.worldKey()) || !play.dimension().equals(zone.dimension())) {
						continue;
					}
					a = zone.anchor();
				}
				double dx = play.x() - a.x();
				double dy = play.y() - a.y();
				double dz = play.z() - a.z();
				double w = zone.weight(dx, dy, dz, p.profileEdge());
				if (w <= 0) {
					continue;
				}
				lines.add(new ZoneLine(zone, w, Math.sqrt(dx * dx + dy * dy + dz * dz), explain(p.zoneRules(i), soundId)));
				indices.add(i);
			}
		}
		Map<TraceProperty, ZoneWinner> zoneWinners = new EnumMap<>(TraceProperty.class);
		for (TraceProperty property : TraceProperty.values()) {
			ZoneWinner winner = zoneWinner(p, lines, indices, property);
			if (winner != null) {
				zoneWinners.put(property, winner);
			}
		}

		SoundDecision d = new SoundDecision();
		if (play != null) {
			p.decide(soundId, play.positional(), play.worldKey(), play.dimension(), play.x(), play.y(), play.z(), play.px(),
					play.py(), play.pz(), d);
		} else {
			p.decide(soundId, false, "", "", 0, 0, 0, d);
		}
		float volume = bypass ? 100 : d.volumePercent();
		if (!bypass && volume > 100) {
			notices.add(TraceNotice.BOOSTED);
		}
		if (play != null && !bypass && Math.abs(volume - play.volumePercent()) > SAME_VOLUME) {
			notices.add(TraceNotice.CHANGED_SINCE_PLAY);
		}
		return new SoundTrace(soundId, notices, global, lines, zoneWinners, volume, bypass ? null : d.limit(),
				!bypass && d.cueOn(), playAllowed, cueShown);
	}

	/** The matched rules of one list and the winner per setting, with the specificity step that decided it. */
	static ListTrace explain(RuleResolver rules, String soundId) {
		List<Rule> matched = new ArrayList<>();
		for (Rule rule : rules.rulesInOrder()) {
			if (rule.pattern().matches(soundId)) {
				matched.add(rule);
			}
		}
		Map<TraceProperty, RuleWinner> winners = new EnumMap<>(TraceProperty.class);
		for (TraceProperty property : TraceProperty.values()) {
			Rule winner = null;
			Rule runnerUp = null;
			for (Rule rule : matched) {
				if (!property.setBy(rule)) {
					continue;
				}
				if (winner == null) {
					winner = rule;
				} else {
					runnerUp = rule;
					break;
				}
			}
			if (winner != null) {
				winners.put(property, new RuleWinner(winner, ruleReason(winner, runnerUp)));
			}
		}
		return new ListTrace(matched, winners);
	}

	private static RuleWinReason ruleReason(Rule winner, Rule runnerUp) {
		if (runnerUp == null) {
			return RuleWinReason.ONLY_MATCH;
		}
		SoundPattern w = winner.pattern();
		SoundPattern r = runnerUp.pattern();
		if (w.isExact() && !r.isExact()) {
			return RuleWinReason.EXACT_ID;
		}
		if (w.literalCount() > r.literalCount()) {
			return RuleWinReason.MORE_LITERAL;
		}
		if (w.starCount() < r.starCount()) {
			return RuleWinReason.FEWER_STARS;
		}
		return RuleWinReason.LIST_ORDER;
	}

	/** Picks the winning zone for one setting exactly as {@code Precedence.decide} does, and names the deciding step. */
	private static ZoneWinner zoneWinner(Precedence p, List<ZoneLine> lines, List<Integer> indices, TraceProperty property) {
		int best = -1;
		for (int k = 0; k < lines.size(); k++) {
			if (sets(lines.get(k), property) && (best < 0 || beats(p, lines, indices, k, best))) {
				best = k;
			}
		}
		if (best < 0) {
			return null;
		}
		// FR-9 step 6: a limit or cue from a zone applies only at weight 0.5 or more; otherwise the global value stays.
		if (property != TraceProperty.LOUDNESS && lines.get(best).weight() < 0.5) {
			return null;
		}
		int runnerUp = -1;
		for (int k = 0; k < lines.size(); k++) {
			if (k != best && sets(lines.get(k), property) && (runnerUp < 0 || beats(p, lines, indices, k, runnerUp))) {
				runnerUp = k;
			}
		}
		Zone zone = lines.get(best).zone();
		if (runnerUp < 0) {
			return new ZoneWinner(zone, ZoneWinReason.ONLY_ZONE);
		}
		if (Precedence.isDistanceTie(lines.get(best).distance(), lines.get(runnerUp).distance())) {
			boolean smaller = p.zoneVolume(indices.get(best)) < p.zoneVolume(indices.get(runnerUp));
			return new ZoneWinner(zone, smaller ? ZoneWinReason.TIE_SMALLER_ZONE : ZoneWinReason.TIE_LIST_ORDER);
		}
		return new ZoneWinner(zone, ZoneWinReason.CLOSEST);
	}

	private static boolean sets(ZoneLine line, TraceProperty property) {
		return line.rules().winners().containsKey(property);
	}

	private static boolean beats(Precedence p, List<ZoneLine> lines, List<Integer> indices, int k, int best) {
		return Precedence.beats(lines.get(k).distance(), p.zoneVolume(indices.get(k)), lines.get(best).distance(),
				p.zoneVolume(indices.get(best)));
	}
}
