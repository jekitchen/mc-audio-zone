package soundsculptor.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.1 to FR-14.3: the "Why does this sound like this?" panel's data. */
class SoundTracerTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";

	private static LastPlay playAt(double x, double y, double z, float volume) {
		return new LastPlay(true, WORLD, OVERWORLD, x, y, z, volume, false, false);
	}

	private static Zone sphere(String id, double x, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), List.of(rules));
	}

	private static SoundTrace trace(Precedence p, String id, LastPlay play) {
		return SoundTracer.trace(p, id, play, false, false);
	}

	// --- FR-14 acceptance checks ---

	@Test
	void exactRuleWinsOverWildcardAndExplainsWhy() {
		SetEntry wildcard = SetEntry.of("w", "*villager*").withMute(true);
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		SoundTrace t = trace(new Precedence(List.of(wildcard, exact), List.of()), AMBIENT, playAt(0, 0, 0, 60));

		assertEquals(List.of(exact, wildcard), t.global().matched(), "most specific first");
		EntryWinner loud = t.global().winners().get(TraceProperty.LOUDNESS);
		assertSame(exact, loud.rule());
		assertEquals(EntryWinReason.EXACT_ID, loud.reason());
		assertSame(exact, t.global().overriddenBy(wildcard, TraceProperty.LOUDNESS));
		assertNull(t.global().overriddenBy(exact, TraceProperty.LOUDNESS));
		assertEquals(60, t.volumePercent(), 1e-6);
	}

	@Test
	void overlappingZonesAreListedWithDistancesAndTheCloserWins() {
		Zone near = sphere("near", 3, 20, SetEntry.of("rn", "*villager*").withVolume(20));
		Zone far = sphere("far", 9, 20, SetEntry.of("rf", "*villager*").withVolume(80));
		SoundTrace t = trace(new Precedence(List.of(), List.of(far, near)), AMBIENT, playAt(0, 0, 0, 20));

		assertEquals(2, t.zones().size());
		ZoneLine farLine = t.zones().get(0);
		assertSame(far, farLine.zone());
		assertEquals(9, farLine.distance(), 1e-9);
		assertEquals(1, farLine.weight(), 1e-9);
		assertEquals(3, t.zones().get(1).distance(), 1e-9);

		ZoneWinner winner = t.zoneWinners().get(TraceProperty.LOUDNESS);
		assertSame(near, winner.zone());
		assertEquals(ZoneWinReason.CLOSEST, winner.reason());
		assertEquals(20, t.volumePercent(), 1e-6);
	}

	@Test
	void bypassLeadsTheTraceAndChangesNothing() {
		Precedence p = new Precedence(List.of(SetEntry.of("m", AMBIENT).withMute(true)), List.of());
		SoundTrace t = SoundTracer.trace(p, AMBIENT, playAt(0, 0, 0, 100), true, false);
		assertEquals(TraceNotice.BYPASS, t.notices().iterator().next());
		assertEquals(100, t.volumePercent(), 1e-6);
		assertFalse(t.global().matched().isEmpty(), "rules are still shown, so the player can see what bypass skips");
	}

	@Test
	void vanillaCoveredSoundSaysSoundSculptorDoesNotHandleIt() {
		SoundTrace t = SoundTracer.trace(new Precedence(List.of(SetEntry.of("all", "*").withMute(true)), List.of()),
				"minecraft:music.game", null, false, true);
		assertEquals(Set.of(TraceNotice.VANILLA_COVERED), t.notices());
		assertTrue(t.global().matched().isEmpty());
		assertEquals(100, t.volumePercent(), 1e-6);
	}

	@Test
	void nonPositionalSoundSaysZonesNeverApply() {
		Zone all = sphere("a", 0, 10, SetEntry.of("r", "*").withMute(true));
		LastPlay eating = new LastPlay(false, WORLD, OVERWORLD, 0, 0, 0, 100, false, false);
		SoundTrace t = trace(new Precedence(List.of(), List.of(all)), "minecraft:entity.generic.eat", eating);
		assertTrue(t.notices().contains(TraceNotice.NON_POSITIONAL));
		assertTrue(t.zones().isEmpty());
		assertEquals(100, t.volumePercent(), 1e-6);
	}

	// --- SetEntry win reasons (FR-4.5 steps) ---

	@Test
	void reasonsNameTheSpecificityStepThatDecided() {
		assertEquals(EntryWinReason.MORE_LITERAL, loudnessReason(
				SetEntry.of("a", "*villager*").withVolume(10), SetEntry.of("b", "minecraft:entity.villager.*").withVolume(20)));
		assertEquals(EntryWinReason.FEWER_STARS, loudnessReason(
				SetEntry.of("a", "minecraft:*entity.villager*").withVolume(10), SetEntry.of("b", "minecraft:entity.villager*").withVolume(20)));
		assertEquals(EntryWinReason.LIST_ORDER, loudnessReason(
				SetEntry.of("a", "*villager*").withVolume(10), SetEntry.of("b", "*villager*").withVolume(20)));
		assertEquals(EntryWinReason.ONLY_MATCH, loudnessReason(SetEntry.of("a", "*villager*").withVolume(10)));
	}

	private static EntryWinReason loudnessReason(SetEntry... rules) {
		SoundTrace t = trace(new Precedence(List.of(rules), List.of()), AMBIENT, playAt(0, 0, 0, 0));
		return t.global().winners().get(TraceProperty.LOUDNESS).reason();
	}

	@Test
	void eachPropertyHasItsOwnWinner() {
		SetEntry mute = SetEntry.of("m", "*villager*").withMute(true);
		SetEntry cue = SetEntry.of("c", AMBIENT).withCue(true);
		ListTrace g = trace(new Precedence(List.of(mute, cue), List.of()), AMBIENT, playAt(0, 0, 0, 0)).global();
		assertSame(mute, g.winners().get(TraceProperty.LOUDNESS).rule());
		assertSame(cue, g.winners().get(TraceProperty.CUE).rule());
		assertFalse(g.winners().containsKey(TraceProperty.LIMIT));
	}

	// --- Zone tie-break reasons (FR-9 step 5) ---

	@Test
	void zoneTieReasonsMatchTheDecision() {
		Zone small = new Zone("s", "s", true, WORLD, OVERWORLD, new Point(5, 0, 0), new Shape.Sphere(6),
				List.of(SetEntry.of("rs", "*villager*").withVolume(20)));
		Zone big = new Zone("b", "b", true, WORLD, OVERWORLD, new Point(0, 5, 0), new Shape.Sphere(10),
				List.of(SetEntry.of("rb", "*villager*").withVolume(80)));
		ZoneWinner w = trace(new Precedence(List.of(), List.of(big, small)), AMBIENT, playAt(0, 0, 0, 20))
				.zoneWinners().get(TraceProperty.LOUDNESS);
		assertSame(small, w.zone());
		assertEquals(ZoneWinReason.TIE_SMALLER_ZONE, w.reason());

		Zone twin = new Zone("t", "t", true, WORLD, OVERWORLD, new Point(0, 0, 5), new Shape.Sphere(10),
				List.of(SetEntry.of("rt", "*villager*").withVolume(50)));
		ZoneWinner order = trace(new Precedence(List.of(), List.of(big, twin)), AMBIENT, playAt(0, 0, 0, 80))
				.zoneWinners().get(TraceProperty.LOUDNESS);
		assertSame(big, order.zone());
		assertEquals(ZoneWinReason.TIE_LIST_ORDER, order.reason());
	}

	@Test
	void singleZoneWinsAsTheOnlyOne() {
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*villager*").withLimit(new Limit(2, 1000, false)));
		SoundTrace t = trace(new Precedence(List.of(), List.of(a)), AMBIENT, playAt(0, 0, 0, 100));
		assertEquals(ZoneWinReason.ONLY_ZONE, t.zoneWinners().get(TraceProperty.LIMIT).reason());
		assertEquals(new Limit(2, 1000, false), t.limit());
	}

	@Test
	void zonesThatDoNotContainTheSoundAreLeftOut() {
		Zone away = sphere("away", 100, 10, SetEntry.of("r", "*villager*").withMute(true));
		assertTrue(trace(new Precedence(List.of(), List.of(away)), AMBIENT, playAt(0, 0, 0, 100)).zones().isEmpty());
	}

	@Test
	void containingZoneWithoutMatchingRulesIsListedButDecidesNothing() {
		Zone cows = sphere("cows", 0, 10, SetEntry.of("r", "minecraft:entity.cow.*").withMute(true));
		SoundTrace t = trace(new Precedence(List.of(), List.of(cows)), AMBIENT, playAt(0, 0, 0, 100));
		assertEquals(1, t.zones().size());
		assertTrue(t.zones().get(0).rules().matched().isEmpty());
		assertTrue(t.zoneWinners().isEmpty());
	}

	// --- Final result and other notices ---

	@Test
	void finalResultAlwaysMatchesTheRealDecision() {
		SetEntry global = SetEntry.of("g", "*villager*").withMute(true).withCue(true);
		Zone a = sphere("a", 2, 10, SetEntry.of("ra", "*villager*").withVolume(40));
		Zone b = sphere("b", 6, 10, SetEntry.of("rb", "*villager*").withLimit(new Limit(2, 1000, false)));
		Precedence p = new Precedence(List.of(global), List.of(a, b));
		SoundDecision d = new SoundDecision();
		p.decide(AMBIENT, true, WORLD, OVERWORLD, 0, 0, 0, d);
		SoundTrace t = trace(p, AMBIENT, playAt(0, 0, 0, d.volumePercent()));
		assertEquals(d.volumePercent(), t.volumePercent(), 1e-6);
		assertEquals(d.limit(), t.limit());
		assertEquals(d.cueOn(), t.cueOn());
		assertSame(d.loudnessZone(), t.zoneWinners().get(TraceProperty.LOUDNESS).zone());
		assertSame(d.limitZone(), t.zoneWinners().get(TraceProperty.LIMIT).zone());
	}

	@Test
	void soundNotHeardYetShowsOnlyGlobalRules() {
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*villager*").withMute(true));
		SoundTrace t = trace(new Precedence(List.of(SetEntry.of("g", "*villager*").withVolume(30)), List.of(a)), AMBIENT, null);
		assertTrue(t.notices().contains(TraceNotice.NOT_HEARD_YET));
		assertTrue(t.zones().isEmpty());
		assertEquals(30, t.volumePercent(), 1e-6);
		assertNull(t.playAllowed());
		assertNull(t.cueShown());
	}

	@Test
	void boostAboveOneHundredIsFlagged() {
		SoundTrace t = trace(new Precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(150)), List.of()), AMBIENT, playAt(0, 0, 0, 150));
		assertTrue(t.notices().contains(TraceNotice.BOOSTED));
	}

	@Test
	void ruleChangesSinceThePlayAreFlagged() {
		// The play happened at 100%; the player has since added a 30% rule.
		SoundTrace t = trace(new Precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(30)), List.of()), AMBIENT, playAt(0, 0, 0, 100));
		assertTrue(t.notices().contains(TraceNotice.CHANGED_SINCE_PLAY));
		SoundTrace same = trace(new Precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(30)), List.of()), AMBIENT, playAt(0, 0, 0, 30));
		assertFalse(same.notices().contains(TraceNotice.CHANGED_SINCE_PLAY));
	}

	@Test
	void limiterAndCueOutcomesComeFromThePlay() {
		LastPlay dropped = new LastPlay(true, WORLD, OVERWORLD, 0, 0, 0, 0, true, true);
		SoundTrace t = trace(new Precedence(List.of(SetEntry.of("g", AMBIENT).withMute(true)), List.of()), AMBIENT, dropped);
		assertEquals(Boolean.FALSE, t.playAllowed());
		assertEquals(Boolean.TRUE, t.cueShown());
	}

	@Test
	void everyEnumHasATranslationKey() {
		for (EntryWinReason r : EntryWinReason.values()) {
			assertTrue(r.translationKey().startsWith("soundsculptor.trace.rule_reason."), r.name());
		}
		for (ZoneWinReason r : ZoneWinReason.values()) {
			assertTrue(r.translationKey().startsWith("soundsculptor.trace.zone_reason."), r.name());
		}
		for (TraceNotice n : TraceNotice.values()) {
			assertTrue(n.translationKey().startsWith("soundsculptor.trace.notice."), n.name());
		}
	}
}
