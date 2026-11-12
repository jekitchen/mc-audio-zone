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
 * empty Quick changes set is removed, so Mute followed by Unmute leaves the profile as it was.
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
	 * The place with {@code edit} applied to the sound's single-sound entry in its Quick changes, or {@code null} when
	 * that changes nothing. Call only with a valid edit that fits (see {@link #apply}).
	 */
	private static PlaceSets edited(PlaceSets place, String soundId, EntryEdit edit) {
		SoundSet quick = quickOf(place);
		SetEntry old = singleSoundEntry(quick, soundId);
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
				return null;
			} else {
				changedSet = quick.putEntry(changed);
			}
		}
		return place.withQuickChanges(changedSet.entries().isEmpty() ? null : changedSet);
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
