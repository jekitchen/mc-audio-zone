package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static soundsculptor.core.profile.ProfileZonesTest.SERVER;
import static soundsculptor.core.profile.ProfileZonesTest.bubble;
import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import soundsculptor.core.TestEnglish;

import org.junit.jupiter.api.Test;

/** PRD FR-11.2 copy settings from another server, FR-11.3 rename, reset and delete. */
class ProfileOpsTest {
	private static final TestEnglish EN = new TestEnglish();

	private static Zone fixedOn(String id, String worldKey) {
		return new Zone(id, "Z", true, worldKey, "minecraft:overworld", new Point(1, 2, 3), new Shape.Sphere(5), PlaceSets.EMPTY);
	}

	/** Server A: 2 global rules, a fixed zone and a bubble. */
	private static final Profile A = new Profile("Server A", new ProfileSettings(false),
			quick(List.of(SetEntry.of("a1", "x:one").withMute(true), SetEntry.of("a2", "x:two").withVolume(30))),
			List.of(fixedOn("aFixed", "a.net"), bubble("aBubble")), Map.of("aFixed", 1, "aBubble", 2));

	/** Server B: its own rule, fixed zone and bubble. */
	private static final Profile B = new Profile("Server B", ProfileSettings.DEFAULTS, quick(List.of(SetEntry.of("b1", "x:b"))),
			List.of(fixed("bFixed", new Shape.Sphere(6)), bubble("bBubble")), Map.of("bFixed", 3, "bBubble", 4));

	@Test
	void copyBringsRulesBubblesAndSettingsButNeverFixedZones() {
		ProfileOps.Copy copy = ProfileOps.copyFrom(A, B);
		Profile result = copy.result();
		assertEquals("Server B", result.name());
		assertEquals(A.everywhere().quickEntries(), result.everywhere().quickEntries());
		assertEquals(A.settings(), result.settings());
		assertEquals(List.of("bFixed", "aBubble"), result.zones().stream().map(Zone::id).toList());
		assertEquals(3, result.colorOf("bFixed"), "B's fixed zone is untouched");
		assertEquals(2, result.colorOf("aBubble"));
		assertNull(result.colorOf("bBubble"), "B's bubbles are replaced");
	}

	@Test
	void copySummaryIsWhatTheDialogLists() {
		ProfileOps.Copy copy = ProfileOps.copyFrom(A, B);
		assertEquals(2, copy.ruleCount());
		assertEquals(1, copy.bubbleCount());
		assertEquals(1, copy.fixedKept());
		assertNull(copy.problem());
	}

	@Test
	void copyingABoostAsksForTheBoostConfirmFirst() {
		// FR-5.4: the first time a profile goes above 100%, a dialog needs a confirm, however the boost arrives.
		Profile loud = A.withEverywhere(quick(List.of(SetEntry.of("a1", "x:one").withVolume(200))));
		assertTrue(ProfileOps.copyFrom(loud, B).needsBoostConfirm(SetLibrary.EMPTY));
		assertFalse(ProfileOps.copyFrom(loud, B.withBoostConfirmed(true)).needsBoostConfirm(SetLibrary.EMPTY));
		assertFalse(ProfileOps.copyFrom(A, B).needsBoostConfirm(SetLibrary.EMPTY));
	}

	@Test
	void copyIsOneTime() {
		Profile copied = ProfileOps.copyFrom(A, B).result();
		Profile aChanged = A.withEverywhere(quick(SetEntry.of("exact:" + "x:new", "x:new").withMute(true)));
		assertEquals(A.everywhere().quickEntries(), copied.everywhere().quickEntries());
		assertFalse(copied.everywhere().quickEntries().equals(aChanged.everywhere().quickEntries()));
	}

	@Test
	void copyingAFromItselfsServerStillSkipsFixedZones() {
		// Fixed zones are never copied, even ones made for the target's own server.
		Profile source = A.withZones(List.of(fixedOn("same", SERVER)));
		ProfileOps.Copy copy = ProfileOps.copyFrom(source, B);
		assertEquals(List.of("bFixed"), copy.result().zones().stream().map(Zone::id).toList());
	}

	@Test
	void aBubbleWhoseIdClashesGetsANewId() {
		Profile source = Profile.empty("S").withZones(List.of(bubble("bFixed")));
		assertEquals(List.of("bFixed", "bFixed-2"), ProfileOps.copyFrom(source, B).result().zones().stream().map(Zone::id).toList());
	}

	@Test
	void copyThatWouldPassSixtyFourZonesIsRefused() {
		List<Zone> fixedZones = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			fixedZones.add(fixedOn("f" + i, SERVER));
		}
		Profile target = Profile.empty("T").withZones(fixedZones);
		List<Zone> bubbles = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			bubbles.add(bubble("b" + i));
		}
		ProfileOps.Copy copy = ProfileOps.copyFrom(Profile.empty("S").withZones(bubbles), target);
		assertEquals("After copying this profile would have 65 zones; the maximum is 64.", copy.problem().text(EN));
		assertEquals(target, copy.result(), "nothing changes");
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
