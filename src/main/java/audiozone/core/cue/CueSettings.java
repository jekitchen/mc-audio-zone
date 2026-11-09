package audiozone.core.cue;

/**
 * Cue settings (PRD FR-7.2, FR-7.6, FR-7.7).
 *
 * @param showCues                  master switch "Show muted-sound cues"; independent of vanilla Subtitles
 * @param cueSoundsWithoutSubtitles show the sound ID for sounds that have no subtitle
 * @param newMuteRulesCueOn         the Cue setting new mute rules start with
 */
public record CueSettings(boolean showCues, boolean cueSoundsWithoutSubtitles, boolean newMuteRulesCueOn) {
	public static final CueSettings DEFAULT = new CueSettings(true, false, true);
}
