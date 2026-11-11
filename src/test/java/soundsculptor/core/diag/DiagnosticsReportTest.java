package soundsculptor.core.diag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-13.3: "Copy report" text for bug reports. */
class DiagnosticsReportTest {
	@Test
	void reportNamesVersionsCountsAndTimings() {
		DiagnosticsReport.Data data = new DiagnosticsReport.Data("0.1.0-alpha", "26.3", false, 12, 3, 2,
				new TimingWindow.Stats(310, 2.4, 41.0), 55.5,
				new TimingWindow.Stats(20, 80.0, 150.0),
				new TimingWindow.Stats(60, 120.0, 260.0), 0.987, 4);
		String text = DiagnosticsReport.format(data);
		assertTrue(text.contains("Sound Sculptor 0.1.0-alpha"), text);
		assertTrue(text.contains("Minecraft 26.3"), text);
		assertTrue(text.contains("Bypass: off"), text);
		assertTrue(text.contains("Global rules: 12"), text);
		assertTrue(text.contains("Zones: 3 (2 active here)"), text);
		assertTrue(text.contains("Sounds per second: 310"), text);
		assertTrue(text.contains("Per sound: avg 2.4 µs, max 41.0 µs (peak 55.5 µs)"), text);
		assertTrue(text.contains("Per tick re-check: avg 80.0 µs, max 150.0 µs"), text);
		assertTrue(text.contains("Outlines per frame: avg 120.0 µs, max 260.0 µs"), text);
		assertTrue(text.contains("Pattern cache hit rate: 98.7%"), text);
		assertTrue(text.contains("Errors this session: 4"), text);
	}

	@Test
	void bypassAndUnknownHitRateAreShownPlainly() {
		DiagnosticsReport.Data data = new DiagnosticsReport.Data("0.1.0", "26.2", true, 0, 0, 0,
				TimingWindow.Stats.EMPTY, 0, TimingWindow.Stats.EMPTY, TimingWindow.Stats.EMPTY, Double.NaN, 0);
		String text = DiagnosticsReport.format(data);
		assertTrue(text.contains("Bypass: on"), text);
		assertTrue(text.contains("Pattern cache hit rate: n/a"), text);
	}

	@Test
	void memoryIsShownAgainstTheBudget() {
		DiagnosticsReport.Data data = new DiagnosticsReport.Data("0.1.0", "26.3", false, 0, 0, 0,
				TimingWindow.Stats.EMPTY, 0, TimingWindow.Stats.EMPTY, TimingWindow.Stats.EMPTY, Double.NaN, 0,
				3 * 512 * 1024);
		assertTrue(DiagnosticsReport.format(data).contains("Memory (estimate): 1.5 MB of 8.0 MB"),
				DiagnosticsReport.format(data));
	}

	@Test
	void memoryIsNotApplicableWhenNotMeasured() {
		DiagnosticsReport.Data data = new DiagnosticsReport.Data("0.1.0", "26.3", false, 0, 0, 0,
				TimingWindow.Stats.EMPTY, 0, TimingWindow.Stats.EMPTY, TimingWindow.Stats.EMPTY, Double.NaN, 0);
		assertTrue(DiagnosticsReport.format(data).contains("Memory (estimate): n/a"), DiagnosticsReport.format(data));
	}
}
