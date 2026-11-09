package audiozone.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-7.4 and FR-7.5: cue lines merge, are capped at 5, and fade after 3 seconds. */
class CueBoxTest {
	private final CueBox box = new CueBox();

	private List<CueLine> lines(long nowMs) {
		return box.visibleLines(nowMs);
	}

	@Test
	void newCueAppearsAsOneLine() {
		box.add("Explosion", CueDirection.LEFT, 1000);
		List<CueLine> lines = lines(1000);
		assertEquals(1, lines.size());
		assertEquals("Explosion", lines.get(0).text());
		assertEquals(1, lines.get(0).count());
		assertEquals(CueDirection.LEFT, lines.get(0).direction());
	}

	@Test
	void repeatsMergeIntoOneLineWithACount() {
		for (int i = 0; i < 4; i++) {
			box.add("Villager mumbles", CueDirection.NONE, 1000 + i * 100);
		}
		List<CueLine> lines = lines(1400);
		assertEquals(1, lines.size());
		assertEquals(4, lines.get(0).count());
	}

	@Test
	void aRepeatRefreshesTheLineAndTakesTheNewDirection() {
		box.add("Villager mumbles", CueDirection.LEFT, 1000);
		box.add("Villager mumbles", CueDirection.RIGHT, 3500);
		CueLine line = lines(3600).get(0);
		assertEquals(CueDirection.RIGHT, line.direction());
		assertEquals(100, line.ageMs(), "age counts from the latest repeat");
	}

	@Test
	void linesFadeAfterThreeSeconds() {
		box.add("Explosion", CueDirection.NONE, 1000);
		assertEquals(1, lines(1000 + CueBox.DISPLAY_MS - 1).size());
		assertTrue(lines(1000 + CueBox.DISPLAY_MS).isEmpty());
	}

	@Test
	void countRestartsAfterALineHasFaded() {
		box.add("Explosion", CueDirection.NONE, 1000);
		box.add("Explosion", CueDirection.NONE, 1000 + CueBox.DISPLAY_MS + 10);
		assertEquals(1, lines(1000 + CueBox.DISPLAY_MS + 20).get(0).count());
	}

	@Test
	void atMostFiveLinesAndTheOldestGoesFirst() {
		for (int i = 0; i < 7; i++) {
			box.add("Sound " + i, CueDirection.NONE, 1000 + i);
		}
		List<CueLine> lines = lines(1010);
		assertEquals(CueBox.MAX_LINES, lines.size());
		assertEquals(List.of("Sound 6", "Sound 5", "Sound 4", "Sound 3", "Sound 2"), lines.stream().map(CueLine::text).toList());
	}

	@Test
	void tenMutedVillagersAtOnceStayOnOneCountedLine() {
		// FR-7 acceptance check: no more than 5 lines, with counts.
		for (int i = 0; i < 10; i++) {
			box.add("Villager mumbles", CueDirection.NONE, 1000);
		}
		box.add("Villager hurts", CueDirection.NONE, 1000);
		List<CueLine> lines = lines(1000);
		assertEquals(2, lines.size());
		assertEquals(10, lines.stream().filter(l -> l.text().equals("Villager mumbles")).findFirst().orElseThrow().count());
	}

	@Test
	void newestLineIsFirst() {
		box.add("A", CueDirection.NONE, 1000);
		box.add("B", CueDirection.NONE, 1100);
		box.add("A", CueDirection.NONE, 1200);
		assertEquals(List.of("A", "B"), lines(1300).stream().map(CueLine::text).toList());
	}

	@Test
	void fadeFractionGoesFromZeroToOne() {
		box.add("Explosion", CueDirection.NONE, 1000);
		assertEquals(0.0, lines(1000).get(0).fadeFraction(), 1e-9);
		assertEquals(0.5, lines(1000 + CueBox.DISPLAY_MS / 2).get(0).fadeFraction(), 1e-9);
	}

	@Test
	void addingCuesAllocatesNothing() {
		// A muted farm can fire hundreds of cues a second (PRD 9.1: no allocation on the hot path).
		com.sun.management.ThreadMXBean threads =
				(com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		String[] texts = {"Villager mumbles", "Villager hurts", "Chicken clucks", "Cow moos", "Explosion", "Glass breaks", "Hmm"};
		for (int i = 0; i < 200_000; i++) {
			box.add(texts[i % texts.length], CueDirection.NONE, i);
		}
		long before = threads.getCurrentThreadAllocatedBytes();
		for (int i = 0; i < 100_000; i++) {
			box.add(texts[i % texts.length], CueDirection.LEFT, 200_000 + i);
		}
		assertEquals(0, threads.getCurrentThreadAllocatedBytes() - before);
	}

	@Test
	void clearRemovesEverything() {
		box.add("Explosion", CueDirection.NONE, 1000);
		box.clear();
		assertTrue(lines(1000).isEmpty());
	}
}
