package audiozone.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SliderModelTest {
	/** Volume slider as in FR-5: 0–200%, step 5, fine step 1, warning above 100. */
	private static SliderModel volume(double initial) {
		return new SliderModel(0, 200, 5, 1, 100, initial);
	}

	@Test
	void arrowsMoveByStepAndShiftByFineStep() {
		SliderModel m = volume(50);
		m.stepBy(1, false);
		assertEquals(55, m.value());
		m.stepBy(-1, false);
		m.stepBy(-1, false);
		assertEquals(45, m.value());
		m.stepBy(1, true);
		assertEquals(46, m.value());
	}

	@Test
	void stepFromOffGridValueLandsOnNextGridPoint() {
		SliderModel m = volume(47);
		m.stepBy(1, false);
		assertEquals(50, m.value());
		m.set(47);
		m.stepBy(-1, false);
		assertEquals(45, m.value());
	}

	@Test
	void clampsAtTheEnds() {
		SliderModel m = volume(198);
		m.stepBy(1, false);
		assertEquals(200, m.value());
		m.stepBy(1, false);
		assertEquals(200, m.value());
		m.set(-30);
		assertEquals(0, m.value());
	}

	@Test
	void homeAndEnd() {
		SliderModel m = volume(70);
		m.toMax();
		assertEquals(200, m.value());
		m.toMin();
		assertEquals(0, m.value());
	}

	@Test
	void typedValuesSnapToFineStep() {
		SliderModel m = volume(0);
		m.set(73.4);
		assertEquals(73, m.value());
		m.set(73.6);
		assertEquals(74, m.value());
	}

	@Test
	void mouseSnapsToStep() {
		SliderModel m = volume(0);
		m.setFraction(0.371); // 74.2 → 75
		assertEquals(75, m.value());
		assertEquals(0.375, m.fraction(), 1e-9);
		m.setFraction(1.4);
		assertEquals(200, m.value());
	}

	@Test
	void warningZoneIsStrictlyAboveThreshold() {
		SliderModel m = volume(100);
		assertFalse(m.isWarning());
		m.stepBy(1, true);
		assertTrue(m.isWarning());
		assertFalse(new SliderModel(0, 10, 1, 1, Double.NaN, 10).isWarning());
	}

	@Test
	void fractionalStepsHaveNoFloatingPointNoise() {
		SliderModel m = new SliderModel(0, 1, 0.1, 0.1, Double.NaN, 0);
		m.stepBy(1, false);
		m.stepBy(1, false);
		m.stepBy(1, false);
		assertEquals(0.3, m.value());
	}

	@Test
	void nanIsIgnored() {
		SliderModel m = volume(40);
		m.set(Double.NaN);
		assertEquals(40, m.value());
	}

	@Test
	void rejectsBadConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> new SliderModel(10, 0, 1, 1, Double.NaN, 0));
		assertThrows(IllegalArgumentException.class, () -> new SliderModel(0, 10, 1, 2, Double.NaN, 0));
	}
}
