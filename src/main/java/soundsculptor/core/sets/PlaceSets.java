package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import soundsculptor.core.Message;

/**
 * The Sound Sets attached to one place, in order, plus the place's Quick changes. Immutable.
 *
 * <p>Invariants: {@code quickChanges} is non-null exactly when the first attachment is {@link SoundSet#QUICK_ID};
 * no set is attached twice; at most {@link #MAX_SETS} library sets are attached (Quick changes is not counted).
 * Breaking one in the constructor throws {@link IllegalArgumentException}; the player-facing operations
 * {@link #canAttach} and {@link #attach} report a {@link Message} instead.
 *
 * @param attached     attachments, top first; Quick changes, when present, is first
 * @param quickChanges the place's Quick changes set, or {@code null}
 */
public record PlaceSets(List<Attachment> attached, SoundSet quickChanges) {
	public static final int MAX_SETS = 32;
	public static final PlaceSets EMPTY = new PlaceSets(List.of(), null);

	public PlaceSets {
		attached = List.copyOf(attached);
		boolean quickFirst = !attached.isEmpty() && attached.get(0).setId().equals(SoundSet.QUICK_ID);
		if ((quickChanges != null) != quickFirst) {
			throw new IllegalArgumentException("Quick changes must be present exactly when it is the first attachment");
		}
		if (quickChanges != null && !quickChanges.isQuick()) {
			throw new IllegalArgumentException("quickChanges must have the quick ID");
		}
		Set<String> seen = new HashSet<>();
		int library = 0;
		for (Attachment a : attached) {
			if (!seen.add(a.setId())) {
				throw new IllegalArgumentException("set attached twice: " + a.setId());
			}
			if (!a.setId().equals(SoundSet.QUICK_ID)) {
				library++;
			}
		}
		if (library > MAX_SETS) {
			throw new IllegalArgumentException("a place can have at most " + MAX_SETS + " Sound Sets");
		}
	}

	/** {@code null} if the set can be attached, else the problem to show. */
	public Message canAttach(String setId) {
		Objects.requireNonNull(setId, "setId");
		if (uses(setId)) {
			return Message.of("soundsculptor.sets.error.already_attached");
		}
		if (!setId.equals(SoundSet.QUICK_ID) && librarySetCount() >= MAX_SETS) {
			return Message.of("soundsculptor.sets.error.place_full", MAX_SETS);
		}
		return null;
	}

	/** Appends the set at the bottom, enabled; unchanged if {@link #canAttach} reports a problem. */
	public PlaceSets attach(String setId) {
		if (canAttach(setId) != null || setId.equals(SoundSet.QUICK_ID)) {
			return this;
		}
		List<Attachment> list = new ArrayList<>(attached);
		list.add(new Attachment(setId, true));
		return new PlaceSets(list, quickChanges);
	}

	/** Removes the attachment; detaching Quick changes also removes its set. */
	public PlaceSets detach(String setId) {
		List<Attachment> list = attached.stream().filter(a -> !a.setId().equals(setId)).toList();
		return new PlaceSets(list, setId.equals(SoundSet.QUICK_ID) ? null : quickChanges);
	}

	/** Moves the attachment up (negative) or down (positive); Quick changes never moves and nothing passes above it. */
	public PlaceSets move(String setId, int delta) {
		int from = indexOf(setId);
		if (from < 0 || setId.equals(SoundSet.QUICK_ID)) {
			return this;
		}
		int min = quickChanges != null ? 1 : 0;
		int to = Math.max(min, Math.min(attached.size() - 1, from + delta));
		if (to == from) {
			return this;
		}
		List<Attachment> list = new ArrayList<>(attached);
		list.add(to, list.remove(from));
		return new PlaceSets(list, quickChanges);
	}

	public PlaceSets setEnabled(String setId, boolean on) {
		List<Attachment> list = new ArrayList<>(attached);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).setId().equals(setId)) {
				list.set(i, new Attachment(setId, on));
			}
		}
		return new PlaceSets(list, quickChanges);
	}

	/** Sets the Quick changes, pinned at the top; {@code null} removes it. */
	public PlaceSets withQuickChanges(SoundSet quick) {
		List<Attachment> list = new ArrayList<>(attached);
		if (quick == null) {
			list.removeIf(a -> a.setId().equals(SoundSet.QUICK_ID));
			return new PlaceSets(list, null);
		}
		if (quickChanges == null) {
			list.add(0, new Attachment(SoundSet.QUICK_ID, true));
		}
		return new PlaceSets(list, quick);
	}

	/** Quick changes was saved to the library as {@code newSetId}: that set takes its slot, and Quick changes is gone. */
	public PlaceSets quickSavedAs(String newSetId) {
		if (quickChanges == null) {
			return this;
		}
		List<Attachment> list = new ArrayList<>(attached);
		list.set(0, new Attachment(newSetId, attached.get(0).enabled()));
		return new PlaceSets(list, null);
	}

	public boolean uses(String setId) {
		return indexOf(setId) >= 0;
	}

	/** Removes attachments to sets not in the library; Quick changes is kept. */
	public PlaceSets dropMissing(Set<String> libraryIds) {
		return new PlaceSets(attached.stream()
				.filter(a -> a.setId().equals(SoundSet.QUICK_ID) || libraryIds.contains(a.setId())).toList(), quickChanges);
	}

	/** Attachments to library sets (Quick changes not counted). */
	public int librarySetCount() {
		return attached.size() - (quickChanges != null ? 1 : 0);
	}

	private int indexOf(String setId) {
		for (int i = 0; i < attached.size(); i++) {
			if (attached.get(i).setId().equals(setId)) {
				return i;
			}
		}
		return -1;
	}
}
