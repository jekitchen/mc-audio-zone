package audiozone.core.lists;

/** Where one of the player's zones is, relative to the player, for the Zones tab (PRD 6.1). */
public enum LocationKind {
	/** The zone affects the player's position now (always true for a personal bubble). */
	HERE("audiozone.summary.zone.here"),
	/** Same world and dimension, but the player is not in the affected area. */
	AWAY("audiozone.summary.zone.away"),
	/** A fixed zone made on another server or world. */
	OTHER_WORLD("audiozone.summary.zone.other_world"),
	/** Same world, another dimension. */
	OTHER_DIMENSION("audiozone.summary.zone.other_dimension");

	private final String key;

	LocationKind(String key) {
		this.key = key;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json}. */
	public String translationKey() {
		return key;
	}
}
