package soundsculptor.core.trace;

import static soundsculptor.core.Fixtures.quick;
import static soundsculptor.core.Fixtures.precedence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD FR-14.2 with v1 zones: the trace shows fade weights and Outside zones exactly as decided. */
class SoundTracerFullZonesTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String COW = "minecraft:entity.cow.ambient";

	private static Zone cylinder(String id, double x, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Cylinder(radius, 2 * radius), quick(List.of(rules)));
	}

	private static LastPlay play(double x) {
		return new LastPlay(true, WORLD, OVERWORLD, x, 0, 0, 0, false, false);
	}

	private static SoundTrace trace(Precedence p, LastPlay play) {
		return SoundTracer.trace(p, SetLibrary.EMPTY, COW, play, false, false);
	}

	@Test
	void fadeZoneShowsItsWeight() {
		Zone a = cylinder("a", 0, 10, SetEntry.of("r", "*cow*").withMute(true)).withEdge(ZoneEdge.fade(4));
		SoundTrace t = trace(precedence(List.of(), List.of(a)), play(8));
		assertEquals(0.5, t.zones().get(0).weight(), 1e-9);
		assertEquals(50, t.volumePercent(), 1e-4);
	}

	@Test
	void zoneAtZeroWeightIsNotListed() {
		Zone a = cylinder("a", 0, 10, SetEntry.of("r", "*cow*").withMute(true)).withEdge(ZoneEdge.fade(4));
		assertTrue(trace(precedence(List.of(), List.of(a)), play(10)).zones().isEmpty(), "w = 0 at the boundary");
	}

	@Test
	void outsideZoneIsListedForSoundsOutsideIt() {
		Zone o = cylinder("o", 0, 10, SetEntry.of("all", "*").withMute(true)).withMode(ZoneMode.OUTSIDE);
		Precedence p = precedence(List.of(), List.of(o));
		assertEquals(1, trace(p, play(30)).zones().size());
		assertTrue(trace(p, play(5)).zones().isEmpty());
	}


	@Test
	void lowWeightFadeZoneDoesNotWinLimitOrCue() {
		// Below w = 0.5 a fade zone's limit and cue do not apply (FR-9 step 6), so the trace must not name it.
		Zone a = cylinder("a", 0, 10, SetEntry.of("r", "*cow*").withLimit(new Limit(2, 1000, false)).withCue(true))
				.withEdge(ZoneEdge.fade(4));
		Precedence p = precedence(List.of(), List.of(a));
		SoundTrace quarter = trace(p, play(9));
		assertFalse(quarter.zoneWinners().containsKey(TraceProperty.LIMIT));
		assertFalse(quarter.zoneWinners().containsKey(TraceProperty.CUE));
		SoundTrace half = trace(p, play(8));
		assertSame(a, half.zoneWinners().get(TraceProperty.LIMIT).zone());
	}

	@Test
	void traceWinnersMatchTheDecisionWithFadeAndOutsideZones() {
		Zone fade = cylinder("fade", 1, 12, SetEntry.of("r1", "*cow*").withVolume(40).withCue(true)).withEdge(ZoneEdge.fade(6));
		Zone outside = cylinder("out", 30, 10, SetEntry.of("r2", "*").withLimit(new Limit(3, 1000, false))).withMode(ZoneMode.OUTSIDE);
		Zone near = cylinder("near", 4, 20, SetEntry.of("r3", "*").withVolume(70));
		Precedence p = precedence(List.of(SetEntry.of("g", "*").withVolume(90)), List.of(fade, outside, near));
		for (double x = -8; x <= 8; x += 0.5) {
			LastPlay play = play(x);
			SoundDecision d = new SoundDecision();
			p.decide(COW, true, WORLD, OVERWORLD, x, 0, 0, d);
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
