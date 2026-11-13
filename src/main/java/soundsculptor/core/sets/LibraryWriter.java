package soundsculptor.core.sets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes library changes through a {@link SetStore} and tries again the ones that failed (PRD 9.2). A changed set is
 * written only when it changes, so without this a set whose save failed (disk full, file locked) would never be
 * written, and the next start would treat it as missing.
 *
 * <p>A set whose file is not failing waits out a brief lock ({@link soundsculptor.core.AtomicFiles}); one whose save
 * already failed gets a single quick attempt each time, so many changes to a file that stays held cost one wait, not
 * one each, and a flush stays short.
 *
 * <p>Each failure is reported once per attempt. A failed save is retried with the set as it is in the latest library;
 * a failed delete is retried while the set stays out of the library. A set that comes back (Undo) is saved and its
 * failed delete forgotten.
 *
 * <p>Not thread-safe: use it from the one thread that writes the store.
 */
public final class LibraryWriter {
	/** Told about each save or delete that failed. */
	@FunctionalInterface
	public interface Failure {
		void failed(Path file, boolean deleting, IOException e);
	}

	private final SetStore store;
	/** IDs whose last save failed. */
	private final Set<String> unsaved = new LinkedHashSet<>();
	/** The name of each set whose last delete failed, by ID. */
	private final Map<String, String> undeleted = new LinkedHashMap<>();
	/** The library given last; failed saves are retried from it. */
	private SetLibrary latest = SetLibrary.EMPTY;

	public LibraryWriter(SetStore store) {
		this.store = store;
	}

	/**
	 * Saves the sets that changed in {@code library}, and again every set whose save or delete failed before and that
	 * is in it now.
	 */
	public void save(SetLibrary library, Collection<SoundSet> changed, Failure onFailure) {
		latest = library;
		List<SoundSet> toSave = new ArrayList<>(changed);
		Set<String> ids = new HashSet<>();
		for (SoundSet set : changed) {
			ids.add(set.id());
		}
		List<String> retry = new ArrayList<>(unsaved);
		retry.addAll(undeleted.keySet());
		for (String id : retry) {
			SoundSet set = library.get(id);
			if (set == null) {
				unsaved.remove(id); // it left the library, so it is deleted instead
				continue;
			}
			undeleted.remove(id); // it came back (Undo), so it is saved instead
			if (ids.add(id)) {
				toSave.add(set);
			}
		}
		for (SoundSet set : toSave) {
			try {
				store.save(set, !unsaved.contains(set.id()));
				unsaved.remove(set.id());
			} catch (IOException e) {
				unsaved.add(set.id());
				onFailure.failed(store.fileFor(set.id()), false, e);
			}
		}
	}

	/** Deletes the files of the sets that left {@code library}, and again every set whose delete failed before. */
	public void delete(SetLibrary library, Collection<SoundSet> removed, Failure onFailure) {
		latest = library;
		Map<String, String> toDelete = new LinkedHashMap<>();
		for (SoundSet set : removed) {
			toDelete.put(set.id(), set.name());
		}
		undeleted.forEach((id, name) -> {
			if (!library.contains(id)) {
				toDelete.putIfAbsent(id, name);
			}
		});
		toDelete.forEach((id, name) -> {
			unsaved.remove(id);
			try {
				store.delete(id, name);
				undeleted.remove(id);
			} catch (IOException e) {
				undeleted.put(id, name);
				onFailure.failed(store.fileFor(id), true, e);
			}
		});
	}

	/** Tries every failed save and delete again, against the library given last. */
	public void retry(Failure onFailure) {
		if (unsaved.isEmpty() && undeleted.isEmpty()) {
			return;
		}
		save(latest, List.of(), onFailure);
		delete(latest, List.of(), onFailure);
	}

	/** The IDs of the sets whose last save or delete failed. */
	public Set<String> pending() {
		Set<String> ids = new LinkedHashSet<>(unsaved);
		ids.addAll(undeleted.keySet());
		return Set.copyOf(ids);
	}
}
