package audiozone.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs a {@link SearchQuery} over every known sound ID (PRD FR-3.1).
 *
 * <p>Vanilla-covered sounds are hidden. A sound's category is only known once it plays, so search hides IDs under
 * {@code minecraft:music.*} and {@code minecraft:ui.*}, plus any ID that has already played in an excluded
 * category (Music, UI, Voice/Speech) this session (PRD section 5).
 */
public final class SoundSearch {
	private static final String MUSIC_PREFIX = "minecraft:music.";
	private static final String UI_PREFIX = "minecraft:ui.";

	private SoundSearch() {
	}

	/** Whether search hides this ID by its name alone. Jukebox discs ({@code minecraft:music_disc.*}) stay in scope. */
	public static boolean isVanillaCovered(String soundId) {
		return soundId.startsWith(MUSIC_PREFIX) || soundId.startsWith(UI_PREFIX);
	}

	/**
	 * @param all              every known sound, in display order
	 * @param query            the parsed query
	 * @param seenExcludedIds  IDs that have played in an excluded category this session
	 * @return matching sounds in input order; the size is the result count shown to the player (FR-3.4)
	 */
	public static List<SoundEntry> search(List<SoundEntry> all, SearchQuery query, Set<String> seenExcludedIds) {
		List<SoundEntry> result = new ArrayList<>();
		for (SoundEntry entry : all) {
			if (!isVanillaCovered(entry.id()) && !seenExcludedIds.contains(entry.id()) && query.matches(entry)) {
				result.add(entry);
			}
		}
		return result;
	}
}
