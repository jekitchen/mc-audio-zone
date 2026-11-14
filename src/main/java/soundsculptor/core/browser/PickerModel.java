package soundsculptor.core.browser;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import soundsculptor.core.Message;
import soundsculptor.core.sets.InvalidPatternException;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.sets.SoundSet;

/**
 * What the player has ticked in the Sounds picker for one Sound Set (PRD FR-1.7). Sounds and groups already in the
 * set cannot be picked. Add creates one entry per pick with no settings, in the order they were ticked.
 *
 * <p>A single-sound entry's target is the sound ID and a group's target is its pattern, so both are compared as
 * normalized pattern text, as the entry editor stores them. An entry that is turned off is still in the set. Text that
 * is not a valid sound ID or pattern is never in the set and cannot be ticked: the toggles ignore it (the picker only
 * passes known sound IDs and {@link LikeThis} patterns, so this is a guard, not a feature).
 */
public final class PickerModel {
	private final SoundSet target;
	/** The target set's entry patterns: the picker asks for every row of the full list (about 2,000) on a refresh. */
	private final Set<String> inTarget = new HashSet<>();
	private final Set<String> picks = new LinkedHashSet<>();

	public PickerModel(SoundSet target) {
		this.target = target;
		for (SetEntry entry : target.entries()) {
			inTarget.add(entry.pattern().text());
		}
	}

	/** Whether a single-sound entry already targets this sound ("In this set"). */
	public boolean inSet(String soundId) {
		return inTarget.contains(normalize(soundId));
	}

	/** Whether a group entry with this pattern is already in the set. */
	public boolean groupInSet(String pattern) {
		return inTarget.contains(normalize(pattern));
	}

	/**
	 * Ticks or unticks a sound. Does nothing for a sound already in the set, or for text that is not a sound ID.
	 */
	public void toggleSound(String soundId) {
		toggle(normalize(soundId));
	}

	/**
	 * Ticks or unticks a group. Does nothing for a group already in the set, or for text that is not a pattern.
	 */
	public void toggleGroup(String pattern) {
		toggle(normalize(pattern));
	}

	/** Whether this sound is ticked; never for a sound already in the set. */
	public boolean picked(String soundId) {
		return picks.contains(normalize(soundId));
	}

	/** Whether this group is ticked; never for a group already in the set. */
	public boolean groupPicked(String pattern) {
		return picks.contains(normalize(pattern));
	}

	public int pickedCount() {
		return picks.size();
	}

	/** {@code null}, or the "entries full" problem when adding the picks would pass the set's limit. */
	public Message canAdd() {
		return target.canAdd(picks.size());
	}

	/**
	 * {@code null} if the picks not yet in {@code set} fit in it, else the "entries full" problem. Check the set as it is
	 * when Add is pressed: it may differ from the one the picker opened on.
	 */
	public Message canAddTo(SoundSet set) {
		int fresh = 0;
		for (String pick : picks) {
			if (!inSet(set, pick)) {
				fresh++;
			}
		}
		return fresh == 0 ? null : set.canAdd(fresh);
	}

	/**
	 * Adds one enabled entry per pick that is not yet in {@code set}, in the order ticked. Returns {@code set}
	 * unchanged if the entries would not fit.
	 */
	public SoundSet addTo(SoundSet set) {
		Set<String> fresh = new LinkedHashSet<>();
		for (String pick : picks) {
			if (!inSet(set, pick)) {
				fresh.add(pick);
			}
		}
		if (fresh.isEmpty() || set.canAdd(fresh.size()) != null) {
			return set;
		}
		SoundSet result = set;
		for (String text : fresh) {
			result = result.putEntry(SetEntry.of(result.newEntryId(), text));
		}
		return result;
	}

	private void toggle(String text) {
		if (text == null || inTarget.contains(text)) {
			return;
		}
		if (!picks.remove(text)) {
			picks.add(text);
		}
	}

	private static boolean inSet(SoundSet set, String text) {
		for (SetEntry entry : set.entries()) {
			if (entry.pattern().text().equals(text)) {
				return true;
			}
		}
		return false;
	}

	/** The normalized pattern text, or {@code null} for text that is not a valid pattern (never in a set or picked). */
	private static String normalize(String text) {
		try {
			return SoundPattern.of(text).text();
		} catch (InvalidPatternException e) {
			return null;
		}
	}
}
