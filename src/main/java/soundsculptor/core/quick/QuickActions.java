package soundsculptor.core.quick;

import java.util.List;
import java.util.function.Predicate;

import soundsculptor.core.Message;
import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.core.trace.TraceEditError;

/**
 * Quick actions on a sound row (PRD FR-1.5, FR-4.10): Mute/Unmute, Volume, Limit and Cue edit the sound's
 * single-sound entry in the Quick changes set of the place "Apply to" chose, creating the set or the entry when needed.
 * Quick changes is pinned first in its place, so the edit always takes effect there. Values are checked like the full
 * editors (FR-12.7), and the result carries the before-state for the 5-second Undo toast. Not on the hot path.
 *
 * <p>An edit that clears a value never adds an entry or turns one on, an entry left setting nothing is removed, and an
 * empty Quick changes set is removed. An edit that sets a value switches the place's Quick changes back on if the
 * player had switched it off (FR-8.6), so Mute never silently does nothing.
 *
 * <p>Mute followed by Unmute leaves the place exactly as it was when the row's button goes through {@link MuteMemory}:
 * Unmute puts the sound's entry back as it was before the Mute (its own Cue included, or no entry), and switches Quick
 * changes back off if the Mute switched it on and nothing else at the place changed since. This holds within one
 * session, also after Undo of the Unmute, while the entry is still what the Mute left. After a restart, or once the
 * entry changed, Unmute is the plain {@link #toggleMute}, which has only the entry to go by: it clears the mute and a
 * Cue equal to the default, or writes "Not muted", and leaves Quick changes switched on.
 */
public final class QuickActions {
	private QuickActions() {
	}

	/**
	 * The sound's single-sound entry (the exact ID, not a group) in the place's Quick changes, or {@code null}: also
	 * when the place has no Quick changes or the zone no longer exists.
	 */
	public static SetEntry quickEntry(Workspace ws, PlaceRef where, String soundId) {
		PlaceSets place = ws.profile().place(where);
		return place == null ? null : singleSoundEntry(place.quickChanges(), soundId);
	}

	/**
	 * Whether the sound is muted at the place now, by any set attached there (FR-1.5: buttons show the actual state).
	 * {@code false} for a zone that no longer exists. For many sounds at once, use {@link #mutedAt}.
	 */
	public static boolean isMuted(Workspace ws, PlaceRef where, String soundId) {
		return mutedAt(ws, where).test(soundId);
	}

	/**
	 * {@link #isMuted} for many sounds at one place: one resolver for the place, built here and shared by every test,
	 * so a list can show each row's state without sorting the place's entries once per row. A snapshot of {@code ws}:
	 * get a new one after a change. Never true for a zone that no longer exists.
	 */
	public static Predicate<String> mutedAt(Workspace ws, PlaceRef where) {
		PlaceSets place = ws.profile().place(where);
		if (place == null) {
			return soundId -> false;
		}
		EntryResolver resolver = resolver(place, ws.library());
		return soundId -> resolver.resolve(soundId).muted();
	}

	/**
	 * The row's Mute/Unmute button (FR-4.10). When the sound is not muted, Mute: a new mute starts with the Cue default
	 * (FR-7.6). When it is muted only by its Quick changes entry, Unmute clears that setting (and the default Cue the
	 * mute added). When any other set at the place, or another Quick changes entry, would still mute it, Unmute writes
	 * {@code mute=false} ("Not muted"). Builds up to two resolvers for the place: call it on a press, not per row.
	 *
	 * <p>Right after a quick Mute, use {@link MuteMemory#toggleMute} instead: from the entry alone, a Cue equal to the
	 * default cannot be told from the one the Mute added, so this Unmute may keep or drop it.
	 */
	public static EntryEdit toggleMute(Workspace ws, PlaceRef where, String soundId, boolean cueDefault) {
		PlaceSets place = ws.profile().place(where);
		if (place == null || !isMuted(place, ws.library(), soundId)) {
			return new EntryEdit.MuteWithCue(cueDefault);
		}
		EntryEdit clear = new EntryEdit.UnmuteWithCue(cueDefault);
		PlaceSets cleared = edited(place, soundId, clear);
		return cleared != null && !isMuted(cleared, ws.library(), soundId) ? clear : new EntryEdit.Mute(false);
	}

	/** Applies one quick action to the sound's single-sound entry in the place's Quick changes. */
	public static QuickActionResult apply(Workspace ws, PlaceRef where, String soundId, EntryEdit edit) {
		TraceEditError invalid = edit.validate();
		if (invalid != null) {
			return refused(ws, invalid.message());
		}
		PlaceSets place = ws.profile().place(where);
		if (place == null) {
			return refused(ws, TraceEditError.ZONE_NOT_FOUND.message());
		}
		SoundSet quick = quickOf(place);
		if (singleSoundEntry(quick, soundId) == null && setsAnything(edit.apply(SetEntry.of("probe", soundId)))) {
			Message full = quick.canAdd(1);
			if (full != null) {
				return refused(ws, full);
			}
		}
		PlaceSets after = edited(place, soundId, edit);
		if (after == null) {
			return new QuickActionResult(null, ws, ws);
		}
		return new QuickActionResult(null, ws, new Workspace(ws.profile().withPlace(where, after), ws.library()));
	}

