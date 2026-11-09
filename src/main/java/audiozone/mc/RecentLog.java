package audiozone.mc;

import audiozone.core.recent.RecentSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Util;

/**
 * Feeds every observed sound start into the recently played log (PRD FR-1.1). {@link RecentSounds} applies the
 * fair-play range filter and the vanilla-covered exclusions. The log is cleared when joining or leaving a world.
 *
 * <p>Runs on the thread that starts sounds (the render thread), as does the screen that reads it.
 */
public final class RecentLog implements SoundObserver {
	private static final RecentLog INSTANCE = new RecentLog();

	private final RecentSounds sounds = new RecentSounds();

	private RecentLog() {
	}

	public static RecentLog get() {
		return INSTANCE;
	}

	public RecentSounds sounds() {
		return sounds;
	}

	public static void register() {
		SoundHook.addObserver(INSTANCE);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> INSTANCE.sounds.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.sounds.clear());
	}

	@Override
	public void onSound(SoundObservation o) {
		sounds.record(o.idString(), o.category().getName(), !o.relative(), o.distance(), o.audible(), o.status(), o.zoneName(), Util.getMillis());
	}
}
