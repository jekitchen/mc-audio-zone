package soundsculptor.core.cue;

/** Whether a muted sound gets a cue, and its text (PRD FR-7.1, FR-7.2, FR-7.7). */
public final class CueRules {
	private CueRules() {
	}

	/**
	 * @param soundId     the sound's ID, shown only when it has no subtitle and the setting allows it
	 * @param subtitle    the vanilla subtitle text in the current language, or {@code null}
	 * @param finalVolume the volume Sound Sculptor decided, in percent
	 * @param byRule      whether that volume comes from an Sound Sculptor rule (mute, 0%, or a fully faded zone)
	 * @param ruleCueOn   the deciding rule's Cue setting
	 * @param audible     whether the sound passes the range check vanilla uses for subtitles (fair play)
	 * @return the cue text, or {@code null} for no cue
	 */
	public static String cueText(String soundId, String subtitle, float finalVolume, boolean byRule, boolean ruleCueOn,
			boolean audible, CueSettings settings) {
		if (!shouldCue(subtitle != null, finalVolume, byRule, ruleCueOn, audible, settings)) {
			return null;
		}
		return subtitle != null ? subtitle : soundId;
	}

	/**
	 * Whether a cue is shown, without building its text: for the sound hook, which only fetches the subtitle when a
	 * cue is due. When true, the text is the subtitle if there is one, otherwise the sound ID.
	 *
	 * @param hasSubtitle whether the sound has a vanilla subtitle
	 */
	public static boolean shouldCue(boolean hasSubtitle, float finalVolume, boolean byRule, boolean ruleCueOn,
			boolean audible, CueSettings settings) {
		if (!settings.showCues() || finalVolume > 0 || !byRule || !ruleCueOn || !audible) {
			return false;
		}
		return hasSubtitle || settings.cueSoundsWithoutSubtitles();
	}
}
