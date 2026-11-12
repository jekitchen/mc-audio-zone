package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD 7.5: zones in the profile and in the profile file. */
class ProfileZonesTest {
	static final String SERVER = "play.example.net";
	static final String OVERWORLD = "minecraft:overworld";

	static Zone fixed(String id, Shape shape) {
		return new Zone(id, "Zone " + id, true, SERVER, OVERWORLD, new Point(120.5, 64, -33.5), shape,
				List.of(SetEntry.of(id + "r1", "*villager*").withVolume(50)));
	}

	/** A bubble as the file stores it: no world key, dimension or position. */
	static Zone bubble(String id) {
		return new Zone(id, "Bubble " + id, true, "", "", new Point(0, 0, 0), new Shape.Sphere(8),
				List.of(SetEntry.of(id + "r1", "*").withMute(true))).asBubble();
	}

	@Test
	void threeArgumentConstructorStillWorksWithNoZones() {
		Profile p = new Profile("A", ProfileSettings.DEFAULTS, List.of());
		assertTrue(p.zones().isEmpty());
		assertTrue(p.zoneColors().isEmpty());
	}

	@Test
	void quickActionsKeepTheZones() {
		Profile p = Profile.empty("A").withZones(List.of(fixed("z1", new Shape.Sphere(10))))
				.withZoneColor("z1", 0xE69F00);
		Profile muted = p.muteExact("minecraft:a").withSettings(new ProfileSettings(false)).unmuteExact("minecraft:a");
		assertEquals(p.zones(), muted.zones());
		assertEquals(p.zoneColors(), muted.zoneColors());
	}

	@Test
	void colorsMustBelongToAZone() {
		assertThrows(IllegalArgumentException.class, () -> new Profile("A", ProfileSettings.DEFAULTS, List.of(),
				List.of(), Map.of("nope", 0xE69F00)));
	}

	@Test
	void replacingZonesDropsColorsOfRemovedZones() {
		Profile p = Profile.empty("A").withZones(List.of(fixed("z1", new Shape.Sphere(10)), fixed("z2", new Shape.Sphere(5))))
				.withZoneColor("z1", 1).withZoneColor("z2", 2);
		Profile q = p.withZones(List.of(p.zones().get(1)));
		assertEquals(Map.of("z2", 2), q.zoneColors());
		assertNull(q.colorOf("z1"));
		assertEquals(2, q.colorOf("z2"));
	}

	@Test
	void zonesRoundTripThroughTheFile() throws ProfileFormatException {
		Zone sphere = fixed("z1", new Shape.Sphere(12.5));
		Zone box = fixed("z2", new Shape.Box(20, 8, 30)).withEdge(ZoneEdge.fade(6));
		Zone cylinder = new Zone("z3", "Tower", false, SERVER, "minecraft:the_nether", new Point(-5, 70, 9),
				new Shape.Cylinder(4, 40), List.of(new SetEntry("r9", false, soundsculptor.core.sets.SoundPattern.of("minecraft:block.*"),
						null, 150, new Limit(2, 500, true), false)))
				.withMode(ZoneMode.OUTSIDE).withEdge(ZoneEdge.hard());
		Profile p = Profile.empty("Server").withZones(List.of(sphere, box, cylinder, bubble("b1")))
				.withZoneColor("z1", 0xE69F00).withZoneColor("b1", 0x123456);
		Profile back = ProfileCodec.fromJson(ProfileCodec.toJson(p));
		assertEquals(p, back);
	}

	@Test
	void fileMatchesTheSketchAndOmitsUnsetProperties() {
		Profile p = Profile.empty("Server").withZones(List.of(fixed("z1", new Shape.Box(20, 8, 30)), bubble("b1")))
				.withZoneColor("z1", 0xE69F00);
		String json = ProfileCodec.toJson(p);
		assertTrue(json.contains("\"color\": \"#E69F00\""), json);
		assertTrue(json.contains("\"type\": \"fixed\""), json);
		assertTrue(json.contains("\"worldKey\": \"play.example.net\""), json);
		assertTrue(json.contains("\"type\": \"box\""), json);
		assertTrue(json.contains("\"mode\": \"inside\""), json);
		assertTrue(json.contains("\"type\": \"bubble\""), json);
		assertFalse(json.contains("\"fade\""), "a default edge has no fade distance: " + json);
		// The bubble has no color, and its anchor carries no world data.
		String bubblePart = json.substring(json.indexOf("\"b1\""));
		assertFalse(bubblePart.contains("color"), bubblePart);
		assertFalse(bubblePart.contains("worldKey"), bubblePart);
	}
}
