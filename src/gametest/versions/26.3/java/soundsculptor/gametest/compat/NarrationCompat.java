package soundsculptor.gametest.compat;

import java.util.function.Consumer;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationTrigger;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;

/**
 * Minecraft 26.3 version of the narration helpers. Keep the public API identical to the 26.2 copy.
 * 26.3 added a {@link NarrationTrigger} argument; keyboard is used because Sound Sculptor narration is about
 * keyboard focus.
 */
public final class NarrationCompat {
	private NarrationCompat() {
	}

	/** The text the narrator would read for {@code source} (the game tests read narration with it). */
	public static String collect(Consumer<NarrationElementOutput> source) {
		ScreenNarrationCollector collector = new ScreenNarrationCollector();
		collector.update(source, NarrationTrigger.KEYBOARD);
		return collector.collectNarrationText(true);
	}
}
