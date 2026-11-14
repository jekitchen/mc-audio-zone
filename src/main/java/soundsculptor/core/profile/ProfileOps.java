package soundsculptor.core.profile;

import soundsculptor.core.Message;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;
import java.util.Locale;

/** The Profiles tab's actions (PRD FR-11.2, FR-11.3): copy from another server, rename, reset and delete. */
public final class ProfileOps {
	/** Display names are at most this long. */
	public static final int MAX_NAME_LENGTH = 64;

	private ProfileOps() {
	}

	/**
	 * The result of "Copy from…", and what the dialog lists ("4 Sound Sets on Everywhere, settings"; this server's zones
	 * are kept).
	 *
	 * @param result   the target profile after copying
	 * @param setCount sets copied onto Everywhere: its library sets and, if it has one, its Quick changes
	 */
	public record Copy(Profile result, int setCount) {
		/**
		 * Whether the boost dialog (FR-5.4) must be confirmed before this is done: the result sets a volume above 100%
		 * and this profile has not confirmed boost yet. After the confirm, save {@code result().withBoostConfirmed(true)}.
		 */
		public boolean needsBoostConfirm(SetLibrary library) {
			return !result.boostConfirmed() && result.hasBoost(library);
		}
	}

	/**
	 * Copies the parts of {@code source} that are not tied to a place (FR-11.2): Everywhere's attachments, its Quick
	 * changes and settings replace {@code target}'s. Library sets are linked, not copied: both profiles attach the same
	 * set ID, so later edits to the set show in both. Quick changes are copied as they are (sets are immutable, so the
	 * copy is independent). An attachment to a set {@code lib} lacks is dropped. Zones are never copied, because they
	 * belong to their own server; the target's zones, their colors and its display name are kept. A one-time copy:
	 * profiles are immutable, so later attaching or detaching on either does not reach the other.
	 */
	public static Copy copyFrom(Profile source, Profile target, SetLibrary lib) {
		PlaceSets everywhere = source.everywhere().dropMissing(lib.ids());
		// Last joined and the boost confirm describe this server and this player's choices, so they stay.
		Profile result = new Profile(target.name(), source.settings(), everywhere, target.zones(), target.zoneColors(),
				target.lastJoined(), target.boostConfirmed());
		return new Copy(result, everywhere.attached().size());
	}

	/** Why a display name cannot be used, or null if it can. Spaces at either end are ignored. */
	public static Message nameProblem(String name) {
		String trimmed = name.strip();
		if (trimmed.isEmpty()) {
			return Message.of(ProfileText.NAME_EMPTY);
		}
		if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
			return Message.of(ProfileText.NAME_TOO_LONG, MAX_NAME_LENGTH);
		}
		return null;
	}

	/**
	 * Rename: changes the display name only (FR-11.3); the world key, and so the file, stay the same.
	 *
	 * @throws IllegalArgumentException if {@link #nameProblem} reports a problem
	 */
	public static Profile rename(Profile profile, String name) {
		Message problem = nameProblem(name);
		if (problem != null) {
			throw new IllegalArgumentException(problem.toString());
		}
		return profile.withName(name.strip());
	}

	/**
	 * Reset: back to empty defaults (no rules, no zones, default settings, boost dialog shown again), keeping the display
	 * name and the last joined date, which is history rather than a setting.
	 */
	public static Profile reset(Profile profile) {
		return Profile.empty(profile.name()).withLastJoined(profile.lastJoined());
	}

	/**
	 * Whether the profile for {@code worldKey} may be deleted: the profile of the server you are connected to can be
	 * reset but not deleted.
	 *
	 * @param connectedWorldKey the current server's world key, or null when not in a world
	 */
	public static boolean canDelete(String worldKey, String connectedWorldKey) {
		return connectedWorldKey == null || !worldKey.toLowerCase(Locale.ROOT).equals(connectedWorldKey.toLowerCase(Locale.ROOT));
	}
}
