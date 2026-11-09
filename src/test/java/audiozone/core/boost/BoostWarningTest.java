package audiozone.core.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-5.2 and FR-5.4: slider range, the warning above 100%, and the once-per-profile confirmation. */
class BoostWarningTest {
	@Test
	void warningShowsOnlyAbove100Percent() {
		assertFalse(BoostWarning.showWarning(100));
		assertTrue(BoostWarning.showWarning(101));
		assertTrue(BoostWarning.showWarning(200));
	}

	@Test
	void firstBoostInAProfileNeedsConfirmation() {
		assertTrue(BoostWarning.needsConfirmation(150, false));
		assertFalse(BoostWarning.needsConfirmation(150, true), "already confirmed for this profile");
		assertFalse(BoostWarning.needsConfirmation(100, false), "100% is not a boost");
	}

	@Test
	void sliderMaximumDependsOnWhetherBoostIsAvailable() {
		assertEquals(200, BoostWarning.sliderMax(true));
		assertEquals(100, BoostWarning.sliderMax(false), "MVP, or a driver that ignores the raised gain cap");
	}

	@Test
	void warningTextHasTranslationKeys() {
		assertTrue(BoostWarning.WARNING_KEY.startsWith("audiozone.boost."));
		assertTrue(BoostWarning.CONFIRM_TITLE_KEY.startsWith("audiozone.boost."));
		assertTrue(BoostWarning.CONFIRM_BODY_KEY.startsWith("audiozone.boost."));
		assertTrue(BoostWarning.UNSUPPORTED_KEY.startsWith("audiozone.boost."));
	}
}
