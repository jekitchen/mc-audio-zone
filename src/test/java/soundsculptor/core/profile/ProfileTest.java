package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** A profile's places (PRD FR-8.6, FR-8.10): Everywhere and its zones, each with attached Sound Sets. */
class ProfileTest {
	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals",
			List.of(SetEntry.of("e1", "minecraft:entity.cow.ambient").withVolume(30)));
	private static final SoundSet LOUD = new SoundSet("set-loud", "Loud", List.of(SetEntry.of("e1", "x:a").withVolume(150)));
	private static final SetLibrary LIBRARY = new SetLibrary(List.of(FARM, LOUD));

	private static Zone zone(String id, PlaceSets sets) {
		return new Zone(id, "Zone " + id, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(10),
				sets);
	}

	private static Profile profile(PlaceSets everywhere, Zone... zones) {
		return new Profile("Default", ProfileSettings.DEFAULTS, everywhere, List.of(zones), Map.of());
	}

	@Test
	void placeIsEverywhereOrTheZonesSets() {
		PlaceSets a = PlaceSets.EMPTY.attach(FARM.id());
		Profile p = profile(quick(SetEntry.of("q1", "x:q").withMute(true)), zone("a", a));
		assertSame(p.everywhere(), p.place(PlaceRef.EVERYWHERE));
		assertSame(a, p.place(PlaceRef.zone("a")));
		assertNull(p.place(PlaceRef.zone("gone")));
	}

	@Test
	void withPlaceReplacesOnlyThatPlace() {
		Profile p = profile(PlaceSets.EMPTY, zone("a", PlaceSets.EMPTY), zone("b", PlaceSets.EMPTY));
		PlaceSets farm = PlaceSets.EMPTY.attach(FARM.id());
		Profile changed = p.withPlace(PlaceRef.zone("b"), farm);
		assertEquals(farm, changed.zone("b").sets());
		assertEquals(PlaceSets.EMPTY, changed.zone("a").sets());
		assertEquals(PlaceSets.EMPTY, changed.everywhere());
		assertEquals(farm, p.withPlace(PlaceRef.EVERYWHERE, farm).everywhere());
		assertSame(p, p.withPlace(PlaceRef.zone("gone"), farm), "a zone that is gone is not added");
	}

	@Test
	void placesUsingListsEverywhereFirstThenZonesInOrder() {
		PlaceSets farm = PlaceSets.EMPTY.attach(FARM.id());
		Profile p = profile(farm, zone("b", farm), zone("c", PlaceSets.EMPTY), zone("a", farm));
		assertEquals(List.of(PlaceRef.EVERYWHERE, PlaceRef.zone("b"), PlaceRef.zone("a")), p.placesUsing(FARM.id()));
		assertEquals(List.of(), p.placesUsing(LOUD.id()));
	}

	@Test
	void hasBoostLooksAtAttachedLibrarySetsAndQuickChanges() {
		assertFalse(profile(PlaceSets.EMPTY.attach(FARM.id())).hasBoost(LIBRARY));
		assertTrue(profile(PlaceSets.EMPTY.attach(LOUD.id())).hasBoost(LIBRARY), "an attached library set");
		assertTrue(profile(PlaceSets.EMPTY, zone("a", PlaceSets.EMPTY.attach(LOUD.id()))).hasBoost(LIBRARY), "in a zone");
		assertTrue(profile(quick(SetEntry.of("q", "x:q").withVolume(101))).hasBoost(LIBRARY), "Quick changes");
		assertFalse(profile(PlaceSets.EMPTY.attach(LOUD.id())).hasBoost(SetLibrary.EMPTY), "a set the library lacks");
		assertFalse(profile(PlaceSets.EMPTY).hasBoost(LIBRARY), "a set in the library but not attached");
	}

	@Test
	void anEmptyProfileHasNoSets() {
		Profile p = Profile.empty("Default");
		assertEquals(PlaceSets.EMPTY, p.everywhere());
		assertTrue(p.zones().isEmpty());
	}
}
