package soundsculptor.core.browser;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import soundsculptor.core.Message;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.sets.SoundSet;

/**
 * What the player has ticked in the Sounds picker for one Sound Set (PRD FR-1.7). Sounds and groups already in the
 * set cannot be picked. Add creates one entry per pick with no settings, in the order they were ticked.
 *
 * <p>A single-sound entry's target is the sound ID and a group's target is its pattern, so both are compared as
 * normalized pattern text, as the entry editor stores them.
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

	/** Ticks or unticks a sound. Does nothing for a sound already in the set. */
	public void toggleSound(String soundId) {
		toggle(normalize(soundId));
	}

	/** Ticks or unticks a group. Does nothing for a group already in the set. */
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
		if (inTarget.contains(text)) {
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

	private static String normalize(String text) {
		return SoundPattern.of(text).text();
	}
}
