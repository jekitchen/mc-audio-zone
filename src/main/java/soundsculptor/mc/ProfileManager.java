package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.Message;
import soundsculptor.core.Translator;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileCodec;
import soundsculptor.core.profile.ProfileFormatException;
import soundsculptor.core.profile.ProfileStore;
import soundsculptor.core.rules.RuleResolver;
import soundsculptor.core.zones.Precedence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;

/**
 * The active profile and the Bypass switch.
 *
 * <ul>
 *   <li>Each server, Realm and singleplayer world has its own profile, chosen by its world key when the player joins
 *   (FR-11.1) and stored as {@code config/soundsculptor/profiles/<key>.json} (FR-11.8). Outside a world no profile is
 *   loaded and every sound plays as in vanilla.</li>
 *   <li>The sound path reads an immutable snapshot (profile plus compiled rules and zones), swapped in one step when the
 *   player changes something (PRD 7.4).</li>
 *   <li>Saving runs on one background thread, in order, so the game never waits on disk.</li>
 *   <li>Bypass (FR-13.1) is for this session only and never changes a rule.</li>
 * </ul>
 */
public final class ProfileManager {
	/** Name of the empty profile shown before joining a world. */
	public static final String NO_WORLD_NAME = "-";

	/** @param key the world key the profile is saved under, or {@code null} outside a world (nothing is saved) */
	private record Snapshot(String key, Profile profile, Precedence precedence) {
	}

	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Sound Sculptor profile saver");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile Snapshot current = snapshot(null, Profile.empty(NO_WORLD_NAME));
	private static volatile boolean bypass;
	private static ProfileStore store;

	private ProfileManager() {
	}

	public static void init(Path configDir) {
		store = new ProfileStore(configDir.resolve("soundsculptor").resolve("profiles"));
	}

	/**
	 * Loads the profile of the world the player just joined, creating an empty one named {@code displayName} if it
	 * has none. A broken file is set aside as {@code .broken} and reported (FR-11.8).
	 */
	public static synchronized void join(String worldKey, String displayName) {
		ProfileStore.LoadResult result = store.load(worldKey, displayName);
		current = snapshot(worldKey, result.profile());
		if (result.failed()) {
			String problem = result.problem().text((key, args) -> Component.translatable(key, args).getString());
			SoundSculptorClient.LOGGER.warn("Sound Sculptor profile {} could not be loaded ({}); using an empty profile. The file was renamed to {}",
					store.fileFor(worldKey), problem, result.brokenCopy());
			Notices.show(Component.translatable("soundsculptor.notice.profile_broken.title"),
					Component.translatable("soundsculptor.notice.profile_broken.message", problem));
		}
		update(profile -> profile.withLastJoined(Instant.now()));
	}

	/** The player left the world: back to vanilla until the next join. */
	public static synchronized void leave() {
		current = snapshot(null, Profile.empty(NO_WORLD_NAME));
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

	/** Compiled global rules and zones for the sound path (render thread). */
	public static Precedence precedence() {
		return current.precedence();
	}

	/** Compiled global rules. */
	public static RuleResolver rules() {
		return current.precedence().globalRules();
	}

	/** Applies a change, swaps in the new snapshot and saves it in the background. */
	public static synchronized void update(UnaryOperator<Profile> change) {
		Snapshot before = current;
		Profile after = change.apply(before.profile());
		if (after.equals(before.profile())) {
			return;
		}
		current = snapshot(before.key(), after);
		String key = before.key();
		if (store != null && key != null) {
			SAVER.execute(() -> save(key, after));
		}
	}

	private static void save(String key, Profile profile) {
		try {
			store.save(key, profile);
		} catch (IOException e) {
			SoundSculptorClient.LOGGER.error("Sound Sculptor could not save {}", store.fileFor(key), e);
			Notices.show(Component.translatable("soundsculptor.notice.save_failed.title"),
					Component.translatable("soundsculptor.notice.save_failed.message", e.getMessage()));
		}
	}

	/** Waits until every queued save has been written (tests, and before the game exits). */
	public static void flushSaves() {
		try {
			SAVER.submit(() -> { }).get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("profile save did not finish", e);
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

	/**
	 * Every profile in the profiles folder, plus the current world's (from memory, so it is up to date even before its
	 * first save). Waits for queued saves first, so the list shows what was last changed. Reading never changes a
	 * file: a broken file is listed with its problem, and an older file is shown migrated without being rewritten.
	 */
	public static synchronized List<Stored> storedProfiles() {
		flushSaves();
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
		return out;
	}

	/** The profile stored under {@code key} as it is now (after queued saves), or null if there is none or it cannot be read. */
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
		SAVER.execute(() -> save(key, profile));
	}

	/**
	 * Deletes another world's profile (FR-11.3); a new empty one is made when the player next joins it. The current
	 * world's profile cannot be deleted, only reset.
	 */
	public static void delete(String key) {
		if (key.equals(current.key())) {
			throw new IllegalArgumentException("the current world's profile cannot be deleted");
		}
		SAVER.execute(() -> {
			try {
				store.delete(key);
			} catch (IOException e) {
				SoundSculptorClient.LOGGER.error("Sound Sculptor could not delete {}", store.fileFor(key), e);
				Notices.show(Component.translatable("soundsculptor.notice.save_failed.title"),
						Component.translatable("soundsculptor.notice.save_failed.message", e.getMessage()));
			}
		});
	}

	/**
	 * Switches this world to a new key with the given profile, saved under that key ("Give this copy its own profile",
	 * FR-11.1). The old key's profile is left as it was.
	 */
	static synchronized void moveTo(String newKey, Profile profile) {
		current = snapshot(newKey, profile);
		SAVER.execute(() -> save(newKey, profile));
	}

	private static Snapshot snapshot(String key, Profile profile) {
		return new Snapshot(key, profile, new Precedence(profile.globalRules(), profile.zones(), profile.settings().defaultEdge()));
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
