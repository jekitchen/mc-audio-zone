package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileSettings;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/** Where each library set is used across stored profiles (FR-4.7, FR-4.11). */
class SetUsageTest {
	private static final PlaceSets FARM = PlaceSets.EMPTY.attach("set-farm");

	private static Zone zone(String id, PlaceSets sets) {
		return new Zone(id, "Zone " + id, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(10), sets);
	}

	private static Profile profile(String name, PlaceSets everywhere, Zone... zones) {
		return new Profile(name, ProfileSettings.DEFAULTS, everywhere, List.of(zones), Map.of());
	}

	@Test
	void countsPlacesHereAndOtherProfiles() {
		Map<String, Profile> all = Map.of(
				"world:here", profile("Here", FARM, zone("b", FARM), zone("c", PlaceSets.EMPTY), zone("a", FARM)),
				"world:other", profile("Other", PlaceSets.EMPTY, zone("z", FARM)),
				"world:none", profile("None", PlaceSets.EMPTY));
		SetUsage usage = SetUsage.of(all);
		assertEquals(List.of(PlaceRef.EVERYWHERE, PlaceRef.zone("b"), PlaceRef.zone("a")), usage.placesHere("set-farm", "world:here"));
		assertEquals(1, usage.otherCount("set-farm", "world:here"));
		assertEquals(2, usage.otherCount("set-farm", "world:none"));
		assertEquals(List.of(), usage.placesHere("set-farm", "world:none"));
		assertEquals(List.of(), usage.placesHere("set-unused", "world:here"));
		assertEquals(0, usage.otherCount("set-unused", "world:here"));
	}

	@Test
	void otherPlaceCountSumsThePlacesInOtherProfiles() {
		Map<String, Profile> all = Map.of(
				"world:here", profile("Here", FARM, zone("a", FARM)),
				"world:other", profile("Other", PlaceSets.EMPTY, zone("y", FARM), zone("z", FARM)));
		SetUsage usage = SetUsage.of(all);
		assertEquals(2, usage.otherPlaceCount("set-farm", "world:here"), "one other profile using the set in 2 places");
		assertEquals(1, usage.otherCount("set-farm", "world:here"));
		assertEquals(4, usage.otherPlaceCount("set-farm", "world:none"), "every profile is another one");
		assertEquals(2, usage.otherPlaceCount("set-farm", "WORLD:HERE"), "world keys compare ignoring case, as for otherCount");
		assertEquals(0, usage.otherPlaceCount("set-unused", "world:here"));
	}

	@Test
	void otherNamesAreDisplayNamesOnly() {
		Map<String, Profile> all = Map.of(
				"world:here", profile("Here", FARM),
				"world:b", profile("creative test world", FARM, zone("secret", FARM)),
				"world:a", profile("Survival", FARM),
				"world:c", profile("Apple server", PlaceSets.EMPTY, zone("x", FARM)));
		SetUsage usage = SetUsage.of(all);
		assertEquals(List.of("Apple server", "creative test world", "Survival"), usage.otherProfileNames("set-farm", "world:here"));
	}

	@Test
	void quickChangesIsNotCounted() {
		PlaceSets quick = PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("q", "x:a")));
		SetUsage usage = SetUsage.of(Map.of("world:here", profile("Here", quick)));
		assertEquals(Map.of(), usage.bySet());
	}
}
