package audiozone.mc;

import audiozone.core.diag.DiagnosticsReport;
import audiozone.core.diag.MemoryMeter;
import audiozone.core.diag.TimingWindow;
import audiozone.core.rules.RuleResolver;
import audiozone.core.zones.Zone;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * Live numbers for Settings → Diagnostics and its "Copy report" text (PRD FR-13.3). The outline readout stays at
 * zero until outlines are drawn (M7c).
 */
public final class Diagnostics {
	/** Per tick re-check of playing sounds (FR-8.8, M7). */
	public static final TimingWindow TICK = new TimingWindow();
	/** Zone outline drawing per frame (FR-10, M7). */
	public static final TimingWindow OUTLINES = new TimingWindow();

	/** Audio Zone's main data structures, against the 8 MB budget (PRD 9.1). */
	private static final MemoryMeter MEMORY = new MemoryMeter();

	static {
		MEMORY.add("Recently played", () -> RecentLog.get().sounds().estimatedBytes());
		MEMORY.add("Playing sounds", () -> PlayingChannels.get().estimatedBytes());
		MEMORY.add("Last plays", () -> SoundHook.lastPlays().estimatedBytes());
		MEMORY.add("Repeat limits", () -> SoundHook.limiter().estimatedBytes());
		MEMORY.add("Rule caches", Diagnostics::ruleCacheBytes);
	}

	private Diagnostics() {
	}

	/** Estimated bytes of Audio Zone's data; recomputed at most once a second. Render thread only. */
	public static long memoryBytes() {
		return MEMORY.totalBytes(Util.getMillis());
	}

	private static long ruleCacheBytes() {
		return ProfileManager.precedence().estimatedBytes();
	}

	public static DiagnosticsReport.Data data() {
		long now = Util.getMillis();
		RuleResolver rules = ProfileManager.rules();
		long lookups = rules.cacheHits() + rules.cacheMisses();
		double hitRate = lookups == 0 ? Double.NaN : rules.cacheHits() / (double) lookups;
		return new DiagnosticsReport.Data(version("audiozone"), version("minecraft"), ProfileManager.isBypassed(),
				rules.ruleCount(), ProfileManager.profile().zones().size(), activeZonesHere(),
				SoundHook.soundTiming().stats(now), SoundHook.soundTiming().peakMaxMicros(),
				TICK.stats(now), OUTLINES.stats(now), hitRate, SoundHook.errorCount());
	}

	/** Enabled zones that can affect sounds here: this world and dimension, plus personal bubbles. */
	static int activeZonesHere() {
		String key = ProfileManager.key();
		String dimension = WorldSession.dimension(net.minecraft.client.Minecraft.getInstance());
		int count = 0;
		for (Zone zone : ProfileManager.profile().zones()) {
			if (zone.enabled() && (zone.isBubble() || (zone.worldKey().equals(key) && zone.dimension().equals(dimension)))) {
				count++;
			}
		}
		return count;
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
