package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.ZoneEdge;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-11.3 "last joined" and FR-5.4 "the first time a profile goes above 100%, a dialog … needs a confirm": both are
 * stored in the profile, and survive every profile change.
 */
class ProfileStateTest {
	private static final Instant JOINED = Instant.parse("2026-11-09T13:37:46Z");
	private static final ProfileSettings SETTINGS = ProfileSettings.DEFAULTS.withOutlines(OutlineMode.ALWAYS)
			.withDefaultEdge(ZoneEdge.fade(6));

	private static Profile joinedAndConfirmed(String name) {
		return new Profile(name, SETTINGS, quick(List.of(SetEntry.of("r1", "x:a").withVolume(150))), List.of(fixed("b", new Shape.Box(8, 8, 8))), Map.of())
				.withLastJoined(JOINED).withBoostConfirmed(true);
	}

	@Test
	void aNewProfileWasNeverJoinedAndHasNotConfirmedBoost() {
		Profile p = Profile.empty("A");
		assertNull(p.lastJoined());
		assertFalse(p.boostConfirmed());
	}

	@Test
	void boostNeedsAConfirmOnlyAbove100PercentAndOnlyOnce() {
		Profile p = Profile.empty("A");
		assertFalse(p.needsBoostConfirm(null), "volume not set");
		assertFalse(p.needsBoostConfirm(100));
		assertTrue(p.needsBoostConfirm(101));
		assertTrue(p.needsBoostConfirm(200));
		assertFalse(p.withBoostConfirmed(true).needsBoostConfirm(150), "once per profile");
	}

	@Test
	void everyChangeKeepsLastJoinedAndTheBoostConfirm() {
		Profile p = joinedAndConfirmed("A");
		for (Profile changed : List.of(p.withName("B"), p.withSettings(ProfileSettings.DEFAULTS), p.withEverywhere(quick(List.of())),
				p.withZones(List.of()), p.withZoneColor("b", 7), p.withEverywhere(quick(SetEntry.of("q1", "x:b").withMute(true))),
				p.withEverywhere(PlaceSets.EMPTY.attach("set-farm")), p.withZones(List.of(fixed("b", new Shape.Box(8, 8, 8)), fixed("c", new Shape.Box(8, 8, 8)))).withZoneColor("c", 1))) {
			assertEquals(JOINED, changed.lastJoined());
			assertTrue(changed.boostConfirmed());
		}
	}

	@Test
	void theOlderConstructorsStartUnjoinedAndUnconfirmed() {
		Profile five = new Profile("A", SETTINGS, PlaceSets.EMPTY, List.of(), Map.of());
		assertNull(five.lastJoined());
		assertFalse(five.boostConfirmed());
		assertEquals(five, new Profile("A", SETTINGS, PlaceSets.EMPTY));
	}

	@Test
	void copyBringsAllSettingsButKeepsTheTargetsOwnState() {
		Profile source = joinedAndConfirmed("Source");
		Profile target = Profile.empty("Target").withLastJoined(Instant.parse("2026-01-01T00:00:00Z"));
		Profile result = ProfileOps.copyFrom(source, target, SetLibrary.EMPTY).result();
		assertEquals(SETTINGS, result.settings());
		assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.lastJoined(), "copying is not joining");
		assertFalse(result.boostConfirmed(), "the confirm belongs to the player's choice on that profile");
	}

	@Test
	void resetKeepsLastJoinedButAsksAboutBoostAgain() {
		Profile reset = ProfileOps.reset(joinedAndConfirmed("A"));
		assertEquals(ProfileSettings.DEFAULTS, reset.settings());
		assertEquals(JOINED, reset.lastJoined(), "the player did join; Reset clears settings, not history");
		assertFalse(reset.boostConfirmed(), "back to empty defaults");
	}

	@Test
	void importTakesTheFilesSettingsButKeepsThisProfilesState() {
		Profile current = Profile.empty("Here").withLastJoined(JOINED);
		Profile file = joinedAndConfirmed("Friend").withLastJoined(Instant.parse("2020-01-01T00:00:00Z"));
		Profile result = ImportPlan.of(current, new ExportCodec.Read(file, List.of(), List.of()), SetLibrary.EMPTY, ProfileZonesTest.SERVER,
				Map.of()).result();
		assertEquals(SETTINGS, result.settings());
		assertEquals(JOINED, result.lastJoined());
		assertFalse(result.boostConfirmed());
	}

	@Test
	void summaryReadsLastJoinedFromTheProfile() {
		ProfileSummary row = ProfileSummary.of("a.net", joinedAndConfirmed("A"), "b.net");
		assertEquals(JOINED, row.lastJoined());
		assertNull(ProfileSummary.of("a.net", Profile.empty("A"), null).lastJoined());
	}
}
