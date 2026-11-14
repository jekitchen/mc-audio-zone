package soundsculptor.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RowFlowTest {
	@Test
	void controlsThatFitShareOneLine() {
		assertEquals(List.of(new RowFlow.Spot(0, 0), new RowFlow.Spot(0, 54), new RowFlow.Spot(0, 108)),
				RowFlow.place(new int[] {50, 50, 50}, 200, 4));
	}

	@Test
	void exactFitStaysOnTheLine() {
		// 100 + 4 + 96 = 200: the last control ends at the edge.
		assertEquals(List.of(new RowFlow.Spot(0, 0), new RowFlow.Spot(0, 104)), RowFlow.place(new int[] {100, 96}, 200, 4));
	}

	@Test
	void aControlThatDoesNotFitStartsTheNextLine() {
		// The toolbar wraps: Apply to and Pause on the first line, Sort on the second.
		assertEquals(List.of(new RowFlow.Spot(0, 0), new RowFlow.Spot(0, 198), new RowFlow.Spot(1, 0)),
				RowFlow.place(new int[] {194, 95, 161}, 415, 4));
	}

	@Test
	void laterControlsContinueOnTheNewLine() {
		assertEquals(List.of(new RowFlow.Spot(0, 0), new RowFlow.Spot(1, 0), new RowFlow.Spot(1, 64), new RowFlow.Spot(2, 0)),
				RowFlow.place(new int[] {90, 60, 30, 90}, 100, 4));
	}

	@Test
	void aControlWiderThanTheLineGetsALineOfItsOwn() {
		assertEquals(List.of(new RowFlow.Spot(0, 0), new RowFlow.Spot(1, 0), new RowFlow.Spot(2, 0)),
				RowFlow.place(new int[] {20, 300, 20}, 100, 4));
	}

	@Test
	void noControlsNoSpots() {
		assertEquals(List.of(), RowFlow.place(new int[0], 100, 4));
	}

	@Test
	void lineCountIsOneMoreThanTheLastLine() {
		assertEquals(0, RowFlow.lines(List.of()));
		assertEquals(2, RowFlow.lines(RowFlow.place(new int[] {90, 60}, 100, 4)));
	}

	@Test
	void negativeWidthsAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> RowFlow.place(new int[] {-1}, 100, 4));
	}
}
