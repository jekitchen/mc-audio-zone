package soundsculptor.mc.compat;

import java.util.function.Consumer;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;

/** Minecraft 26.2 version of the narration helpers. Keep the public API identical to the 26.3 copy. */
public final class NarrationCompat {
	private NarrationCompat() {
	}

	/** The text the narrator would read for {@code source} (used by tests and diagnostics). */
	public static String collect(Consumer<NarrationElementOutput> source) {
		ScreenNarrationCollector collector = new ScreenNarrationCollector();
		collector.update(source);
		return collector.collectNarrationText(true);
	}
}
