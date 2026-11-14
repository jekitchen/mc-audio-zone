package soundsculptor.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WrappedLinesTest {
	@Test
	void eachReservedLineShowsItsLine() {
		List<String> lines = List.of("Zones change sounds inside them.", "Overlaps: the closest center wins.");
		assertEquals("Zones change sounds inside them.", WrappedLines.line(lines, 2, 0));
		assertEquals("Overlaps: the closest center wins.", WrappedLines.line(lines, 2, 1));
	}

	@Test
	void fewerLinesThanReservedLeaveTheRestEmpty() {
		// A live number got shorter: the text now fits on one line.
		assertEquals("Sounds: 3", WrappedLines.line(List.of("Sounds: 3"), 2, 0));
		assertEquals("", WrappedLines.line(List.of("Sounds: 3"), 2, 1));
		assertEquals("", WrappedLines.line(List.of(), 1, 0));
	}

	@Test
	void moreLinesThanReservedJoinTheLastLine() {
		// A live number got longer: the overflow goes on the last line, which is then cut with "…" when drawn.
		List<String> lines = List.of("Time per sound:", "average 12.5 µs,", "max 40.1 µs");
		assertEquals("Time per sound:", WrappedLines.line(lines, 2, 0));
		assertEquals("average 12.5 µs, max 40.1 µs", WrappedLines.line(lines, 2, 1));
	}

	@Test
	void countIsAtLeastOne() {
		assertEquals(1, WrappedLines.count(List.of()));
		assertEquals(3, WrappedLines.count(List.of("a", "b", "c")));
	}
}
