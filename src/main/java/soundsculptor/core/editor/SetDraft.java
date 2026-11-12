package soundsculptor.core.editor;

import java.util.List;
import java.util.Objects;

import soundsculptor.core.Message;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;

/**
 * The set editor's working copy of one Sound Set (PRD FR-4.3): its name and its entries, in order. Entry edits, On/Off,
 * moves and removals change only the draft; nothing changes in game until the editor's Save builds the set
 * (FR-12.4). {@link #changed} drives the Save / Discard / Keep editing dialog.
 *
 * <p>A player-facing limit is never thrown: {@link #putEntry} refuses entry {@value SoundSet#MAX_ENTRIES} + 1 and
 * {@link #canPut} says why. The name is kept as typed; the editor checks it and makes it unique when it saves.
 *
 * <p>Not thread-safe; used from the UI thread only.
 */
public final class SetDraft {
	private final SoundSet original;
	private SoundSet current;
	private String name;

	private SetDraft(SoundSet original) {
		this.original = Objects.requireNonNull(original, "original");
		this.current = original;
		this.name = original.name();
	}

	/** Editing an existing set (a library set, or a place's Quick changes). */
	public static SetDraft of(SoundSet set) {
		return new SetDraft(set);
	}

	/** A set with no entries yet, e.g. a place's first Quick changes. */
	public static SetDraft blank(String id, String name) {
		return new SetDraft(new SoundSet(id, name, List.of()));
	}

	public String id() {
		return original.id();
	}

	/** The name as typed. */
	public String name() {
		return name;
	}

	public void setName(String value) {
		name = Objects.requireNonNull(value, "value");
	}

	/** The entries in order, as edited so far. */
	public List<SetEntry> entries() {
		return current.entries();
	}

	/** The entry with this ID, or {@code null}. */
	public SetEntry entry(String entryId) {
		return current.entry(entryId);
	}

	/** The first of "e1", "e2", ... that no entry of the draft uses. */
	public String newEntryId() {
		return current.newEntryId();
	}

	/** {@code null} if a new entry fits, else the "entries full" problem (FR-4.7: up to {@value SoundSet#MAX_ENTRIES}). */
	public Message canAdd() {
		return current.canAdd(1);
	}

	/** {@code null} if {@link #putEntry} will take this entry, else why not: it is new and the set is full. */
	public Message canPut(SetEntry entry) {
		return current.entry(entry.id()) != null ? null : current.canAdd(1);
	}

	/**
	 * Replaces the entry with the same ID where it is, or adds it at the end.
	 *
	 * @return false, changing nothing, when {@link #canPut} reports a problem
	 */
	public boolean putEntry(SetEntry entry) {
		if (canPut(entry) != null) {
			return false;
		}
		current = current.putEntry(entry);
		return true;
	}

	public void removeEntry(String entryId) {
		current = current.removeEntry(entryId);
	}

	/** Turns an entry on or off without deleting it (FR-4.1); nothing happens for an ID the draft does not have. */
	public void setEntryEnabled(String entryId, boolean enabled) {
		SetEntry entry = current.entry(entryId);
		if (entry != null) {
			current = current.putEntry(entry.withEnabled(enabled));
		}
	}

	/** Moves an entry up (negative) or down (positive), staying inside the list. */
	public void moveEntry(String entryId, int delta) {
		current = current.moveEntry(entryId, delta);
	}

	/** The set as edited: the same ID, the name without spaces at either end, and the entries in order. */
	public SoundSet build() {
		return new SoundSet(original.id(), name.strip(), current.entries());
	}

	/** Whether the name or any entry means something different from when the editor opened (FR-12.4). */
	public boolean changed() {
		return !build().equals(original);
	}
}
