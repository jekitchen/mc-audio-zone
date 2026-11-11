package soundsculptor.core.recent;

/**
 * One row of the recently played list (PRD FR-1.2): every play of one sound ID in the last 3 minutes. Band, status
 * and zone come from the latest play. The screen adds the subtitle text and "2s ago" from {@code lastPlayedMs}.
 *
 * @param zoneName the zone that decided the latest play, or {@code null} if the global list or nothing did
 */
public record RecentRow(String id, String category, int count, long lastPlayedMs, DistanceBand band, PlayStatus status,
		String zoneName) {
}
