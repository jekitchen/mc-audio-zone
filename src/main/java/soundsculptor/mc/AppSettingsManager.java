package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.settings.AppSettings;
import soundsculptor.core.settings.AppSettingsStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * App-wide settings (not per server): {@code config/soundsculptor/settings.json}. Read once at startup; every change is
 * saved on a background thread. A file that cannot be read is set aside as {@code .broken} and the defaults are used.
 */
public final class AppSettingsManager {
	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Sound Sculptor settings saver");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile AppSettings current = AppSettings.DEFAULTS;
	private static AppSettingsStore store;

	private AppSettingsManager() {
	}

	public static void init(Path configDir) {
		store = new AppSettingsStore(configDir.resolve("soundsculptor").resolve("settings.json"));
		AppSettingsStore.LoadResult result = store.load();
		current = result.settings();
		if (result.hasProblems()) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor settings {}: {}{}", store.file(), String.join(" ", result.problems()),
					result.brokenCopy() == null ? "" : " The file was renamed to " + result.brokenCopy());
		}
	}

	public static AppSettings get() {
		return current;
	}

	public static synchronized void update(UnaryOperator<AppSettings> change) {
		AppSettings after = change.apply(current);
		if (after.equals(current)) {
			return;
		}
		current = after;
		if (store != null) {
			SAVER.execute(() -> {
				try {
					store.save(after);
				} catch (IOException e) {
					SoundSculptorClient.LOGGER.error("Sound Sculptor could not save {}", store.file(), e);
				}
			});
		}
	}

	/**
	 * Waits until every queued save has been written (tests, and before the game exits), like
	 * {@link ProfileManager#flushSaves()}: at most 10 seconds, and it never throws.
	 *
	 * @return whether the queued saves finished in time
	 */
	public static boolean flushSaves() {
		try {
			SAVER.submit(() -> { }).get(10, TimeUnit.SECONDS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			SoundSculptorClient.LOGGER.warn("Sound Sculptor stopped waiting for the settings save: interrupted");
			return false;
		} catch (Exception e) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor's settings save did not finish within 10 s; it goes on in the background", e);
			return false;
		}
	}
}
