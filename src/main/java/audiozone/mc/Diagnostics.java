package audiozone.mc;

import audiozone.core.diag.DiagnosticsReport;
import audiozone.core.diag.TimingWindow;
import audiozone.core.rules.RuleResolver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * Live numbers for Settings → Diagnostics and its "Copy report" text (PRD FR-13.3). Zones, their per-tick re-check
 * and outlines arrive in M7; until then those readouts stay at zero.
 */
public final class Diagnostics {
	/** Per tick re-check of playing sounds (FR-8.8, M7). */
	public static final TimingWindow TICK = new TimingWindow();
	/** Zone outline drawing per frame (FR-10, M7). */
	public static final TimingWindow OUTLINES = new TimingWindow();

	private Diagnostics() {
	}

	public static DiagnosticsReport.Data data() {
		long now = Util.getMillis();
		RuleResolver rules = ProfileManager.rules();
		long lookups = rules.cacheHits() + rules.cacheMisses();
		double hitRate = lookups == 0 ? Double.NaN : rules.cacheHits() / (double) lookups;
		return new DiagnosticsReport.Data(version("audiozone"), version("minecraft"), ProfileManager.isBypassed(),
				rules.ruleCount(), 0, 0, SoundHook.soundTiming().stats(now), SoundHook.soundTiming().peakMaxMicros(),
				TICK.stats(now), OUTLINES.stats(now), hitRate, SoundHook.errorCount());
	}

	public static String report() {
		return DiagnosticsReport.format(data());
	}

	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
	}
}
