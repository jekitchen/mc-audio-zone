package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static soundsculptor.core.profile.ProfileZonesTest.SERVER;
import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import soundsculptor.core.TestEnglish;

import org.junit.jupiter.api.Test;

/** PRD FR-11.2 copy settings from another server, FR-11.3 rename, reset and delete. */
class ProfileOpsTest {
	private static final TestEnglish EN = new TestEnglish();

	private static Zone fixedOn(String id, String worldKey) {
		return new Zone(id, "Z", true, worldKey, "minecraft:overworld", new Point(1, 2, 3), new Shape.Cylinder(5, 10), PlaceSets.EMPTY);
	}

	/** Server A: 2 global rules and two zones. */
	private static final Profile A = new Profile("Server A", new ProfileSettings(false),
			quick(List.of(SetEntry.of("a1", "x:one").withMute(true), SetEntry.of("a2", "x:two").withVolume(30))),
			List.of(fixedOn("aFixed", "a.net"), fixedOn("aOther", "a.net")), Map.of("aFixed", 1, "aOther", 2));

	/** Server B: its own rule and two zones. */
	private static final Profile B = new Profile("Server B", ProfileSettings.DEFAULTS, quick(List.of(SetEntry.of("b1", "x:b"))),
			List.of(fixed("bFixed", new Shape.Cylinder(6, 12)), fixed("bOther", new Shape.Box(8, 8, 8))),
			Map.of("bFixed", 3, "bOther", 4));

	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)));
	private static final SetLibrary LIB = new SetLibrary(List.of(FARM));

	private static List<String> ids(PlaceSets place) {
		return place.attached().stream().map(Attachment::setId).toList();
	}

	@Test
	void copyFromLinksLibrarySetsAndCopiesQuick() {
		Profile source = A.withEverywhere(A.everywhere().attach("set-farm").attach("set-gone"));
		ProfileOps.Copy copy = ProfileOps.copyFrom(source, B, LIB);
		Profile result = copy.result();
		assertEquals(List.of(SoundSet.QUICK_ID, "set-farm"), ids(result.everywhere()),
				"the same library set is attached (linked, not copied); a set the library lacks is dropped");
		assertEquals(source.everywhere().quickChanges(), result.everywhere().quickChanges(), "Quick changes is copied as it is");
		assertEquals(2, copy.setCount(), "Quick changes and Farm animals");
	}

	@Test
	void copyFromKeepsTargetZones() {
		Profile target = B.withZones(List.of(fixed("bFixed", new Shape.Cylinder(6, 12)).withSets(PlaceSets.EMPTY.attach("set-farm")),
				fixed("bOther", new Shape.Box(8, 8, 8)))).withZoneColor("bFixed", 3);
		Profile source = A.withZones(List.of(fixedOn("aFixed", SERVER).withSets(PlaceSets.EMPTY.attach("set-farm"))));
		ProfileOps.Copy copy = ProfileOps.copyFrom(source, target, LIB);
		assertEquals(target.zones(), copy.result().zones(), "the target's zones keep their sets; none are copied");
		assertEquals(3, copy.result().colorOf("bFixed"));
	}

	@Test
	void copyBringsRulesAndSettingsButNeverZones() {
		ProfileOps.Copy copy = ProfileOps.copyFrom(A, B, LIB);
		Profile result = copy.result();
		assertEquals("Server B", result.name());
		assertEquals(A.everywhere().quickEntries(), result.everywhere().quickEntries());
		assertEquals(A.settings(), result.settings());
		assertEquals(List.of("bFixed", "bOther"), result.zones().stream().map(Zone::id).toList());
		assertEquals(B.zoneColors(), result.zoneColors(), "B's zones are untouched");
	}

	@Test
	void copySummaryIsWhatTheDialogLists() {
		ProfileOps.Copy copy = ProfileOps.copyFrom(A, B, LIB);
		assertEquals(1, copy.setCount(), "Everywhere's Quick changes");
	}

	@Test
	void copyingABoostAsksForTheBoostConfirmFirst() {
		// FR-5.4: the first time a profile goes above 100%, a dialog needs a confirm, however the boost arrives.
		Profile loud = A.withEverywhere(quick(List.of(SetEntry.of("a1", "x:one").withVolume(200))));
		assertTrue(ProfileOps.copyFrom(loud, B, LIB).needsBoostConfirm(SetLibrary.EMPTY));
		assertFalse(ProfileOps.copyFrom(loud, B.withBoostConfirmed(true), LIB).needsBoostConfirm(SetLibrary.EMPTY));
		assertFalse(ProfileOps.copyFrom(A, B, LIB).needsBoostConfirm(SetLibrary.EMPTY));
	}

	@Test
	void copyIsOneTime() {
		Profile copied = ProfileOps.copyFrom(A, B, LIB).result();
		Profile aChanged = A.withEverywhere(quick(SetEntry.of("exact:" + "x:new", "x:new").withMute(true)));
		assertEquals(A.everywhere().quickEntries(), copied.everywhere().quickEntries());
		assertFalse(copied.everywhere().quickEntries().equals(aChanged.everywhere().quickEntries()));
	}

	@Test
	void copyingFromTheSameServerStillSkipsItsZones() {
		// Zones are never copied, even ones made for the target's own server.
		Profile source = A.withZones(List.of(fixedOn("same", SERVER)));
		ProfileOps.Copy copy = ProfileOps.copyFrom(source, B, LIB);
		assertEquals(List.of("bFixed", "bOther"), copy.result().zones().stream().map(Zone::id).toList());
	}

	@Test
	void aProfileWithTheMostZonesCanStillCopy() {
		// Copy brings no zones, so a target at the 64-zone limit keeps exactly its own.
		List<Zone> fixedZones = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			fixedZones.add(fixedOn("f" + i, SERVER));
		}
		Profile target = Profile.empty("T").withZones(fixedZones);
		ProfileOps.Copy copy = ProfileOps.copyFrom(A, target, LIB);
		assertEquals(target.zones(), copy.result().zones());
		assertEquals(A.everywhere(), copy.result().everywhere());
	}

	@Test
	void renameChangesOnlyTheDisplayName() {
		Profile renamed = ProfileOps.rename(A, "  Friends' SMP ");
		assertEquals("Friends' SMP", renamed.name());
		assertEquals(A.withName("Friends' SMP"), renamed);
	}

	@Test
	void renameRefusesEmptyOrLongNames() {
		assertEquals("Enter a name.", ProfileOps.nameProblem("   ").text(EN));
		assertEquals("The name can be at most 64 characters.", ProfileOps.nameProblem("x".repeat(65)).text(EN));
		assertNull(ProfileOps.nameProblem("x".repeat(64)));
		assertThrows(IllegalArgumentException.class, () -> ProfileOps.rename(A, ""));
	}

	@Test
	void resetGoesBackToEmptyDefaultsAndKeepsTheName() {
		Profile reset = ProfileOps.reset(A);
		assertEquals(Profile.empty("Server A"), reset);
		assertTrue(reset.everywhere().quickEntries().isEmpty());
		assertTrue(reset.zones().isEmpty());
		assertEquals(ProfileSettings.DEFAULTS, reset.settings());
	}

	@Test
	void theConnectedServersProfileCannotBeDeleted() {
		assertFalse(ProfileOps.canDelete("play.example.net", "play.example.net"));
		assertFalse(ProfileOps.canDelete("Play.Example.net", "play.example.NET"));
		assertTrue(ProfileOps.canDelete("other.net", "play.example.net"));
		assertTrue(ProfileOps.canDelete("other.net", null), "not connected: any profile can be deleted");
	}
}
