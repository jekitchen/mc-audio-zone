package soundsculptor.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD FR-14.2 with v1 zones: the trace shows fade weights, Outside zones and bubbles exactly as decided. */
class SoundTracerFullZonesTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String COW = "minecraft:entity.cow.ambient";

	private static Zone sphere(String id, double x, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), List.of(rules));
	}

	private static LastPlay play(double x, double px) {
		return new LastPlay(true, WORLD, OVERWORLD, x, 0, 0, px, 0, 0, 0, false, false);
	}

	private static SoundTrace trace(Precedence p, LastPlay play) {
		return SoundTracer.trace(p, COW, play, false, false);
	}

	@Test
	void fadeZoneShowsItsWeight() {
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*cow*").withMute(true)).withEdge(ZoneEdge.fade(4));
		SoundTrace t = trace(new Precedence(List.of(), List.of(a)), play(8, Double.NaN));
		assertEquals(0.5, t.zones().get(0).weight(), 1e-9);
		assertEquals(50, t.volumePercent(), 1e-4);
	}

	@Test
	void zoneAtZeroWeightIsNotListed() {
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*cow*").withMute(true)).withEdge(ZoneEdge.fade(4));
		assertTrue(trace(new Precedence(List.of(), List.of(a)), play(10, Double.NaN)).zones().isEmpty(), "w = 0 at the boundary");
	}

	@Test
	void outsideZoneIsListedForSoundsOutsideIt() {
		Zone o = sphere("o", 0, 10, SetEntry.of("all", "*").withMute(true)).withMode(ZoneMode.OUTSIDE);
		Precedence p = new Precedence(List.of(), List.of(o));
		assertEquals(1, trace(p, play(30, Double.NaN)).zones().size());
		assertTrue(trace(p, play(5, Double.NaN)).zones().isEmpty());
	}

	@Test
	void bubbleIsMeasuredFromThePlayer() {
		Zone bubble = sphere("bubble", 0, 8, SetEntry.of("all", "*").withVolume(30)).asBubble();
		SoundTrace t = trace(new Precedence(List.of(), List.of(bubble)), play(105, 100));
		assertEquals(1, t.zones().size());
		assertEquals(5, t.zones().get(0).distance(), 1e-9);
		assertEquals(30, t.volumePercent(), 1e-4);
	}

	@Test
	void lowWeightFadeZoneDoesNotWinLimitOrCue() {
		// Below w = 0.5 a fade zone's limit and cue do not apply (FR-9 step 6), so the trace must not name it.
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*cow*").withLimit(new Limit(2, 1000, false)).withCue(true))
				.withEdge(ZoneEdge.fade(4));
		Precedence p = new Precedence(List.of(), List.of(a));
		SoundTrace quarter = trace(p, play(9, Double.NaN));
		assertFalse(quarter.zoneWinners().containsKey(TraceProperty.LIMIT));
		assertFalse(quarter.zoneWinners().containsKey(TraceProperty.CUE));
		SoundTrace half = trace(p, play(8, Double.NaN));
		assertSame(a, half.zoneWinners().get(TraceProperty.LIMIT).zone());
	}

	@Test
	void traceWinnersMatchTheDecisionWithFadeOutsideAndBubbles() {
		Zone fade = sphere("fade", 1, 12, SetEntry.of("r1", "*cow*").withVolume(40).withCue(true)).withEdge(ZoneEdge.fade(6));
		Zone outside = sphere("out", 30, 10, SetEntry.of("r2", "*").withLimit(new Limit(3, 1000, false))).withMode(ZoneMode.OUTSIDE);
		Zone bubble = sphere("bubble", 0, 20, SetEntry.of("r3", "*").withVolume(70)).asBubble();
		Precedence p = new Precedence(List.of(SetEntry.of("g", "*").withVolume(90)), List.of(fade, outside, bubble));
		for (double x = -8; x <= 8; x += 0.5) {
			LastPlay play = play(x, 4);
			SoundDecision d = new SoundDecision();
			p.decide(COW, true, WORLD, OVERWORLD, x, 0, 0, 4, 0, 0, d);
			SoundTrace t = trace(p, play);
			assertEquals(d.volumePercent(), t.volumePercent(), 1e-4, "x=" + x);
			assertSame(d.loudnessZone(), zoneOf(t, TraceProperty.LOUDNESS), "loudness at x=" + x);
			assertSame(d.limitZone(), zoneOf(t, TraceProperty.LIMIT), "limit at x=" + x);
			assertSame(d.cueZone(), zoneOf(t, TraceProperty.CUE), "cue at x=" + x);
		}
	}

	private static Zone zoneOf(SoundTrace t, TraceProperty property) {
		ZoneWinner w = t.zoneWinners().get(property);
		return w == null ? null : w.zone();
	}
}
