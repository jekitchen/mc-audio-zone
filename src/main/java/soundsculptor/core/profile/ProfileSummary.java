package soundsculptor.core.profile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SoundSet;

/**
 * One row of the Profiles tab (PRD FR-11.3): display name, world key, Sound Set and zone counts, and last joined date.
 *
 * @param setCount   the Sound Sets the profile uses: each library set attached anywhere (Everywhere or a zone) counts
 *                   once, and each place's Quick changes counts as one set
 * @param lastJoined when the player last joined this server or world, or null if never
 * @param connected  whether this is the profile of the server the player is on
 */
public record ProfileSummary(String displayName, String worldKey, int setCount, int zoneCount, Instant lastJoined,
		boolean connected) {
	/** Most recently joined first; profiles never joined after them, by name (ignoring case). */
	public static final Comparator<ProfileSummary> ORDER = Comparator
			.comparing(ProfileSummary::lastJoined, Comparator.nullsLast(Comparator.reverseOrder()))
			.thenComparing(ProfileSummary::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(ProfileSummary::worldKey);

	/**
	 * The row for a stored profile, with the last joined date it records.
	 *
	 * @param connectedWorldKey the current server's world key, or null when not in a world
	 */
	public static ProfileSummary of(String worldKey, Profile profile, String connectedWorldKey) {
		return of(worldKey, profile, profile.lastJoined(), connectedWorldKey);
	}

	/**
	 * @param connectedWorldKey the current server's world key, or null when not in a world
	 */
	public static ProfileSummary of(String worldKey, Profile profile, Instant lastJoined, String connectedWorldKey) {
		List<PlaceSets> places = new ArrayList<>();
		places.add(profile.everywhere());
		for (var zone : profile.zones()) {
			places.add(zone.sets());
		}
		Set<String> library = new HashSet<>();
		int quick = 0;
		for (PlaceSets place : places) {
			for (Attachment a : place.attached()) {
				if (a.setId().equals(SoundSet.QUICK_ID)) {
					quick++;
				} else {
					library.add(a.setId());
				}
			}
		}
		return new ProfileSummary(profile.name(), worldKey, library.size() + quick, profile.zones().size(), lastJoined,
				!ProfileOps.canDelete(worldKey, connectedWorldKey));
	}

	/** The Delete action is offered for every profile except the connected server's. */
	public boolean canDelete() {
		return !connected;
	}
}
