package soundsculptor.core.browser;

import soundsculptor.core.recent.RecentRow;

/**
 * One row of the Sounds browser (PRD FR-1.2, FR-3.2).
 *
 * @param subtitle the subtitle text, or {@code null} if the sound has none
 * @param recent   the sound's recently played data, or {@code null} if it has not played in the last 3 minutes
 */
public record BrowserRow(String soundId, String subtitle, RecentRow recent) {
}
