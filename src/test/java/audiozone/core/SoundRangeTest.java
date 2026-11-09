package audiozone.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundRangeTest {
	@Test
	void normalSoundUsesAttenuationDistance() {
		assertEquals(16.0F, SoundRange.vanillaRange(1.0F, 16, false, false));
	}

	@Test
	void quietSoundsStillReachTheFullDistance() {
		// Vanilla uses max(volume, 1): turning a sound down never shrinks its range.
		assertEquals(16.0F, SoundRange.vanillaRange(0.2F, 16, false, false));
		assertEquals(16.0F, SoundRange.vanillaRange(0.0F, 16, false, false));
	}

	@Test
	void loudInstancesReachFurther() {
		assertEquals(64.0F, SoundRange.vanillaRange(4.0F, 16, false, false));
	}

	@Test
	void customAttenuationDistanceIsUsed() {
		assertEquals(48.0F, SoundRange.vanillaRange(1.0F, 48, false, false));
	}

	@Test
	void relativeAndNonAttenuatedSoundsHaveInfiniteRange() {
		assertEquals(Float.POSITIVE_INFINITY, SoundRange.vanillaRange(1.0F, 16, true, false));
		assertEquals(Float.POSITIVE_INFINITY, SoundRange.vanillaRange(1.0F, 16, false, true));
	}

	@Test
	void audibleOnlyWhenStrictlyCloserThanRange() {
		assertTrue(SoundRange.isAudible(16.0F, 15.99 * 15.99));
		assertFalse(SoundRange.isAudible(16.0F, 16.0 * 16.0), "exactly at the range is not heard (Vec3.closerThan is strict)");
		assertFalse(SoundRange.isAudible(16.0F, 40.0 * 40.0));
	}

	@Test
	void infiniteRangeIsAlwaysAudible() {
		assertTrue(SoundRange.isAudible(Float.POSITIVE_INFINITY, 1.0e12));
	}
}
