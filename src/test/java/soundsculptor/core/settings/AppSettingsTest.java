package soundsculptor.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.cue.CueSettings;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PRD FR-7.2, FR-7.7, A-6, A-7, 8.1, FR-10.5, FR-12.2 and FR-12.6 (reset buttons). */
class AppSettingsTest {
	private static final AppSettings D = AppSettings.DEFAULTS;

	/** Every value differs from its default. */
	private static AppSettings changed() {
		return D.withShowCues(false)
				.withCuesWithoutSubtitles(true)
				.withCueColor(0x123456)
				.withCueTextScale(150)
				.withCueOffset(-20, 30)
				.withOutlinesThroughBlocks(false)
				.withReducedMotion(true)
				.withLastTab("rules");
	}

	@Test
	void defaultsFollowThePrd() {
		assertTrue(D.showCues(), "FR-7.7: Show muted-sound cues is on by default");
		assertFalse(D.cuesWithoutSubtitles(), "FR-7.2: default off");
		assertEquals(AppSettings.DEFAULT_CUE_COLOR, D.cueColor());
		assertEquals(100, D.cueTextScale(), "A-7: 100% to 200%, starting at 100%");
		assertEquals(0, D.cueOffsetX());
		assertEquals(0, D.cueOffsetY());
		assertTrue(D.outlinesThroughBlocks(), "FR-10.5: default on");
		assertFalse(D.reducedMotion());
		assertEquals(AppSettings.DEFAULT_TAB, D.lastTab());
		assertEquals("sounds", AppSettings.DEFAULT_TAB);
	}

	@Test
	void defaultCueColorIsALightBlueGrayThatReadsOnTheDarkCueBox() {
		int rgb = AppSettings.DEFAULT_CUE_COLOR;
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		assertTrue(b > r && b >= g, "blue-ish");
		assertTrue(Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 64, "gray-ish, not saturated");
		// A-3: contrast of at least 4.5:1 against the black subtitle background.
		assertTrue((luminance(rgb) + 0.05) / 0.05 >= 4.5);
	}

