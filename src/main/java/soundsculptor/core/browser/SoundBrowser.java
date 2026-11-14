package soundsculptor.core.browser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundSearch;

/**
 * The rows of the Sounds browser, the same list for the Sounds tab and the picker (PRD FR-1.0, FR-3.1).
 *
 * <p>The screen calls this up to 4 times a second, so it is linear in its inputs: the caller parses the query once
 * and each row is matched against it with no per-row compilation.
 */
public final class SoundBrowser {
	private SoundBrowser() {
	}

	/**
	 * @param recent       the recently played rows
	 * @param all          every known sound, in display order (FR-3.1)
	 * @param query        the parsed search text; an empty query allows everything
	 * @param recentOnly   true for the "Recently played" filter: only recent rows that match, sorted by {@code sort}
	 *                     (FR-1.3); false for every known sound that matches, in {@code all} order, each carrying its
	 *                     recent data when it has some
	 * @param sort         order for the recent-only list
	 * @param seenExcluded IDs that played in a vanilla-covered category, hidden from the full list (FR-3.1)
	 */
	public static List<BrowserRow> rows(List<RecentRow> recent, List<SoundEntry> all, SearchQuery query,
			boolean recentOnly, SortOrder sort, Set<String> seenExcluded) {
		if (recentOnly) {
			return recentRows(recent, all, query, sort);
		}
		Map<String, RecentRow> recentById = new HashMap<>(recent.size() * 2);
		for (RecentRow row : recent) {
			recentById.put(row.id(), row);
		}
		List<SoundEntry> found = SoundSearch.search(all, query, seenExcluded);
		List<BrowserRow> result = new ArrayList<>(found.size());
		for (SoundEntry entry : found) {
			result.add(new BrowserRow(entry.id(), entry.subtitle(), recentById.get(entry.id())));
		}
		return result;
	}

	private static List<BrowserRow> recentRows(List<RecentRow> recent, List<SoundEntry> all, SearchQuery query,
			SortOrder sort) {
		Map<String, SoundEntry> byId = new HashMap<>(all.size() * 2);
		for (SoundEntry entry : all) {
			byId.putIfAbsent(entry.id(), entry);
		}
		List<BrowserRow> result = new ArrayList<>(recent.size());
		for (RecentRow row : recent) {
			SoundEntry entry = byId.get(row.id());
			if (entry == null) {
				entry = new SoundEntry(row.id(), null);
			}
			if (query.matches(entry)) {
				result.add(new BrowserRow(row.id(), entry.subtitle(), row));
			}
		}
		result.sort(comparator(sort));
		return result;
	}

	private static Comparator<BrowserRow> comparator(SortOrder sort) {
		Comparator<BrowserRow> recent = Comparator.comparingLong((BrowserRow r) -> r.recent().lastPlayedMs()).reversed();
		return switch (sort) {
			case MOST_RECENT -> recent.thenComparing(BrowserRow::soundId);
			case PLAY_COUNT -> Comparator.comparingInt((BrowserRow r) -> r.recent().count()).reversed()
					.thenComparing(recent).thenComparing(BrowserRow::soundId);
			case NAME -> Comparator.comparing(SoundBrowser::nameOf).thenComparing(BrowserRow::soundId);
		};
	}

	private static String nameOf(BrowserRow row) {
		return (row.subtitle() == null ? row.soundId() : row.subtitle()).toLowerCase(Locale.ROOT);
	}
}
