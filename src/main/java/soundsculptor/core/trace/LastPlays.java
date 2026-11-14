package soundsculptor.core.trace;

import java.util.LinkedHashMap;
import java.util.Map;

import soundsculptor.core.diag.MemoryEstimate;

/**
 * The only thing the sound trace records on the hot path (PRD FR-14.6): the last play of each sound ID, in a table
 * of fixed size. The panel recomputes the full trace from this when it opens.
 *
 * <p>Updating an ID already in the table allocates nothing; only a new ID creates an entry. Not thread-safe:
 * record and read on the render thread.
 */
public final class LastPlays {
	public static final int CAPACITY = 256;

	private final Map<String, Slot> entries = new LinkedHashMap<>(CAPACITY * 2, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Slot> eldest) {
			return size() > CAPACITY;
		}
	};

	/** Records a play. */
	public void record(String soundId, boolean positional, String worldKey, String dimension, double x, double y, double z,
			float volumePercent, boolean dropped, boolean cueShown) {
		Slot e = entries.get(soundId);
		if (e == null) {
			e = new Slot();
			entries.put(soundId, e);
		}
		e.positional = positional;
		e.worldKey = worldKey;
		e.dimension = dimension;
		e.x = x;
		e.y = y;
		e.z = z;
		e.volumePercent = volumePercent;
		e.dropped = dropped;
		e.cueShown = cueShown;
	}

	/** The last play of {@code soundId}, or {@code null} if it has not played (or was forgotten). */
	public LastPlay get(String soundId) {
		Slot e = entries.get(soundId);
		if (e == null) {
			return null;
		}
		return new LastPlay(e.positional, e.worldKey, e.dimension, e.x, e.y, e.z, e.volumePercent, e.dropped, e.cueShown);
	}

	public int size() {
		return entries.size();
	}

	/**
	 * Estimated bytes this table keeps, for the Diagnostics memory readout (PRD 9.1). It walks the IDs, so call it on
	 * demand through {@code MemoryMeter}, not per sound. World keys and dimensions are shared and not counted.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(MemoryEstimate.REFERENCE) + MemoryEstimate.LINKED_HASH_MAP
				+ MemoryEstimate.array(MemoryEstimate.REFERENCE, Integer.highestOneBit(CAPACITY * 2 - 1) << 1)
				+ entries.size() * (MemoryEstimate.LINKED_HASH_NODE + Slot.BYTES);
		for (String id : entries.keySet()) {
			bytes += MemoryEstimate.string(id);
		}
		return bytes;
	}

	private static final class Slot {
		static final long BYTES = MemoryEstimate.object(2 * MemoryEstimate.REFERENCE + 3 * 8 + 4 + 3);

		boolean positional;
		String worldKey;
		String dimension;
		double x;
		double y;
		double z;
		float volumePercent;
		boolean dropped;
		boolean cueShown;
	}
}
