package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.Message;
import soundsculptor.core.Translator;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileCodec;
import soundsculptor.core.profile.ProfileFormatException;
import soundsculptor.core.profile.ProfilePrune;
import soundsculptor.core.profile.ProfileStore;
import soundsculptor.core.profile.ProfileWriter;
import soundsculptor.core.sets.LibraryWriter;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SetStore;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Precedence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;

/**
 * The active profile, the Sound Set library and the Bypass switch.
 *
 * <ul>
 *   <li>Each server, Realm and singleplayer world has its own profile, chosen by its world key when the player joins
 *   (FR-11.1) and stored as {@code config/soundsculptor/profiles/<key>.json} (FR-11.8). Outside a world no profile is
 *   loaded and every sound plays as in vanilla.</li>
 *   <li>The library of Sound Sets is shared by every world: loaded once at start from
 *   {@code config/soundsculptor/sets/}, one file per set (FR-11.8).</li>
 *   <li>The sound path reads an immutable snapshot (profile, library and the places' flattened sets), swapped in one
 *   step when the player changes something (PRD 7.4).</li>
 *   <li>Saving runs on one background thread, in order, so the game never waits on disk.</li>
 *   <li>Bypass (FR-13.1) is for this session only and never changes a set.</li>
 * </ul>
 */
public final class ProfileManager {
	/** Name of the empty profile shown before joining a world. */
	public static final String NO_WORLD_NAME = "-";

	/**
	 * @param key     the world key the profile is saved under, or {@code null} outside a world (the profile is not saved)
	 * @param library the Sound Set library, the same in every world
	 */
	private record Snapshot(String key, Profile profile, SetLibrary library, Precedence precedence) {
	}

	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Sound Sculptor profile saver");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile Snapshot current = snapshot(null, Profile.empty(NO_WORLD_NAME), SetLibrary.EMPTY);
	private static volatile boolean bypass;
	private static ProfileStore store;
	private static SetStore setStore;
	/** Writes library sets and retries failed writes; used only on the SAVER thread. */
	private static LibraryWriter setWriter;
	/**
	 * Whether every set file loaded or was set aside as broken at start. When not, attachments to sets the library
	 * lacks are kept, since those sets may load at the next start (PRD 9.2).
	 */
	private static boolean libraryComplete = true;
	/**
	 * Writes profiles and keeps every profile not on disk yet (queued, or whose save failed), so a file held open for a
	 * while (Windows) does not lose a change, not even the last one before the game exits; joining a world and the
	 * Profiles tab read the newest profile from it before the file.
	 */
	private static ProfileWriter profileWriter;
	/** How long the game thread waits for queued saves before the Profiles tab reads; it never throws. */
	private static final long STORED_FLUSH_MILLIS = 2000;
	/** How long {@link #flushSaves()} waits. */
	private static final long FLUSH_MILLIS = 10_000;
	/** Files whose failed write was already reported with a toast; they are reported again only after a write succeeds. */
	private static final Set<Path> reportedFailures = ConcurrentHashMap.newKeySet();

	private ProfileManager() {
	}

