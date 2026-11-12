package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import soundsculptor.core.Message;
import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** A set deleted while a profile was not loaded is dropped from it on the next load, with a note (PRD FR-4.3). */
class ProfilePruneTest {
	private static final SoundSet KEPT = new SoundSet("set-kept", "Kept", List.of(SetEntry.of("e1", "x:a").withMute(true)));
	private static final SetLibrary LIBRARY = new SetLibrary(List.of(KEPT));

	private static Zone zone(String id, String name, PlaceSets sets) {
		return new Zone(id, name, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(10), sets);
	}

	private static Profile profile(PlaceSets everywhere, Zone... zones) {
		return new Profile("Default", ProfileSettings.DEFAULTS, everywhere, List.of(zones), Map.of());
	}

	@Test
	void missingSetIsDroppedWithOneNote() {
		PlaceSets base = quick(SetEntry.of("q1", "x:q").withMute(true)).attach("set-farm").attach(KEPT.id());
		Profile p = profile(PlaceSets.EMPTY.attach(KEPT.id()), zone("z1", "Main base", base));
		ProfilePrune.Pruned pruned = ProfilePrune.dropMissingSets(p, LIBRARY, Map.of("set-farm", "Farm animals"));

		assertEquals(List.of(Message.of(ProfilePrune.NOTE_REMOVED, "Farm animals", "Main base")), pruned.notes());
		assertEquals("'Farm animals' was deleted, so it was removed from Main base",
				pruned.notes().get(0).text(new TestEnglish()));
		assertEquals(List.of(new Attachment(SoundSet.QUICK_ID, true), new Attachment(KEPT.id(), true)),
				pruned.profile().zone("z1").sets().attached(), "Quick changes and the other set stay, in order");
		assertEquals(base.quickChanges(), pruned.profile().zone("z1").sets().quickChanges());
		assertSame(p.everywhere(), pruned.profile().everywhere());
	}

	@Test
	void unknownMissingSetUsesUnnamedNote() {
		Profile p = profile(PlaceSets.EMPTY.attach("set-gone"));
		ProfilePrune.Pruned pruned = ProfilePrune.dropMissingSets(p, LIBRARY, Map.of());
		assertEquals(PlaceSets.EMPTY, pruned.profile().everywhere());
		assertEquals(1, pruned.notes().size());
		assertEquals("A Sound Set that no longer exists was removed from Everywhere",
				pruned.notes().get(0).text(new TestEnglish()));
	}

	@Test
	void nothingMissingNoNotes() {
		Profile p = profile(PlaceSets.EMPTY.attach(KEPT.id()), zone("z1", "Main base", quick(SetEntry.of("q1", "x:q"))));
		ProfilePrune.Pruned pruned = ProfilePrune.dropMissingSets(p, LIBRARY, Map.of("set-farm", "Farm animals"));
		assertSame(p, pruned.profile());
		assertEquals(List.of(), pruned.notes());
	}

	@Test
	void everyPlaceIsCheckedEverywhereFirst() {
		Profile p = profile(PlaceSets.EMPTY.attach("set-a"), zone("z1", "", PlaceSets.EMPTY.attach("set-a")),
				zone("z2", "Barn", PlaceSets.EMPTY.attach("set-b")));
		ProfilePrune.Pruned pruned = ProfilePrune.dropMissingSets(p, SetLibrary.EMPTY, Map.of("set-a", "A", "set-b", "B"));
		TestEnglish en = new TestEnglish();
		assertEquals(List.of("'A' was deleted, so it was removed from Everywhere",
				"'A' was deleted, so it was removed from z1",
				"'B' was deleted, so it was removed from Barn"),
				pruned.notes().stream().map(m -> m.text(en)).toList(), "a zone with no name is named by its ID");
	}
}
