package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * M1 diagnostic: writes one log line per sound the player can hear (PRD 11.1, M1). Sounds out of vanilla range are
 * not logged, matching the PRD 7 flowchart.
 *
 * <p>On by default in the development environment, off in release builds. Override with the JVM property
 * {@code -Dsoundsculptor.logSounds=true|false}.
 */
public final class SoundLogger implements SoundObserver {
	public static final String PROPERTY = "soundsculptor.logSounds";

	private SoundLogger() {
	}

	public static void registerIfEnabled() {
		String setting = System.getProperty(PROPERTY);
		boolean enabled = setting != null
				? Boolean.parseBoolean(setting)
				: FabricLoader.getInstance().isDevelopmentEnvironment();
		if (enabled) {
			SoundHook.addObserver(new SoundLogger());
			SoundSculptorClient.LOGGER.info("Sound logging is on (-D{}=false to turn it off)", PROPERTY);
		}
	}

	@Override
	public void onSound(SoundObservation o) {
		if (!o.audible()) {
			return;
		}
		SoundSculptorClient.LOGGER.info(String.format(Locale.ROOT,
				"sound %s category=%s pos=(%.1f, %.1f, %.1f) volume=%.2f range=%s distance=%s",
				o.id(), o.category().getName(), o.x(), o.y(), o.z(), o.volume(),
				Float.isInfinite(o.range()) ? "inf" : String.format(Locale.ROOT, "%.1f", o.range()),
				o.relative() ? "-" : String.format(Locale.ROOT, "%.1f", o.distance())));
	}
}
