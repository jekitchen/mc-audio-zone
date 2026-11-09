package audiozone.core.recent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import audiozone.core.diag.MemoryEstimate;

/**
 * The recently played sounds log behind the Recently Played tab (PRD FR-1.1 to FR-1.3).
 *
 * <p>A play is recorded only if it is non-positional, or positional and within vanilla hearing range: the same set
 * vanilla subtitles would show (fair play). Sounds Audio Zone muted or limited are recorded too, with their status.
 * Vanilla-covered categories (Music, UI, Voice/Speech) are never recorded, but their IDs are remembered so search can
 * hide them (FR-3.1).
 *
 * <p>Rows are grouped by sound ID and kept for 3 minutes after their last play, at most {@link #MAX_IDS} of them.
 * Play counts use one-second buckets, so a play leaves the count within a second of turning 3 minutes old. The
 * bucket holding a row's most recent play always counts while the row is listed, so a listed row never shows 0 plays.
 *
 * <p>Not thread-safe: record and snapshot on the same thread (the render thread, where sounds start). Recording a
 * sound already in the list allocates nothing.
 */
public final class RecentSounds {
	public static final long WINDOW_MS = 180_000;
	public static final int MAX_IDS = 200;

	/** Sound source names ({@code SoundSource.getName()}) whose sounds have their own vanilla slider (section 5, Q8). */
	public static final Set<String> EXCLUDED_CATEGORIES = Set.of("music", "ui", "voice");

	/** Cap for the session-long sets, since servers can send arbitrary sound IDs. */
	static final int MAX_SEEN_IDS = 4096;

	private static final int BUCKETS = (int) (WINDOW_MS / 1000);

	private final Map<String, Row> rows = new HashMap<>();
	private final Set<String> seenExcluded = new HashSet<>();
	private final Map<String, String> seenCategories = new HashMap<>();

	/**
	 * Records one sound start.
	 *
	 * @param soundId    e.g. {@code minecraft:entity.villager.ambient}
	 * @param category   the sound source name, e.g. {@code neutral} (null is accepted and listed as unknown)
	 * @param positional whether the sound has a world position (not relative to the listener)
	 * @param distance   distance from the listener in blocks (ignored when not positional)
	 * @param audible    whether vanilla would treat it as heard (range check from the un-boosted volume)
	 * @param status     what Audio Zone did with this play
	 * @param zoneName   the zone that decided, or {@code null}
	 * @param nowMs      current time in milliseconds from a clock that never goes backwards, such as Minecraft's
	 *                   {@code Util.getMillis()} (not the wall clock); the same clock must be passed to {@link #snapshot}
	 */
	public void record(String soundId, String category, boolean positional, double distance, boolean audible,
			PlayStatus status, String zoneName, long nowMs) {
		if (category != null && EXCLUDED_CATEGORIES.contains(category)) {
			if (seenExcluded.size() < MAX_SEEN_IDS) {
				seenExcluded.add(soundId);
			}
			return;
		}
		if (positional && !audible) {
			return;
		}
		if (seenCategories.size() < MAX_SEEN_IDS || seenCategories.containsKey(soundId)) {
			seenCategories.put(soundId, category);
		}
		Row row = rows.get(soundId);
		if (row == null) {
			makeRoom(nowMs);
			row = new Row(soundId);
			rows.put(soundId, row);
		}
		row.add(category, DistanceBand.of(positional, distance), status, zoneName, nowMs);
	}

	/** Frees a slot for a new ID: drops expired rows, then the least recently played one. */
	private void makeRoom(long nowMs) {
		if (rows.size() < MAX_IDS) {
			return;
		}
		pruneExpired(nowMs);
		if (rows.size() < MAX_IDS) {
			return;
		}
		Row oldest = null;
		for (Row row : rows.values()) {
			if (oldest == null || row.lastPlayedMs < oldest.lastPlayedMs) {
				oldest = row;
			}
		}
		rows.remove(oldest.id);
	}

	private void pruneExpired(long nowMs) {
		Iterator<Row> it = rows.values().iterator();
		while (it.hasNext()) {
			if (it.next().expired(nowMs)) {
				it.remove();
			}
		}
	}

	/**
	 * The current rows, sorted. The list refreshes at most 4 times a second (FR-1.6), so this may allocate.
	 *
	 * @param displayName subtitle text per ID for {@link SortOrder#NAME}, or {@code null} to sort by ID; may return
	 *                    {@code null} for sounds without a subtitle
	 */
	public List<RecentRow> snapshot(long nowMs, SortOrder order, Function<String, String> displayName) {
		pruneExpired(nowMs);
		List<RecentRow> result = new ArrayList<>(rows.size());
		for (Row row : rows.values()) {
			result.add(row.toRecord(nowMs));
		}
		result.sort(comparator(order, displayName));
		return result;
	}

