package soundsculptor.core.recent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-1.4: distance is a coarse band, never a number. */
class DistanceBandTest {
	@Test
	void fourBlocksOrLessIsClose() {
		assertEquals(DistanceBand.CLOSE, DistanceBand.of(true, 0.0));
		assertEquals(DistanceBand.CLOSE, DistanceBand.of(true, 4.0));
	}

	@Test
	void overFourUpToSixteenIsNear() {
		assertEquals(DistanceBand.NEAR, DistanceBand.of(true, 4.01));
		assertEquals(DistanceBand.NEAR, DistanceBand.of(true, 16.0));
	}

	@Test
	void overSixteenIsFar() {
		assertEquals(DistanceBand.FAR, DistanceBand.of(true, 16.01));
		assertEquals(DistanceBand.FAR, DistanceBand.of(true, 48.0));
	}

	@Test
	void nonPositionalSoundsAreAroundYou() {
		assertEquals(DistanceBand.AROUND_YOU, DistanceBand.of(false, 0.0));
		assertEquals(DistanceBand.AROUND_YOU, DistanceBand.of(false, 30.0));
	}

	@Test
	void everyBandAndStatusHasATranslationKey() {
		for (DistanceBand band : DistanceBand.values()) {
			assertTrue(band.translationKey().startsWith("soundsculptor.sounds.distance."), band.name());
		}
		for (PlayStatus status : PlayStatus.values()) {
			assertTrue(status.translationKey().startsWith("soundsculptor.sounds.status."), status.name());
		}
	}
}
