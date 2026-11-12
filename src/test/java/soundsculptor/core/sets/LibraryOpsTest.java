package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileSettings;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/** New, Duplicate, Rename and Delete in the library (FR-4.7, FR-4.11). */
class LibraryOpsTest {
	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals", List.of(SetEntry.of("e1", "x:cow").withVolume(30)));

	private static Zone zone(String id, PlaceSets sets) {
		return new Zone(id, "Zone " + id, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(10), sets);
	}

	private static Workspace ws(PlaceSets everywhere, SetLibrary library, Zone... zones) {
		return new Workspace(new Profile("Here", ProfileSettings.DEFAULTS, everywhere, List.of(zones), Map.of()), library);
	}

	@Test
	void duplicateIsIndependentAndUnattached() {
		PlaceSets farm = PlaceSets.EMPTY.attach("set-farm");
		Workspace start = ws(farm, SetLibrary.EMPTY.put(FARM));
		NewSet result = LibraryOps.duplicate(start, "set-farm");
		Workspace after = result.workspace();
		assertEquals(2, after.library().size());
		SoundSet copy = after.library().sets().stream().filter(s -> !s.id().equals("set-farm")).findFirst().orElseThrow();
		assertEquals("Farm animals (copy)", copy.name());
		assertEquals(copy.id(), result.setId(), "the result names the copy");
		assertEquals("Farm animals (copy)", result.name());
		assertNotEquals("set-farm", copy.id());
		assertEquals(FARM.entries(), copy.entries());
		assertEquals(List.of(PlaceRef.EVERYWHERE), after.profile().placesUsing("set-farm"));
		assertEquals(List.of(), after.profile().placesUsing(copy.id()), "attached nowhere");
		assertSame(FARM, after.library().get("set-farm"), "the original is untouched");
		Workspace twice = LibraryOps.duplicate(after, "set-farm").workspace();
		assertTrue(twice.library().sets().stream().anyMatch(s -> s.name().equals("Farm animals (copy) (2)")));
	}

	@Test
	void duplicateOfA64CharacterNameStillFits() {
		String name = "x".repeat(64);
		Workspace start = ws(PlaceSets.EMPTY, SetLibrary.EMPTY.put(new SoundSet("a", name, List.of())));
		Workspace after = LibraryOps.duplicate(start, "a").workspace();
		Workspace third = LibraryOps.duplicate(after, "a").workspace();
		for (SoundSet s : third.library().sets()) {
			assertTrue(s.name().length() <= SoundSet.MAX_NAME_LENGTH, s.name());
		}
		assertEquals(3, third.library().size());
		assertTrue(third.library().sets().stream().anyMatch(s -> s.name().equals("x".repeat(57) + " (copy)")));
		assertTrue(third.library().sets().stream().anyMatch(s -> s.name().equals("x".repeat(53) + " (copy) (2)")));
	}

