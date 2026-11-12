package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import soundsculptor.core.Message;

/**
 * A named, ordered group of entries that can be attached to places (Everywhere and zones). Immutable; every
 * "with" or edit method returns a new set.
 *
 * <p>The set whose ID is {@link #QUICK_ID} is a place's "Quick changes": it is never in the library and has no name.
 *
 * @param id      stable ID; {@code "quick"} only for a place's Quick changes
 * @param name    display name, unique in the library (FR-11.5a)
 * @param entries the entries in order; entry IDs are unique within the set
 */
public record SoundSet(String id, String name, List<SetEntry> entries) {
	public static final String QUICK_ID = "quick";
	public static final int MAX_ENTRIES = 512;
	public static final int MAX_NAME_LENGTH = 64;

	public SoundSet {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		entries = List.copyOf(entries);
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException("a Sound Set can have at most " + MAX_ENTRIES + " entries");
		}
		Set<String> seen = new HashSet<>();
		for (SetEntry entry : entries) {
			if (!seen.add(entry.id())) {
				throw new IllegalArgumentException("duplicate entry ID " + entry.id());
			}
		}
	}

	/** A place's Quick changes: ID {@link #QUICK_ID}, no name. */
	public static SoundSet quick(List<SetEntry> entries) {
		return new SoundSet(QUICK_ID, "", entries);
	}

	public boolean isQuick() {
		return QUICK_ID.equals(id);
	}

	/** The entry with this ID, or {@code null}. */
	public SetEntry entry(String entryId) {
		for (SetEntry entry : entries) {
			if (entry.id().equals(entryId)) {
				return entry;
			}
		}
		return null;
	}

	public SoundSet withName(String value) {
		return new SoundSet(id, value, entries);
	}

	public SoundSet withEntries(List<SetEntry> value) {
		return new SoundSet(id, name, value);
	}

	/** Replaces the entry with the same ID, or appends it. */
	public SoundSet putEntry(SetEntry e) {
		List<SetEntry> list = new ArrayList<>(entries);
		int at = indexOf(e.id());
		if (at >= 0) {
			list.set(at, e);
		} else {
			list.add(e);
		}
		return withEntries(list);
	}

	public SoundSet removeEntry(String entryId) {
		return withEntries(entries.stream().filter(e -> !e.id().equals(entryId)).toList());
	}

	/** Moves an entry up (negative) or down (positive) by {@code delta}, staying inside the list. */
	public SoundSet moveEntry(String entryId, int delta) {
		int from = indexOf(entryId);
		if (from < 0) {
			return this;
		}
		int to = Math.max(0, Math.min(entries.size() - 1, from + delta));
		if (to == from) {
			return this;
		}
		List<SetEntry> list = new ArrayList<>(entries);
		list.add(to, list.remove(from));
		return withEntries(list);
	}

	/** The first of "e1", "e2", ... that no entry uses. */
	public String newEntryId() {
		for (int n = 1;; n++) {
			String candidate = "e" + n;
			if (entry(candidate) == null) {
				return candidate;
			}
		}
	}

	/** {@code null} if {@code count} more entries fit, else the "entries full" problem. */
	public Message canAdd(int count) {
		return entries.size() + count > MAX_ENTRIES ? Message.of("soundsculptor.sets.error.entries_full", MAX_ENTRIES) : null;
	}

	/** Equal name and equal entries in the same order, ignoring the ID (FR-11.5a). */
	public boolean sameContents(SoundSet o) {
		return name.equals(o.name) && entries.equals(o.entries);
	}

	private int indexOf(String entryId) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id().equals(entryId)) {
				return i;
			}
		}
		return -1;
	}
}
