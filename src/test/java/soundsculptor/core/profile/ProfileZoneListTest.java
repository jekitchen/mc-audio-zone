package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Zones tab's list operations and zone rule lists (PRD 6.1 Zones, FR-8.6, FR-9 step 5). */
class ProfileZoneListTest {
	private static Zone zone(String id) {
		return new Zone(id, "Zone " + id, true, "world:1", "minecraft:overworld", new Point(0, 64, 0), new Shape.Cylinder(10, 20), PlaceSets.EMPTY);
	}

	private static List<String> ids(Profile p) {
		return p.zones().stream().map(Zone::id).toList();
	}

	private static Profile profile(Zone... zones) {
		return Profile.empty("Default").withZones(List.of(zones));
	}

	@Test
	void putAddsOrReplacesInPlace() {
		Profile p = profile(zone("z1"), zone("z2")).putZone(zone("z3"));
		assertEquals(List.of("z1", "z2", "z3"), ids(p));
		Zone renamed = new Zone("z2", "Hall", true, "world:1", "minecraft:overworld", new Point(1, 2, 3), new Shape.Box(4, 5, 6), PlaceSets.EMPTY);
		p = p.putZone(renamed);
		assertEquals(List.of("z1", "z2", "z3"), ids(p));
		assertSame(renamed, p.zone("z2"));
	}

	@Test
	void removeDeletesTheZoneAndItsColor() {
		Profile p = profile(zone("z1"), zone("z2")).withZoneColor("z2", 0xE69F00);
		p = p.removeZone("z2");
		assertEquals(List.of("z1"), ids(p));
		assertNull(p.colorOf("z2"));
		assertSame(p, p.removeZone("missing"));
	}

	@Test
	void moveChangesOrderAndStopsAtTheEnds() {
		Profile p = profile(zone("z1"), zone("z2"), zone("z3"));
		assertEquals(List.of("z1", "z3", "z2"), ids(p.moveZone("z3", -1)));
		assertSame(p, p.moveZone("z1", -1));
		assertSame(p, p.moveZone("z3", 1));
	}

	@Test
	void newZoneIdIsNotInUse() {
		assertEquals("z1", Profile.empty("Default").newZoneId());
		assertEquals("z3", profile(zone("z1"), zone("z2")).newZoneId());
		assertEquals("z1", profile(zone("z2")).newZoneId());
	}
}
