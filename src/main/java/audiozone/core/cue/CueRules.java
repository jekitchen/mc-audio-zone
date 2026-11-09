package audiozone.core.cue;

/** Whether a muted sound gets a cue, and its text (PRD FR-7.1, FR-7.2, FR-7.7). */
public final class CueRules {
	private CueRules() {
	}

	/**
	 * @param soundId     the sound's ID, shown only when it has no subtitle and the setting allows it
	 * @param subtitle    the vanilla subtitle text in the current language, or {@code null}
	 * @param finalVolume the volume Audio Zone decided, in percent
	 * @param byRule      whether that volume comes from an Audio Zone rule (mute, 0%, or a fully faded zone)
	 * @param ruleCueOn   the deciding rule's Cue setting
	 * @param audible     whether the sound passes the range check vanilla uses for subtitles (fair play)
	 * @return the cue text, or {@code null} for no cue
	 */
	public static String cueText(String soundId, String subtitle, float finalVolume, boolean byRule, boolean ruleCueOn,
			boolean audible, CueSettings settings) {
		if (!settings.showCues() || finalVolume > 0 || !byRule || !ruleCueOn || !audible) {
			return null;
		}
		if (subtitle != null) {
			return subtitle;
		}
		return settings.cueSoundsWithoutSubtitles() ? soundId : null;
	}
}