	/**
	 * {@code null} if {@link #putQuickEntry} can save the entry, else the problem to show: the zone no longer exists, or
	 * the entry is new and the place's Quick changes already holds {@value SoundSet#MAX_ENTRIES} entries.
	 */
	public static Message canPutQuickEntry(Workspace ws, PlaceRef where, SetEntry entry) {
		PlaceSets place = ws.profile().place(where);
		if (place == null) {
			return TraceEditError.ZONE_NOT_FOUND.message();
		}
		SoundSet quick = quickOf(place);
		return quick.entry(entry.id()) == null ? quick.canAdd(1) : null;
	}

	/**
	 * Saves an entry from the entry editor (Edit… on a sound row, FR-1.5) into the place's Quick changes: it replaces
	 * the entry with the same ID or is added at the end, creating the set if needed. An entry that is on and sets
	 * something switches Quick changes back on, like a quick action. Returns {@code ws} unchanged when
	 * {@link #canPutQuickEntry} reports a problem.
	 */
	public static Workspace putQuickEntry(Workspace ws, PlaceRef where, SetEntry entry) {
		if (canPutQuickEntry(ws, where, entry) != null) {
			return ws;
		}
		PlaceSets place = ws.profile().place(where);
		SoundSet quick = quickOf(place);
		PlaceSets after = place.withQuickChanges(quick.putEntry(entry));
		if (entry.enabled() && setsAnything(entry)) {
			after = after.setEnabled(SoundSet.QUICK_ID, true);
		}
		return new Workspace(ws.profile().withPlace(where, after), ws.library());
	}

	/**
	 * The place with {@code edit} applied to the sound's single-sound entry in its Quick changes, or {@code null} when
	 * that changes nothing. Call only with a valid edit that fits (see {@link #apply}).
	 */
	private static PlaceSets edited(PlaceSets place, String soundId, EntryEdit edit) {
		SoundSet quick = quickOf(place);
		SetEntry old = singleSoundEntry(quick, soundId);
		if (edit instanceof EntryEdit.PutBack putBack) {
			// The entry exactly as it was before a quick Mute, on or off, or none (EntryEdit.PutBack is only applied here),
			// and Quick changes switched back off if that Mute switched it on.
			SoundSet changedSet = putBack.entry() != null ? quick.putEntry(putBack.entry())
					: old != null ? quick.removeEntry(old.id()) : quick;
			PlaceSets after = place.withQuickChanges(changedSet.entries().isEmpty() ? null : changedSet);
			if (putBack.quickOff() && after.quickChanges() != null) {
				after = after.setEnabled(SoundSet.QUICK_ID, false);
			}
			return after.equals(place) ? null : after;
		}
		boolean setsSomething = setsAnything(edit.apply(SetEntry.of("probe", soundId)));
		SoundSet changedSet;
		if (old == null) {
			if (!setsSomething) {
				return null;
			}
			changedSet = quick.putEntry(edit.apply(SetEntry.of(quick.newEntryId(), soundId)));
		} else {
			// Clearing a value leaves an entry off if it was off; setting one turns the entry on.
			SetEntry changed = setsSomething ? edit.apply(old.withEnabled(true)) : edit.apply(old);
			if (!setsAnything(changed)) {
				changedSet = quick.removeEntry(old.id());
			} else if (changed.equals(old)) {
				changedSet = quick;
			} else {
				changedSet = quick.putEntry(changed);
			}
		}
		PlaceSets after = place.withQuickChanges(changedSet.entries().isEmpty() ? null : changedSet);
		if (setsSomething) {
			// Setting a value also switches the place's Quick changes back on, so the action takes effect (FR-8.6).
			after = after.setEnabled(SoundSet.QUICK_ID, true);
		}
		return after.equals(place) ? null : after;
	}

	/**
	 * Whether an edit to a Quick changes entry switches the place's Quick changes back on when it was switched off
	 * (FR-8.6 On/Off): it does when it sets a value or turns the entry on, so a quick action always does what it says.
	 * Clearing a value or turning the entry off leaves Quick changes as it was.
	 */
	public static boolean switchesQuickOn(EntryEdit edit) {
		if (edit instanceof EntryEdit.Enabled enabled) {
			return enabled.value();
		}
		return setsAnything(edit.apply(SetEntry.of("probe", "*")));
	}

	/** The place's Quick changes, or an empty one to start from. */
	private static SoundSet quickOf(PlaceSets place) {
		return place.quickChanges() != null ? place.quickChanges() : SoundSet.quick(List.of());
	}

	private static boolean isMuted(PlaceSets place, SetLibrary library, String soundId) {
		return resolver(place, library).resolve(soundId).muted();
	}

	private static EntryResolver resolver(PlaceSets place, SetLibrary library) {
		return EntryResolver.forPlace(place, library, null);
	}

	/** The first entry whose pattern is exactly {@code soundId}, or {@code null}. */
	private static SetEntry singleSoundEntry(SoundSet set, String soundId) {
		if (set == null) {
			return null;
		}
		for (SetEntry entry : set.entries()) {
			if (entry.pattern().isExact() && entry.pattern().text().equals(soundId)) {
				return entry;
			}
		}
		return null;
	}

	private static boolean setsAnything(SetEntry entry) {
		return entry.setsLoudness() || entry.setsLimit() || entry.setsCue();
	}

	private static QuickActionResult refused(Workspace ws, Message error) {
		return new QuickActionResult(error, ws, ws);
	}
}
