package soundsculptor.core.settings;

/**
 * One app-wide setting, for its reset-to-default button (PRD FR-12.6) and the Settings page it is shown on.
 * The cue box offset is one setting with two parts (x and y), reset together.
 */
public enum AppSetting {
	/** FR-7.7 "Show muted-sound cues". */
	SHOW_CUES(SettingsPage.GENERAL),
	/** FR-7.2 "Cue sounds without subtitles". */
	CUES_WITHOUT_SUBTITLES(SettingsPage.GENERAL),
	/** FR-10.5 "Outlines visible through blocks". */
	OUTLINES_THROUGH_BLOCKS(SettingsPage.ZONES),
	/** A-7 cue text color. */
	CUE_COLOR(SettingsPage.ACCESSIBILITY),
	/** A-7 cue text scale, 100% to 200%. */
	CUE_TEXT_SCALE(SettingsPage.ACCESSIBILITY),
	/** Section 8.1: moves the cue box away from subtitle and HUD mods. */
	CUE_OFFSET(SettingsPage.ACCESSIBILITY),
	/** A-6 "Reduced motion". */
	REDUCED_MOTION(SettingsPage.ACCESSIBILITY),
	/** FR-12.2: the hotkey opens the last used tab. Remembered, not shown on a page. */
	LAST_TAB(null);

	private final SettingsPage page;

	AppSetting(SettingsPage page) {
		this.page = page;
	}

	/** The page that shows this setting, or {@code null} if it is remembered rather than shown. */
	public SettingsPage page() {
		return page;
	}
}
