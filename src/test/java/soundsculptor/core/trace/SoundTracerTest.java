package soundsculptor.core.trace;

import static soundsculptor.core.Fixtures.precedence;
import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.1 to FR-14.3: the "Why does this sound like this?" panel's data, in Sound Set terms. */
class SoundTracerTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";

	private static LastPlay playAt(double x, double y, double z, float volume) {
		return new LastPlay(true, WORLD, OVERWORLD, x, y, z, volume, false, false);
	}

	private static Zone sphere(String id, double x, double radius, SetEntry... rules) {
		return sphere(id, x, radius, quick(List.of(rules)));
	}

	private static Zone sphere(String id, double x, double radius, PlaceSets sets) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), sets);
	}

	private static PlaceSets attached(String... setIds) {
		PlaceSets place = PlaceSets.EMPTY;
		for (String id : setIds) {
			place = place.attach(id);
		}
		return place;
	}

	private static SoundTrace trace(Precedence p, String id, LastPlay play) {
		return SoundTracer.trace(p, SetLibrary.EMPTY, id, play, false, false);
	}

	private static SoundTrace trace(SetLibrary library, PlaceSets everywhere, List<Zone> zones, String id, LastPlay play) {
		return SoundTracer.trace(new Precedence(everywhere, zones, library), library, id, play, false, false);
	}

	// --- FR-14 acceptance checks ---

	@Test
	void ambientSingleBeatsVillagerGroupInOneSet() {
		SetEntry group = SetEntry.of("g", "*villager*").withMute(true);
		SetEntry single = SetEntry.of("s", AMBIENT).withVolume(60);
		SetLibrary library = new SetLibrary(List.of(new SoundSet("villagers", "Villagers", List.of(group, single))));
		SoundTrace t = trace(library, attached("villagers"), List.of(), AMBIENT, playAt(0, 0, 0, 60));

		assertEquals(1, t.everywhere().sets().size());
		SetLine line = t.everywhere().sets().get(0);
		assertEquals("villagers", line.setId());
		assertEquals("Villagers", line.setName());
		assertFalse(line.quick());
		assertEquals(List.of(single, group), line.matched(), "both entries, most specific first");
		EntryWinner loud = t.everywhere().winners().get(TraceProperty.LOUDNESS);
		assertSame(single, loud.entry());
		assertEquals("villagers", loud.setId());
		assertEquals(EntryWinReason.EXACT_ID, loud.reason());
		assertSame(loud, t.everywhere().overriddenBy("villagers", group, TraceProperty.LOUDNESS));
		assertNull(t.everywhere().overriddenBy("villagers", single, TraceProperty.LOUDNESS));
		assertEquals(60, t.volumePercent(), 1e-6);
	}

	@Test
	void twoSetsBothSettingCowsMarksHigherSet() {
		SetEntry farmCows = SetEntry.of("e1", "*cow*").withVolume(40);
		SetEntry quietCow = SetEntry.of("e1", COW).withVolume(10);
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(farmCows)),
				new SoundSet("quiet", "Quiet", List.of(quietCow))));
		SoundTrace t = trace(library, attached("farm", "quiet"), List.of(), COW, playAt(0, 0, 0, 40));

		assertEquals(List.of("farm", "quiet"), t.everywhere().sets().stream().map(SetLine::setId).toList(), "both sets are named");
		EntryWinner loud = t.everywhere().winners().get(TraceProperty.LOUDNESS);
		assertSame(farmCows, loud.entry(), "a higher set beats a more specific entry in a lower one");
		assertEquals("farm", loud.setId());
		assertEquals(EntryWinReason.HIGHER_SET, loud.reason());
		assertSame(loud, t.everywhere().overriddenBy("quiet", quietCow, TraceProperty.LOUDNESS));
		assertEquals(40, t.volumePercent(), 1e-6);
	}

	@Test
	void duplicateSetsTraceAsTwoLines() {
		// A Duplicate shares its entry instances with the original, so the trace must not tell them apart by instance.
		SetEntry entry = SetEntry.of("e1", "*villager*").withVolume(30);
		SoundSet a = new SoundSet("a", "Villagers", List.of(entry));
		SoundSet a2 = new SoundSet("a2", "Villagers (2)", a.entries());
		assertSame(a.entries().get(0), a2.entries().get(0));
		SoundTrace t = trace(new SetLibrary(List.of(a, a2)), attached("a", "a2"), List.of(), AMBIENT, playAt(0, 0, 0, 30));

		List<SetLine> lines = t.everywhere().sets();
		assertEquals(2, lines.size(), "one line per set: " + lines);
		assertEquals("a", lines.get(0).setId());
		assertEquals("a2", lines.get(1).setId());
		assertEquals(List.of(entry), lines.get(0).matched());
		assertEquals(List.of(entry), lines.get(1).matched());
		EntryWinner loud = t.everywhere().winners().get(TraceProperty.LOUDNESS);
		assertEquals("a", loud.setId(), "the set that actually won, the higher one");
		assertEquals(EntryWinReason.HIGHER_SET, loud.reason());
		assertTrue(t.everywhere().wins("a", entry, TraceProperty.LOUDNESS));
		assertFalse(t.everywhere().wins("a2", entry, TraceProperty.LOUDNESS));
		assertSame(loud, t.everywhere().overriddenBy("a2", entry, TraceProperty.LOUDNESS));
		assertNull(t.everywhere().overriddenBy("a", entry, TraceProperty.LOUDNESS));
	}

	@Test
	void overlappingZonesCloserWins() {
		Zone near = sphere("near", 3, 20, SetEntry.of("rn", "*villager*").withVolume(20));
		Zone far = sphere("far", 9, 20, SetEntry.of("rf", "*villager*").withVolume(80));
		SoundTrace t = trace(precedence(List.of(), List.of(far, near)), AMBIENT, playAt(0, 0, 0, 20));

		assertEquals(2, t.zones().size());
		ZoneLine farLine = t.zones().get(0);
		assertSame(far, farLine.zone());
		assertEquals(9, farLine.distance(), 1e-9);
		assertEquals(1, farLine.weight(), 1e-9);
		assertEquals(3, t.zones().get(1).distance(), 1e-9);
		assertEquals(SoundSet.QUICK_ID, farLine.sets().sets().get(0).setId());
		assertTrue(farLine.sets().sets().get(0).quick());

		ZoneWinner winner = t.zoneWinners().get(TraceProperty.LOUDNESS);
		assertSame(near, winner.zone());
		assertEquals(ZoneWinReason.CLOSEST, winner.reason());
		assertEquals(20, t.volumePercent(), 1e-6);
	}

	@Test
	void bypassLeadsTheTraceAndChangesNothing() {
		Precedence p = precedence(List.of(SetEntry.of("m", AMBIENT).withMute(true)), List.of());
		SoundTrace t = SoundTracer.trace(p, SetLibrary.EMPTY, AMBIENT, playAt(0, 0, 0, 100), true, false);
		assertEquals(TraceNotice.BYPASS, t.notices().iterator().next());
		assertEquals(100, t.volumePercent(), 1e-6);
		assertFalse(t.everywhere().sets().isEmpty(), "sets are still shown, so the player can see what bypass skips");
	}

	@Test
	void vanillaCoveredSoundSaysSoundSculptorDoesNotHandleIt() {
		SoundTrace t = SoundTracer.trace(precedence(List.of(SetEntry.of("all", "*").withMute(true)), List.of()), SetLibrary.EMPTY,
				"minecraft:music.game", null, false, true);
		assertEquals(Set.of(TraceNotice.VANILLA_COVERED), t.notices());
		assertSame(PlaceTrace.EMPTY, t.everywhere());
		assertEquals(100, t.volumePercent(), 1e-6);
	}

	@Test
	void nonPositionalSoundSaysZonesNeverApply() {
		Zone all = sphere("a", 0, 10, SetEntry.of("r", "*").withMute(true));
		LastPlay eating = new LastPlay(false, WORLD, OVERWORLD, 0, 0, 0, 100, false, false);
		SoundTrace t = trace(precedence(List.of(), List.of(all)), "minecraft:entity.generic.eat", eating);
		assertTrue(t.notices().contains(TraceNotice.NON_POSITIONAL));
		assertTrue(t.zones().isEmpty());
		assertEquals(100, t.volumePercent(), 1e-6);
	}

	// --- Sets that do not take part ---

	@Test
	void setsWithoutAMatchOffAttachmentsAndOffEntriesAreNotListed() {
		SetEntry off = SetEntry.of("e1", AMBIENT).withVolume(10).withEnabled(false);
		SetLibrary library = new SetLibrary(List.of(new SoundSet("cows", "Cows", List.of(SetEntry.of("e1", "*cow*").withMute(true))),
				new SoundSet("off", "Off", List.of(SetEntry.of("e1", "*villager*").withMute(true))),
				new SoundSet("offentry", "Off entry", List.of(off))));
		PlaceSets everywhere = attached("cows", "off", "offentry").setEnabled("off", false);
		SoundTrace t = trace(library, everywhere, List.of(), AMBIENT, playAt(0, 0, 0, 100));
		assertEquals(PlaceTrace.EMPTY, t.everywhere());
	}

	@Test
	void quickChangesIsListedFirstWithoutAName() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*villager*").withVolume(50)))));
		PlaceSets everywhere = attached("farm").withQuickEntries(List.of(SetEntry.of("e1", AMBIENT).withMute(true)));
		SoundTrace t = trace(library, everywhere, List.of(), AMBIENT, playAt(0, 0, 0, 0));
		SetLine first = t.everywhere().sets().get(0);
		assertTrue(first.quick());
		assertEquals(SoundSet.QUICK_ID, first.setId());
		assertEquals("", first.setName());
		assertEquals("farm", t.everywhere().sets().get(1).setId());
		assertEquals(EntryWinReason.HIGHER_SET, t.everywhere().winners().get(TraceProperty.LOUDNESS).reason());
	}

	@Test
	void placesUsingComesFromTheCaller() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*villager*").withVolume(50)))));
		PlaceSets everywhere = attached("farm").withQuickEntries(List.of(SetEntry.of("e1", AMBIENT).withCue(true)));
		SoundTrace t = SoundTracer.trace(new Precedence(everywhere, List.of(), library), library, id -> id.equals("farm") ? 3 : 99,
				AMBIENT, playAt(0, 0, 0, 50), false, false);
		assertEquals(1, t.everywhere().sets().get(0).placesUsing(), "Quick changes belongs to its one place");
		assertEquals(3, t.everywhere().sets().get(1).placesUsing());
	}

	// --- Entry win reasons (FR-4.5 steps) ---

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
		SoundTrace t = trace(precedence(List.of(rules), List.of()), AMBIENT, playAt(0, 0, 0, 0));
		return t.everywhere().winners().get(TraceProperty.LOUDNESS).reason();
	}

	@Test
	void eachPropertyHasItsOwnWinner() {
		SetEntry mute = SetEntry.of("m", "*villager*").withMute(true);
		SetEntry cue = SetEntry.of("c", AMBIENT).withCue(true);
		PlaceTrace g = trace(precedence(List.of(mute, cue), List.of()), AMBIENT, playAt(0, 0, 0, 0)).everywhere();
		assertSame(mute, g.winners().get(TraceProperty.LOUDNESS).entry());
		assertSame(cue, g.winners().get(TraceProperty.CUE).entry());
		assertFalse(g.winners().containsKey(TraceProperty.LIMIT));
	}

	// --- Zone tie-break reasons (FR-9 step 5) ---

	@Test
	void zoneTieReasonsMatchTheDecision() {
		Zone small = new Zone("s", "s", true, WORLD, OVERWORLD, new Point(5, 0, 0), new Shape.Sphere(6),
				quick(List.of(SetEntry.of("rs", "*villager*").withVolume(20))));
		Zone big = new Zone("b", "b", true, WORLD, OVERWORLD, new Point(0, 5, 0), new Shape.Sphere(10),
				quick(List.of(SetEntry.of("rb", "*villager*").withVolume(80))));
		ZoneWinner w = trace(precedence(List.of(), List.of(big, small)), AMBIENT, playAt(0, 0, 0, 20))
				.zoneWinners().get(TraceProperty.LOUDNESS);
		assertSame(small, w.zone());
		assertEquals(ZoneWinReason.TIE_SMALLER_ZONE, w.reason());

		Zone twin = new Zone("t", "t", true, WORLD, OVERWORLD, new Point(0, 0, 5), new Shape.Sphere(10),
				quick(List.of(SetEntry.of("rt", "*villager*").withVolume(50))));
		ZoneWinner order = trace(precedence(List.of(), List.of(big, twin)), AMBIENT, playAt(0, 0, 0, 80))
				.zoneWinners().get(TraceProperty.LOUDNESS);
		assertSame(big, order.zone());
		assertEquals(ZoneWinReason.TIE_LIST_ORDER, order.reason());
	}

	@Test
	void singleZoneWinsAsTheOnlyOne() {
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*villager*").withLimit(new Limit(2, 1000, false)));
		SoundTrace t = trace(precedence(List.of(), List.of(a)), AMBIENT, playAt(0, 0, 0, 100));
		assertEquals(ZoneWinReason.ONLY_ZONE, t.zoneWinners().get(TraceProperty.LIMIT).reason());
		assertEquals(new Limit(2, 1000, false), t.limit());
	}

	@Test
	void zonesThatDoNotContainTheSoundAreLeftOut() {
		Zone away = sphere("away", 100, 10, SetEntry.of("r", "*villager*").withMute(true));
		assertTrue(trace(precedence(List.of(), List.of(away)), AMBIENT, playAt(0, 0, 0, 100)).zones().isEmpty());
	}

	@Test
	void containingZoneWithoutMatchingEntriesIsListedButDecidesNothing() {
		Zone cows = sphere("cows", 0, 10, SetEntry.of("r", "minecraft:entity.cow.*").withMute(true));
		SoundTrace t = trace(precedence(List.of(), List.of(cows)), AMBIENT, playAt(0, 0, 0, 100));
		assertEquals(1, t.zones().size());
		assertTrue(t.zones().get(0).sets().sets().isEmpty());
		assertTrue(t.zoneWinners().isEmpty());
	}

	@Test
	void zoneWithALibrarySetIsTracedInSetTerms() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*villager*").withVolume(25)))));
		Zone pen = sphere("pen", 0, 10, attached("farm"));
		SoundTrace t = trace(library, PlaceSets.EMPTY, List.of(pen), AMBIENT, playAt(0, 0, 0, 25));
		PlaceTrace sets = t.zones().get(0).sets();
		assertEquals("Farm animals", sets.sets().get(0).setName());
		assertEquals("farm", sets.winners().get(TraceProperty.LOUDNESS).setId());
		assertEquals(25, t.volumePercent(), 1e-6);
	}

	// --- Final result and other notices ---

	@Test
	void finalResultAlwaysMatchesTheRealDecision() {
		SetEntry global = SetEntry.of("g", "*villager*").withMute(true).withCue(true);
		Zone a = sphere("a", 2, 10, SetEntry.of("ra", "*villager*").withVolume(40));
		Zone b = sphere("b", 6, 10, SetEntry.of("rb", "*villager*").withLimit(new Limit(2, 1000, false)));
		Precedence p = precedence(List.of(global), List.of(a, b));
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
	void soundNotHeardYetShowsOnlyEverywhere() {
		Zone a = sphere("a", 0, 10, SetEntry.of("r", "*villager*").withMute(true));
		SoundTrace t = trace(precedence(List.of(SetEntry.of("g", "*villager*").withVolume(30)), List.of(a)), AMBIENT, null);
		assertTrue(t.notices().contains(TraceNotice.NOT_HEARD_YET));
		assertTrue(t.zones().isEmpty());
		assertNotNull(t.everywhere().winners().get(TraceProperty.LOUDNESS));
		assertEquals(30, t.volumePercent(), 1e-6);
		assertNull(t.playAllowed());
		assertNull(t.cueShown());
	}

	@Test
	void boostAboveOneHundredIsFlagged() {
		SoundTrace t = trace(precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(150)), List.of()), AMBIENT, playAt(0, 0, 0, 150));
		assertTrue(t.notices().contains(TraceNotice.BOOSTED));
	}

	@Test
	void changesSinceThePlayAreFlagged() {
		// The play happened at 100%; the player has since added a 30% entry.
		SoundTrace t = trace(precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(30)), List.of()), AMBIENT, playAt(0, 0, 0, 100));
		assertTrue(t.notices().contains(TraceNotice.CHANGED_SINCE_PLAY));
		SoundTrace same = trace(precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(30)), List.of()), AMBIENT, playAt(0, 0, 0, 30));
		assertFalse(same.notices().contains(TraceNotice.CHANGED_SINCE_PLAY));
	}

	@Test
	void limiterAndCueOutcomesComeFromThePlay() {
		LastPlay dropped = new LastPlay(true, WORLD, OVERWORLD, 0, 0, 0, 0, true, true);
		SoundTrace t = trace(precedence(List.of(SetEntry.of("g", AMBIENT).withMute(true)), List.of()), AMBIENT, dropped);
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
