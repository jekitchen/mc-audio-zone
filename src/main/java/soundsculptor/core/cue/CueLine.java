package soundsculptor.core.cue;

/**
 * One visible line in the cue box (PRD FR-7.3 to FR-7.5). The HUD draws it as "(muted)", a muted-speaker icon, the
 * text in italics, then "×count" when {@code count > 1}, with the arrow on the side the sound came from.
 *
 * @param ageMs time since the latest repeat; the line fades out as this approaches {@link CueBox#DISPLAY_MS}
 */
public record CueLine(String text, int count, CueDirection direction, long ageMs) {
	/** 0 when the line is new, 1 when it is about to disappear; for fading like vanilla subtitles. */
	public double fadeFraction() {
		return Math.min(1.0, ageMs / (double) CueBox.DISPLAY_MS);
	}
}
