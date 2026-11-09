package audiozone.core.trace;

/** Why a zone won a setting over the other zones (PRD FR-14.2 item 3, mirroring FR-9 steps 4 and 5). */
public enum ZoneWinReason {
	/** No other zone containing the sound sets this. */
	ONLY_ZONE("only_zone"),
	/** Its anchor is closest to the sound. */
	CLOSEST("closest"),
	/** Tie on distance; the smaller zone wins. */
	TIE_SMALLER_ZONE("tie_smaller_zone"),
	/** Tie on distance and size; higher in the zone list. */
	TIE_LIST_ORDER("tie_list_order");

	private final String key;

	ZoneWinReason(String key) {
		this.key = key;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json}. */
	public String translationKey() {
		return "audiozone.trace.zone_reason." + key;
	}
}
