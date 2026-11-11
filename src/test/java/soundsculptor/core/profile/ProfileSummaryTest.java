package soundsculptor.core.profile;

import static soundsculptor.core.profile.ProfileZonesTest.bubble;
import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.rules.Rule;
import soundsculptor.core.zones.Shape;

import org.junit.jupiter.api.Test;

/** PRD FR-11.3: one row per profile in the Profiles tab. */
class ProfileSummaryTest {
	private static final Instant MONDAY = Instant.parse("2026-11-09T10:00:00Z");
	private static final Instant SUNDAY = Instant.parse("2026-11-08T10:00:00Z");

	@Test
	void rowHasNameKeyCountsAndLastJoined() {
		// fixed() carries one zone rule.
		Profile p = new Profile("My server", ProfileSettings.DEFAULTS, List.of(Rule.of("a", "x:a"), Rule.of("b", "x:b")))
				.withZones(List.of(fixed("z1", new Shape.Sphere(4)), bubble("b1")));
		ProfileSummary row = ProfileSummary.of("play.example.net", p, MONDAY, "play.example.net");
		assertEquals("My server", row.displayName());
		assertEquals("play.example.net", row.worldKey());
		assertEquals(4, row.ruleCount(), "global rules plus zone rules");
		assertEquals(2, row.zoneCount());
		assertEquals(MONDAY, row.lastJoined());
		assertTrue(row.connected());
		assertFalse(row.canDelete());
	}

	@Test
	void otherServersCanBeDeleted() {
		ProfileSummary row = ProfileSummary.of("other.net", Profile.empty("Other"), null, "play.example.net");
		assertFalse(row.connected());
		assertTrue(row.canDelete());
	}

	@Test
	void listIsMostRecentlyJoinedFirstThenNeverJoinedByName() {
		List<ProfileSummary> rows = new ArrayList<>(List.of(
				ProfileSummary.of("c", Profile.empty("charlie"), null, null),
				ProfileSummary.of("a", Profile.empty("Alpha"), SUNDAY, null),
				ProfileSummary.of("b", Profile.empty("bravo"), null, null),
				ProfileSummary.of("d", Profile.empty("Delta"), MONDAY, null)));
		rows.sort(ProfileSummary.ORDER);
		assertEquals(List.of("Delta", "Alpha", "bravo", "charlie"), rows.stream().map(ProfileSummary::displayName).toList());
	}
}
