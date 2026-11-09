package audiozone.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-7.4: the left/right arrow works exactly like vanilla subtitles: no arrow when the source is clearly in
 * front, otherwise {@code <} or {@code >} by side. Inputs are dot products of the normalised direction to the sound
 * with the listener's right and forward vectors.
 */
class CueDirectionTest {
	@Test
	void sourceInFrontHasNoArrow() {
		assertEquals(CueDirection.NONE, CueDirection.of(0.3, 0.9));
	}

	@Test
	void sourceToTheSideOrBehindShowsWhichSide() {
		assertEquals(CueDirection.RIGHT, CueDirection.of(0.9, 0.1));
		assertEquals(CueDirection.LEFT, CueDirection.of(-0.9, 0.1));
		assertEquals(CueDirection.LEFT, CueDirection.of(-0.2, -0.9), "behind and slightly left");
	}

	@Test
	void frontThresholdIsHalf() {
		assertEquals(CueDirection.RIGHT, CueDirection.of(0.8, 0.5), "exactly 0.5 is not 'in front'");
		assertEquals(CueDirection.NONE, CueDirection.of(0.8, 0.51));
	}

	@Test
	void deadAheadOrBehindWithNoSideHasNoArrow() {
		assertEquals(CueDirection.NONE, CueDirection.of(0.0, -1.0));
	}

	@Test
	void nonPositionalSoundsHaveNoArrow() {
		assertEquals(CueDirection.NONE, CueDirection.NON_POSITIONAL);
	}
}
