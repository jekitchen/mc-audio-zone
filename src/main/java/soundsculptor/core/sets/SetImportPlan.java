package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.Zone;

/**
 * What importing a file does with the Sound Sets it carries (PRD FR-11.5a), worked out before anything changes. Two
 * sets have the same entries when their entries are identical and in the same order ({@link #sameEntries}); names and
 * entry IDs are not compared. Only the file's sets that the imported places attach take part; the others (used only by
 * a skipped fixed zone, or by no place) are ignored. Each of them is compared with the library:
 *
 * <ul>
 *   <li>{@link Status#SAME}: a library set has the same ID and the same entries. It is the player's own set; the
 *       library set is used and nothing is duplicated.</li>
 *   <li>{@link Status#EXACT_MATCH}: a library set with another ID has the same entries, and they are not empty (any
 *       two empty sets would match). The whole import is refused ({@link #canApply}), naming that set. This wins over a
 *       name clash.</li>
 *   <li>{@link Status#NAME_CLASH}: the set's name (ignoring case) is used by a library set with other entries. The
 *       player chooses a new name ({@link #suggestedName} fills in "name (imported)"); the set is added under that
 *       name with a new ID.</li>
 *   <li>{@link Status#NEW}: anything else; the set is added. If a library set with other entries has its ID, it gets a
 *       new ID.</li>
 * </ul>
 *
 * <p>The imported places attach the sets as added, keeping each attachment's position and on/off. A place in the file
 * that attaches a set which is neither in the file nor in the library loses that attachment, and it is counted
 * (FR-11.6, "1 Sound Set not found, skipped"). Quick changes travel inline and are never compared.
 *
 * <p>Nothing here throws for a player-facing problem: {@link #canApply} reports it, and {@link #apply} then changes
 * nothing.
 *
 * @param fileSets    the library sets in the file that the imported places attach, in file order
 * @param statusById  the status of each set in the file, by ID, in file order (for the import dialog)
 * @param matchById   for an {@link Status#EXACT_MATCH} or {@link Status#NAME_CLASH} set, the ID of the library set it
 *                    matches or clashes with
 * @param missingRefs attachments in the file's places to sets that are neither in the file nor in the library
 */
