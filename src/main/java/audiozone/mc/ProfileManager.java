package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.profile.Profile;
import audiozone.core.profile.ProfileStore;
import audiozone.core.rules.RuleResolver;
import java.io.IOException;
import java.nio.file.Path;
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
 *   <li>The sound path reads an immutable snapshot (profile plus compiled rules), swapped in one step when the player
 *   changes something (PRD 7.4).</li>
 *   <li>Saving runs on one background thread, in order, so the game never waits on disk (PRD 10.1, M4 risk).</li>
 *   <li>M4 has one profile, {@value #DEFAULT_KEY}; per-server profiles arrive with FR-11.1 (M7).</li>
 *   <li>Bypass (FR-13.1) is for this session only and never changes a rule.</li>
 * </ul>
 */
public final class ProfileManager {
	public static final String DEFAULT_KEY = "default";
	public static final String DEFAULT_NAME = "Default";

	private record Snapshot(Profile profile, RuleResolver rules) {
	}

	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Audio Zone profile saver");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile Snapshot current = snapshot(Profile.empty(DEFAULT_NAME));
	private static volatile boolean bypass;
	private static ProfileStore store;

	private ProfileManager() {
	}

	/** Loads the profile from {@code config/audiozone/profiles/}. A broken file is set aside and reported. */
	public static void init(Path configDir) {
		store = new ProfileStore(configDir.resolve("audiozone").resolve("profiles"));
		reload();
	}

	/** Reads the profile file again (also used by tests to check what was saved). */
	public static synchronized void reload() {
		ProfileStore.LoadResult result = store.load(DEFAULT_KEY, DEFAULT_NAME);
		current = snapshot(result.profile());
		if (result.failed()) {
			AudioZoneClient.LOGGER.warn("Audio Zone profile {} could not be loaded ({}); using an empty profile. The file was renamed to {}",
					store.fileFor(DEFAULT_KEY), result.problem(), result.brokenCopy());
			Notices.show(Component.translatable("audiozone.notice.profile_broken.title"),
					Component.translatable("audiozone.notice.profile_broken.message", result.problem()));
		}
	}

	public static ProfileStore store() {
		return store;
	}

	public static Profile profile() {
		return current.profile();
	}

	/** Compiled rules for the sound path (render thread). */
	public static RuleResolver rules() {
		return current.rules();
	}

	/** Applies a change, swaps in the new snapshot and saves it in the background. */
	public static synchronized void update(UnaryOperator<Profile> change) {
		Profile before = current.profile();
		Profile after = change.apply(before);
		if (after.equals(before)) {
			return;
		}
		current = snapshot(after);
		if (store != null) {
			SAVER.execute(() -> save(after));
		}
	}

	private static void save(Profile profile) {
		try {
			store.save(DEFAULT_KEY, profile);
		} catch (IOException e) {
			AudioZoneClient.LOGGER.error("Audio Zone could not save {}", store.fileFor(DEFAULT_KEY), e);
			Notices.show(Component.translatable("audiozone.notice.save_failed.title"),
					Component.translatable("audiozone.notice.save_failed.message", e.getMessage()));
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

	private static Snapshot snapshot(Profile profile) {
		return new Snapshot(profile, new RuleResolver(List.copyOf(profile.globalRules())));
	}

	// ---- Bypass (FR-13.1) ----

	public static boolean isBypassed() {
		return bypass;
	}

	public static void setBypass(boolean value) {
		if (bypass && !value) {
			// The player turned Audio Zone back on: give the error budget a fresh start (FR-13.2).
			SoundHook.resetErrorBudget();
		}
		bypass = value;
	}

	public static void toggleBypass() {
		setBypass(!bypass);
	}
}
