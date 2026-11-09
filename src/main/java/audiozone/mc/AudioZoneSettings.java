package audiozone.mc;

/** Player settings (PRD 6.1, Settings tab), stored in the profile file (FR-11). */
public final class AudioZoneSettings {
	private AudioZoneSettings() {
	}

	/** FR-1.4 "Show distance" (default on): show the distance band in the recently played list. */
	public static boolean showDistance() {
		return ProfileManager.profile().settings().showDistance();
	}

	public static void setShowDistance(boolean value) {
		ProfileManager.update(profile -> profile.withSettings(profile.settings().withShowDistance(value)));
	}
}