	@Test
	void uniqueNameFitsAndAgreesWithTheSort() {
		String name = "y".repeat(64);
		SetLibrary lib = SetLibrary.EMPTY.put(new SoundSet("a", name, List.of()));
		String second = lib.uniqueName(name, null);
		assertEquals("y".repeat(60) + " (2)", second);
		lib = lib.put(new SoundSet("b", second, List.of()));
		assertEquals("y".repeat(60) + " (3)", lib.uniqueName(name, null));
		assertEquals(name, lib.uniqueName(name, "a"), "its own name is not a clash");
		// The clash check lowercases the way the sort does.
		assertEquals("Farm (2)", SetLibrary.EMPTY.put(new SoundSet("c", "FARM", List.of())).uniqueName("Farm", null));
		// A cut never ends inside a surrogate pair.
		String emoji = "a".repeat(59) + "😀" + "b";
		SetLibrary withEmoji = SetLibrary.EMPTY.put(new SoundSet("e", emoji, List.of()));
		String cut = withEmoji.uniqueName(emoji, null);
		assertTrue(cut.length() <= SoundSet.MAX_NAME_LENGTH);
		assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - " (2)".length() - 1)));
	}

	@Test
	void deletePlanListsZonesHereAndOtherProfileNames() {
		PlaceSets farm = PlaceSets.EMPTY.attach("set-farm");
		Workspace here = ws(PlaceSets.EMPTY, SetLibrary.EMPTY.put(FARM), zone("A", farm), zone("x", PlaceSets.EMPTY), zone("B", farm));
		Profile other = new Profile("Creative test world", ProfileSettings.DEFAULTS, PlaceSets.EMPTY,
				List.of(zone("secret", farm)), Map.of());
		SetUsage usage = SetUsage.of(Map.of("world:here", here.profile(), "world:other", other));
		LibraryOps.DeletePlan plan = LibraryOps.planDelete(here, usage, "world:here", "set-farm");
		assertEquals("Farm animals", plan.name());
		assertEquals(List.of(PlaceRef.zone("A"), PlaceRef.zone("B")), plan.placesHere());
		assertEquals(List.of("Creative test world"), plan.otherProfiles());
	}

	@Test
	void deletePlanUsesTheLiveProfileForPlacesHere() {
		PlaceSets farm = PlaceSets.EMPTY.attach("set-farm");
		Workspace live = ws(farm, SetLibrary.EMPTY.put(FARM));
		assertEquals(List.of(PlaceRef.EVERYWHERE), LibraryOps.planDelete(live, SetUsage.EMPTY, "world:here", "set-farm").placesHere());
	}

	@Test
	void deleteDetachesEverywhereInLoadedProfile() {
		PlaceSets farm = PlaceSets.EMPTY.attach("set-farm");
		Workspace start = ws(farm, SetLibrary.EMPTY.put(FARM), zone("a", farm), zone("b", PlaceSets.EMPTY));
		Workspace after = LibraryOps.delete(start, "set-farm");
		assertFalse(after.library().contains("set-farm"));
		assertEquals(List.of(), after.profile().placesUsing("set-farm"));
		assertFalse(after.profile().everywhere().uses("set-farm"));
	}

	@Test
	void renameKeepsId() {
		Workspace start = ws(PlaceSets.EMPTY, SetLibrary.EMPTY.put(FARM).put(new SoundSet("b", "Loud", List.of())));
		Workspace after = LibraryOps.rename(start, "set-farm", "  Barn  ");
		assertEquals("Barn", after.library().get("set-farm").name());
		assertEquals(FARM.entries(), after.library().get("set-farm").entries());
		assertEquals("loud (2)", LibraryOps.rename(start, "set-farm", "loud").library().get("set-farm").name());
		assertEquals("farm ANIMALS", LibraryOps.rename(start, "set-farm", "farm ANIMALS").library().get("set-farm").name(),
				"changing only the case of its own name is allowed");
		assertSame(start, LibraryOps.rename(start, "set-farm", "Farm animals"), "same name changes nothing");
	}

	@Test
	void renameRefusesBadNamesWithoutThrowing() {
		Workspace start = ws(PlaceSets.EMPTY, SetLibrary.EMPTY.put(FARM));
		assertSame(start, LibraryOps.rename(start, "set-farm", "   "));
		assertNotNull(LibraryOps.canRename(start, "set-farm", "   "));
		assertSame(start, LibraryOps.rename(start, "set-farm", "z".repeat(65)));
		assertNotNull(LibraryOps.canRename(start, "set-farm", "z".repeat(65)));
		assertNull(LibraryOps.canRename(start, "set-farm", "z".repeat(64)));
		assertSame(start, LibraryOps.rename(start, "gone", "Name"));
	}

	@Test
	void createMakesAnEmptyUnattachedSet() {
		Workspace start = ws(PlaceSets.EMPTY, SetLibrary.EMPTY.put(FARM));
		NewSet result = LibraryOps.create(start, " farm animals ");
		Workspace after = result.workspace();
		assertEquals(2, after.library().size());
		assertEquals("farm animals (2)", result.name());
		assertTrue(after.library().get(result.setId()).entries().isEmpty());
		assertEquals(List.of(), after.profile().placesUsing(result.setId()), "attached nowhere");
	}

	@Test
	void createBeyond256Refused() {
		List<SoundSet> sets = new ArrayList<>();
		for (int i = 0; i < SetLibrary.MAX_SETS; i++) {
			sets.add(new SoundSet("s" + i, "N" + i, List.of()));
		}
		Workspace full = ws(PlaceSets.EMPTY, new SetLibrary(sets));
		assertSame(full, LibraryOps.create(full, "One more").workspace());
		assertEquals("soundsculptor.sets.error.library_full", LibraryOps.create(full, "One more").problem().key());
		assertEquals("soundsculptor.sets.error.library_full", LibraryOps.canCreate(full, "One more").key());
		assertSame(full, LibraryOps.duplicate(full, "s0").workspace());
		assertNull(LibraryOps.duplicate(full, "s0").setId());
		assertEquals("soundsculptor.sets.error.library_full", LibraryOps.canDuplicate(full, "s0").key());
		assertNotNull(LibraryOps.canCreate(ws(PlaceSets.EMPTY, SetLibrary.EMPTY), ""));
	}

	@Test
	void duplicateOfAMissingSetIsRefused() {
		Workspace start = ws(PlaceSets.EMPTY, SetLibrary.EMPTY);
		NewSet result = LibraryOps.duplicate(start, "gone");
		assertSame(start, result.workspace());
		assertEquals("soundsculptor.sets.error.not_found", result.problem().key());
	}

	@Test
	void createAttachedMakesTheSetAndAttachesItAtTheBottom() {
		Workspace start = ws(PlaceSets.EMPTY.attach("set-farm"), SetLibrary.EMPTY.put(FARM));
		NewSet result = LibraryOps.createAttached(start, PlaceRef.EVERYWHERE, "Barn");
		assertTrue(result.ok());
		assertEquals("Barn", result.name());
		assertEquals(List.of("set-farm", result.setId()),
				result.workspace().profile().everywhere().attached().stream().map(Attachment::setId).toList());
	}

	@Test
	void createAttachedRefusesAFullPlaceAGoneZoneAndABadName() {
		List<SoundSet> sets = new ArrayList<>();
		PlaceSets place = PlaceSets.EMPTY;
		for (int i = 0; i < PlaceSets.MAX_SETS; i++) {
			sets.add(new SoundSet("s" + i, "N" + i, List.of()));
			place = place.attach("s" + i);
		}
		Workspace full = ws(place, new SetLibrary(sets));
		assertEquals("soundsculptor.sets.error.place_full", LibraryOps.canCreateAttached(full, PlaceRef.EVERYWHERE, "More").key());
		NewSet refused = LibraryOps.createAttached(full, PlaceRef.EVERYWHERE, "More");
		assertSame(full, refused.workspace(), "nothing is added to the library either");
		assertEquals("soundsculptor.sets.error.place_full", refused.problem().key());

		Workspace start = ws(PlaceSets.EMPTY, SetLibrary.EMPTY);
		assertEquals("soundsculptor.place_sets.zone_gone", LibraryOps.createAttached(start, PlaceRef.zone("gone"), "More").problem().key());
		assertNotNull(LibraryOps.createAttached(start, PlaceRef.EVERYWHERE, " ").problem());
	}
}
