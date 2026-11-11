package soundsculptor.core.zones;

/** Which side of its shape a zone affects (PRD FR-8.4). */
public enum ZoneMode {
	/** Sounds inside the shape (default, MVP). */
	INSIDE,
	/** Sounds outside the shape, in the same dimension (v1). */
	OUTSIDE
}