	private static Comparator<RecentRow> comparator(SortOrder order, Function<String, String> displayName) {
		Comparator<RecentRow> recent = Comparator.comparingLong(RecentRow::lastPlayedMs).reversed();
		return switch (order) {
			case MOST_RECENT -> recent.thenComparing(RecentRow::id);
			case PLAY_COUNT -> Comparator.comparingInt(RecentRow::count).reversed().thenComparing(recent).thenComparing(RecentRow::id);
			case NAME -> Comparator.comparing((RecentRow r) -> nameOf(r.id(), displayName)).thenComparing(RecentRow::id);
		};
	}

	private static String nameOf(String id, Function<String, String> displayName) {
		String name = displayName == null ? null : displayName.apply(id);
		return (name == null ? id : name).toLowerCase(Locale.ROOT);
	}

	/** IDs that played in an excluded category this session, for {@code SoundSearch} (FR-3.1). */
	public Set<String> seenExcludedIds() {
		return Collections.unmodifiableSet(seenExcluded);
	}

	/**
	 * The category a sound last played in this session within the player's hearing range, or {@code null} (search's
	 * "Seen as", FR-3.2). Out-of-range plays are deliberately not remembered: showing "Seen as" for a sound the player
	 * could not hear would reveal that it played (fair play).
	 */
	public String seenCategory(String soundId) {
		return seenCategories.get(soundId);
	}

	/**
	 * Estimated bytes this log keeps, with its session sets, for the Diagnostics memory readout (PRD 9.1). It walks
	 * every entry, so call it on demand through {@code MemoryMeter}, not per sound. Category and zone names are
	 * shared constants and not counted.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(3 * MemoryEstimate.REFERENCE)
				+ MemoryEstimate.HASH_MAP + MemoryEstimate.hashTable(rows.size())
				+ rows.size() * (MemoryEstimate.HASH_NODE + Row.BYTES)
				+ MemoryEstimate.HASH_SET + MemoryEstimate.hashTable(seenExcluded.size())
				+ seenExcluded.size() * MemoryEstimate.HASH_NODE
				+ MemoryEstimate.HASH_MAP + MemoryEstimate.hashTable(seenCategories.size())
				+ seenCategories.size() * MemoryEstimate.HASH_NODE;
		for (Row row : rows.values()) {
			bytes += MemoryEstimate.string(row.id);
		}
		for (String id : seenExcluded) {
			bytes += MemoryEstimate.string(id);
		}
		for (String id : seenCategories.keySet()) {
			bytes += MemoryEstimate.string(id);
		}
		return bytes;
	}

	/** Empties the list. Session memory for search is kept. */
	public void clear() {
		rows.clear();
	}

	/** Mutable per-ID state. Counts live in a ring of one-second buckets covering the window. */
	private static final class Row {
		/** The row and its two bucket arrays, without the ID string. */
		static final long BYTES = MemoryEstimate.object(7 * MemoryEstimate.REFERENCE + 8)
				+ MemoryEstimate.array(4, BUCKETS) + MemoryEstimate.array(8, BUCKETS);

		final String id;
		final int[] counts = new int[BUCKETS];
		final long[] seconds = new long[BUCKETS];
		String category;
		DistanceBand band;
		PlayStatus status;
		String zoneName;
		long lastPlayedMs;

		Row(String id) {
			this.id = id;
			Arrays.fill(seconds, Long.MIN_VALUE);
		}

		void add(String category, DistanceBand band, PlayStatus status, String zoneName, long nowMs) {
			long second = Math.floorDiv(nowMs, 1000);
			int i = (int) Math.floorMod(second, (long) BUCKETS);
			if (seconds[i] != second) {
				seconds[i] = second;
				counts[i] = 0;
			}
			counts[i]++;
			this.category = category;
			this.band = band;
			this.status = status;
			this.zoneName = zoneName;
			this.lastPlayedMs = nowMs;
		}

		boolean expired(long nowMs) {
			return nowMs - lastPlayedMs >= WINDOW_MS;
		}

		/**
		 * Plays in the last 3 minutes, to the second. While the row is listed, the bucket of its most recent play
		 * counts even at the window's edge, so a listed row never shows 0 plays. (That bucket cannot have been reused:
		 * reusing it needs a newer play, which would be the most recent one.)
		 */
		int count(long nowMs) {
			long oldestKept = Math.floorDiv(nowMs, 1000) - BUCKETS;
			long lastSecond = Math.floorDiv(lastPlayedMs, 1000);
			boolean listed = !expired(nowMs);
			int total = 0;
			for (int i = 0; i < BUCKETS; i++) {
				if (seconds[i] > oldestKept || (listed && seconds[i] == lastSecond)) {
					total += counts[i];
				}
			}
			return total;
		}

		RecentRow toRecord(long nowMs) {
			return new RecentRow(id, category, count(nowMs), lastPlayedMs, band, status, zoneName);
		}
	}
}
