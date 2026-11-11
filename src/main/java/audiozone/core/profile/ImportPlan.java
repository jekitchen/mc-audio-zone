package audiozone.core.profile;

import audiozone.core.zones.Precedence;
import audiozone.core.zones.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What importing a checked file does to the current server's profile (PRD FR-11.5, FR-11.7), for the import dialog
 * to show before anything changes.
 *
 * <ul>
 *   <li>The file's global rules, personal bubbles and settings replace this profile's.</li>
 *   <li>Fixed zones are imported only if they were made for this server (same world key, ignoring case); one with the
 *       same ID as an existing zone replaces it, so restoring a backup does not duplicate zones. The others are
 *       skipped and counted.</li>
 *   <li>This profile's other fixed zones are kept, and so is its display name.</li>
 * </ul>
 *
 * @param result        the profile after the import (the current profile, unchanged, if there is a problem)
 * @param ruleCount     global rules imported
 * @param bubbleCount   personal bubbles imported
 * @param fixedImported fixed zones imported (made for this server)
 * @param fixedSkipped  fixed zones skipped (made for another server)
 * @param problem       why the import cannot be done (too many zones), or null
 */
public record ImportPlan(Profile result, int ruleCount, int bubbleCount, int fixedImported, int fixedSkipped,
		String problem) {
	/**
	 * Whether the boost dialog (FR-5.4) must be confirmed before this is done: the result sets a volume above 100%
	 * and this profile has not confirmed boost yet. After the confirm, save {@code result().withBoostConfirmed(true)}.
	 */
	public boolean needsBoostConfirm() {
		return problem == null && !result.boostConfirmed() && result.hasBoost();
	}

	public static ImportPlan of(Profile current, Profile file, String currentWorldKey) {
		String here = currentWorldKey.toLowerCase(Locale.ROOT);
		List<Zone> imported = new ArrayList<>();
		int bubbles = 0;
		int fixedIn = 0;
		int skipped = 0;
		Set<String> replacedFixed = new HashSet<>();
		for (Zone zone : file.zones()) {
			if (zone.isBubble()) {
				imported.add(zone);
				bubbles++;
			} else if (zone.worldKey().toLowerCase(Locale.ROOT).equals(here)) {
				imported.add(zone);
				replacedFixed.add(zone.id());
				fixedIn++;
			} else {
				skipped++;
			}
		}
		List<Zone> zones = new ArrayList<>();
		Map<String, Integer> colors = new LinkedHashMap<>();
		Set<String> used = new HashSet<>();
		for (Zone zone : current.zones()) {
			if (!zone.isBubble() && !replacedFixed.contains(zone.id())) {
				zones.add(zone);
				used.add(zone.id());
				Integer rgb = current.colorOf(zone.id());
				if (rgb != null) {
					colors.put(zone.id(), rgb);
				}
			}
		}
		for (Zone zone : imported) {
			String id = freeId(zone.id(), used);
			used.add(id);
			zones.add(id.equals(zone.id()) ? zone : withId(zone, id));
			Integer rgb = file.colorOf(zone.id());
			if (rgb != null) {
				colors.put(id, rgb);
			}
		}
		if (zones.size() > Precedence.MAX_ZONES) {
			return new ImportPlan(current, file.globalRules().size(), bubbles, fixedIn, skipped,
					"After the import this profile would have " + zones.size() + " zones; the maximum is "
							+ Precedence.MAX_ZONES + ".");
		}
		Profile result = new Profile(current.name(), file.settings(), file.globalRules(), zones, colors, current.lastJoined(),
				current.boostConfirmed());
		return new ImportPlan(result, file.globalRules().size(), bubbles, fixedIn, skipped, null);
	}

	/** {@code id}, or {@code id-2}, {@code id-3}... if it is taken. */
	static String freeId(String id, Set<String> used) {
		if (!used.contains(id)) {
			return id;
		}
		for (int n = 2;; n++) {
			String candidate = id + "-" + n;
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
	}

	static Zone withId(Zone z, String id) {
		return new Zone(id, z.name(), z.enabled(), z.worldKey(), z.dimension(), z.anchor(), z.shape(), z.rules(), z.mode(),
				z.edge(), z.anchorType());
	}
}
