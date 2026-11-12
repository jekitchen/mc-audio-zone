package soundsculptor.mc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileCodec;
import soundsculptor.core.profile.ProfileFormatException;
import soundsculptor.core.profile.ProfileStore;
import soundsculptor.core.sets.SetUsage;

/**
 * Where each library Sound Set is used, across every stored profile (FR-4.7, FR-4.9, FR-4.11).
 *
 * <p>Reading many profile files takes time, so the files are read on one background thread of their own (not the
 * profile saver) and {@link #current} only reads a volatile field: the game thread never waits. The index is rebuilt
 * after {@link ProfileManager#init}, after a join or leave, and after each save or delete of another world's profile.
 * The current world's own saves do not trigger a rebuild: its profile always comes from the live snapshot, so "places
 * here" is never stale, and the files are only read for the other profiles.
 */
public final class SetUsageIndex {
	/** One profile file read by the last rebuild. */
	private record FileProfile(Path file, String key, Profile profile) {
	}

	/** The files read by the last rebuild, and the usage built from them and the live profile. */
	private record Built(List<FileProfile> files, Map.Entry<String, Profile> live, SetUsage usage) {
	}

	private static final ExecutorService INDEXER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Sound Sculptor usage index");
		thread.setDaemon(true);
		return thread;
	});

	/** Whether a rebuild is waiting to start; requests made meanwhile are covered by it. */
	private static final AtomicBoolean queued = new AtomicBoolean();
	private static volatile List<FileProfile> files = List.of();
	private static volatile Built built = new Built(files, null, SetUsage.EMPTY);

	private SetUsageIndex() {
	}

	/** Reads every stored profile again in the background. Cheap to call often: pending requests are merged. */
	public static void rebuild() {
		if (queued.compareAndSet(false, true)) {
			INDEXER.execute(SetUsageIndex::read);
		}
	}

	/**
	 * The usage as of the last finished rebuild, with the loaded profile taken from the live snapshot. Never blocks
	 * (it reads volatile fields and, when the live profile changed, merges the profiles in memory).
	 */
	public static SetUsage current() {
		List<FileProfile> read = files;
		Map.Entry<String, Profile> live = ProfileManager.liveProfile();
		Built b = built;
		if (b.files() == read && sameLive(b.live(), live)) {
			return b.usage();
		}
		ProfileStore store = ProfileManager.store();
		Path liveFile = live == null || store == null ? null : store.fileFor(live.getKey());
		Map<String, Profile> all = new LinkedHashMap<>();
		for (FileProfile f : read) {
			if (!f.file().equals(liveFile)) {
				all.put(f.key(), f.profile());
			}
		}
		if (live != null) {
			all.put(live.getKey(), live.getValue());
		}
		SetUsage usage = SetUsage.of(all);
		built = new Built(read, live, usage);
		return usage;
	}

	private static boolean sameLive(Map.Entry<String, Profile> a, Map.Entry<String, Profile> b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.getKey().equals(b.getKey()) && a.getValue() == b.getValue();
	}

	/** Runs on the indexer thread. */
	private static void read() {
		queued.set(false);
		ProfileStore store = ProfileManager.store();
		if (store == null) {
			return;
		}
		List<FileProfile> out = new ArrayList<>();
		try {
			for (ProfileStore.Stored stored : store.list()) {
				Profile profile = readFile(stored.file());
				if (profile != null) {
					String name = stored.file().getFileName().toString();
					String key = stored.worldKey() != null ? stored.worldKey() : name.substring(0, name.length() - ".json".length());
					out.add(new FileProfile(stored.file(), key, profile));
				}
			}
		} catch (IOException e) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor could not list {} for the Sound Set usage", store.directory(), e);
		}
		files = List.copyOf(out);
	}

	/** The profile in the file, or {@code null} if it cannot be read (reading never changes a file). */
	private static Profile readFile(Path file) {
		try {
			if (Files.size(file) > ProfileStore.MAX_FILE_BYTES) {
				return null;
			}
			return ProfileCodec.read(Files.readString(file, StandardCharsets.UTF_8), version -> { }).profile();
		} catch (ProfileFormatException | IOException e) {
			return null;
		}
	}
}
