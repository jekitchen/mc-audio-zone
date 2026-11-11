package soundsculptor.core.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import soundsculptor.core.rules.SoundPattern;

/**
 * Wildcard patterns to offer for one sound ID in "Make pattern…" (PRD FR-1.5), closest family first.
 *
 * <p>For {@code minecraft:entity.villager.ambient}, in order:
 * <ol>
 *   <li>{@code minecraft:entity.villager.*}: the last part replaced, so "all of this mob's sounds";</li>
 *   <li>{@code minecraft:entity.*.ambient}: the same kind of sound from any mob or block (paths of 3+ parts);</li>
 *   <li>{@code minecraft:entity.*}: each shorter prefix, longest first;</li>
 *   <li>{@code minecraft:*}: everything from this namespace.</li>
 * </ol>
 * The order is fixed rather than sorted by specificity, so the first suggestion is always the one the PRD
 * describes, whatever the length of the names. Every suggestion matches the original ID.
 */
public final class PatternSuggestions {
	private PatternSuggestions() {
	}

	/** Suggested patterns, closest family first; never the exact ID itself. */
	public static List<String> suggest(String soundId) {
		int colon = soundId.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : soundId.substring(0, colon);
		String path = colon < 0 ? soundId : soundId.substring(colon + 1);
		String fullId = namespace + ":" + path;
		String[] parts = path.split("\\.", -1);

		Set<String> texts = new LinkedHashSet<>();
		if (parts.length >= 2) {
			texts.add(prefix(namespace, parts, parts.length - 1));
		}
		if (parts.length >= 3) {
			texts.add(namespace + ":" + parts[0] + ".*." + parts[parts.length - 1]);
		}
		for (int keep = parts.length - 2; keep >= 1; keep--) {
			texts.add(prefix(namespace, parts, keep));
		}
		texts.add(namespace + ":*");

		List<String> result = new ArrayList<>(texts.size());
		for (String text : texts) {
			if (SoundPattern.validate(text) == null && SoundPattern.of(text).matches(fullId)) {
				result.add(SoundPattern.of(text).text());
			}
		}
		return result;
	}

	/** {@code namespace:} plus the first {@code keep} parts and {@code .*}. */
	private static String prefix(String namespace, String[] parts, int keep) {
		return namespace + ":" + String.join(".", Arrays.copyOf(parts, keep)) + ".*";
	}
}
