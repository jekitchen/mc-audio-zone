package soundsculptor.core.browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import soundsculptor.core.Message;
import soundsculptor.core.editor.PatternSuggestions;
import soundsculptor.core.sets.SoundPattern;

/** The "Like this" groups of one sound (PRD FR-4.8). */
public final class LikeThis {
	private LikeThis() {
	}

	/** One group: a wildcard pattern and how many known sounds it matches. */
	public record Group(String pattern, int count) {
		/** The entry editor's button: "minecraft:entity.villager.* · 3 sounds", or "· 1 sound" for one. */
		public Message label() {
			return Message.of(count == 1 ? "soundsculptor.rule_editor.family.one" : "soundsculptor.rule_editor.family", pattern, count);
		}

		/** The Add to set… and picker lists' second line: "Matches 3 sounds", or "Matches 1 sound" for one. */
		public Message matches() {
			return Message.of(count == 1 ? "soundsculptor.add_to_set.matches.one" : "soundsculptor.add_to_set.matches", count);
		}
	}

	/**
	 * The sound's groups, closest first ({@link PatternSuggestions#suggest} order), each with the number of
	 * {@code knownIds} it matches. Each pattern is compiled once.
	 */
	public static List<Group> groups(String soundId, Collection<String> knownIds) {
		List<String> patterns = PatternSuggestions.suggest(soundId);
		List<Group> result = new ArrayList<>(patterns.size());
		for (String text : patterns) {
			SoundPattern pattern = SoundPattern.of(text);
			int count = 0;
			for (String id : knownIds) {
				if (pattern.matches(id)) {
					count++;
				}
			}
			result.add(new Group(pattern.text(), count));
		}
		return result;
	}
}
