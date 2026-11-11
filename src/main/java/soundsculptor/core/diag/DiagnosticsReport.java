package soundsculptor.core.diag;

import java.util.Locale;

/**
 * The text the Diagnostics page's "Copy report" button puts on the clipboard (PRD FR-13.3). It is meant for bug
 * reports, so it is plain English with fixed number formats regardless of the game language.
 */
public final class DiagnosticsReport {
	/**
	 * Everything the report shows.
	 *
	 * @param activeZonesHere zones in the current world and dimension
	 * @param sounds          per sound start; its count is sounds per second
	 * @param peakSoundMicros worst single sound start since the last reset
	 * @param tick            per tick re-check of playing sounds
	 * @param outlines        zone outline drawing per frame
	 * @param cacheHitRate    0 to 1, or {@code NaN} when nothing has been looked up yet
	 * @param memoryBytes     estimated bytes of Sound Sculptor's data ({@link MemoryMeter#totalBytes}), or -1 if not
	 *                        measured
	 */
	public record Data(String modVersion, String minecraftVersion, boolean bypass, int globalRules, int zones,
			int activeZonesHere, TimingWindow.Stats sounds, double peakSoundMicros, TimingWindow.Stats tick,
			TimingWindow.Stats outlines, double cacheHitRate, long errors, long memoryBytes) {
		/** Without a memory figure; the report then says n/a. */
		public Data(String modVersion, String minecraftVersion, boolean bypass, int globalRules, int zones,
				int activeZonesHere, TimingWindow.Stats sounds, double peakSoundMicros, TimingWindow.Stats tick,
				TimingWindow.Stats outlines, double cacheHitRate, long errors) {
			this(modVersion, minecraftVersion, bypass, globalRules, zones, activeZonesHere, sounds, peakSoundMicros, tick,
					outlines, cacheHitRate, errors, -1);
		}
	}

	private DiagnosticsReport() {
	}

	public static String format(Data d) {
		StringBuilder out = new StringBuilder();
		line(out, "Sound Sculptor %s, Minecraft %s", d.modVersion(), d.minecraftVersion());
		line(out, "Bypass: %s", d.bypass() ? "on" : "off");
		line(out, "Global rules: %d", d.globalRules());
		line(out, "Zones: %d (%d active here)", d.zones(), d.activeZonesHere());
		line(out, "Sounds per second: %d", d.sounds().count());
		line(out, "Per sound: avg %.1f µs, max %.1f µs (peak %.1f µs)", d.sounds().averageMicros(), d.sounds().maxMicros(),
				d.peakSoundMicros());
		line(out, "Per tick re-check: avg %.1f µs, max %.1f µs", d.tick().averageMicros(), d.tick().maxMicros());
		line(out, "Outlines per frame: avg %.1f µs, max %.1f µs", d.outlines().averageMicros(), d.outlines().maxMicros());
		if (Double.isNaN(d.cacheHitRate())) {
			line(out, "Pattern cache hit rate: n/a");
		} else {
			line(out, "Pattern cache hit rate: %.1f%%", d.cacheHitRate() * 100);
		}
		if (d.memoryBytes() < 0) {
			line(out, "Memory (estimate): n/a");
		} else {
			line(out, "Memory (estimate): %s MB of %s MB", MemoryEstimate.megabytes(d.memoryBytes()),
					MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES));
		}
		line(out, "Errors this session: %d", d.errors());
		return out.toString();
	}

	private static void line(StringBuilder out, String format, Object... args) {
		out.append(String.format(Locale.ROOT, format, args)).append('\n');
	}
}