	private static double luminance(int rgb) {
		double[] c = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = (rgb >> (16 - 8 * i) & 0xFF) / 255.0;
			c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	@Test
	void withersChangeOnlyTheirOwnValue() {
		AppSettings s = D.withCueTextScale(150);
		assertEquals(150, s.cueTextScale());
		assertEquals(D.withCueTextScale(100), D);
		assertEquals(D.showCues(), s.showCues());
		assertEquals(D.lastTab(), s.lastTab());
	}

	@Test
	void numbersAreClampedToTheirRange() {
		assertEquals(100, D.withCueTextScale(50).cueTextScale());
		assertEquals(200, D.withCueTextScale(250).cueTextScale());
		assertEquals(200, D.withCueTextScale(200).cueTextScale());
		AppSettings far = D.withCueOffset(-99_999, 99_999);
		assertEquals(-AppSettings.MAX_CUE_OFFSET, far.cueOffsetX());
		assertEquals(AppSettings.MAX_CUE_OFFSET, far.cueOffsetY());
		assertEquals(AppSettings.MAX_CUE_OFFSET, D.withCueOffset(AppSettings.MAX_CUE_OFFSET, 0).cueOffsetX());
	}

	@Test
	void cueColorKeepsOnlyTheRgbBits() {
		assertEquals(0x123456, D.withCueColor(0xFF123456).cueColor());
	}

	@Test
	void constructorClampsToo() {
		AppSettings s = new AppSettings(true, false, 0x1000000, 999, 5000, -5000, true, false, "recent");
		assertEquals(200, s.cueTextScale());
		assertEquals(0, s.cueColor());
		assertEquals(AppSettings.MAX_CUE_OFFSET, s.cueOffsetX());
		assertEquals(-AppSettings.MAX_CUE_OFFSET, s.cueOffsetY());
	}

	@Test
	void lastTabMustBeAPlainId() {
		assertEquals("profiles", D.withLastTab("profiles").lastTab());
		assertTrue(AppSettings.isValidTab("inspect"));
		assertTrue(AppSettings.isValidTab("tab_2"));
		for (String bad : new String[] {"", "Rules", "rules tab", "../x", "a".repeat(33)}) {
			assertFalse(AppSettings.isValidTab(bad), bad);
			assertThrows(IllegalArgumentException.class, () -> D.withLastTab(bad), bad);
		}
		assertFalse(AppSettings.isValidTab(null));
		assertThrows(NullPointerException.class, () -> D.withLastTab(null));
	}

	@Test
	void resetOneValueLeavesTheOthers() {
		AppSettings s = changed();
		for (AppSetting setting : AppSetting.values()) {
			AppSettings reset = s.reset(setting);
			assertTrue(reset.isDefault(setting), setting.name());
			for (AppSetting other : AppSetting.values()) {
				if (other != setting) {
					assertFalse(reset.isDefault(other), setting + " also reset " + other);
				}
			}
		}
	}

	@Test
	void cueOffsetIsOneValueWithTwoParts() {
		AppSettings onlyX = D.withCueOffset(5, 0);
		assertFalse(onlyX.isDefault(AppSetting.CUE_OFFSET));
		assertEquals(D, onlyX.reset(AppSetting.CUE_OFFSET));
		assertFalse(D.withCueOffset(0, 5).isDefault(AppSetting.CUE_OFFSET));
	}

	@Test
	void resetPageResetsOnlyThatPage() {
		AppSettings s = changed();
		AppSettings reset = s.resetPage(SettingsPage.ACCESSIBILITY);
		assertEquals(D.cueColor(), reset.cueColor());
		assertEquals(D.cueTextScale(), reset.cueTextScale());
		assertEquals(D.cueOffsetX(), reset.cueOffsetX());
		assertEquals(D.cueOffsetY(), reset.cueOffsetY());
		assertEquals(D.reducedMotion(), reset.reducedMotion());
		assertEquals(s.showCues(), reset.showCues());
		assertEquals(s.cuesWithoutSubtitles(), reset.cuesWithoutSubtitles());
		assertEquals(s.outlinesThroughBlocks(), reset.outlinesThroughBlocks());
		assertEquals(s.lastTab(), reset.lastTab());
		assertTrue(reset.isPageDefault(SettingsPage.ACCESSIBILITY));
		assertFalse(reset.isPageDefault(SettingsPage.GENERAL));
	}

	@Test
	void resettingEveryPageKeepsTheLastTab() {
		AppSettings s = changed();
		for (SettingsPage page : SettingsPage.values()) {
			s = s.resetPage(page);
		}
		assertEquals(D.withLastTab("rules"), s);
	}

	@Test
	void everyPageHasSettingsAndOnlyTheLastTabHasNoPage() {
		Set<SettingsPage> used = EnumSet.noneOf(SettingsPage.class);
		for (AppSetting setting : AppSetting.values()) {
			if (setting == AppSetting.LAST_TAB) {
				assertNull(setting.page());
			} else {
				used.add(setting.page());
			}
		}
		assertEquals(EnumSet.allOf(SettingsPage.class), used);
		assertEquals(SettingsPage.GENERAL, AppSetting.SHOW_CUES.page());
		assertEquals(SettingsPage.ZONES, AppSetting.OUTLINES_THROUGH_BLOCKS.page());
		assertEquals(SettingsPage.ACCESSIBILITY, AppSetting.REDUCED_MOTION.page());
	}

	@Test
	void cueSettingsCombineTheseWithTheProfilesCueDefault() {
		AppSettings s = D.withShowCues(false).withCuesWithoutSubtitles(true);
		assertEquals(new CueSettings(false, true, true), s.cueSettings(true));
		assertEquals(new CueSettings(true, false, false), D.cueSettings(false));
		assertEquals(CueSettings.DEFAULT, D.cueSettings(true));
	}
}
