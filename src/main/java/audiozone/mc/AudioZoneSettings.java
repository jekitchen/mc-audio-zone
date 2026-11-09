package audiozone.mc;

import audiozone.core.outline.OutlineMode;

/** Player settings (PRD 6.1, Settings tab), stored in the profile file (FR-11). */
public final class AudioZoneSettings {
	private AudioZoneSettings() {
	}

	/** FR-1.4 "Show distance" (default on): show the distance band in the recently played list. */
	/** FR-10.1 "Show zone outlines" (a profile setting). */
	public static OutlineMode outlineMode() {
		return ProfileManager.profile().settings().outlines();
	}

	public static void setOutlineMode(OutlineMode value) {
		ProfileManager.update(profile -> profile.withSettings(profile.settings().withOutlines(value)));
	}

	/** The outlines hotkey: Always, Only while editing, Never, then Always again. */
	public static void cycleOutlineMode() {
		OutlineMode[] modes = OutlineMode.values();
		setOutlineMode(modes[(outlineMode().ordinal() + 1) % modes.length]);
	}

	/** FR-10.5 "Outlines visible through blocks" (an app-wide setting). */
	public static boolean outlinesThroughBlocks() {
		return AppSettingsManager.get().outlinesThroughBlocks();
	}

	public static void setOutlinesThroughBlocks(boolean value) {
		AppSettingsManager.update(settings -> settings.withOutlinesThroughBlocks(value));
	}

	public static boolean showDistance() {
		return ProfileManager.profile().settings().showDistance();
	}

	public static void setShowDistance(boolean value) {
		ProfileManager.update(profile -> profile.withSettings(profile.settings().withShowDistance(value)));
	}
}