public record SetImportPlan(List<SoundSet> fileSets, Map<String, Status> statusById, Map<String, String> matchById,
		int missingRefs) {
	/** How a set in the file compares with the library (FR-11.5a). */
	public enum Status { SAME, EXACT_MATCH, NAME_CLASH, NEW }

	/** "The library can hold up to %1$s Sound Sets. This import adds %2$s, and there is room for %3$s." */
	public static final String LIBRARY_FULL = "soundsculptor.sets.error.import_library_full";
	/** "The import failed because \"%s\" already has exactly these entries." */
	public static final String EXACT_MATCH_PROBLEM = "soundsculptor.import.exact_match";
	/** "That name is already in use. Choose a unique name to import this set." */
	public static final String NAME_TAKEN = "soundsculptor.import.name_taken";
	/** "Choose a new name for the Sound Set \"%s\" to import it." */
	public static final String NAME_MISSING = "soundsculptor.import.name_missing";
	private static final String IMPORTED_SUFFIX = " (imported)";

	public SetImportPlan {
		fileSets = List.copyOf(fileSets);
		statusById = Collections.unmodifiableMap(new LinkedHashMap<>(statusById));
		matchById = Map.copyOf(matchById);
	}

	/**
	 * @param fileSets    the library sets in the file ({@code librarySets})
	 * @param fileProfile the places that are imported (fixed zones for another server already left out)
	 */
	public static SetImportPlan of(List<SoundSet> fileSets, Profile fileProfile, SetLibrary lib) {
		// Only the sets the imported places attach take part: one used only by a skipped fixed zone, or by no place, is
		// not compared, not added, and never cancels the import or asks for a name.
		Set<String> attached = new HashSet<>();
		for (PlaceSets place : placesByRef(fileProfile).values()) {
			for (Attachment a : place.attached()) {
				attached.add(a.setId());
			}
		}
		List<SoundSet> used = new ArrayList<>();
		for (SoundSet set : fileSets) {
			if (attached.contains(set.id())) {
				used.add(set);
			}
		}
		Match match = match(used, lib);
		Set<String> known = new HashSet<>(lib.ids());
		for (SoundSet set : used) {
			known.add(set.id());
		}
		int missing = 0;
		for (PlaceSets place : placesByRef(fileProfile).values()) {
			for (Attachment a : place.attached()) {
				if (!a.setId().equals(SoundSet.QUICK_ID) && !known.contains(a.setId())) {
					missing++;
				}
			}
		}
		return new SetImportPlan(used, match.status(), match.libraryId(), missing);
	}

	/** The file's sets whose name clashes with a library set, in file order: each needs a new name before the import. */
	public List<SoundSet> nameClashes() {
		List<SoundSet> out = new ArrayList<>();
		for (SoundSet set : fileSets) {
			if (statusById.get(set.id()) == Status.NAME_CLASH) {
				out.add(set);
			}
		}
		return out;
	}

	/**
	 * Why the import cannot go ahead because a set in the file has exactly the entries of a library set with another ID
	 * (FR-11.5a), naming that library set; {@code null} if there is no such set.
	 */
	public Message exactMatchProblem(SetLibrary lib) {
		Match match = match(fileSets, lib);
		for (SoundSet set : fileSets) {
			if (match.status().get(set.id()) == Status.EXACT_MATCH) {
				return Message.of(EXACT_MATCH_PROBLEM, lib.get(match.libraryId().get(set.id())).name());
			}
		}
		return null;
	}

	/**
	 * The name the rename dialog fills in for the file's set {@code fileSetId}: "name (imported)", or "name (imported)
	 * (2)" and so on when that is in use in the library, by another set the import adds under its own name, or as a
	 * name chosen for another set in this import. It fits in {@link SoundSet#MAX_NAME_LENGTH}.
	 *
	 * @param chosen the names chosen so far, by file set ID
	 */
	public String suggestedName(String fileSetId, SetLibrary lib, Map<String, String> chosen) {
		SoundSet set = fileSet(fileSetId);
		return lib.uniqueName(set.name().strip(), null, IMPORTED_SUFFIX, takenNames(match(fileSets, lib), chosen, fileSetId));
	}

	/** A suggested name for every name clash, each avoiding the ones before it (file order). */
	public Map<String, String> suggestedNames(SetLibrary lib) {
		Map<String, String> names = new LinkedHashMap<>();
		for (SoundSet set : nameClashes()) {
			names.put(set.id(), suggestedName(set.id(), lib, names));
		}
		return names;
	}

	/**
	 * Why {@code name} cannot be chosen for the file's set {@code fileSetId}, or {@code null} if it can: it is empty or
	 * too long (as for any set name), or it is already in use, ignoring case, by a library set, by another set the import
	 * adds under its own name, or as the name chosen for another set in this import ({@value #NAME_TAKEN}).
	 *
	 * @param chosen the names chosen for the other sets, by file set ID (an entry for {@code fileSetId} is ignored)
	 */
	public Message nameProblem(String fileSetId, String name, SetLibrary lib, Map<String, String> chosen) {
		Message problem = LibraryOps.nameProblem(name);
		if (problem != null) {
			return problem;
		}
		String wanted = key(name);
		List<String> taken = new ArrayList<>();
		for (SoundSet mine : lib.sets()) {
			taken.add(mine.name());
		}
		taken.addAll(takenNames(match(fileSets, lib), chosen, fileSetId));
		for (String other : taken) {
			if (key(other).equals(wanted)) {
				return Message.of(NAME_TAKEN);
			}
		}
		return null;
	}

	/**
	 * {@code null} if {@link #apply} will import with these names, else the first problem: a set in the file has exactly
	 * the entries of a library set with another ID; the library would pass {@link SetLibrary#MAX_SETS}; or a name
	 * clash has no name, or one {@link #nameProblem} refuses. The sets are compared with {@code lib} again, so it may be
	 * a library that changed since the plan was made.
	 *
	 * @param names the new name of each {@link Status#NAME_CLASH} set, by file set ID
	 */
	public Message canApply(SetLibrary lib, Map<String, String> names) {
		Match match = match(fileSets, lib);
		Message exact = exactMatchProblem(lib);
		if (exact != null) {
			return exact;
		}
		int added = 0;
		for (Status status : match.status().values()) {
			if (status == Status.NEW || status == Status.NAME_CLASH) {
				added++;
			}
		}
		int room = SetLibrary.MAX_SETS - lib.size();
		if (added > room) {
			return Message.of(LIBRARY_FULL, SetLibrary.MAX_SETS, added, Math.max(0, room));
		}
		for (SoundSet set : fileSets) {
			if (match.status().get(set.id()) != Status.NAME_CLASH) {
				continue;
			}
			String name = names.get(set.id());
			if (name == null) {
				return Message.of(NAME_MISSING, set.name());
			}
			Message problem = nameProblem(set.id(), name, lib, names);
			if (problem != null) {
				return problem;
			}
		}
		return null;
	}

	/**
	 * The library with the file's sets added, and the file's profile attaching them. {@code lib} should be the library
	 * this plan was made with; if it has changed since, each set is compared again, so the result never refers to a set
	 * the library lacks. If {@link #canApply} reports a problem, nothing changes and {@link Applied#problem()} says why:
	 * never a partly imported library.
	 *
	 * @param names the new name of each {@link Status#NAME_CLASH} set, by file set ID; spaces at either end are ignored
	 */
	public Applied apply(Profile fileProfile, SetLibrary lib, Map<String, String> names) {
		Message problem = canApply(lib, names);
		if (problem != null) {
			return new Applied(fileProfile, lib, problem);
		}
		Match match = match(fileSets, lib);
		// New IDs avoid every ID the file uses, so an added set can never be overwritten by one added after it, nor share
		// an ID with a missing attachment.
		Set<String> fileIds = new HashSet<>();
		for (SoundSet set : fileSets) {
			fileIds.add(set.id());
		}
		for (PlaceSets place : placesByRef(fileProfile).values()) {
			for (Attachment a : place.attached()) {
				fileIds.add(a.setId());
			}
		}
		SetLibrary out = lib;
		Map<String, String> renamed = new HashMap<>();
		for (SoundSet set : fileSets) {
			switch (match.status().get(set.id())) {
				case SAME, EXACT_MATCH -> { }
				case NAME_CLASH -> {
					String id = newId(out, fileIds);
					out = out.put(new SoundSet(id, names.get(set.id()).strip(), set.entries()));
					renamed.put(set.id(), id);
				}
				case NEW -> {
					String id = set.id();
					if (out.contains(id)) {
						// A library set with other entries has this ID: the file's set gets its own.
						id = newId(out, fileIds);
						renamed.put(set.id(), id);
					}
					out = out.put(new SoundSet(id, out.uniqueName(set.name(), null), set.entries()));
				}
			}
		}
		Profile profile = fileProfile;
		for (Map.Entry<PlaceRef, PlaceSets> e : placesByRef(fileProfile).entrySet()) {
			PlaceSets rewritten = rewrite(e.getValue(), renamed, out);
			if (!rewritten.equals(e.getValue())) {
				profile = profile.withPlace(e.getKey(), rewritten);
			}
		}
		return new Applied(profile, out, null);
	}

	/**
	 * The result of {@link #apply}.
	 *
	 * @param profile the file's profile, attaching the library sets (unchanged if there is a problem)
	 * @param library the library after the import ({@code lib} unchanged if there is a problem)
	 * @param problem why nothing was imported, or null
	 */
	public record Applied(Profile profile, SetLibrary library, Message problem) {
		public Applied {
			Objects.requireNonNull(profile, "profile");
			Objects.requireNonNull(library, "library");
		}
	}

	/** Each file set's status, and the library set it matches or clashes with, by file set ID. */
	private record Match(Map<String, Status> status, Map<String, String> libraryId) {
	}

	private static Match match(List<SoundSet> fileSets, SetLibrary lib) {
		Map<String, Status> status = new LinkedHashMap<>();
		Map<String, String> libraryId = new HashMap<>();
		for (SoundSet set : fileSets) {
			SoundSet sameId = lib.get(set.id());
			if (sameId != null && sameEntries(sameId.entries(), set.entries())) {
				status.put(set.id(), Status.SAME);
				continue;
			}
			SoundSet exact = null;
			SoundSet clash = null;
			for (SoundSet mine : lib.sets()) {
				// Empty sets are never an exact match: any two would be (controller ruling, 2026-11-13).
				if (exact == null && !set.entries().isEmpty() && sameEntries(mine.entries(), set.entries())) {
					exact = mine;
				}
				if (clash == null && key(mine.name()).equals(key(set.name()))) {
					clash = mine;
				}
			}
			// An exact match wins over a name clash: the import is refused either way the player names it.
			if (exact != null) {
				status.put(set.id(), Status.EXACT_MATCH);
				libraryId.put(set.id(), exact.id());
			} else if (clash != null) {
				status.put(set.id(), Status.NAME_CLASH);
				libraryId.put(set.id(), clash.id());
			} else {
				status.put(set.id(), Status.NEW);
			}
		}
		return new Match(status, libraryId);
	}

	/**
	 * Whether two sets have the same entries (FR-11.5a): the same number, and in each position the same on/off, pattern,
	 * mute, level, repeat limit and cue. Entry IDs are not compared: they only tell entries apart within one set.
	 */
	static boolean sameEntries(List<SetEntry> a, List<SetEntry> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			SetEntry x = a.get(i);
			SetEntry y = b.get(i);
			if (x.enabled() != y.enabled() || !x.pattern().equals(y.pattern()) || !Objects.equals(x.mute(), y.mute())
					|| !Objects.equals(x.volume(), y.volume()) || !Objects.equals(x.limit(), y.limit())
					|| !Objects.equals(x.cue(), y.cue())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The names the import will use besides the library's: those of the sets added under their own name, and those
	 * chosen for the other name clashes.
	 */
	private List<String> takenNames(Match match, Map<String, String> chosen, String exceptId) {
		List<String> taken = new ArrayList<>();
		for (SoundSet set : fileSets) {
			Status status = match.status().get(set.id());
			if (status == Status.NEW) {
				taken.add(set.name().strip());
			} else if (status == Status.NAME_CLASH && !set.id().equals(exceptId) && chosen.get(set.id()) != null) {
				taken.add(chosen.get(set.id()).strip());
			}
		}
		return taken;
	}

	private SoundSet fileSet(String id) {
		for (SoundSet set : fileSets) {
			if (set.id().equals(id)) {
				return set;
			}
		}
		throw new IllegalArgumentException("no set " + id + " in the file");
	}

	/** Names compare ignoring case and spaces at either end, as in the library (FR-4.7). */
	private static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

	/** A new library ID that the file does not use either. */
	private static String newId(SetLibrary lib, Set<String> fileIds) {
		String id;
		do {
			id = lib.newId();
		} while (fileIds.contains(id));
		return id;
	}

	/**
	 * Attachments to a set added under a new ID point at it, those to sets the library lacks are dropped, and the rest
	 * stay; each keeps its position and on/off.
	 */
	private static PlaceSets rewrite(PlaceSets place, Map<String, String> renamed, SetLibrary lib) {
		List<Attachment> out = new ArrayList<>(place.attached().size());
		for (Attachment a : place.attached()) {
			String to = renamed.get(a.setId());
			if (to != null) {
				out.add(new Attachment(to, a.enabled()));
			} else if (a.setId().equals(SoundSet.QUICK_ID) || lib.contains(a.setId())) {
				out.add(a);
			}
		}
		return new PlaceSets(out, place.quickChanges());
	}

	private static Map<PlaceRef, PlaceSets> placesByRef(Profile p) {
		Map<PlaceRef, PlaceSets> out = new LinkedHashMap<>();
		out.put(PlaceRef.EVERYWHERE, p.everywhere());
		for (Zone zone : p.zones()) {
			out.put(PlaceRef.zone(zone.id()), zone.sets());
		}
		return out;
	}
}
