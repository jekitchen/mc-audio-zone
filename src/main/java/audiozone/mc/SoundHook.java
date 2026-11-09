package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.diag.ErrorBudget;
import audiozone.core.recent.PlayStatus;
import audiozone.core.rules.RuleResolution;
import audiozone.core.SoundRange;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Decides what happens to every sound start (PRD section 7 flowchart) and reports it to observers.
 *
 * <ol>
 *   <li>Range: computes the vanilla hearing range from the sound's own volume (M1).</li>
 *   <li>Decide: with Bypass on, everything plays as vanilla; otherwise the profile's global rules may mute it (M4)
 *   or set its volume (M5). Vanilla-covered sounds (Music, UI, Voice/Speech categories) are never changed.</li>
 *   <li>Report: observers (the recently played log, the dev log) see the sound with its status.</li>
 * </ol>
 *
 * <p>Volume (FR-5.3): final loudness is vanilla loudness (including the Master and category sliders) times the rule
 * volume. The factor is applied where vanilla calculates a sound's volume: once at the start, and again whenever
 * vanilla recalculates it (moving sounds every tick, and after a slider change). A volume of 0% is then skipped by
 * vanilla like a zero slider.
 *
 * <p>Fail open (FR-13.2): if anything throws, the sound plays as vanilla, the error is logged once, and more than 10
 * errors in a minute turn on Bypass and show a notice asking the player to report the bug.
 */
public final class SoundHook {
	private static final List<SoundObserver> OBSERVERS = new CopyOnWriteArrayList<>();
	private static final SoundObservation OBSERVATION = new SoundObservation();
	private static final ErrorBudget ERRORS = new ErrorBudget();
	/** Sound ID strings, so a known sound builds no new string on the hot path (PRD 7.4). */
	private static final Map<Identifier, String> ID_STRINGS = new HashMap<>();
	/** The volume factor decided by the last {@link #onSoundStart}, used once by {@link #applyStartVolume}. */
	private static float startFactor = 1.0F;
	private static float lastAppliedFactor = 1.0F;

	private SoundHook() {
	}

	public static void addObserver(SoundObserver observer) {
		OBSERVERS.add(observer);
	}

	public static void removeObserver(SoundObserver observer) {
		OBSERVERS.remove(observer);
	}

	/**
	 * Called from {@code SoundEngine.play} after the sound is resolved, just before vanilla calculates its volume.
	 * Runs on the thread that started the sound (normally the render thread).
	 *
	 * @return true if the sound must not start (muted)
	 */
	public static boolean onSoundStart(SoundInstance instance) {
		startFactor = 1.0F;
		try {
			return handle(instance);
		} catch (RuntimeException | LinkageError e) {
			startFactor = 1.0F;
			failOpen(e);
			return false;
		}
	}

	private static boolean handle(SoundInstance instance) {
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

		Identifier id = instance.getIdentifier();
		String idString = idString(id);

		PlayStatus status = PlayStatus.PLAYING;
		RuleResolution decision = decide(instance.getSource(), idString);
		if (decision != null) {
			if (decision.muted()) {
				status = PlayStatus.MUTED;
			} else {
				startFactor = decision.effectiveVolumePercent() / 100.0F;
			}
		}

		SoundObservation o = OBSERVATION;
		o.id = id;
		o.idString = idString;
		o.category = instance.getSource();
		o.x = instance.getX();
		o.y = instance.getY();
		o.z = instance.getZ();
		o.volume = volume;
		o.range = range;
		o.relative = relative;
		o.distance = Math.sqrt(distanceSq);
		o.audible = SoundRange.isAudible(range, distanceSq);
		o.status = status;
		for (SoundObserver observer : OBSERVERS) {
			observer.onSound(o);
		}
		return status == PlayStatus.MUTED;
	}

	/** The global rules' decision for this sound, or null when Audio Zone leaves it alone (Bypass, vanilla-covered). */
	private static RuleResolution decide(SoundSource source, String idString) {
		if (ProfileManager.isBypassed() || isVanillaCovered(source)) {
			return null;
		}
		return ProfileManager.rules().resolve(idString);
	}

	/** Sounds with their own vanilla slider: Audio Zone never changes them (PRD section 5, Q8). */
	public static boolean isVanillaCovered(SoundSource source) {
		return source == SoundSource.MUSIC || source == SoundSource.UI || source == SoundSource.VOICE;
	}

	private static String idString(Identifier id) {
		String idString = ID_STRINGS.get(id);
		if (idString == null) {
			idString = id.toString();
			ID_STRINGS.put(id, idString);
		}
		return idString;
	}

	/**
	 * Called from {@code SoundEngine.play} with vanilla's volume for the sound that {@link #onSoundStart} just
	 * handled. Returns the volume with the rule's factor applied.
	 */
	public static float applyStartVolume(float vanillaVolume) {
		float factor = startFactor;
		startFactor = 1.0F;
		lastAppliedFactor = factor;
		return vanillaVolume * factor;
	}

	/**
	 * Called whenever vanilla recalculates a playing sound's volume (every tick for moving sounds, and after a
	 * slider change). A sound muted since it started goes silent.
	 */
	public static float applyVolume(SoundInstance instance, float vanillaVolume) {
		try {
			RuleResolution decision = decide(instance.getSource(), idString(instance.getIdentifier()));
			float factor = decision == null ? 1.0F : decision.effectiveVolumePercent() / 100.0F;
			lastAppliedFactor = factor;
			return vanillaVolume * factor;
		} catch (RuntimeException | LinkageError e) {
			failOpen(e);
			return vanillaVolume;
		}
	}

	/** The factor most recently applied to a volume (1 = unchanged), for tests. */
	public static float lastAppliedFactor() {
		return lastAppliedFactor;
	}

	private static void failOpen(Throwable error) {
		StackTraceElement[] trace = error.getStackTrace();
		String key = error.getClass().getName() + " at " + (trace.length > 0 ? trace[0] : "?");
		if (ERRORS.firstTime(key)) {
			AudioZoneClient.LOGGER.error("Audio Zone could not handle a sound; it plays as in vanilla", error);
		}
		if (ERRORS.recordError(Util.getMillis())) {
			ProfileManager.setBypass(true);
			AudioZoneClient.LOGGER.error("Audio Zone hit more than {} errors in a minute and turned on Bypass", ErrorBudget.MAX_ERRORS_PER_MINUTE);
			Notices.show(Component.translatable("audiozone.notice.errors.title"),
					Component.translatable("audiozone.notice.errors.message"));
		}
	}

	/** Called when the player turns Bypass off after it was on. */
	static void resetErrorBudget() {
		ERRORS.reset();
	}

	/** Errors handled this session (for diagnostics and tests). */
	public static long errorCount() {
		return ERRORS.totalErrors();
	}
}
