package audiozone.core.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import audiozone.core.rules.PatternError;
import audiozone.core.rules.SoundPattern;
import audiozone.core.search.SoundSearch;

/**
 * The rule editor's live preview, "Matches N sounds" with an expandable list (PRD FR-4.3).
 *
 * <p>Uses the same ID set as search (FR-3.1) and never counts vanilla-covered sounds, which no rule can match
 * (FR-4.4). Runs while the player types, over a few thousand IDs, so it is not on the sound hot path.
 *
 * @param normalizedPattern the pattern as it will be saved, or {@code null} when invalid
 * @param error             why the pattern is invalid, or {@code null}
 * @param matches           matching IDs in alphabetical order, without duplicates; empty when invalid
 */
public record PatternPreview(String normalizedPattern, PatternError error, List<String> matches) {
	public PatternPreview {
		matches = List.copyOf(matches);
	}

	/**
	 * @param raw             the text in the pattern field
	 * @param knownIds        every sound ID the client knows (registry plus resource packs)
	 * @param seenExcludedIds IDs that played in an excluded category this session
	 */
	public static PatternPreview of(String raw, Collection<String> knownIds, Set<String> seenExcludedIds) {
		PatternError error = SoundPattern.validate(raw);
		if (error != null) {
			return new PatternPreview(null, error, List.of());
		}
		SoundPattern pattern = SoundPattern.of(raw);
		TreeSet<String> found = new TreeSet<>();
		for (String id : knownIds) {
			if (!SoundSearch.isVanillaCovered(id) && !seenExcludedIds.contains(id) && pattern.matches(id)) {
				found.add(id);
			}
		}
		return new PatternPreview(pattern.text(), null, new ArrayList<>(found));
	}

	/** The N in "Matches N sounds". */
	public int count() {
		return matches.size();
	}
}
