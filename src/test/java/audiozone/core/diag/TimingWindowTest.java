package audiozone.core.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** PRD FR-13.3 and 9.1: live per-second timing readouts. */
class TimingWindowTest {
	private final TimingWindow window = new TimingWindow();

	@Test
	void reportsTheLastCompleteSecond() {
		window.add(2_000, 1_000); // 2 µs
		window.add(4_000, 1_400); // 4 µs
		window.add(9_000, 1_900); // 9 µs
		TimingWindow.Stats stats = window.stats(2_100);
		assertEquals(3, stats.count());
		assertEquals(5.0, stats.averageMicros(), 1e-9);
		assertEquals(9.0, stats.maxMicros(), 1e-9);
	}

	@Test
	void theCurrentSecondIsNotReportedUntilItEnds() {
		window.add(5_000, 1_000);
		assertEquals(0, window.stats(1_500).count());
		assertEquals(1, window.stats(2_000).count());
	}

	@Test
	void aQuietSecondReportsZero() {
		window.add(5_000, 1_000);
		assertEquals(1, window.stats(2_500).count());
		TimingWindow.Stats stats = window.stats(3_500);
		assertEquals(0, stats.count());
		assertEquals(0.0, stats.averageMicros(), 1e-9);
		assertEquals(0.0, stats.maxMicros(), 1e-9);
	}

	@Test
	void secondsRollOverWhenSamplesArrive() {
		window.add(1_000, 1_000);
		window.add(3_000, 2_000);
		window.add(5_000, 2_500);
		TimingWindow.Stats stats = window.stats(3_000);
		assertEquals(2, stats.count());
		assertEquals(4.0, stats.averageMicros(), 1e-9);
	}

	@Test
	void peakMaxIsKeptAcrossSecondsUntilReset() {
		window.add(80_000, 1_000);
		window.add(1_000, 2_000);
		window.stats(3_000);
		assertEquals(80.0, window.peakMaxMicros(), 1e-9);
		window.resetPeak();
		assertEquals(0.0, window.peakMaxMicros(), 1e-9);
	}
}
