package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.settings.AppSettings;
import audiozone.core.settings.AppSettingsStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * App-wide settings (not per server): {@code config/audiozone/settings.json}. Read once at startup; every change is
 * saved on a background thread. A file that cannot be read is set aside as {@code .broken} and the defaults are used.
 */
public final class AppSettingsManager {
	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Audio Zone settings saver");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile AppSettings current = AppSettings.DEFAULTS;
	private static AppSettingsStore store;

	private AppSettingsManager() {
	}

	public static void init(Path configDir) {
		store = new AppSettingsStore(configDir.resolve("audiozone").resolve("settings.json"));
		AppSettingsStore.LoadResult result = store.load();
		current = result.settings();
		if (result.hasProblems()) {
			AudioZoneClient.LOGGER.warn("Audio Zone settings {}: {}{}", store.file(), String.join(" ", result.problems()),
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
					AudioZoneClient.LOGGER.error("Audio Zone could not save {}", store.file(), e);
				}
			});
		}
	}

	/** Waits until every queued save has been written (before the game exits). */
	public static void flushSaves() {
		try {
			SAVER.submit(() -> { }).get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("settings save did not finish", e);
		}
	}
}
