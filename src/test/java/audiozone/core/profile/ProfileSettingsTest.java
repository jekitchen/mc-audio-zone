package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import audiozone.core.outline.OutlineMode;
import audiozone.core.zones.ZoneEdge;

import org.junit.jupiter.api.Test;

/** PRD FR-11: profile settings (cue default, outline mode, default edge, show distance); FR-12.6 reset to default. */
class ProfileSettingsTest {
	private static final ProfileSettings CHANGED = ProfileSettings.DEFAULTS.withShowDistance(false).withCueDefault(false)
			.withOutlines(OutlineMode.ALWAYS).withDefaultEdge(ZoneEdge.fade(8));

	@Test
	void defaultsMatchThePrd() {
		ProfileSettings d = ProfileSettings.DEFAULTS;
		assertTrue(d.showDistance(), "FR-1.4: distance band on");
		assertTrue(d.cueDefault(), "FR-7.6: new mute rules have Cue on");
		assertEquals(OutlineMode.EDITING, d.outlines(), "FR-10.1: only while editing");
		assertEquals(ZoneEdge.hard(), d.defaultEdge(), "FR-8: hard edge");
	}

	@Test
	void theOldOneValueConstructorKeepsTheOtherDefaults() {
		ProfileSettings s = new ProfileSettings(false);
		assertFalse(s.showDistance());
		assertEquals(ProfileSettings.DEFAULTS.withShowDistance(false), s);
	}

	@Test
	void withersChangeOneValue() {
		assertEquals(new ProfileSettings(false, false, OutlineMode.ALWAYS, ZoneEdge.fade(8)), CHANGED);
	}

	@Test
	void theProfileEdgeMustBeHardOrFade() {
		assertThrows(IllegalArgumentException.class, () -> ProfileSettings.DEFAULTS.withDefaultEdge(ZoneEdge.DEFAULT),
				"\"use the profile default\" cannot be the profile default");
		assertThrows(NullPointerException.class, () -> ProfileSettings.DEFAULTS.withOutlines(null));
	}

	@Test
	void changedListsOnlyValuesThatDifferFromTheDefault() {
		assertEquals(Set.of(), ProfileSettings.DEFAULTS.changed());
		assertEquals(EnumSet.allOf(ProfileSetting.class), CHANGED.changed());
		assertEquals(EnumSet.of(ProfileSetting.OUTLINES), ProfileSettings.DEFAULTS.withOutlines(OutlineMode.NEVER).changed());
		assertEquals(EnumSet.of(ProfileSetting.DEFAULT_EDGE),
				ProfileSettings.DEFAULTS.withDefaultEdge(ZoneEdge.fade(ZoneEdge.DEFAULT_FADE)).changed(),
				"Fade at 4 blocks is not the default Hard edge");
	}

	@Test
	void isDefaultMatchesChanged() {
		for (ProfileSetting setting : ProfileSetting.values()) {
			assertTrue(ProfileSettings.DEFAULTS.isDefault(setting));
			assertFalse(CHANGED.isDefault(setting), setting.name());
		}
	}

	@Test
	void resetPutsBackOneValueAndLeavesTheOthers() {
		assertEquals(CHANGED.withShowDistance(true), CHANGED.reset(ProfileSetting.SHOW_DISTANCE));
		assertEquals(CHANGED.withCueDefault(true), CHANGED.reset(ProfileSetting.CUE_DEFAULT));
		assertEquals(CHANGED.withOutlines(OutlineMode.EDITING), CHANGED.reset(ProfileSetting.OUTLINES));
		assertEquals(CHANGED.withDefaultEdge(ZoneEdge.hard()), CHANGED.reset(ProfileSetting.DEFAULT_EDGE));
	}

	@Test
	void resetPageResetsEverySetting() {
		assertEquals(ProfileSettings.DEFAULTS, CHANGED.resetPage());
	}
}
