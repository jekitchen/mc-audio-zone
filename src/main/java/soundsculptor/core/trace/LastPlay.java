package soundsculptor.core.trace;

/**
 * Where and how a sound last played: enough to rebuild its trace later (PRD FR-14.6).
 *
 * @param px            the local player's eye position at the play, the anchor of personal bubbles; {@code NaN} if
 *                      unknown (bubbles are then left out)
 * @param volumePercent the volume Sound Sculptor actually applied, to spot rule changes since
 * @param dropped       whether a repeat limit dropped the play
 * @param cueShown      whether a muted-sound cue was shown
 */
public record LastPlay(boolean positional, String worldKey, String dimension, double x, double y, double z, double px,
		double py, double pz, float volumePercent, boolean dropped, boolean cueShown) {
	/** A play without the player's position. */
	public LastPlay(boolean positional, String worldKey, String dimension, double x, double y, double z,
			float volumePercent, boolean dropped, boolean cueShown) {
		this(positional, worldKey, dimension, x, y, z, Double.NaN, Double.NaN, Double.NaN, volumePercent, dropped, cueShown);
	}
}
