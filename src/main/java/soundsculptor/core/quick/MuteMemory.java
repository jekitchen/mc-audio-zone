package soundsculptor.core.quick;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;

/**
 * What each recent quick Mute changed, so the Unmute that follows puts the sound's Quick changes entry back exactly as
 * it was (FR-4.10: Mute then Unmute leaves the profile as it was). Without it, Unmute can only guess from the entry
 * itself: a Cue equal to the profile's default may be the one Mute added (FR-7.6) or the entry's own, and "Not muted"
 * may or may not have been there before.
 *
 * <p>A memory is used only while the sound's entry is still exactly what that Mute left, in the same world and place,
 * and while putting it back does unmute the sound; otherwise Unmute works as {@link QuickActions#toggleMute} says. It is
 * kept for the session only (a later session's Unmute works without it) and for the last {@value #MAX_SOUNDS} sounds.
 *
 * <p>Not thread-safe: use it on the game thread.
 */
public final class MuteMemory {
	/** How many muted sounds are remembered; the one muted longest ago is forgotten first. */
	public static final int MAX_SOUNDS = 64;

	private record Key(String worldKey, PlaceRef where, String soundId) {
	}

	/**
	 * @param before the entry before the Mute, or {@code null} if the Mute created it
	 * @param after  the entry the Mute left
	 */
	private record Muted(SetEntry before, SetEntry after) {
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
			EntryEdit putBack = new EntryEdit.PutBack(last.before());
			QuickActionResult result = QuickActions.apply(ws, where, soundId, putBack);
			if (result.ok() && !QuickActions.isMuted(result.after(), where, soundId)) {
				return putBack;
			}
		}
		return QuickActions.toggleMute(ws, where, soundId, cueDefault);
	}

	/**
	 * Notes a Mute/Unmute that was applied: a quick Mute that changed the entry is remembered, and any other edit
	 * forgets the sound's memory.
	 */
	public void applied(String worldKey, PlaceRef where, String soundId, EntryEdit edit, Workspace before, Workspace after) {
		Key key = new Key(worldKey, where, soundId);
		SetEntry left = QuickActions.quickEntry(after, where, soundId);
		if (edit instanceof EntryEdit.MuteWithCue && left != null && !Objects.equals(before, after)) {
			muted.put(key, new Muted(QuickActions.quickEntry(before, where, soundId), left));
		} else {
			muted.remove(key);
		}
	}
}
