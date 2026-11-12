package soundsculptor.core.trace;

import static soundsculptor.core.Fixtures.precedence;
import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.5: "Copy trace" gives the panel as plain text, in the FR-14.2 order. */
class TraceTextTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";

	private static LastPlay playAt(double x, float volume, boolean dropped, boolean cueShown) {
		return new LastPlay(true, WORLD, OVERWORLD, x, 0, 0, volume, dropped, cueShown);
	}

	private static Zone sphere(String id, double x, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), quick(List.of(rules)));
	}

	private static PlaceSets attached(String... setIds) {
		PlaceSets place = PlaceSets.EMPTY;
		for (String id : setIds) {
			place = place.attach(id);
		}
		return place;
	}

	private static String text(Precedence p, LastPlay play, boolean bypass) {
		return TraceText.of(SoundTracer.trace(p, SetLibrary.EMPTY, AMBIENT, play, bypass, false), EN);
	}

	private static String text(SetLibrary library, PlaceSets everywhere, String soundId, ToIntFunction<String> placesUsing) {
		Precedence p = new Precedence(everywhere, List.of(), library);
		return TraceText.of(SoundTracer.trace(p, library, placesUsing, soundId, playAt(0, 100, false, false), false, false), EN);
	}

	@Test
	void everywhereShowsEachSetTheWinnerItsReasonAndWhoWasOverridden() {
		SetEntry wildcard = SetEntry.of("w", "*villager*").withMute(true);
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		SetLibrary library = new SetLibrary(List.of(new SoundSet("villagers", "Villagers", List.of(wildcard, exact))));
		String text = TraceText.of(SoundTracer.trace(new Precedence(attached("villagers"), List.of(), library), library, AMBIENT,
				playAt(0, 60, false, false), false, false), EN);
		assertEquals(String.join("\n",
				"Sound Sculptor trace for minecraft:entity.villager.ambient",
				"Everywhere",
				"  Villagers",
				"    minecraft:entity.villager.ambient: Volume 60%",
				"      Decides loudness (Exact sound ID)",
				"    *:*villager*: Muted",
				"      loudness overridden by Villagers: minecraft:entity.villager.ambient",
				"Zones",
				"  No zone affects this sound",
				"Result",
				"  Volume 60%",
				"  No repeat limit",
				"  Last play was allowed",
				"  Cue off",
				"  No cue was shown",
				""), text);
	}

	@Test
	void higherSetSaysHigherInTheList() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40))),
				new SoundSet("quiet", "Quiet", List.of(SetEntry.of("e1", COW).withVolume(10)))));
		String text = text(library, attached("farm", "quiet"), COW, id -> 1);
		assertTrue(text.contains(String.join("\n",
				"Everywhere",
				"  Farm animals",
				"    *:*cow*: Volume 40%",
				"      Decides loudness (Higher in the list)",
				"  Quiet",
				"    minecraft:entity.cow.ambient: Volume 10%",
				"      loudness overridden by Farm animals: *:*cow*",
				"Zones")), text);
	}

	@Test
	void setUsedInMoreThanOnePlaceSaysSo() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)))));
		assertTrue(text(library, attached("farm"), COW, id -> 3).contains("\n  Farm animals · used in 3 places\n"));
		assertTrue(text(library, attached("farm"), COW, id -> 1).contains("\n  Farm animals\n"));
	}

	@Test
	void quickChangesIsNamedAndListedFirst() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)))));
		PlaceSets everywhere = attached("farm").withQuickEntries(List.of(SetEntry.of("e1", COW).withMute(true)));
		String text = text(library, everywhere, COW, id -> 1);
		assertTrue(text.contains(String.join("\n",
				"Everywhere",
				"  Quick changes",
				"    minecraft:entity.cow.ambient: Muted",
				"      Decides loudness (Higher in the list)",
				"  Farm animals",
				"    *:*cow*: Volume 40%",
				"      loudness overridden by Quick changes: minecraft:entity.cow.ambient",
				"Zones")), text);
	}

	@Test
	void zonesShowModeWeightDistanceTheirSetsAndTheWinners() {
		Zone near = sphere("near", 3, 20, SetEntry.of("rn", "*villager*").withVolume(20));
		Zone far = sphere("far", 9, 20, SetEntry.of("rf", "*villager*").withVolume(80));
		String text = text(precedence(List.of(), List.of(far, near)), playAt(0, 20, false, false), false);
		assertTrue(text.contains(String.join("\n",
				"Zones",
				"  far (Inside): weight 100%, 9 blocks from its anchor",
				"    Quick changes",
				"      *:*villager*: Volume 80%",
				"        Decides loudness (Only matching entry)",
				"  near (Inside): weight 100%, 3 blocks from its anchor",
				"    Quick changes",
				"      *:*villager*: Volume 20%",
				"        Decides loudness (Only matching entry)",
				"Deciding zones",
				"  loudness: near (Closest anchor)",
				"Result",
				"  Volume 20%")), text);
		assertTrue(text.startsWith("Sound Sculptor trace for " + AMBIENT + "\nEverywhere\n  No entry matches this sound\n"), text);
	}

	@Test
	void noticesComeFirst() {
		Precedence p = precedence(List.of(SetEntry.of("m", AMBIENT).withMute(true)), List.of());
		List<String> lines = text(p, playAt(0, 100, false, false), true).lines().toList();
		assertEquals("  Bypass is on: nothing is changed.", lines.get(1));
		assertEquals("Everywhere", lines.get(2));
	}

	@Test
	void limitDroppedPlayAndCueShown() {
		SetEntry rule = SetEntry.of("l", AMBIENT).withMute(true).withCue(true).withLimit(new Limit(3, 1000, true));
		String text = text(precedence(List.of(rule), List.of()), playAt(0, 0, true, true), false);
		assertTrue(text.endsWith(String.join("\n",
				"Result",
				"  Volume 0%",
				"  At most 3 per 1 s, per source",
				"  Last play was dropped by the repeat limit",
				"  Cue on",
				"  A cue was shown",
				"")), text);
	}

	@Test
	void notHeardYetLeavesOutWhatHappenedAtTheLastPlay() {
		String text = text(precedence(List.of(SetEntry.of("m", AMBIENT).withMute(true)), List.of()), null, false);
		assertTrue(text.contains("Not heard yet; zones depend on where it plays"), text);
		assertFalse(text.contains("Last play"), text);
		assertFalse(text.contains("cue was shown"), text);
	}

	@Test
	void fadeWeightAndUnnamedZones() {
		Zone fade = new Zone("z7", "", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(20),
				quick(List.of(SetEntry.of("r", "*").withMute(true)))).withEdge(soundsculptor.core.zones.ZoneEdge.fade(8));
		String text = text(precedence(List.of(), List.of(fade)), playAt(16, 50, false, false), false);
		assertTrue(text.contains("  z7 (Inside): weight 50%, 16 blocks from its anchor"), text);
	}
}
