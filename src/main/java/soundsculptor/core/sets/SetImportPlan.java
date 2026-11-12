package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.Zone;

/**
 * What importing a file does with the Sound Sets it carries (PRD FR-11.5a), worked out before anything changes. Each
 * set in the file is matched to the library by ID:
 *
 * <ul>
 *   <li>{@link Status#NEW}: not in the library; it is added.</li>
 *   <li>{@link Status#SAME}: in the library with the same name and entries; the library set is used.</li>
 *   <li>{@link Status#DIFFERENT}: in the library with other contents; the player's {@link Choice} decides, and
 *       {@link Choice#KEEP_BOTH} is the default.</li>
 * </ul>
 *
 * <p>A place in the file that attaches a set which is neither in the file nor in the library loses that attachment,
 * and it is counted (FR-11.6, "1 Sound Set not found, skipped"). Quick changes travel inline and are never matched.
 *
 * <p>Limits never throw: {@link #canApply} reports a library that would pass {@link SetLibrary#MAX_SETS}, and
 * {@link #apply} then changes nothing.
 *
 * @param statusById  the status of each set in the file, by ID, in file order (for the import dialog)
 * @param missingRefs attachments in the file's places to sets that are neither in the file nor in the library
 */
public record SetImportPlan(Map<String, Status> statusById, int missingRefs) {
	/** How a set in the file compares with the library set of the same ID. */
	public enum Status { NEW, SAME, DIFFERENT }

	/** What to do with a {@link Status#DIFFERENT} set. */
	public enum Choice {
		/** The default: the file's version is added as "name (imported)" with a new ID, and the imported places attach it. */
		KEEP_BOTH,
		/** The library version is kept, and the imported places attach it. */
		USE_MINE,
		/** The library version is overwritten by the file's, changing every place that uses it. */
		REPLACE
	}

	/** "The library can hold up to %1$s Sound Sets. This import adds %2$s, and there is room for %3$s." */
	public static final String LIBRARY_FULL = "soundsculptor.sets.error.import_library_full";
	private static final String IMPORTED_SUFFIX = " (imported)";

	public SetImportPlan {
		statusById = Collections.unmodifiableMap(new LinkedHashMap<>(statusById));
	}

	/**
	 * @param fileSets    the library sets in the file ({@code librarySets})
	 * @param fileProfile the places that are imported (fixed zones for another server already left out)
	 */
	public static SetImportPlan of(List<SoundSet> fileSets, Profile fileProfile, SetLibrary lib) {
		Map<String, Status> status = new LinkedHashMap<>();
		Set<String> known = new HashSet<>(lib.ids());
		for (SoundSet set : fileSets) {
			status.put(set.id(), statusOf(set, lib));
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
		return new SetImportPlan(status, missing);
	}

	/** {@code null} if the import fits in the library with these choices, else the problem to show. */
	public Message canApply(SetLibrary lib, Map<String, Choice> choices) {
		int added = 0;
		for (Map.Entry<String, Status> e : statusById.entrySet()) {
			if (adds(e.getValue(), choice(choices, e.getKey()))) {
				added++;
			}
		}
		return fits(lib, added);
	}

	/** Whether a set with this status and choice is added to the library as a new set. */
	private static boolean adds(Status status, Choice choice) {
		return status == Status.NEW || status == Status.DIFFERENT && choice == Choice.KEEP_BOTH;
	}

	private static Message fits(SetLibrary lib, int added) {
		int room = SetLibrary.MAX_SETS - lib.size();
		return added > room ? Message.of(LIBRARY_FULL, SetLibrary.MAX_SETS, added, Math.max(0, room)) : null;
	}

	/**
	 * The library with the file's sets added (or replaced), and the file's profile attaching them. {@code lib} should be
	 * the library this plan was made with; if it has changed since, each set is matched again, so the result never
	 * refers to a set the library lacks. If the sets do not fit in the library, nothing changes and
	 * {@link Applied#problem()} says why: never a partly imported library.
	 *
	 * @param choices what to do with each {@link Status#DIFFERENT} set, by ID; a set not listed uses {@link Choice#KEEP_BOTH}
	 */
	public Applied apply(Profile fileProfile, List<SoundSet> fileSets, SetLibrary lib, Map<String, Choice> choices) {
		int added = 0;
		for (SoundSet set : fileSets) {
			if (adds(statusOf(set, lib), choice(choices, set.id()))) {
				added++;
			}
		}
		Message problem = fits(lib, added);
		if (problem != null) {
			return new Applied(fileProfile, lib, problem);
		}
		// New IDs avoid every ID the file uses, so a copy can never be overwritten by a set added after it, nor share an
		// ID with a missing attachment.
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
			switch (statusOf(set, lib)) {
				case NEW -> out = out.put(new SoundSet(set.id(), out.uniqueName(set.name(), null), set.entries()));
				case SAME -> { }
				case DIFFERENT -> {
					switch (choice(choices, set.id())) {
						case KEEP_BOTH -> {
							String id = newId(out, fileIds);
							out = out.put(new SoundSet(id, out.uniqueName(set.name(), null, IMPORTED_SUFFIX), set.entries()));
							renamed.put(set.id(), id);
						}
						case USE_MINE -> { }
						case REPLACE -> out = out.put(set.withName(out.uniqueName(set.name(), set.id())));
					}
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
	 * @param problem why nothing was imported (the library would be too full), or null
	 */
	public record Applied(Profile profile, SetLibrary library, Message problem) {
		public Applied {
			Objects.requireNonNull(profile, "profile");
			Objects.requireNonNull(library, "library");
		}
	}

	private static Status statusOf(SoundSet set, SetLibrary lib) {
		SoundSet mine = lib.get(set.id());
		if (mine == null) {
			return Status.NEW;
		}
		return mine.sameContents(set) ? Status.SAME : Status.DIFFERENT;
	}

	private static Choice choice(Map<String, Choice> choices, String id) {
		return choices.getOrDefault(id, Choice.KEEP_BOTH);
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
	 * Attachments to a Keep both set point at its copy, those to sets the library lacks are dropped, and the rest stay;
	 * each keeps its position and on/off.
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
