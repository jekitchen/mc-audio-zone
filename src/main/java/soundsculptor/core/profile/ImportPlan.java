package soundsculptor.core.profile;

import soundsculptor.core.Message;
import soundsculptor.core.sets.SetImportPlan;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What importing a checked file does to the current server's profile and to the Sound Set library (PRD FR-11.5,
 * FR-11.5a, FR-11.7), for the import dialog to show before anything changes.
 *
 * <ul>
 *   <li>The file's Everywhere sets, personal bubbles and settings replace this profile's.</li>
 *   <li>Fixed zones are imported only if they were made for this server (same world key, ignoring case); one with the
 *       same ID as an existing zone replaces it, so restoring a backup does not duplicate zones. The others are
 *       skipped and counted.</li>
 *   <li>This profile's other fixed zones are kept, and so is its display name.</li>
 *   <li>The Sound Sets in the file are added to the library as {@link SetImportPlan} describes, and the imported places
 *       attach them; an attachment to a set in neither the file nor the library is dropped and counted.</li>
 * </ul>
 *
 * <p>Limits never throw: too many zones, or a library that would pass {@value SetLibrary#MAX_SETS} sets, is a
 * {@link #problem()}, and then the plan changes nothing.
 *
 * @param result        the profile after the import (the current profile, unchanged, if there is a problem)
 * @param library       the library after the import (unchanged if there is a problem)
 * @param sets          how each set in the file compares with the library, for the dialog
 * @param setCount      sets on Everywhere after the import, Quick changes included
 * @param bubbleCount   personal bubbles imported
 * @param fixedImported fixed zones imported (made for this server)
 * @param fixedSkipped  fixed zones skipped (made for another server)
 * @param setsAdded     sets added to the library
 * @param setsMissing   attachments dropped because the set is in neither the file nor the library
 * @param problem       why the import cannot be done, or null
 */
public record ImportPlan(Profile result, SetLibrary library, SetImportPlan sets, int setCount, int bubbleCount,
		int fixedImported, int fixedSkipped, int setsAdded, int setsMissing, Message problem) {
	/** "%s Sound Sets not found, skipped". */
	static final String MISSING = "soundsculptor.profile.import.sets_missing";
	/** "1 Sound Set not found, skipped". */
	static final String MISSING_ONE = "soundsculptor.profile.import.sets_missing.one";

	public ImportPlan {
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(library, "library");
		Objects.requireNonNull(sets, "sets");
	}

	/**
	 * Whether the boost dialog (FR-5.4) must be confirmed before this is done: the result sets a volume above 100%, in
	 * Quick changes or in a library set (the imported ones included), and this profile has not confirmed boost yet.
	 * After the confirm, save {@code result().withBoostConfirmed(true)}.
	 */
	public boolean needsBoostConfirm() {
		return problem == null && !result.boostConfirmed() && result.hasBoost(library);
	}

	/** "1 Sound Set not found, skipped" (FR-11.6), or null if none is missing. */
	public Message missingNote() {
		if (setsMissing == 0) {
			return null;
		}
		return setsMissing == 1 ? Message.of(MISSING_ONE) : Message.of(MISSING, setsMissing);
	}

	/**
	 * @param current         this server's profile
	 * @param file            the checked file
	 * @param lib             the library now
	 * @param currentWorldKey this server's world key
	 * @param choices         what to do with each set whose contents differ from the library's, by ID; a set not
	 *                        listed is kept both (the default)
	 */
	public static ImportPlan of(Profile current, ExportCodec.Read file, SetLibrary lib, String currentWorldKey,
			Map<String, SetImportPlan.Choice> choices) {
		String here = currentWorldKey.toLowerCase(Locale.ROOT);
		List<Zone> imported = new ArrayList<>();
		int bubbles = 0;
		int fixedIn = 0;
		int skipped = 0;
		Set<String> replacedFixed = new HashSet<>();
		for (Zone zone : file.profile().zones()) {
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
		// Only the places that are imported attach sets here; skipped zones are left out before the sets are matched.
		Profile importedPart = file.profile().withZones(imported);
		SetImportPlan setPlan = SetImportPlan.of(file.sets(), importedPart, lib);

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
		int keptZones = zones.size();
		if (keptZones + imported.size() > Precedence.MAX_ZONES) {
			return refused(current, lib, setPlan, importedPart, bubbles, fixedIn, skipped,
					Message.of(ProfileText.IMPORT_TOO_MANY_ZONES, keptZones + imported.size(), Precedence.MAX_ZONES));
		}
		Message full = setPlan.canApply(lib, choices);
		if (full != null) {
			return refused(current, lib, setPlan, importedPart, bubbles, fixedIn, skipped, full);
		}
		SetImportPlan.Applied applied = setPlan.apply(importedPart, file.sets(), lib, choices);
		Profile source = applied.profile();
		for (Zone zone : source.zones()) {
			String id = freeId(zone.id(), used);
			used.add(id);
			zones.add(id.equals(zone.id()) ? zone : withId(zone, id));
			Integer rgb = source.colorOf(zone.id());
			if (rgb != null) {
				colors.put(id, rgb);
			}
		}
		Profile result = new Profile(current.name(), source.settings(), source.everywhere(), zones, colors,
				current.lastJoined(), current.boostConfirmed());
		return new ImportPlan(result, applied.library(), setPlan, source.everywhere().attached().size(), bubbles, fixedIn,
				skipped, applied.library().size() - lib.size(), setPlan.missingRefs(), null);
	}

	/** Nothing changes; the counts still describe the file. */
	private static ImportPlan refused(Profile current, SetLibrary lib, SetImportPlan setPlan, Profile importedPart,
			int bubbles, int fixedIn, int skipped, Message problem) {
		return new ImportPlan(current, lib, setPlan, importedPart.everywhere().attached().size(), bubbles, fixedIn, skipped, 0,
				setPlan.missingRefs(), problem);
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
		return new Zone(id, z.name(), z.enabled(), z.worldKey(), z.dimension(), z.anchor(), z.shape(), z.sets(), z.mode(),
				z.edge(), z.anchorType());
	}
}
