package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.diag.ErrorBudget;
import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.start.SoundStart;
import soundsculptor.core.start.SoundStartDecider;
import soundsculptor.core.start.SoundStartResult;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.SoundRange;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
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
 *   <li>Report: observers (the recently played log, the dev log) see the sound with its status. The sound's last play
 *   is kept for its trace (FR-14.6), and the time all this took feeds Diagnostics (FR-13.3).</li>
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
	/** The sound {@link #onSoundStart} just decided, until {@link #applyStartVolume} tracks it. */
	private static SoundInstance pendingInstance;
	private static String pendingId;
	private static boolean pendingTracked;
	/** Start volume factor of a sound that a zone mutes but may unmute while it plays: inaudible, but not zero. */
	static final float SILENT = 1.0E-6F;
	private static final SoundStart START = new SoundStart();
	private static final SoundStartResult RESULT = new SoundStartResult();
	private static Precedence deciderProfile;
	private static float lastAppliedFactor = 1.0F;
	private static float lastStartFactor = 1.0F;
	private static final TimingWindow SOUND_TIMING = new TimingWindow();
	private static final LastPlays LAST_PLAYS = new LastPlays();
	private static final SoundStartDecider DECIDER = new SoundStartDecider(LAST_PLAYS);

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
		long started = System.nanoTime();
		startFactor = 1.0F;
		pendingInstance = null;
		try {
			return handle(instance);
		} catch (RuntimeException | LinkageError e) {
			startFactor = 1.0F;
			pendingInstance = null;
			failOpen(e);
			return false;
		} finally {
			SOUND_TIMING.add(System.nanoTime() - started, Util.getMillis());
		}
	}

	private static boolean handle(SoundInstance instance) {
		Minecraft minecraft = Minecraft.getInstance();
		Sound sound = instance.getSound();
		boolean relative = instance.isRelative();
		boolean noAttenuation = instance.getAttenuation() == SoundInstance.Attenuation.NONE;
		float volume = instance.getVolume();
		float range = SoundRange.vanillaRange(volume, sound.getAttenuationDistance(), relative, noAttenuation);

		double distanceSq = 0.0;
		if (!relative) {
			Vec3 listener = minecraft.getSoundManager().getListenerTransform().position();
			double dx = instance.getX() - listener.x();
			double dy = instance.getY() - listener.y();
			double dz = instance.getZ() - listener.z();
			distanceSq = dx * dx + dy * dy + dz * dz;
		}

		Identifier id = instance.getIdentifier();
		String idString = idString(id);

		// Decide: global rules, zones and precedence (core SoundStartDecider). Outside a world nothing is decided.
		SoundStartResult result = null;
		if (ProfileManager.isLoaded()) {
			Precedence precedence = ProfileManager.precedence();
			if (precedence != deciderProfile) {
				DECIDER.setProfile(precedence);
				deciderProfile = precedence;
			}
			SoundStart in = START;
			in.reset();
			in.soundId = idString;
			in.vanillaCovered = isVanillaCovered(instance.getSource());
			in.positional = !relative;
			in.range = range;
			in.distanceSq = distanceSq;
			in.worldKey = ProfileManager.key();
			in.dimension = WorldSession.dimension(minecraft);
			in.x = instance.getX();
			in.y = instance.getY();
			in.z = instance.getZ();
			if (minecraft.player != null) {
				// Read the eye position without building a Vec3 per sound (PRD 7.4: no allocation on the hot path).
				in.px = minecraft.player.getX();
				in.py = minecraft.player.getEyeY();
				in.pz = minecraft.player.getZ();
			}
			WeighedSoundEvents events = minecraft.getSoundManager().getSoundEvent(id);
			in.hasSubtitle = events != null && events.getSubtitle() != null;
			in.bypass = ProfileManager.isBypassed();
			in.nowMs = Util.getMillis();
			result = RESULT;
			DECIDER.start(in, result);
		}

		PlayStatus status = result == null ? PlayStatus.PLAYING : result.status();
		boolean handled = result != null && result.handled();
		if (handled && !result.cancel()) {
			// A sound muted by a zone starts silently, so the per-tick check can bring it back (FR-8.8). Vanilla skips
			// a sound whose volume is exactly 0, so it starts at an inaudible volume instead.
			startFactor = result.factor() > 0 ? result.factor() : SILENT;
			pendingInstance = instance;
			pendingId = idString;
			pendingTracked = !relative;
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
		o.zoneName = handled ? result.zoneName() : null;
		for (SoundObserver observer : OBSERVERS) {
			observer.onSound(o);
		}
		return handled && result.cancel();
	}

	/** Sounds with their own vanilla slider: Sound Sculptor never changes them (PRD section 5, Q8). */
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
		SoundInstance instance = pendingInstance;
		startFactor = 1.0F;
		pendingInstance = null;
		lastAppliedFactor = factor;
		lastStartFactor = factor;
		float gain = vanillaVolume * factor;
		if (instance != null && pendingTracked && gain > 0) {
			try {
				PlayingChannels.get().start(instance, pendingId, vanillaVolume, gain);
			} catch (RuntimeException | LinkageError e) {
				failOpen(e);
			}
		}
		return gain;
	}

	/**
	 * Called whenever vanilla recalculates a playing sound's volume (every tick for moving sounds, and after a
	 * slider change). A sound muted since it started goes silent.
	 */
	public static float applyVolume(SoundInstance instance, float vanillaVolume) {
		try {
			float tracked = PlayingChannels.get().recalculated(instance, vanillaVolume);
			if (!Float.isNaN(tracked)) {
				lastAppliedFactor = vanillaVolume == 0 ? 1.0F : tracked / vanillaVolume;
				return tracked;
			}
			float factor = 1.0F;
			if (ProfileManager.isLoaded() && !ProfileManager.isBypassed() && !isVanillaCovered(instance.getSource())) {
				// Not tracked (non-positional, or the table was full): only Everywhere applies.
				String id = idString(instance.getIdentifier());
				factor = Math.min(ProfileManager.precedence().everywhere().resolve(id).effectiveVolumePercent(), 100) / 100.0F;
			}
			lastAppliedFactor = factor;
			return vanillaVolume * factor;
		} catch (RuntimeException | LinkageError e) {
			failOpen(e);
			return vanillaVolume;
		}
	}

	/** The repeat-limit counters, for the Diagnostics memory readout. */
	public static soundsculptor.core.limit.RepeatLimiter limiter() {
		return DECIDER.limiter();
	}

	/** Time spent per sound start, for Diagnostics (FR-13.3). Render thread only. */
	public static TimingWindow soundTiming() {
		return SOUND_TIMING;
	}

	/** Each sound's last play, for the "Why?" panel (FR-14.6). Render thread only. */
	public static LastPlays lastPlays() {
		return LAST_PLAYS;
	}

	/**
	 * The factor applied to the most recent sound start (1 = unchanged), for tests. Unlike
	 * {@link #lastAppliedFactor()}, per-tick recalculations of other playing sounds do not change it.
	 */
	public static float lastStartFactor() {
		return lastStartFactor;
	}

	/** The factor most recently applied to a volume (1 = unchanged), for tests. */
	public static float lastAppliedFactor() {
		return lastAppliedFactor;
	}

	private static void failOpen(Throwable error) {
		StackTraceElement[] trace = error.getStackTrace();
		String key = error.getClass().getName() + " at " + (trace.length > 0 ? trace[0] : "?");
		if (ERRORS.firstTime(key)) {
			SoundSculptorClient.LOGGER.error("Sound Sculptor could not handle a sound; it plays as in vanilla", error);
		}
		if (ERRORS.recordError(Util.getMillis())) {
			ProfileManager.setBypass(true);
			SoundSculptorClient.LOGGER.error("Sound Sculptor hit more than {} errors in a minute and turned on Bypass", ErrorBudget.MAX_ERRORS_PER_MINUTE);
			Notices.show(Component.translatable("soundsculptor.notice.errors.title"),
					Component.translatable("soundsculptor.notice.errors.message"));
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
