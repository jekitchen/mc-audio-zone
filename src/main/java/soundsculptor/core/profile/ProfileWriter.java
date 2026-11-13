package soundsculptor.core.profile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes profiles through a {@link ProfileStore} and keeps each profile that is not on disk yet (PRD 9.2: no data loss),
 * by world key: <em>queued</em> from the moment it is handed to the writing thread, <em>unsaved</em> after its write
 * failed. An unsaved profile is tried again; meanwhile anyone who reads a profile (joining the world again, the Profiles
 * tab) gets the newest one from {@link #latest} instead of the older file.
 *
 * <p>A file that is not failing waits out a brief lock ({@link soundsculptor.core.AtomicFiles}); one whose save already
 * failed gets a single quick attempt each time, so many changes to a file that stays held cost one wait, not one each,
 * and a flush stays short.
 *
 * <p>{@link #queue}, {@link #latest}, {@link #notOnDisk} and {@link #forget} may be called from any thread;
 * {@link #save} and {@link #retry} only from the one thread that writes the store.
 */
public final class ProfileWriter {
	/** Told about each save that failed. */
	@FunctionalInterface
	public interface Failure {
		void failed(Path file, IOException e);
	}

	private final ProfileStore store;
	private final Map<String, Profile> queued = new ConcurrentHashMap<>();
	private final Map<String, Profile> unsaved = new ConcurrentHashMap<>();

	public ProfileWriter(ProfileStore store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	/** The profile is about to be written (its save task is queued); until then {@link #latest} gives it. */
	public void queue(String key, Profile profile) {
		queued.put(key, profile);
	}

	/** The newest profile for {@code key} that is not on disk yet (queued, or whose save failed), or {@code null}. */
	public Profile latest(String key) {
		Profile newest = queued.get(key);
		return newest != null ? newest : unsaved.get(key);
	}

	/** Every profile not on disk yet, by key, the newest for each. */
	public Map<String, Profile> notOnDisk() {
		Map<String, Profile> all = new HashMap<>(unsaved);
		all.putAll(queued);
		return all;
	}

	/** The keys whose last save failed. */
	public Set<String> unsavedKeys() {
		return Set.copyOf(unsaved.keySet());
	}

	/** The profile is deleted: nothing for {@code key} is written or offered any more. */
	public void forget(String key) {
		queued.remove(key);
		unsaved.remove(key);
	}

	/**
	 * Writes {@code profile}; on failure it is kept (it is newer than any profile kept for the key before) and tried
	 * again by {@link #retry}.
	 *
	 * @return whether it was written
	 */
	public boolean save(String key, Profile profile, Failure onFailure) {
		boolean failing = unsaved.containsKey(key);
		try {
			store.save(key, profile, !failing);
			unsaved.remove(key);
			return true;
		} catch (IOException e) {
			unsaved.put(key, profile);
			onFailure.failed(store.fileFor(key), e);
			return false;
		} finally {
			// Kept as unsaved first, so latest() never misses it in between.
			queued.remove(key, profile);
		}
	}

	/**
	 * Tries every unsaved profile again, once each without waiting.
	 *
	 * @param skip a key just saved in the same pass, or {@code null}
	 */
	public void retry(String skip, Failure onFailure) {
		for (Map.Entry<String, Profile> entry : List.copyOf(unsaved.entrySet())) {
			if (!entry.getKey().equals(skip)) {
				save(entry.getKey(), entry.getValue(), onFailure);
			}
		}
	}
}
