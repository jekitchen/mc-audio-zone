package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.playing.PlayingSounds;
import audiozone.core.zones.Precedence;
import audiozone.mc.mixin.SoundEngineAccessor;
import audiozone.mc.mixin.SoundManagerAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Re-decides playing positional sounds every client tick (PRD FR-8.8), so a sound changes volume when it moves into
 * or out of a zone, when the player edits a zone, or when Bypass changes. Only the volume changes; a sound muted by a
 * zone keeps playing silently and comes back when it leaves the zone.
 *
 * <p>The core {@link PlayingSounds} table does the deciding; this class connects it to vanilla's channels. Each tracked
 * sound gets a handle: its slot in a fixed pool, so tracking allocates nothing per sound. Each tick, sounds whose
 * channel is gone are dropped, moved sources are updated, and changed gains are sent to their channels.
 *
 * <p>Everything runs on the game thread.
 */
public final class PlayingChannels {
	private static final PlayingChannels INSTANCE = new PlayingChannels();

	private final PlayingSounds playing = new PlayingSounds();
	private final SoundInstance[] instances = new SoundInstance[PlayingSounds.CAPACITY];
	private final int[] seenTick = new int[PlayingSounds.CAPACITY];
	private final IdentityHashMap<SoundInstance, Integer> handles = new IdentityHashMap<>(PlayingSounds.CAPACITY * 2);
	private final int[] free = new int[PlayingSounds.CAPACITY];
	private int freeCount;
	private int tick;
	private Map<SoundInstance, ChannelAccess.ChannelHandle> channels;
	private final PlayingSounds.GainListener applyGain = this::applyGain;

	private PlayingChannels() {
		for (int i = 0; i < free.length; i++) {
			free[i] = free.length - 1 - i;
		}
		freeCount = free.length;
	}

	public static PlayingChannels get() {
		return INSTANCE;
	}

	/** A positional sound Audio Zone decided is starting with {@code startGain}. Returns false if it is not tracked. */
	boolean start(SoundInstance instance, String soundId, float vanillaGain, float startGain) {
		if (freeCount == 0 || handles.containsKey(instance)) {
			return false;
		}
		int handle = free[--freeCount];
		if (!playing.start(handle, soundId, instance.getX(), instance.getY(), instance.getZ(), vanillaGain, startGain)) {
			free[freeCount++] = handle;
			return false;
		}
		instances[handle] = instance;
		seenTick[handle] = tick;
		handles.put(instance, HANDLES[handle]);
		return true;
	}

	/** Boxed handles made once, so tracking a sound never allocates. */
	private static final Integer[] HANDLES = new Integer[PlayingSounds.CAPACITY];

	static {
		for (int i = 0; i < HANDLES.length; i++) {
			HANDLES[i] = i;
		}
	}

	/**
	 * Vanilla recalculated a playing sound's volume (a moving sound's tick, a slider change). Returns the gain to use:
	 * the tracked gain for a sound this class manages, or {@code NaN} if it is not tracked.
	 */
	float recalculated(SoundInstance instance, float vanillaGain) {
		Integer handle = handles.get(instance);
		if (handle == null) {
			return Float.NaN;
		}
		playing.setVanillaGain(handle, vanillaGain);
		return playing.gain(handle);
	}

	public boolean isTracked(SoundInstance instance) {
		return handles.containsKey(instance);
	}

	/** The gain last given to a tracked sound, or {@code NaN}. */
	public float gain(SoundInstance instance) {
		Integer handle = handles.get(instance);
		return handle == null ? Float.NaN : playing.gain(handle);
	}

	public int size() {
		return playing.size();
	}

	/** Estimated bytes of the tracking tables, for the Diagnostics memory readout. */
	public long estimatedBytes() {
		return playing.estimatedBytes();
	}

	/** Called at the end of every client tick. */
	public void tick(Minecraft minecraft) {
		if (playing.size() == 0) {
			return;
		}
		long started = System.nanoTime();
		try {
			channels = ((SoundEngineAccessor) ((SoundManagerAccessor) minecraft.getSoundManager()).audiozone$soundEngine())
					.audiozone$instanceToChannel();
			tick++;
			for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : channels.entrySet()) {
				Integer handle = handles.get(entry.getKey());
				if (handle != null) {
					seenTick[handle] = tick;
					SoundInstance instance = entry.getKey();
					playing.move(handle, instance.getX(), instance.getY(), instance.getZ());
				}
			}
			for (int handle = 0; handle < instances.length; handle++) {
				if (instances[handle] != null && seenTick[handle] != tick) {
					release(handle);
				}
			}
			if (ProfileManager.isBypassed() || !ProfileManager.isLoaded()) {
				playing.updateBypassed(applyGain);
			} else {
				Vec3 eye = minecraft.player == null ? null : minecraft.player.getEyePosition();
				Precedence precedence = ProfileManager.precedence();
				playing.update(precedence, ProfileManager.key(), WorldSession.dimension(minecraft),
						eye == null ? Double.NaN : eye.x, eye == null ? Double.NaN : eye.y, eye == null ? Double.NaN : eye.z,
						false, applyGain);
			}
		} catch (RuntimeException | LinkageError e) {
			// Fail open: stop re-deciding; the sounds keep the volume they have.
			AudioZoneClient.LOGGER.error("Audio Zone could not update playing sounds", e);
			clear();
		} finally {
			channels = null;
			Diagnostics.TICK.add(System.nanoTime() - started, Util.getMillis());
		}
	}

	private void applyGain(long handle, float gain) {
		SoundInstance instance = instances[(int) handle];
		ChannelAccess.ChannelHandle channel = instance == null || channels == null ? null : channels.get(instance);
		if (channel != null) {
			channel.execute(c -> c.setVolume(gain));
		}
	}

	private void release(int handle) {
		playing.stop(handle);
		handles.remove(instances[handle]);
		instances[handle] = null;
		free[freeCount++] = handle;
	}

	/** Forgets every sound, e.g. when leaving a world. */
	public void clear() {
		playing.clear();
		handles.clear();
		java.util.Arrays.fill(instances, null);
		for (int i = 0; i < free.length; i++) {
			free[i] = free.length - 1 - i;
		}
		freeCount = free.length;
	}
}
