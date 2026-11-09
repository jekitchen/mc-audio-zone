package audiozone.core.profile;

import static audiozone.core.profile.ProfileZonesTest.SERVER;
import static audiozone.core.profile.ProfileZonesTest.bubble;
import static audiozone.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import audiozone.core.rules.Rule;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-11.2 copy settings from another server, FR-11.3 rename, reset and delete. */
class ProfileOpsTest {
	private static Zone fixedOn(String id, String worldKey) {
		return new Zone(id, "Z", true, worldKey, "minecraft:overworld", new Point(1, 2, 3), new Shape.Sphere(5), List.of());
	}

	/** Server A: 2 global rules, a fixed zone and a bubble. */
	private static final Profile A = new Profile("Server A", new ProfileSettings(false),
			List.of(Rule.of("a1", "x:one").withMute(true), Rule.of("a2", "x:two").withVolume(30)),
			List.of(fixedOn("aFixed", "a.net"), bubble("aBubble")), Map.of("aFixed", 1, "aBubble", 2));

	/** Server B: its own rule, fixed zone and bubble. */
	private static final Profile B = new Profile("Server B", ProfileSettings.DEFAULTS, List.of(Rule.of("b1", "x:b")),
			List.of(fixed("bFixed", new Shape.Sphere(6)), bubble("bBubble")), Map.of("bFixed", 3, "bBubble", 4));

	@Test
	void copyBringsRulesBubblesAndSettingsButNeverFixedZones() {
		ProfileOps.Copy copy = ProfileOps.copyFrom(A, B);
		Profile result = copy.result();
		assertEquals("Server B", result.name());
		assertEquals(A.globalRules(), result.globalRules());
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
	void copyIsOneTime() {
		Profile copied = ProfileOps.copyFrom(A, B).result();
		Profile aChanged = A.muteExact("x:new");
		assertEquals(A.globalRules(), copied.globalRules());
		assertFalse(copied.globalRules().equals(aChanged.globalRules()));
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
		assertEquals("After copying this profile would have 65 zones; the maximum is 64.", copy.problem());
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
		assertEquals("Enter a name.", ProfileOps.nameProblem("   "));
		assertEquals("The name can be at most 64 characters.", ProfileOps.nameProblem("x".repeat(65)));
		assertNull(ProfileOps.nameProblem("x".repeat(64)));
		assertThrows(IllegalArgumentException.class, () -> ProfileOps.rename(A, ""));
	}

	@Test
	void resetGoesBackToEmptyDefaultsAndKeepsTheName() {
		Profile reset = ProfileOps.reset(A);
		assertEquals(Profile.empty("Server A"), reset);
		assertTrue(reset.globalRules().isEmpty());
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
