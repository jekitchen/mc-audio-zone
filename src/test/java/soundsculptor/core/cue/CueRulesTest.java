package soundsculptor.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** PRD FR-7.1, FR-7.2 and FR-7.7: when a muted sound gets a cue, and what it says. */
class CueRulesTest {
	private static final CueSettings DEFAULTS = CueSettings.DEFAULT;
	private static final String ID = "minecraft:entity.generic.explode";

	/** A sound Sound Sculptor muted, whose deciding rule has Cue on, within vanilla subtitle range. */
	private static String cue(float volume, boolean byRule, boolean cueOn, boolean audible, String subtitle, CueSettings s) {
		return CueRules.cueText(ID, subtitle, volume, byRule, cueOn, audible, s);
	}

	@Test
	void mutedSoundWithCueOnShowsItsSubtitle() {
		assertEquals("Explosion", cue(0, true, true, true, "Explosion", DEFAULTS));
	}

	@Test
	void soundThatStillPlaysGetsNoCue() {
		assertNull(cue(0.4F, true, true, true, "Explosion", DEFAULTS));
	}

	@Test
	void ruleWithCueOffGetsNoCue() {
		assertNull(cue(0, true, false, true, "Explosion", DEFAULTS));
	}

	@Test
	void silenceNotCausedBySoundSculptorGetsNoCue() {
		// e.g. the player's own category slider at 0%: vanilla's business, not a muted-sound cue.
		assertNull(cue(0, false, true, true, "Explosion", DEFAULTS));
	}

	@Test
	void soundOutOfVanillaSubtitleRangeGetsNoCue() {
		// Fair play: a cue shows only what the vanilla subtitle would have shown.
		assertNull(cue(0, true, true, false, "Explosion", DEFAULTS));
	}

	@Test
	void masterSwitchOffHidesEveryCue() {
		CueSettings off = new CueSettings(false, false, true);
		assertNull(cue(0, true, true, true, "Explosion", off));
	}

	@Test
	void soundWithoutSubtitleShowsNothingByDefault() {
		assertNull(cue(0, true, true, true, null, DEFAULTS));
	}

	@Test
	void settingShowsTheIdForSoundsWithoutSubtitles() {
		CueSettings ids = new CueSettings(true, true, true);
		assertEquals(ID, cue(0, true, true, true, null, ids));
		assertEquals("Explosion", cue(0, true, true, true, "Explosion", ids), "a subtitle still wins");
	}

	@Test
	void defaultsMatchThePrd() {
		assertEquals(true, DEFAULTS.showCues(), "FR-7.7: master switch on by default");
		assertEquals(false, DEFAULTS.cueSoundsWithoutSubtitles(), "FR-7.2: off by default");
		assertEquals(true, DEFAULTS.newMuteRulesCueOn(), "FR-7.6: new mute rules have Cue on");
	}
	@Test
	void shouldCueAgreesWithCueText() {
		CueSettings[] all = {DEFAULTS, new CueSettings(true, true, true), new CueSettings(false, true, true)};
		for (CueSettings settings : all) {
			for (int bits = 0; bits < 32; bits++) {
				float volume = (bits & 1) == 0 ? 0 : 40;
				boolean byRule = (bits & 2) != 0;
				boolean cueOn = (bits & 4) != 0;
				boolean audible = (bits & 8) != 0;
				String subtitle = (bits & 16) != 0 ? "Explosion" : null;
				assertEquals(CueRules.cueText(ID, subtitle, volume, byRule, cueOn, audible, settings) != null,
						CueRules.shouldCue(subtitle != null, volume, byRule, cueOn, audible, settings), settings + " bits " + bits);
			}
		}
	}
}
