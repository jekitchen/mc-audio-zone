package soundsculptor.core.trace;

import java.util.List;
import java.util.Map;
import java.util.Set;

import soundsculptor.core.rules.Limit;

/**
 * Everything the "Why?" panel shows for one sound, in the order FR-9 applies it (PRD FR-14.2). Structured data:
 * the panel turns it into translated text.
 *
 * @param notices       special cases, most important first
 * @param global        global rules that matched and the winner per setting
 * @param zones         zones containing the sound, in zone-list order
 * @param zoneWinners   the deciding zone per setting; absent when no zone sets it
 * @param volumePercent final volume, 0 when muted
 * @param limit         the repeat limit that applies, or {@code null}
 * @param cueOn         whether the deciding cue setting is on
 * @param playAllowed   whether the last play got past the repeat limit, or {@code null} if not heard yet
 * @param cueShown      whether the last play showed a cue, or {@code null} if not heard yet
 */
public record SoundTrace(String soundId, Set<TraceNotice> notices, ListTrace global, List<ZoneLine> zones,
		Map<TraceProperty, ZoneWinner> zoneWinners, float volumePercent, Limit limit, boolean cueOn, Boolean playAllowed,
		Boolean cueShown) {
}
