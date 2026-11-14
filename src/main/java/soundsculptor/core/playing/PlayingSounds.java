package soundsculptor.core.playing;

import soundsculptor.core.boost.Gain;
import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.sets.EntryResolution;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;

/**
 * The positional sounds that are playing right now, re-decided every client tick (PRD FR-8.8), so moving sources and
 * long sounds change volume while they play.
 *
 * <p>How the sound hook uses it, all on the game thread:
 * <ol>
 *   <li>When a positional sound starts and is allowed to play, {@link #start} it with the handle the sound hook uses
 *       for that channel, the vanilla gain and the gain it was started with. A sound dropped by a repeat limit never
 *       starts, so it is never tracked.</li>
 *   <li>Each tick, {@link #move} sounds whose source moved, then call {@link #update}. It re-runs
 *       {@link Precedence#decide} and calls the listener only for sounds whose gain changed. Apply that gain to the
 *       channel; never stop a sound because its gain became 0 (mute re-evaluates to silence, FR-8.8).</li>
 *   <li>{@link #stop} a sound when its channel stops.</li>
 * </ol>
 *
 * <p>Only the volume is re-decided. Repeat limits are decided at start only, and cues are shown at start only.
 *
 * <p>Range-safe: the new gain comes from {@link Gain#finalGain}, which scales the gain vanilla computed and never
 * touches the hearing range (FR-5.6).
 *
 * <p>A sound whose inputs did not change since the last tick is not re-decided: same position, same profile snapshot,
 * same world and dimension. Zones have fixed anchors, so the player moving changes nothing. That keeps quiet ticks
 * almost free.
 *
 * <p>Allocates nothing once created. Not thread-safe.
 */
public final class PlayingSounds {
	/**
	 * Most sounds tracked at once. Vanilla plays at most 247 normal and 8 streaming channels together, so 512 leaves
	 * room for audio mods that raise the channel count. When the table is full, further sounds simply play as they
	 * started and are not re-decided (fail open).
	 */
	public static final int CAPACITY = 512;

	/** Gain changes smaller than this are not reported: nobody can hear them, and each report is an OpenAL call. */
	static final float GAIN_EPSILON = 1e-4F;

	/** Receives a sound's new gain. */
	@FunctionalInterface
	public interface GainListener {
		void gainChanged(long handle, float gain);
	}

	private static final int SLOTS = CAPACITY * 2; // hash slots, a power of two at most half full
	private static final int EMPTY = -1;

	// Dense arrays of tracked sounds, indexes 0 to size - 1.
	private final long[] handles = new long[CAPACITY];
	private final String[] soundIds = new String[CAPACITY];
	private final double[] xs = new double[CAPACITY];
	private final double[] ys = new double[CAPACITY];
	private final double[] zs = new double[CAPACITY];
	private final float[] vanillaGains = new float[CAPACITY];
	private final float[] gains = new float[CAPACITY];
	private final boolean[] dirty = new boolean[CAPACITY];
	/** Per sound, what each rule list resolves its ID to, for the current snapshot; see {@link Precedence#resolve}. */
	private final EntryResolution[][] resolved = new EntryResolution[CAPACITY][Precedence.MAX_ZONES + 1];
	private final boolean[] isResolved = new boolean[CAPACITY];
	private int size;

	// Open-addressing hash from handle to dense index.
	private final long[] slotHandles = new long[SLOTS];
	private final int[] slotIndexes = new int[SLOTS];

	private final SoundDecision scratch = new SoundDecision();
	private long rejected;
	private long decisions;

	// Inputs of the last update, to skip sounds nothing could have changed.
	private Precedence lastPrecedence;
	private String lastWorldKey;
	private String lastDimension;
	private boolean lastBoostSupported;
	private boolean lastBypassed;

	public PlayingSounds() {
		java.util.Arrays.fill(slotIndexes, EMPTY);
	}

