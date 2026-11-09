package audiozone.mc;

/**
 * Player settings (PRD 6.1, Settings tab). Held in memory for now; saving them with the profile file arrives with
 * the profile store (M4).
 */
public final class AudioZoneSettings {
	private AudioZoneSettings() {
	}

	/** FR-1.4 "Show distance" (default on): show the distance band in the recently played list. */
	public static volatile boolean showDistance = true;
}
