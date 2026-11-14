package soundsculptor.core.quick;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;

/**
 * What each recent quick Mute changed, so the Unmute that follows puts the place back exactly as it was (FR-4.10: Mute
 * then Unmute leaves the profile as it was). Without it, Unmute can only guess from the entry itself: a Cue equal to the
 * profile's default may be the one Mute added (FR-7.6) or the entry's own, "Not muted" may or may not have been there
 * before, and Quick changes may have been switched on by the Mute (FR-8.6).
 *
 * <p>A memory is used only while the sound's entry is still exactly what that Mute left, in the same world and place,
 * and while putting it back does unmute the sound; otherwise Unmute works as {@link QuickActions#toggleMute} says. Quick
 * changes is switched back off only if the Mute switched it on and the whole place is still as the Mute left it. The
 * memory survives the Unmute it served, so Undo of that Unmute and a second Unmute put the place back again. It is kept
 * for the session only (a later session's Unmute works without it) and for the last {@value #MAX_SOUNDS} sounds.
 *
 * <p>Not thread-safe: use it on the game thread.
 */
public final class MuteMemory {
	/** How many muted sounds are remembered; the one muted longest ago is forgotten first. */
	public static final int MAX_SOUNDS = 64;

	private record Key(String worldKey, PlaceRef where, String soundId) {
	}

	/**
	 * @param before          the entry before the Mute, or {@code null} if the Mute created it
	 * @param after           the entry the Mute left
	 * @param switchedQuickOn whether the Mute switched the place's Quick changes on
	 * @param placeAfter      the place as the Mute left it
	 */
	private record Muted(SetEntry before, SetEntry after, boolean switchedQuickOn, PlaceSets placeAfter) {
	}

	private final Map<Key, Muted> muted = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Muted> eldest) {
			return size() > MAX_SOUNDS;
		}
	};

	/**
	 * The edit for the row's Mute/Unmute: {@link EntryEdit.PutBack} right after this sound's quick Mute at this place,
	 * else {@link QuickActions#toggleMute}'s.
	 *
	 * @param worldKey the world key of the profile {@code ws} holds, or {@code null} outside a world
	 */
	public EntryEdit toggleMute(String worldKey, Workspace ws, PlaceRef where, String soundId, boolean cueDefault) {
		Muted last = muted.get(new Key(worldKey, where, soundId));
		if (last != null && QuickActions.isMuted(ws, where, soundId)
				&& last.after().equals(QuickActions.quickEntry(ws, where, soundId))) {
			boolean quickOff = last.switchedQuickOn() && last.placeAfter().equals(ws.profile().place(where));
			EntryEdit putBack = new EntryEdit.PutBack(last.before(), quickOff);
			QuickActionResult result = QuickActions.apply(ws, where, soundId, putBack);
			if (result.ok() && !QuickActions.isMuted(result.after(), where, soundId)) {
				return putBack;
			}
		}
		return QuickActions.toggleMute(ws, where, soundId, cueDefault);
	}

	/**
	 * Notes a Mute/Unmute that was applied: a quick Mute that changed the place is remembered, an Unmute that put it
	 * back keeps the memory (Undo may bring the Mute back), and any other edit forgets the sound's memory.
	 */
	public void applied(String worldKey, PlaceRef where, String soundId, EntryEdit edit, Workspace before, Workspace after) {
		Key key = new Key(worldKey, where, soundId);
		SetEntry left = QuickActions.quickEntry(after, where, soundId);
		if (edit instanceof EntryEdit.MuteWithCue && left != null && !Objects.equals(before, after)) {
			PlaceSets placeAfter = after.profile().place(where);
			boolean switchedOn = quickSwitchedOff(before.profile().place(where)) && !quickSwitchedOff(placeAfter);
			muted.put(key, new Muted(QuickActions.quickEntry(before, where, soundId), left, switchedOn, placeAfter));
		} else if (!(edit instanceof EntryEdit.PutBack)) {
			muted.remove(key);
		}
	}

	/** Whether the place has Quick changes and the player switched it off. */
	private static boolean quickSwitchedOff(PlaceSets place) {
		if (place == null || place.quickChanges() == null) {
			return false;
		}
		for (Attachment a : place.attached()) {
			if (a.setId().equals(SoundSet.QUICK_ID)) {
				return !a.enabled();
			}
		}
		return false;
	}
}
