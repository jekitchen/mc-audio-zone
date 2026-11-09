package audiozone.core.zones;

/** What a zone's anchor is (PRD FR-8.1, FR-8.2). */
public enum AnchorType {
	/** Fixed coordinates in one world and dimension. */
	FIXED,
	/** The local player's eye position, updated every tick (v1). Never another player. */
	BUBBLE
}
