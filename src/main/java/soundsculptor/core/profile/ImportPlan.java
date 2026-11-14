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
 *   <li>The file's Everywhere sets and settings replace this profile's.</li>
 *   <li>Its zones, which are all fixed (FR-8.1), are imported only if they were made for this server (same world key,
 *       ignoring case); one with the same ID as an existing zone replaces it, so restoring a backup does not duplicate
 *       zones. The others are skipped and counted.</li>
 *   <li>This profile's other zones are kept, and so is its display name.</li>
 *   <li>The Sound Sets in the file are added to the library as {@link SetImportPlan} describes, and the imported places
 *       attach them; an attachment to a set in neither the file nor the library is dropped and counted.</li>
 * </ul>
 *
 * <p>Nothing throws for a player-facing problem: too many zones, a set in the file with exactly the entries of a
 * library set under another ID, a library that would pass {@value SetLibrary#MAX_SETS} sets, or a name clash without a
 * unique new name is a {@link #problem()}, and then the plan changes nothing.
 *
 * @param result        the profile after the import (the current profile, unchanged, if there is a problem)
 * @param library       the library after the import (unchanged if there is a problem)
 * @param sets          how each set in the file compares with the library, for the dialog
 * @param setCount      sets on Everywhere after the import, Quick changes included
 * @param fixedImported fixed zones imported (made for this server)
 * @param fixedSkipped  fixed zones skipped (made for another server)
 * @param setsAdded     sets added to the library
 * @param setsMissing   attachments dropped because the set is in neither the file nor the library
 * @param problem       why the import cannot be done, or null
 */
public record ImportPlan(Profile result, SetLibrary library, SetImportPlan sets, int setCount, int fixedImported,
		int fixedSkipped, int setsAdded, int setsMissing, Message problem) {
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
	 * @param names           the new name of each set whose name clashes with a library set
	 *                        ({@link SetImportPlan#nameClashes()}), by file set ID; without them the plan is refused
	 *                        until they are chosen
	 */
	public static ImportPlan of(Profile current, ExportCodec.Read file, SetLibrary lib, String currentWorldKey,
			Map<String, String> names) {
		String here = currentWorldKey.toLowerCase(Locale.ROOT);
		List<Zone> imported = new ArrayList<>();
		int skipped = 0;
		Set<String> importedIds = new HashSet<>();
		for (Zone zone : file.profile().zones()) {
			if (zone.worldKey().toLowerCase(Locale.ROOT).equals(here)) {
				imported.add(zone);
				importedIds.add(zone.id());
			} else {
				skipped++;
			}
		}
		int fixedIn = imported.size();
		// Only the places that are imported attach sets here; skipped zones are left out before the sets are matched.
		Profile importedPart = file.profile().withZones(imported);
		SetImportPlan setPlan = SetImportPlan.of(file.sets(), importedPart, lib);

		List<Zone> zones = new ArrayList<>();
		Map<String, Integer> colors = new LinkedHashMap<>();
		for (Zone zone : current.zones()) {
			if (!importedIds.contains(zone.id())) {
				zones.add(zone);
				Integer rgb = current.colorOf(zone.id());
				if (rgb != null) {
					colors.put(zone.id(), rgb);
				}
			}
		}
		int keptZones = zones.size();
		if (keptZones + imported.size() > Precedence.MAX_ZONES) {
			return refused(current, lib, setPlan, importedPart, fixedIn, skipped,
					Message.of(ProfileText.IMPORT_TOO_MANY_ZONES, keptZones + imported.size(), Precedence.MAX_ZONES));
		}
		// An exact match, a full library or a missing or taken name: nothing changes (FR-11.5a).
		Message setProblem = setPlan.canApply(lib, names);
		if (setProblem != null) {
			return refused(current, lib, setPlan, importedPart, fixedIn, skipped, setProblem);
		}
		SetImportPlan.Applied applied = setPlan.apply(importedPart, lib, names);
		Profile source = applied.profile();
		// An imported zone with the same ID as one of this profile's has replaced it above, so IDs never clash.
		for (Zone zone : source.zones()) {
			zones.add(zone);
			Integer rgb = source.colorOf(zone.id());
			if (rgb != null) {
				colors.put(zone.id(), rgb);
			}
		}
		Profile result = new Profile(current.name(), source.settings(), source.everywhere(), zones, colors,
				current.lastJoined(), current.boostConfirmed());
		return new ImportPlan(result, applied.library(), setPlan, source.everywhere().attached().size(), fixedIn, skipped,
				applied.library().size() - lib.size(), setPlan.missingRefs(), null);
	}

	/** Nothing changes; the counts still describe the file. */
	private static ImportPlan refused(Profile current, SetLibrary lib, SetImportPlan setPlan, Profile importedPart,
			int fixedIn, int skipped, Message problem) {
		return new ImportPlan(current, lib, setPlan, importedPart.everywhere().attached().size(), fixedIn, skipped, 0,
				setPlan.missingRefs(), problem);
	}
}
