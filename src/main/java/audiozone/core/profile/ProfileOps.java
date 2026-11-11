package audiozone.core.profile;

import audiozone.core.Message;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The Profiles tab's actions (PRD FR-11.2, FR-11.3): copy from another server, rename, reset and delete. */
public final class ProfileOps {
	/** Display names are at most this long. */
	public static final int MAX_NAME_LENGTH = 64;

	private ProfileOps() {
	}

	/**
	 * The result of "Copy from…", and what the dialog lists ("12 global rules, 2 bubble zones, settings"; this server's
	 * fixed zones are kept).
	 *
	 * @param result      the target profile after copying (unchanged if there is a problem)
	 * @param ruleCount   global rules copied
	 * @param bubbleCount personal bubbles copied
	 * @param fixedKept   the target's own fixed zones, which are kept
	 * @param problem     why the copy cannot be done (too many zones), or null
	 */
	public record Copy(Profile result, int ruleCount, int bubbleCount, int fixedKept, Message problem) {
		/**
		 * Whether the boost dialog (FR-5.4) must be confirmed before this is done: the result sets a volume above 100%
		 * and this profile has not confirmed boost yet. After the confirm, save {@code result().withBoostConfirmed(true)}.
		 */
		public boolean needsBoostConfirm() {
			return problem == null && !result.boostConfirmed() && result.hasBoost();
		}
	}

	/**
	 * Copies the parts of {@code source} that are not tied to a place (FR-11.2): its global rules, personal bubbles and
	 * settings replace {@code target}'s. Fixed zones are never copied; the target's own fixed zones and display name are
	 * kept. A one-time copy: profiles are immutable, so later changes to either do not reach the other.
	 */
	public static Copy copyFrom(Profile source, Profile target) {
		List<Zone> zones = new ArrayList<>();
		Map<String, Integer> colors = new LinkedHashMap<>();
		Set<String> used = new HashSet<>();
		int fixedKept = 0;
		for (Zone zone : target.zones()) {
			if (!zone.isBubble()) {
				zones.add(zone);
				used.add(zone.id());
				fixedKept++;
				Integer rgb = target.colorOf(zone.id());
				if (rgb != null) {
					colors.put(zone.id(), rgb);
				}
			}
		}
		int bubbles = 0;
		for (Zone zone : source.zones()) {
			if (zone.isBubble()) {
				String id = ImportPlan.freeId(zone.id(), used);
				used.add(id);
				zones.add(id.equals(zone.id()) ? zone : ImportPlan.withId(zone, id));
				bubbles++;
				Integer rgb = source.colorOf(zone.id());
				if (rgb != null) {
					colors.put(id, rgb);
				}
			}
		}
		int rules = source.globalRules().size();
		if (zones.size() > Precedence.MAX_ZONES) {
			return new Copy(target, rules, bubbles, fixedKept,
					Message.of(ProfileText.COPY_TOO_MANY_ZONES, zones.size(), Precedence.MAX_ZONES));
		}
		// Last joined and the boost confirm describe this server and this player's choices, so they stay.
		Profile result = new Profile(target.name(), source.settings(), source.globalRules(), zones, colors, target.lastJoined(),
				target.boostConfirmed());
		return new Copy(result, rules, bubbles, fixedKept, null);
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
