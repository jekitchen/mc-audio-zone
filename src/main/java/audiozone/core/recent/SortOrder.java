package audiozone.core.recent;

/** Sort orders for the recently played list (PRD FR-1.3). */
public enum SortOrder {
	/** Most recently played first (default). */
	MOST_RECENT,
	/** Most plays in the last 3 minutes first. */
	PLAY_COUNT,
	/** By display name (subtitle text, or the ID when there is none), ignoring case. */
	NAME
}