	/** Opens the profile and set folders and loads the library; a set file that cannot be loaded is reported. */
	public static synchronized void init(Path configDir) {
		Path root = configDir.resolve("soundsculptor");
		store = new ProfileStore(root.resolve("profiles"));
		setStore = new SetStore(root.resolve("sets"));
		setWriter = new LibraryWriter(setStore);
		profileWriter = new ProfileWriter(store);
		reportedFailures.clear();
		SetStore.LoadAll loaded = setStore.loadAll();
		for (Message problem : loaded.problems()) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor: {}", problem);
			Notices.show(Component.translatable("soundsculptor.notice.set_broken.title"), component(problem));
		}
		libraryComplete = loaded.complete();
		if (!libraryComplete) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor: the Sound Set library did not load completely; "
					+ "attachments to sets it lacks are kept until a start where it does");
		}
		current = snapshot(null, Profile.empty(NO_WORLD_NAME), loaded.library());
		reindex();
	}

	/**
	 * Loads the profile of the world the player just joined, creating an empty one named {@code displayName} if it
	 * has none. A broken file is set aside as {@code .broken} and reported (FR-11.8). Attachments to sets the library no
	 * longer has are removed, each with a toast and a log line, unless the library did not load completely.
	 *
	 * <p>A profile of this world that is not on disk yet (its save is queued, or failed and waits to be tried again) is
	 * newer than the file, so it is used instead, and the save on join writes it.
	 */
	public static synchronized void join(String worldKey, String displayName) {
		Profile pending = profileWriter.latest(worldKey);
		ProfileStore.LoadResult result = pending != null ? new ProfileStore.LoadResult(pending, null, null)
				: store.load(worldKey, displayName);
		SetLibrary library = current.library();
		ProfilePrune.Pruned pruned = ProfilePrune.dropMissingSetsIfComplete(result.profile(), library, libraryComplete,
				setStore.deletedNames());
		current = snapshot(worldKey, pruned.profile(), library);
		if (result.failed()) {
			String problem = result.problem().text((key, args) -> Component.translatable(key, args).getString());
			SoundSculptorClient.LOGGER.warn("Sound Sculptor profile {} could not be loaded ({}); using an empty profile. The file was renamed to {}",
					store.fileFor(worldKey), problem, result.brokenCopy());
			Notices.show(Component.translatable("soundsculptor.notice.profile_broken.title"),
					Component.translatable("soundsculptor.notice.profile_broken.message", problem));
		}
		for (Message note : pruned.notes()) {
			SoundSculptorClient.LOGGER.info("Sound Sculptor profile {}: {}", worldKey, text(note));
			Notices.show(Component.translatable("soundsculptor.notice.set_removed.title"), component(note));
		}
		// Always a change, so the profile is saved, pruned or not.
		update(profile -> profile.withLastJoined(Instant.now()));
		reindex();
	}

	/** The player left the world: back to vanilla until the next join. */
	public static synchronized void leave() {
		current = snapshot(null, Profile.empty(NO_WORLD_NAME), current.library());
		reindex(); // the profile just left is now read from its file
	}

	/** Reads the current world's profile file again (tests use it to check what was saved). */
	public static synchronized void reload() {
		String key = current.key();
		if (key != null) {
			join(key, current.profile().name());
		}
	}

	public static ProfileStore store() {
		return store;
	}

	/** Whether a world's profile is loaded; when not, Sound Sculptor leaves every sound as vanilla plays it. */
	public static boolean isLoaded() {
		return current.key() != null;
	}

	/** The world key of the loaded profile (e.g. {@code play.example.net}, {@code world:<id>}), or {@code null}. */
	public static String key() {
		return current.key();
	}

	public static Profile profile() {
		return current.profile();
	}

	/** The Sound Set library, shared by every world. */
	public static SetLibrary library() {
		return current.library();
	}

	/** The profile and the library together, as {@link #change} works on them. */
	public static Workspace workspace() {
		Snapshot snapshot = current;
		return new Workspace(snapshot.profile(), snapshot.library());
	}

	/** Where library sets are saved, or {@code null} before {@link #init}. */
	public static SetStore setStore() {
		return setStore;
	}

	/** Everywhere and the zones with their sets flattened, for the sound path (render thread). */
	public static Precedence precedence() {
		return current.precedence();
	}

	/**
	 * The loaded profile with its world key, read together from one snapshot, or {@code null} outside a world. For
	 * {@link SetUsageIndex}, which must not mix a key with another snapshot's profile.
	 */
	static Map.Entry<String, Profile> liveProfile() {
		Snapshot snapshot = current;
		return snapshot.key() == null ? null : Map.entry(snapshot.key(), snapshot.profile());
	}

	/** Rebuilds the usage index once the saves queued so far are written. */
	private static void reindex() {
		SAVER.execute(SetUsageIndex::rebuild);
	}

	/** Applies a change to the profile; see {@link #change}. */
	public static synchronized void update(UnaryOperator<Profile> f) {
		change(ws -> new Workspace(f.apply(ws.profile()), ws.library()));
	}

	/**
	 * Applies a change to the profile and the library, swaps in the new snapshot and saves in the background, in order:
	 * every library set that was added or changed, the profile if it changed (only in a world), and the file of every
	 * set the library lost. Sets go first so a saved profile never refers to a set that was not written. Sets are
	 * compared by identity against the previous library, so an untouched set is not written again. A set or profile
	 * whose write failed is tried again with the next change and in {@link #flushSaves}.
	 */
	public static synchronized void change(UnaryOperator<Workspace> f) {
		Snapshot before = current;
		Workspace after = f.apply(new Workspace(before.profile(), before.library()));
		boolean profileChanged = !after.profile().equals(before.profile());
		if (!profileChanged && after.library() == before.library()) {
			return;
		}
		current = snapshot(before.key(), after.profile(), after.library());
		List<SoundSet> saved = new ArrayList<>();
		for (SoundSet set : after.library().sets()) {
			if (before.library().get(set.id()) != set) {
				saved.add(set);
			}
		}
		List<SoundSet> deleted = new ArrayList<>();
		for (SoundSet set : before.library().sets()) {
			if (!after.library().contains(set.id())) {
				deleted.add(set);
			}
		}
		String key = before.key();
		Profile profile = after.profile();
		SetLibrary library = after.library();
		boolean saveProfile = profileChanged && key != null && profileWriter != null;
		if (saveProfile) {
			profileWriter.queue(key, profile);
		}
		SAVER.execute(() -> {
			if (setWriter != null) {
				setWriter.save(library, saved, ProfileManager::setWriteFailed);
			}
			if (saveProfile) {
				profileWriter.save(key, profile, ProfileManager::profileWriteFailed);
			}
			if (profileWriter != null) {
				profileWriter.retry(saveProfile ? key : null, ProfileManager::profileWriteFailed);
			}
			if (setWriter != null) {
				setWriter.delete(library, deleted, ProfileManager::setWriteFailed);
			}
			forgetRecoveredFailures();
		});
	}

	/** Queues {@code profile} to be written under {@code key}, and writes it on the SAVER thread. */
	private static void saveInBackground(String key, Profile profile, Runnable then) {
		profileWriter.queue(key, profile);
		SAVER.execute(() -> {
			profileWriter.save(key, profile, ProfileManager::profileWriteFailed);
			then.run();
		});
	}

	/** Like a set, a failing profile file is logged every time but toasts once until a write of it works again. */
	private static void profileWriteFailed(Path file, IOException e) {
		SoundSculptorClient.LOGGER.error("Sound Sculptor could not save {}; it is tried again with the next change", file, e);
		if (reportedFailures.add(file)) {
			saveFailed(e);
		}
	}

	/** Logs every failure, but shows the toast once per file until a write of that file works again. */
	private static void setWriteFailed(Path file, boolean deleting, IOException e) {
		SoundSculptorClient.LOGGER.error("Sound Sculptor could not {} {}; it is tried again with the next change",
				deleting ? "delete" : "save", file, e);
		if (reportedFailures.add(file)) {
			saveFailed(e);
		}
	}

	/** After a write pass: files that no longer fail may toast again if they fail later. */
	private static void forgetRecoveredFailures() {
		Set<Path> stillFailing = new HashSet<>();
		if (setWriter != null && setStore != null) {
			for (String id : setWriter.pending()) {
				stillFailing.add(setStore.fileFor(id));
			}
		}
		if (store != null && profileWriter != null) {
			for (String key : profileWriter.unsavedKeys()) {
				stillFailing.add(store.fileFor(key));
			}
		}
		reportedFailures.retainAll(stillFailing);
	}

	private static void saveFailed(IOException e) {
		Notices.show(Component.translatable("soundsculptor.notice.save_failed.title"),
				Component.translatable("soundsculptor.notice.save_failed.message", e.getMessage()));
	}

	/**
	 * Waits until every queued save has been written (tests, and before the game exits), trying again, once each, any
	 * library set or profile whose write failed. It waits at most 10 seconds and never throws: a file still failing
	 * after that stays kept in memory, and the caller goes on (at exit, the other settings still flush).
	 *
	 * @return whether every queued save finished in time (some may still have failed and be kept for later)
	 */
	public static boolean flushSaves() {
		return flushSaves(FLUSH_MILLIS);
	}

	private static boolean flushSaves(long timeoutMillis) {
		try {
			SAVER.submit(() -> {
				if (setWriter != null) {
					setWriter.retry(ProfileManager::setWriteFailed);
				}
				if (profileWriter != null) {
					profileWriter.retry(null, ProfileManager::profileWriteFailed);
				}
				forgetRecoveredFailures();
			}).get(timeoutMillis, TimeUnit.MILLISECONDS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			SoundSculptorClient.LOGGER.warn("Sound Sculptor stopped waiting for saves: interrupted");
			return false;
		} catch (Exception e) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor saves did not finish within {} ms; they go on in the background", timeoutMillis, e);
			return false;
		}
	}

	// ---- Every stored profile (the Profiles tab, FR-11.3) ----

	/**
	 * One profile on disk, for the Profiles tab.
	 *
	 * @param key       the world key, or the file name without {@code .json} for a file that records no key
	 * @param keyKnown  whether the file records its world key
	 * @param profile   the profile, or null if the file cannot be read
	 * @param problem   why the file cannot be read, or null
	 * @param connected whether this is the profile of the world the player is in
	 */
	public record Stored(String key, boolean keyKnown, Profile profile, Message problem, boolean connected) {
	}

	/** Core text (a key and its arguments) in the player's language. */
	public static String text(Message message) {
		return message.text(TRANSLATOR);
	}

	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();

	/** Core text as a component, translated when shown (so it can be made before the game has loaded its language). */
	static Component component(Message message) {
		Object[] args = new Object[message.args().size()];
		for (int i = 0; i < args.length; i++) {
			Object arg = message.args().get(i);
			args[i] = arg instanceof Message inner ? component(inner) : arg;
		}
		return Component.translatable(message.key(), args);
	}

	/**
	 * Every profile in the profiles folder, plus the current world's (from memory, so it is up to date even before its
	 * first save). Waits briefly for queued saves first; a profile that is still not on disk (queued, or its save
	 * failed) is shown as it is in memory instead of its older file, so the list always shows what was last changed,
	 * even when a file is held. Reading never changes a file: a broken file is listed with its problem, and an older
	 * file is shown migrated without being rewritten.
	 */
	public static synchronized List<Stored> storedProfiles() {
		flushSaves(STORED_FLUSH_MILLIS);
		Map<String, Profile> notOnDisk = new java.util.HashMap<>(profileWriter.notOnDisk());
		Snapshot snapshot = current;
		List<Stored> out = new ArrayList<>();
		boolean currentListed = false;
		List<ProfileStore.Stored> files;
		try {
			files = store.list();
		} catch (IOException e) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor could not list {}", store.directory(), e);
			files = List.of();
		}
		Path currentFile = snapshot.key() == null ? null : store.fileFor(snapshot.key());
		for (ProfileStore.Stored file : files) {
			if (file.file().equals(currentFile)) {
				out.add(new Stored(snapshot.key(), true, snapshot.profile(), null, true));
				currentListed = true;
				continue;
			}
			String name = file.file().getFileName().toString();
			String key = file.worldKey() != null ? file.worldKey() : name.substring(0, name.length() - ".json".length());
			Profile newer = file.worldKey() == null ? null : notOnDisk.remove(key);
			if (newer != null) {
				out.add(new Stored(key, true, newer, null, false));
				continue;
			}
			Profile profile = null;
			Message problem = null;
			try {
				if (Files.size(file.file()) > ProfileStore.MAX_FILE_BYTES) {
					problem = Message.of("soundsculptor.profile.problem.too_large");
				} else {
					profile = ProfileCodec.read(Files.readString(file.file(), StandardCharsets.UTF_8), version -> { }).profile();
				}
			} catch (ProfileFormatException e) {
				problem = e.problem();
			} catch (IOException e) {
				problem = Message.of("soundsculptor.profile.problem.unreadable", String.valueOf(e.getMessage()));
			}
			out.add(new Stored(key, file.worldKey() != null, profile, problem, false));
		}
		if (!currentListed && snapshot.key() != null) {
			out.add(new Stored(snapshot.key(), true, snapshot.profile(), null, true));
		}
		// Profiles whose first file is not written yet.
		notOnDisk.forEach((key, profile) -> {
			if (!key.equals(snapshot.key()) && !store.fileFor(key).equals(currentFile) && out.stream().noneMatch(s -> s.key().equals(key))) {
				out.add(new Stored(key, true, profile, null, false));
			}
		});
		return out;
	}

	/**
	 * The profile stored under {@code key} as it is now (the newest not on disk yet, else the file), or null if there is
	 * none or it cannot be read.
	 */
	public static Profile storedProfile(String key) {
		for (Stored s : storedProfiles()) {
			if (s.key().equals(key)) {
				return s.profile();
			}
		}
		return null;
	}

	/** Saves another world's profile in the background (Rename, Reset). For the current world, use {@link #update}. */
	public static void saveOther(String key, Profile profile) {
		if (key.equals(current.key())) {
			update(p -> profile);
			return;
		}
		saveInBackground(key, profile, SetUsageIndex::rebuild);
	}

	/**
	 * Deletes another world's profile (FR-11.3); a new empty one is made when the player next joins it. The current
	 * world's profile cannot be deleted, only reset.
	 */
	public static void delete(String key) {
		if (key.equals(current.key())) {
			throw new IllegalArgumentException("the current world's profile cannot be deleted");
		}
		profileWriter.forget(key); // not offered any more
		SAVER.execute(() -> {
			profileWriter.forget(key); // a save that failed meanwhile must not bring it back
			try {
				store.delete(key);
			} catch (IOException e) {
				SoundSculptorClient.LOGGER.error("Sound Sculptor could not delete {}", store.fileFor(key), e);
				Notices.show(Component.translatable("soundsculptor.notice.save_failed.title"),
						Component.translatable("soundsculptor.notice.save_failed.message", e.getMessage()));
			}
			SetUsageIndex.rebuild();
		});
	}

	/**
	 * Switches this world to a new key with the given profile, saved under that key ("Give this copy its own profile",
	 * FR-11.1). The old key's profile is left as it was.
	 */
	static synchronized void moveTo(String newKey, Profile profile) {
		current = snapshot(newKey, profile, current.library());
		saveInBackground(newKey, profile, () -> { });
		reindex();
	}

	private static Snapshot snapshot(String key, Profile profile, SetLibrary library) {
		return new Snapshot(key, profile, library,
				new Precedence(profile.everywhere(), profile.zones(), library, profile.settings().defaultEdge()));
	}

	// ---- Bypass (FR-13.1) ----

	public static boolean isBypassed() {
		return bypass;
	}

	public static void setBypass(boolean value) {
		if (bypass && !value) {
			// The player turned Sound Sculptor back on: give the error budget a fresh start (FR-13.2).
			SoundHook.resetErrorBudget();
		}
		bypass = value;
	}

	public static void toggleBypass() {
		setBypass(!bypass);
	}
}
