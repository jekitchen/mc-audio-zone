package soundsculptor.core.trace;

import static soundsculptor.core.Fixtures.precedence;
import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.4: each trace line says which entry or zone it is about, so the panel can put edit controls on it. */
class TraceTextLinesTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final LastPlay PLAY = new LastPlay(true, WORLD, OVERWORLD, 0, 0, 0, 60, false, false);

	private static Zone sphere(String id, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(1, 0, 0), new Shape.Sphere(20), quick(List.of(rules)));
	}

	private static SoundTrace trace(List<SetEntry> everywhere, List<Zone> zones) {
		return SoundTracer.trace(precedence(everywhere, zones, soundsculptor.core.zones.ZoneEdge.hard()), SetLibrary.EMPTY, AMBIENT,
				PLAY, false, false);
	}

	/** Entry, zone and "no entry" targets; set lines are checked on their own. */
	private static List<TraceText.Target> targets(List<TraceText.Line> lines) {
		List<TraceText.Target> targets = new ArrayList<>();
		for (TraceText.Line line : lines) {
			if (line.target() != null && !(line.target() instanceof TraceText.SetTarget)) {
				targets.add(line.target());
			}
		}
		return targets;
	}

	@Test
	void linesJoinToTheCopyTraceText() {
		SoundTrace t = trace(List.of(SetEntry.of("w", "*villager*").withMute(true), SetEntry.of("e", AMBIENT).withVolume(60)),
				List.of(sphere("z", SetEntry.of("zr", "*villager*").withVolume(20)), sphere("empty")));
		StringBuilder joined = new StringBuilder();
		for (TraceText.Line line : TraceText.lines(t, EN)) {
			joined.append(line.text()).append('\n');
		}
		assertEquals(TraceText.of(t, EN), joined.toString());
	}

	@Test
	void entryZoneAndNoEntryLinesCarryTheirTargets() {
		SetEntry wildcard = SetEntry.of("w", "*villager*").withMute(true);
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		SetEntry zoneEntry = SetEntry.of("zr", "*villager*").withVolume(20);
		Zone z = sphere("z", zoneEntry);
		Zone empty = sphere("empty");
		SoundTrace t = trace(List.of(wildcard, exact), List.of(z, empty));
		List<TraceText.Line> lines = TraceText.lines(t, EN);

		assertEquals(List.of(
				new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, "e"), exact),
				new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, "w"), wildcard),
				new TraceText.ZoneTarget(z),
				new TraceText.EntryTarget(new EntryLocation(PlaceRef.zone("z"), SoundSet.QUICK_ID, "zr"), zoneEntry),
				new TraceText.ZoneTarget(empty),
				new TraceText.NoEntryTarget(PlaceRef.zone("empty"))), targets(lines));
		int everywhere = indexOf(lines, "Everywhere");
		assertEquals("  Quick changes", lines.get(everywhere + 1).text());
		assertEquals(new TraceText.SetTarget(PlaceRef.EVERYWHERE, t.everywhere().sets().get(0)), lines.get(everywhere + 1).target(),
				"the set's own line is about the set at its place");
		assertEquals("    minecraft:entity.villager.ambient: Level 60%", lines.get(everywhere + 2).text());
		assertNull(lines.get(everywhere + 3).target(), "the 'Decides loudness' line has nothing to edit");
	}

	@Test
	void duplicateSetsGiveEachEntryLineItsOwnSet() {
		SetEntry entry = SetEntry.of("e1", "*villager*").withVolume(30);
		SoundSet a = new SoundSet("a", "Villagers", List.of(entry));
		SoundSet a2 = new SoundSet("a2", "Villagers (2)", a.entries());
		SetLibrary library = new SetLibrary(List.of(a, a2));
		PlaceSets everywhere = PlaceSets.EMPTY.attach("a").attach("a2");
		SoundTrace t = SoundTracer.trace(new Precedence(everywhere, List.of(), library), library, AMBIENT, PLAY, false, false);
		List<TraceText.Line> lines = TraceText.lines(t, EN);

		assertEquals(List.of(
				new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, "a", "e1"), entry),
				new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, "a2", "e1"), entry)), targets(lines));
		int first = indexOf(lines, "  Villagers");
		assertEquals("      Decides loudness (Higher in the list)", lines.get(first + 2).text());
		int second = indexOf(lines, "  Villagers (2)");
		assertEquals("      loudness overridden by Villagers: *:*villager*", lines.get(second + 2).text());
	}

	@Test
	void setLinesCarryTheirSetAndPlaceQuickChangesFirst() {
		SetEntry farmEntry = SetEntry.of("e1", "*villager*").withVolume(30);
		SoundSet farm = new SoundSet("farm", "Farm animals", List.of(farmEntry));
		SetLibrary library = new SetLibrary(List.of(farm));
		SetEntry quickEntry = SetEntry.of("q", AMBIENT).withVolume(60);
		PlaceSets everywhere = PlaceSets.EMPTY.attach("farm").withQuickEntries(List.of(quickEntry));
		Zone pen = new Zone("pen", "Pen", true, WORLD, OVERWORLD, new Point(1, 0, 0), new Shape.Sphere(20), PlaceSets.EMPTY.attach("farm"));
		SoundTrace t = SoundTracer.trace(new Precedence(everywhere, List.of(pen), library), library, setId -> 2, AMBIENT, PLAY, false, false);
		List<TraceText.Line> lines = TraceText.lines(t, EN);

		List<TraceText.SetTarget> sets = new ArrayList<>();
		for (TraceText.Line line : lines) {
			if (line.target() instanceof TraceText.SetTarget set) {
				sets.add(set);
			}
		}
		assertEquals(3, sets.size(), "Everywhere's Quick changes and Farm animals, then the zone's Farm animals: " + sets);
		assertEquals(PlaceRef.EVERYWHERE, sets.get(0).place());
		assertEquals(SoundSet.QUICK_ID, sets.get(0).set().setId());
		assertEquals("farm", sets.get(1).set().setId());
		assertEquals(PlaceRef.zone("pen"), sets.get(2).place());
		assertEquals("farm", sets.get(2).set().setId());
		int everywhereIndex = indexOf(lines, "Everywhere");
		assertEquals("  Quick changes", lines.get(everywhereIndex + 1).text());
		assertEquals(sets.get(1), lines.get(indexOf(lines, "  Farm animals · used in 2 places")).target());
	}

	@Test
	void noEverywhereEntryGivesAnAddToQuickChangesLine() {
		List<TraceText.Line> lines = TraceText.lines(trace(List.of(), List.of()), EN);
		int everywhere = indexOf(lines, "Everywhere");
		assertEquals("  No entry matches this sound", lines.get(everywhere + 1).text());
		assertEquals(new TraceText.NoEntryTarget(PlaceRef.EVERYWHERE), lines.get(everywhere + 1).target());
		assertNull(lines.get(indexOf(lines, "  No zone affects this sound")).target(), "'No zone affects this sound' has nothing to edit");
	}

	private static int indexOf(List<TraceText.Line> lines, String text) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).text().equals(text)) {
				return i;
			}
		}
		throw new AssertionError("no line " + text + " in " + lines);
	}
}