	/**
	 * Starts tracking a playing positional sound.
	 *
	 * @param handle      the sound hook's ID for the playing channel; unique among playing sounds
	 * @param vanillaGain vanilla's gain for the channel, including the Master and category sliders
	 * @param startGain   the gain the sound started with, after Sound Sculptor's decision
	 * @return false if the table is full or the handle is already tracked; the sound then just plays on unchanged
	 */
	public boolean start(long handle, String soundId, double x, double y, double z, float vanillaGain, float startGain) {
		if (size == CAPACITY || indexOf(handle) != EMPTY) {
			rejected++;
			return false;
		}
		int i = size++;
		handles[i] = handle;
		soundIds[i] = soundId;
		xs[i] = x;
		ys[i] = y;
		zs[i] = z;
		vanillaGains[i] = vanillaGain;
		gains[i] = startGain;
		dirty[i] = true;
		isResolved[i] = false;
		putSlot(handle, i);
		return true;
	}

	/** The source moved (a mob, a minecart). Ignored for a sound that is not tracked. */
	public void move(long handle, double x, double y, double z) {
		int i = indexOf(handle);
		if (i == EMPTY || (xs[i] == x && ys[i] == y && zs[i] == z)) {
			return;
		}
		xs[i] = x;
		ys[i] = y;
		zs[i] = z;
		dirty[i] = true;
	}

	/** Vanilla's gain for the channel changed, for example the player moved a volume slider. */
	public void setVanillaGain(long handle, float vanillaGain) {
		int i = indexOf(handle);
		if (i != EMPTY && vanillaGains[i] != vanillaGain) {
			vanillaGains[i] = vanillaGain;
			dirty[i] = true;
		}
	}

	/** The channel stopped. Ignored for a sound that is not tracked. */
	public void stop(long handle) {
		int i = indexOf(handle);
		if (i == EMPTY) {
			return;
		}
		removeSlot(handle);
		int last = --size;
		if (i != last) {
			handles[i] = handles[last];
			soundIds[i] = soundIds[last];
			xs[i] = xs[last];
			ys[i] = ys[last];
			zs[i] = zs[last];
			vanillaGains[i] = vanillaGains[last];
			gains[i] = gains[last];
			dirty[i] = dirty[last];
			isResolved[i] = isResolved[last];
			EntryResolution[] row = resolved[i]; // swap the rows, so no array is ever allocated
			resolved[i] = resolved[last];
			resolved[last] = row;
			slotIndexes[findSlot(handles[i])] = i;
		}
		soundIds[last] = null;
	}

	/** Stops tracking everything, for example when leaving a world. */
	public void clear() {
		for (int i = 0; i < size; i++) {
			soundIds[i] = null;
		}
		size = 0;
		java.util.Arrays.fill(slotIndexes, EMPTY);
		lastPrecedence = null;
	}

