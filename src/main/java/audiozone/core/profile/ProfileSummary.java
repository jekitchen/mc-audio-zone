package audiozone.core.profile;

import java.time.Instant;
import java.util.Comparator;

/**
 * One row of the Profiles tab (PRD FR-11.3): display name, world key, rule and zone counts, and last joined date.
 *
 * @param ruleCount  global rules plus every zone's rules
 * @param lastJoined when the player last joined this server or world, or null if never
 * @param connected  whether this is the profile of the server the player is on
 */
public record ProfileSummary(String displayName, String worldKey, int ruleCount, int zoneCount, Instant lastJoined,
		boolean connected) {
	/** Most recently joined first; profiles never joined after them, by name (ignoring case). */
	public static final Comparator<ProfileSummary> ORDER = Comparator
			.comparing(ProfileSummary::lastJoined, Comparator.nullsLast(Comparator.reverseOrder()))
			.thenComparing(ProfileSummary::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(ProfileSummary::worldKey);

	/**
	 * @param connectedWorldKey the current server's world key, or null when not in a world
	 */
	public static ProfileSummary of(String worldKey, Profile profile, Instant lastJoined, String connectedWorldKey) {
		int rules = profile.globalRules().size();
		for (var zone : profile.zones()) {
			rules += zone.rules().size();
		}
		return new ProfileSummary(profile.name(), worldKey, rules, profile.zones().size(), lastJoined,
				!ProfileOps.canDelete(worldKey, connectedWorldKey));
	}

	/** The Delete action is offered for every profile except the connected server's. */
	public boolean canDelete() {
		return !connected;
	}
}
