package audiozone.core.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audiozone.core.SoundRange;

import org.junit.jupiter.api.Test;

/** PRD FR-5.3 to FR-5.6 and section 7.2: final gain, the 2.0 cap, and range that never grows. */
class GainTest {
	@Test
	void ruleVolumeMultipliesVanillaLoudness() {
		// FR-5.3: vanilla loudness (already including Master and category sliders) times rule volume.
		assertEquals(0.4F, Gain.finalGain(0.8F, 50), 1e-6);
		assertEquals(0.8F, Gain.finalGain(0.8F, 100), 1e-6);
	}

	@Test
	void boostRaisesLoudnessAboveVanilla() {
		assertEquals(1.2F, Gain.finalGain(0.8F, 150), 1e-6);
	}

	@Test
	void finalGainIsCappedAtTwo() {
		// FR-5.6: however the multipliers combine.
		assertEquals(Gain.MAX_GAIN, Gain.finalGain(1.0F, 200), 1e-6);
		assertEquals(Gain.MAX_GAIN, Gain.finalGain(1.5F, 200), 1e-6, "even if vanilla is already above 1");
	}

	@Test
	void masterAtZeroSilencesEvenA200PercentRule() {
		assertEquals(0F, Gain.finalGain(0F, 200), 1e-9);
	}

	@Test
	void mutedOrZeroPercentIsSilent() {
		assertEquals(0F, Gain.finalGain(0.8F, 0), 1e-9);
	}

	@Test
	void badInputFailsOpenToVanilla() {
		// FR-13.2: on any error the sound plays as vanilla.
		assertEquals(0.8F, Gain.finalGain(0.8F, Float.NaN), 1e-6);
		assertEquals(0.8F, Gain.finalGain(0.8F, -5), 1e-6);
	}

	@Test
	void withoutDriverSupportBoostIsLimitedTo100Percent() {
		// 7.2 fallback: if the driver ignores the raised AL_MAX_GAIN, volumes cap at 100%.
		assertEquals(0.8F, Gain.finalGain(0.8F, 200, false), 1e-6);
		assertEquals(0.4F, Gain.finalGain(0.8F, 50, false), 1e-6);
		assertEquals(1.6F, Gain.finalGain(0.8F, 200, true), 1e-6);
	}

	@Test
	void channelMaxGainIsRaisedOnlyWhenNeeded() {
		assertEquals(1.0F, Gain.channelMaxGain(0.9F), 1e-6);
		assertEquals(Gain.MAX_GAIN, Gain.channelMaxGain(1.4F), 1e-6);
	}

	// --- Boost never increases range (FR-5.5, test 11.3 in unit form) ---

	@Test
	void loudnessFallsToZeroAtTheVanillaRangeEdgeWhateverTheBoost() {
		float range = SoundRange.vanillaRange(1.0F, 16, false, false);
		for (float percent : new float[] {50, 100, 150, 200}) {
			float gain = Gain.finalGain(1.0F, percent);
			assertEquals(0F, Gain.loudnessAt(gain, range, range), 1e-9, percent + "% at the edge");
			assertEquals(0F, Gain.loudnessAt(gain, range + 5, range), 1e-9, percent + "% beyond the edge");
		}
	}

	@Test
	void farthestAudibleDistanceMatchesVanillaWithinOneBlock() {
		// Test 11.3: villager at 100% (bypassed) vs 200%, and a loud note block (vanilla volume 3).
		for (float instanceVolume : new float[] {1.0F, 3.0F}) {
			float range = SoundRange.vanillaRange(instanceVolume, 16, false, false);
			double vanilla = farthestAudible(Gain.finalGain(1.0F, 100), range);
			double boosted = farthestAudible(Gain.finalGain(1.0F, 200), range);
			assertTrue(Math.abs(vanilla - boosted) <= 1.0, instanceVolume + ": " + vanilla + " vs " + boosted);
			assertTrue(boosted < range, "never audible at or past vanilla range");
		}
	}

	@Test
	void insideRangeTheBoostedSoundIsClearlyLouder() {
		float range = 16;
		assertTrue(Gain.loudnessAt(Gain.finalGain(1.0F, 200), 8, range) > 1.9 * Gain.loudnessAt(Gain.finalGain(1.0F, 100), 8, range));
	}

	/** Farthest whole-tenth-block distance where the sound is still above a just-audible threshold. */
	private static double farthestAudible(float gain, float range) {
		double last = 0;
		for (double d = 0; d < range + 10; d += 0.1) {
			if (Gain.loudnessAt(gain, d, range) > 0) {
				last = d;
			}
		}
		return last;
	}
}
