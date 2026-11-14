package soundsculptor.core.trace;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;

/**
 * Builds the sound trace (PRD FR-14) when the "Why?" panel opens, in Sound Set terms: for Everywhere and each zone,
 * the attached sets with entries that match the sound, the winning entry per setting and the set it won in. Not on the
 * hot path: it allocates freely. Entries are listed and picked in the order the place's resolver uses, the final
 * result comes from {@link Precedence#decide} and the zone tie-break from
 * {@link Precedence#beats(double, double, double, double)}, so the trace can never disagree with what was played.
 */
public final class SoundTracer {
	private static final float SAME_VOLUME = 0.01F;

	private SoundTracer() {
	}

	/** {@link #trace(Precedence, SetLibrary, ToIntFunction, String, LastPlay, boolean, boolean)} with no place counts. */
	public static SoundTrace trace(Precedence p, SetLibrary lib, String soundId, LastPlay play, boolean bypass,
			boolean vanillaCovered) {
		return trace(p, lib, setId -> 0, soundId, play, bypass, vanillaCovered);
	}

	/**
	 * @param p              the places, built from {@code lib}
	 * @param lib            the library the places' set IDs refer to, for set names
	 * @param placesUsing    for a library set ID, how many places use the set ("used in 3 places", FR-14.4)
	 * @param play           the sound's last play, or {@code null} if it has not played this session
	 * @param bypass         whether Bypass Sound Sculptor is on
	 * @param vanillaCovered whether the sound belongs to Music, UI or Voice/Speech
	 */
	public static SoundTrace trace(Precedence p, SetLibrary lib, ToIntFunction<String> placesUsing, String soundId,
			LastPlay play, boolean bypass, boolean vanillaCovered) {
		Set<TraceNotice> notices = EnumSet.noneOf(TraceNotice.class);
		Boolean playAllowed = play == null ? null : !play.dropped();
		Boolean cueShown = play == null ? null : play.cueShown();
		if (vanillaCovered) {
			notices.add(TraceNotice.VANILLA_COVERED);
			return new SoundTrace(soundId, notices, PlaceTrace.EMPTY, List.of(), Map.of(), 100, null, false, playAllowed, cueShown);
		}
		if (bypass) {
			notices.add(TraceNotice.BYPASS);
		}
		if (play == null) {
			notices.add(TraceNotice.NOT_HEARD_YET);
		} else if (!play.positional()) {
			notices.add(TraceNotice.NON_POSITIONAL);
		}

		PlaceTrace everywhere = explain(p.everywhere(), soundId, lib, placesUsing);
		List<ZoneLine> lines = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		if (play != null && play.positional()) {
			List<Zone> zones = p.zones();
			for (int i = 0; i < zones.size(); i++) {
				Zone zone = zones.get(i);
				if (!zone.enabled() || !play.worldKey().equals(zone.worldKey()) || !play.dimension().equals(zone.dimension())) {
					continue;
				}
				Point a = zone.anchor();
				double dx = play.x() - a.x();
				double dy = play.y() - a.y();
				double dz = play.z() - a.z();
				double w = zone.weight(dx, dy, dz, p.profileEdge());
				if (w <= 0) {
					continue;
				}
				PlaceTrace sets = explain(p.zoneResolver(i), soundId, lib, placesUsing);
				lines.add(new ZoneLine(zone, w, Math.sqrt(dx * dx + dy * dy + dz * dz), sets));
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
			p.decide(soundId, play.positional(), play.worldKey(), play.dimension(), play.x(), play.y(), play.z(), d);
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
		return new SoundTrace(soundId, notices, everywhere, lines, zoneWinners, volume, bypass ? null : d.limit(),
				!bypass && d.cueOn(), playAllowed, cueShown);
	}

	/**
	 * The sets of one place with entries that match the sound, and the winner per setting with the step that decided
	 * it. Works on the resolver's positions, so each entry stays with the set it is listed in even when a set and its
	 * duplicate share entry instances.
	 */
	static PlaceTrace explain(EntryResolver place, String soundId, SetLibrary lib, ToIntFunction<String> placesUsing) {
		List<SetEntry> entries = place.rulesInOrder();
		List<String> setIds = place.setIdsInOrder();
		List<Integer> matched = new ArrayList<>();
		Map<String, List<SetEntry>> bySet = new LinkedHashMap<>();
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).pattern().matches(soundId)) {
				matched.add(i);
				bySet.computeIfAbsent(setIds.get(i), id -> new ArrayList<>()).add(entries.get(i));
			}
		}
		List<SetLine> sets = new ArrayList<>(bySet.size());
		bySet.forEach((setId, list) -> sets.add(setLine(setId, list, lib, placesUsing)));

		Map<TraceProperty, EntryWinner> winners = new EnumMap<>(TraceProperty.class);
		for (TraceProperty property : TraceProperty.values()) {
			int winner = -1;
			int runnerUp = -1;
			for (int i : matched) {
				if (!property.setBy(entries.get(i))) {
					continue;
				}
				if (winner < 0) {
					winner = i;
				} else {
					runnerUp = i;
					break;
				}
			}
			if (winner >= 0) {
				EntryWinReason reason = runnerUp < 0 ? EntryWinReason.ONLY_MATCH
						: !setIds.get(winner).equals(setIds.get(runnerUp)) ? EntryWinReason.HIGHER_SET
						: specificityReason(entries.get(winner), entries.get(runnerUp));
				winners.put(property, new EntryWinner(entries.get(winner), setIds.get(winner), reason));
			}
		}
		return new PlaceTrace(sets, winners);
	}

	private static SetLine setLine(String setId, List<SetEntry> matched, SetLibrary lib, ToIntFunction<String> placesUsing) {
		if (setId.equals(SoundSet.QUICK_ID)) {
			return new SetLine(setId, "", true, matched, 1);
		}
		SoundSet set = lib.get(setId);
		return new SetLine(setId, set == null ? setId : set.name(), false, matched, placesUsing.applyAsInt(setId));
	}

	/** Why {@code winner} beat {@code runnerUp} within one set: the FR-4.5 specificity step that decided. */
	private static EntryWinReason specificityReason(SetEntry winner, SetEntry runnerUp) {
		SoundPattern w = winner.pattern();
		SoundPattern r = runnerUp.pattern();
		if (w.isExact() && !r.isExact()) {
			return EntryWinReason.EXACT_ID;
		}
		if (w.literalCount() > r.literalCount()) {
			return EntryWinReason.MORE_LITERAL;
		}
		if (w.starCount() < r.starCount()) {
			return EntryWinReason.FEWER_STARS;
		}
		return EntryWinReason.LIST_ORDER;
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
		return line.sets().winners().containsKey(property);
	}

	private static boolean beats(Precedence p, List<ZoneLine> lines, List<Integer> indices, int k, int best) {
		return Precedence.beats(lines.get(k).distance(), p.zoneVolume(indices.get(k)), lines.get(best).distance(),
				p.zoneVolume(indices.get(best)));
	}
}
