package audiozone.core.outline;

/** Setting "Show zone outlines" (PRD FR-10.1). */
public enum OutlineMode {
	/** Every nearby zone. */
	ALWAYS,
	/** Only the zone open in the zone editor. */
	EDITING,
	/** No outlines at all. */
	NEVER;

	public static final OutlineMode DEFAULT = EDITING;
}
