package soundsculptor.core.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileSettings;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD FR-1.5: the "Apply to" selector offers Everywhere and the zones the player is standing in. */
class ApplyToTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final Point HOME = new Point(100, 64, 100);

	private static Zone cylinder(String id, String world, String dimension, Point anchor, double radius) {
		return new Zone(id, "Zone " + id, true, world, dimension, anchor, new Shape.Cylinder(radius, 2 * radius), PlaceSets.EMPTY);
	}

	private static Profile profile(Zone... zones) {
		return new Profile("p", ProfileSettings.DEFAULTS, PlaceSets.EMPTY).withZones(List.of(zones));
	}

	private static List<String> ids(List<ApplyTarget> targets) {
		return targets.stream().map(t -> t.isEverywhere() ? "everywhere" : t.zoneId()).toList();
	}

	@Test
	void everywhereIsAlwaysFirstAndAloneWithNoZones() {
		List<ApplyTarget> choices = ApplyTo.choices(profile(), WORLD, OVERWORLD, HOME);
		assertEquals(List.of(ApplyTarget.EVERYWHERE), choices);
		assertEquals("soundsculptor.sounds.apply_to.everywhere", choices.get(0).translationKey());
		assertEquals(List.of(), choices.get(0).args());
	}

	@Test
	void offersOnlyZonesThePlayerIsStandingInInProfileListOrder() {
		Zone far = cylinder("far", WORLD, OVERWORLD, new Point(500, 64, 500), 10);
		Zone big = cylinder("big", WORLD, OVERWORLD, HOME, 50);
		Zone small = cylinder("small", WORLD, OVERWORLD, new Point(103, 64, 100), 5);
		assertEquals(List.of("everywhere", "big", "small"), ids(ApplyTo.choices(profile(far, big, small), WORLD, OVERWORLD, HOME)));
		assertEquals(List.of("everywhere", "small", "big"), ids(ApplyTo.choices(profile(small, far, big), WORLD, OVERWORLD, HOME)));
	}

	@Test
	void fixedZonesCountOnlyInTheirOwnWorldAndDimension() {
		Zone otherServer = cylinder("other", "other.example.net", OVERWORLD, HOME, 50);
		Zone nether = cylinder("nether", WORLD, NETHER, HOME, 50);
		Zone here = cylinder("here", WORLD, OVERWORLD, HOME, 50);
		assertEquals(List.of("everywhere", "here"), ids(ApplyTo.choices(profile(otherServer, nether, here), WORLD, OVERWORLD, HOME)));
		assertEquals(List.of("everywhere", "nether"), ids(ApplyTo.choices(profile(otherServer, nether, here), WORLD, NETHER, HOME)));
	}

	@Test
	void theEdgeOfAZoneCountsAsInside() {
		Zone edge = cylinder("edge", WORLD, OVERWORLD, new Point(110, 64, 100), 10);
		assertEquals(List.of("everywhere", "edge"), ids(ApplyTo.choices(profile(edge), WORLD, OVERWORLD, HOME)));
	}

	@Test
	void aFadeEdgeStillCountsAsInsideAtTheBoundary() {
		Zone fading = cylinder("fading", WORLD, OVERWORLD, new Point(109.9, 64, 100), 10).withEdge(ZoneEdge.fade(8));
		assertEquals(List.of("everywhere", "fading"), ids(ApplyTo.choices(profile(fading), WORLD, OVERWORLD, HOME)));
	}


	@Test
	void outsideZonesAreOfferedWhenThePlayerIsOnTheirAffectedSide() {
		Zone base = cylinder("base", WORLD, OVERWORLD, HOME, 20).withMode(ZoneMode.OUTSIDE);
		assertEquals(List.of("everywhere"), ids(ApplyTo.choices(profile(base), WORLD, OVERWORLD, HOME)),
				"standing inside the base, the Outside zone does not affect sounds around you");
		assertEquals(List.of("everywhere", "base"),
				ids(ApplyTo.choices(profile(base), WORLD, OVERWORLD, new Point(200, 64, 100))));
	}

	@Test
	void disabledZonesAreNotOffered() {
		Zone off = new Zone("off", "Off", false, WORLD, OVERWORLD, HOME, new Shape.Cylinder(50, 100), PlaceSets.EMPTY);
		assertEquals(List.of("everywhere"), ids(ApplyTo.choices(profile(off), WORLD, OVERWORLD, HOME)));
	}

	@Test
	void zoneLabelsUseTheZoneName() {
		ApplyTarget target = ApplyTo.choices(profile(cylinder("z1", WORLD, OVERWORLD, HOME, 5)), WORLD, OVERWORLD, HOME).get(1);
		assertEquals("soundsculptor.sounds.apply_to.zone", target.translationKey());
		assertEquals(List.of("Zone z1"), target.args());
	}

	@Test
	void eachChoiceIsAPlaceWhoseQuickChangesTheActionsEdit() {
		List<ApplyTarget> choices = ApplyTo.choices(profile(cylinder("z1", WORLD, OVERWORLD, HOME, 5)), WORLD, OVERWORLD, HOME);
		assertEquals(List.of(PlaceRef.EVERYWHERE, PlaceRef.zone("z1")), choices.stream().map(ApplyTarget::place).toList());
	}

	@Test
	void selectKeepsTheLastChoiceWhileItIsStillOffered() {
		Zone a = cylinder("a", WORLD, OVERWORLD, HOME, 5);
		Zone b = cylinder("b", WORLD, OVERWORLD, HOME, 10);
		List<ApplyTarget> choices = ApplyTo.choices(profile(a, b), WORLD, OVERWORLD, HOME);
		assertSame(choices.get(2), ApplyTo.select(choices, "b"));
		assertSame(ApplyTarget.EVERYWHERE, ApplyTo.select(choices, null));
	}

	@Test
	void selectFallsBackToEverywhereWhenTheLastZoneIsNoLongerOffered() {
		List<ApplyTarget> choices = ApplyTo.choices(profile(cylinder("a", WORLD, OVERWORLD, HOME, 5)), WORLD, OVERWORLD,
				new Point(0, 64, 0));
		assertSame(ApplyTarget.EVERYWHERE, ApplyTo.select(choices, "a"));
		assertTrue(ApplyTo.select(choices, "gone").isEverywhere());
	}
}