	/**
	 * Re-decides every tracked sound and reports the ones whose gain changed.
	 *
	 * @param precedence     the current profile snapshot
	 * @param worldKey       the current server or world
	 * @param dimension      the current dimension
	 * @param boostSupported whether volumes above 100% can play (FR-5.5)
	 * @return how many sounds changed gain
	 */
	public int update(Precedence precedence, String worldKey, String dimension, boolean boostSupported,
			GainListener listener) {
		if (precedence != lastPrecedence) {
			java.util.Arrays.fill(isResolved, 0, size, false);
		}
		boolean everything = lastBypassed || precedence != lastPrecedence || boostSupported != lastBoostSupported
				|| !worldKey.equals(lastWorldKey) || !dimension.equals(lastDimension);
		lastPrecedence = precedence;
		lastWorldKey = worldKey;
		lastDimension = dimension;
		lastBoostSupported = boostSupported;
		lastBypassed = false;

		int changed = 0;
		SoundDecision out = scratch;
		for (int i = 0; i < size; i++) {
			if (!everything && !dirty[i]) {
				continue;
			}
			dirty[i] = false;
			if (!isResolved[i]) {
				// A playing sound's ID never changes, so look its rules up once per snapshot, not every tick.
				precedence.resolve(soundIds[i], resolved[i]);
				isResolved[i] = true;
			}
			decisions++;
			precedence.decideResolved(resolved[i], true, worldKey, dimension, xs[i], ys[i], zs[i], out);
			if (report(i, Gain.finalGain(vanillaGains[i], out.volumePercent(), boostSupported), listener)) {
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Bypass is on (FR-13.1): every tracked sound goes back to its vanilla gain. The next {@link #update} re-decides
	 * everything.
	 */
	public int updateBypassed(GainListener listener) {
		lastBypassed = true;
		int changed = 0;
		for (int i = 0; i < size; i++) {
			dirty[i] = false;
			if (report(i, vanillaGains[i], listener)) {
				changed++;
			}
		}
		return changed;
	}

	private boolean report(int i, float gain, GainListener listener) {
		if (Math.abs(gain - gains[i]) < GAIN_EPSILON) {
			return false;
		}
		gains[i] = gain;
		listener.gainChanged(handles[i], gain);
		return true;
	}

	/** The gain last given to a tracked sound, or {@code NaN} if it is not tracked. */
	public float gain(long handle) {
		int i = indexOf(handle);
		return i == EMPTY ? Float.NaN : gains[i];
	}

	public boolean isTracked(long handle) {
		return indexOf(handle) != EMPTY;
	}

	/** Sounds tracked now. */
	public int size() {
		return size;
	}

	/**
	 * Estimated bytes this table keeps, for the Diagnostics memory readout (PRD 9.1). The arrays have a fixed size;
	 * it walks the tracked IDs, so call it on demand through {@code MemoryMeter}, not per tick. The resolutions it
	 * points at belong to the rule lists' caches and are counted there.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(18 * MemoryEstimate.REFERENCE + 4 + 2 * 8 + 2)
				+ MemoryEstimate.object(16 * MemoryEstimate.REFERENCE + 8 * 4)
				+ MemoryEstimate.array(8, CAPACITY) + MemoryEstimate.array(MemoryEstimate.REFERENCE, CAPACITY)
				+ 3 * MemoryEstimate.array(8, CAPACITY) + 2 * MemoryEstimate.array(4, CAPACITY)
				+ 2 * MemoryEstimate.array(1, CAPACITY)
				+ MemoryEstimate.array(MemoryEstimate.REFERENCE, CAPACITY)
				+ CAPACITY * MemoryEstimate.array(MemoryEstimate.REFERENCE, Precedence.MAX_ZONES + 1)
				+ MemoryEstimate.array(8, SLOTS) + MemoryEstimate.array(4, SLOTS);
		for (int i = 0; i < size; i++) {
			bytes += MemoryEstimate.string(soundIds[i]);
		}
		return bytes;
	}

	/** Sounds not tracked because the table was full (or a handle was started twice), for diagnostics. */
	public long rejectedCount() {
		return rejected;
	}

	/** Sounds re-decided so far (skipped sounds do not count), for diagnostics. */
	public long decisionCount() {
		return decisions;
	}

	// --- handle hash (linear probing, backward-shift deletion) ---

	private static int home(long handle) {
		long h = handle * 0x9E3779B97F4A7C15L;
		return (int) (h >>> 40) & (SLOTS - 1);
	}

	/** The hash slot holding {@code handle}, or {@code EMPTY}. */
	private int findSlot(long handle) {
		for (int s = home(handle);; s = (s + 1) & (SLOTS - 1)) {
			if (slotIndexes[s] == EMPTY) {
				return EMPTY;
			}
			if (slotHandles[s] == handle) {
				return s;
			}
		}
	}

	private int indexOf(long handle) {
		int s = findSlot(handle);
		return s == EMPTY ? EMPTY : slotIndexes[s];
	}

	private void putSlot(long handle, int index) {
		int s = home(handle);
		while (slotIndexes[s] != EMPTY) {
			s = (s + 1) & (SLOTS - 1);
		}
		slotHandles[s] = handle;
		slotIndexes[s] = index;
	}

	private void removeSlot(long handle) {
		int hole = findSlot(handle);
		slotIndexes[hole] = EMPTY;
		for (int s = (hole + 1) & (SLOTS - 1); slotIndexes[s] != EMPTY; s = (s + 1) & (SLOTS - 1)) {
			int want = home(slotHandles[s]);
			// Move the entry back into the hole if the hole lies between its home slot and where it sits now.
			if (((s - want) & (SLOTS - 1)) >= ((s - hole) & (SLOTS - 1))) {
				slotHandles[hole] = slotHandles[s];
				slotIndexes[hole] = slotIndexes[s];
				slotIndexes[s] = EMPTY;
				hole = s;
			}
		}
	}
}
