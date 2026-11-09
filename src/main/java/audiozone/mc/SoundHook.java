package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.SoundRange;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Receives every sound start from {@code SoundEngineMixin} and reports it to observers. M1 only observes:
 * nothing here changes what the player hears.
 *
 * <p>Fail-open: if anything throws, the error is logged once and the hook stops observing, so a broken port can
 * never stop sounds from playing.
 */
public final class SoundHook {
	private static final List<SoundObserver> OBSERVERS = new CopyOnWriteArrayList<>();
	private static final SoundObservation OBSERVATION = new SoundObservation();
	private static volatile boolean disabled;

	private SoundHook() {
	}

	public static void addObserver(SoundObserver observer) {
		OBSERVERS.add(observer);
	}

	public static void removeObserver(SoundObserver observer) {
		OBSERVERS.remove(observer);
	}

	/**
	 * Called from {@code SoundEngine.play} after the sound has been resolved, just before vanilla calculates the
	 * final volume. Runs on the thread that started the sound (normally the render thread).
	 */
	public static void onSoundStart(SoundInstance instance) {
		if (disabled || OBSERVERS.isEmpty()) {
			return;
		}
		try {
			observe(instance);
		} catch (RuntimeException | LinkageError e) {
			disabled = true;
			AudioZoneClient.LOGGER.error("Audio Zone sound hook failed and has been turned off; sounds play as in vanilla", e);
		}
	}

	private static void observe(SoundInstance instance) {
		Sound sound = instance.getSound();
		boolean relative = instance.isRelative();
		boolean noAttenuation = instance.getAttenuation() == SoundInstance.Attenuation.NONE;
		float volume = instance.getVolume();
		float range = SoundRange.vanillaRange(volume, sound.getAttenuationDistance(), relative, noAttenuation);

		double distanceSq = 0.0;
		if (!relative) {
			Vec3 listener = Minecraft.getInstance().getSoundManager().getListenerTransform().position();
			double dx = instance.getX() - listener.x();
			double dy = instance.getY() - listener.y();
			double dz = instance.getZ() - listener.z();
			distanceSq = dx * dx + dy * dy + dz * dz;
		}

		SoundObservation o = OBSERVATION;
		o.id = instance.getIdentifier();
		o.category = instance.getSource();
		o.x = instance.getX();
		o.y = instance.getY();
		o.z = instance.getZ();
		o.volume = volume;
		o.range = range;
		o.relative = relative;
		o.distance = Math.sqrt(distanceSq);
		o.audible = SoundRange.isAudible(range, distanceSq);

		for (SoundObserver observer : OBSERVERS) {
			observer.onSound(o);
		}
	}
}
