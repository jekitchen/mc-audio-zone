package audiozone.core.recent;

/**
 * What Audio Zone did with one play (PRD FR-1.2). The list shows it as text and an icon, never color alone. When a
 * zone decided, the row also shows "Zone: name" ({@link RecentRow#zoneName()}).
 */
public enum PlayStatus {
	/** Played at vanilla loudness or turned down. */
	PLAYING("playing"),
	/** Silenced by a rule. */
	MUTED("muted"),
	/** Dropped by a repeat limit (v1). */
	LIMITED("limited"),
	/** Played above 100% (v1). */
	BOOSTED("boosted");

	private final String key;

	PlayStatus(String key) {
		this.key = key;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json}. */
	public String translationKey() {
		return "audiozone.recent.status." + key;
	}
}
