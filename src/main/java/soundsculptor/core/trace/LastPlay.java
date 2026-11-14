package soundsculptor.core.trace;

/**
 * Where and how a sound last played: enough to rebuild its trace later (PRD FR-14.6).
 *
 * @param volumePercent the volume Sound Sculptor actually applied, to spot rule changes since
 * @param dropped       whether a repeat limit dropped the play
 * @param cueShown      whether a muted-sound cue was shown
 */
public record LastPlay(boolean positional, String worldKey, String dimension, double x, double y, double z,
		float volumePercent, boolean dropped, boolean cueShown) {
}
