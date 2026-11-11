package soundsculptor.core.settings;

import soundsculptor.core.cue.CueSettings;
import java.util.Objects;

/**
 * App-wide settings: the same on every server and world (PRD FR-11: "Keybinds and UI preferences are app-wide, not per
 * profile"). Profile settings (cue default for new mute rules, outline mode, default edge, show distance) live in the
 * profile file instead.
 *
 * <p>Values are always in range: numbers are clamped and the color keeps only its RGB bits.
 *
 * @param showCues              FR-7.7 master switch "Show muted-sound cues"; independent of vanilla Subtitles
 * @param cuesWithoutSubtitles  FR-7.2 show the sound ID for sounds that have no subtitle
 * @param cueColor              A-7 cue text color, {@code 0xRRGGBB}
 * @param cueTextScale          A-7 cue text scale in percent, {@value #MIN_CUE_SCALE} to {@value #MAX_CUE_SCALE}
 * @param cueOffsetX            section 8.1 cue box offset in GUI pixels, positive is right
 * @param cueOffsetY            section 8.1 cue box offset in GUI pixels, positive is down
 * @param outlinesThroughBlocks FR-10.5 "Outlines visible through blocks"
 * @param reducedMotion         A-6 no list animations and no toast slide-in
 * @param lastTab               FR-12.2 the tab the hotkey opens, e.g. "recent"; see {@link #isValidTab}
 */
public record AppSettings(boolean showCues, boolean cuesWithoutSubtitles, int cueColor, int cueTextScale, int cueOffsetX,
		int cueOffsetY, boolean outlinesThroughBlocks, boolean reducedMotion, String lastTab) {
	/** FR-7.3 "light blue-gray"; 10:1 contrast on black. */
	public static final int DEFAULT_CUE_COLOR = 0xA9BCD0;
	public static final int MIN_CUE_SCALE = 100;
	public static final int MAX_CUE_SCALE = 200;
	/** The offset can move the cue box this far in each direction; the renderer still keeps it on screen. */
	public static final int MAX_CUE_OFFSET = 1000;
	public static final String DEFAULT_TAB = "recent";
	static final int MAX_TAB_LENGTH = 32;

	public static final AppSettings DEFAULTS = new AppSettings(true, false, DEFAULT_CUE_COLOR, 100, 0, 0, true, false,
			DEFAULT_TAB);

	public AppSettings {
		cueColor &= 0xFFFFFF;
		cueTextScale = clamp(cueTextScale, MIN_CUE_SCALE, MAX_CUE_SCALE);
		cueOffsetX = clamp(cueOffsetX, -MAX_CUE_OFFSET, MAX_CUE_OFFSET);
		cueOffsetY = clamp(cueOffsetY, -MAX_CUE_OFFSET, MAX_CUE_OFFSET);
		Objects.requireNonNull(lastTab, "lastTab");
		if (!isValidTab(lastTab)) {
			throw new IllegalArgumentException("Not a tab name: " + lastTab);
		}
	}

	static int clamp(long value, int min, int max) {
		return (int) Math.max(min, Math.min(max, value));
	}

	/** A tab name is 1 to 32 characters of a-z, 0-9 and _. */
	public static boolean isValidTab(String tab) {
		if (tab == null || tab.isEmpty() || tab.length() > MAX_TAB_LENGTH) {
			return false;
		}
		for (int i = 0; i < tab.length(); i++) {
			char c = tab.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
				return false;
			}
		}
		return true;
	}

	public AppSettings withShowCues(boolean value) {
		return new AppSettings(value, cuesWithoutSubtitles, cueColor, cueTextScale, cueOffsetX, cueOffsetY,
				outlinesThroughBlocks, reducedMotion, lastTab);
	}

	public AppSettings withCuesWithoutSubtitles(boolean value) {
		return new AppSettings(showCues, value, cueColor, cueTextScale, cueOffsetX, cueOffsetY, outlinesThroughBlocks,
				reducedMotion, lastTab);
	}

	public AppSettings withCueColor(int rgb) {
		return new AppSettings(showCues, cuesWithoutSubtitles, rgb, cueTextScale, cueOffsetX, cueOffsetY,
				outlinesThroughBlocks, reducedMotion, lastTab);
	}

	/** Clamped to {@value #MIN_CUE_SCALE}..{@value #MAX_CUE_SCALE}. */
	public AppSettings withCueTextScale(int percent) {
		return new AppSettings(showCues, cuesWithoutSubtitles, cueColor, percent, cueOffsetX, cueOffsetY,
				outlinesThroughBlocks, reducedMotion, lastTab);
	}

	/** Each part is clamped to ±{@value #MAX_CUE_OFFSET}. */
	public AppSettings withCueOffset(int x, int y) {
		return new AppSettings(showCues, cuesWithoutSubtitles, cueColor, cueTextScale, x, y, outlinesThroughBlocks,
				reducedMotion, lastTab);
	}

	public AppSettings withOutlinesThroughBlocks(boolean value) {
		return new AppSettings(showCues, cuesWithoutSubtitles, cueColor, cueTextScale, cueOffsetX, cueOffsetY, value,
				reducedMotion, lastTab);
	}

	public AppSettings withReducedMotion(boolean value) {
		return new AppSettings(showCues, cuesWithoutSubtitles, cueColor, cueTextScale, cueOffsetX, cueOffsetY,
				outlinesThroughBlocks, value, lastTab);
	}

	/** @throws IllegalArgumentException if {@code tab} is not a {@linkplain #isValidTab tab name} */
	public AppSettings withLastTab(String tab) {
		return new AppSettings(showCues, cuesWithoutSubtitles, cueColor, cueTextScale, cueOffsetX, cueOffsetY,
				outlinesThroughBlocks, reducedMotion, tab);
	}

	/** Whether {@code setting} has its default value; the reset button is disabled then (FR-12.6). */
	public boolean isDefault(AppSetting setting) {
		return equals(reset(setting));
	}

	/** Whether every setting on {@code page} has its default value; "Reset page" is disabled then. */
	public boolean isPageDefault(SettingsPage page) {
		return equals(resetPage(page));
	}

	/** {@code setting} back to its default; every other value is kept (FR-12.6). */
	public AppSettings reset(AppSetting setting) {
		AppSettings d = DEFAULTS;
		return switch (setting) {
			case SHOW_CUES -> withShowCues(d.showCues);
			case CUES_WITHOUT_SUBTITLES -> withCuesWithoutSubtitles(d.cuesWithoutSubtitles);
			case OUTLINES_THROUGH_BLOCKS -> withOutlinesThroughBlocks(d.outlinesThroughBlocks);
			case CUE_COLOR -> withCueColor(d.cueColor);
			case CUE_TEXT_SCALE -> withCueTextScale(d.cueTextScale);
			case CUE_OFFSET -> withCueOffset(d.cueOffsetX, d.cueOffsetY);
			case REDUCED_MOTION -> withReducedMotion(d.reducedMotion);
			case LAST_TAB -> withLastTab(d.lastTab);
		};
	}

	/** Every setting on {@code page} back to its default ("Reset page", FR-12.6). The last used tab is never reset. */
	public AppSettings resetPage(SettingsPage page) {
		AppSettings result = this;
		for (AppSetting setting : AppSetting.values()) {
			if (setting.page() == page) {
				result = result.reset(setting);
			}
		}
		return result;
	}

	/** The cue settings the cue logic reads, with the profile's Cue default for new mute rules (FR-7.6). */
	public CueSettings cueSettings(boolean newMuteRulesCueOn) {
		return new CueSettings(showCues, cuesWithoutSubtitles, newMuteRulesCueOn);
	}
}
