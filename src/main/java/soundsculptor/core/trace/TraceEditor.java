package soundsculptor.core.trace;

import java.util.List;

import soundsculptor.core.Message;
import soundsculptor.core.quick.QuickActionResult;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Zone;

/**
 * Edits made straight from the sound trace panel (PRD FR-14.4). Each edit is validated like the full editors
 * (FR-12.7), then returns the new workspace (saved at once, no Save button), the before-state for the 5-second Undo
 * toast, and the trace recomputed from the sound's last play. A refused edit comes back as a {@link Message}; nothing
 * here throws on a limit. Not on the hot path.
 *
 * <p>An entry of a library set is edited in the library: sets are linked (FR-4.9), so every place that uses the set
 * changes too. An entry of a place's Quick changes changes only that place. New entries are only ever made in Quick
 * changes, through {@link QuickActions}, so the trace and the Sounds tab edit the same single-sound entry.
 */
public final class TraceEditor {
	/** Toast after changing an entry or a zone: "Changed %s", with the entry's pattern or the zone's name. */
	public static final String TOAST_CHANGED = "soundsculptor.trace.edit.toast.changed";
	/** Toast after Add to Quick changes made a new entry: "Added %s to Quick changes", with the sound ID. */
	public static final String TOAST_ADDED = "soundsculptor.trace.edit.toast.added";

	private TraceEditor() {
	}

	/**
	 * Applies one inline control to an entry line: the entry in its library set (every place using the set changes), or
	 * in the place's Quick changes for {@link SoundSet#QUICK_ID}.
	 */
	public static TraceEditResult edit(TraceEditContext ctx, Workspace ws, EntryLocation where, EntryEdit edit) {
		TraceEditError invalid = edit.validate();
		if (invalid != null) {
			return refused(ctx, ws, invalid.message());
		}
		if (where.isQuick()) {
			PlaceSets place = ws.profile().place(where.place());
			if (place == null) {
				return refused(ctx, ws, TraceEditError.ZONE_NOT_FOUND.message());
			}
			SoundSet quick = place.quickChanges();
			SetEntry old = quick == null ? null : quick.entry(where.entryId());
			if (old == null) {
				return refused(ctx, ws, TraceEditError.RULE_NOT_FOUND.message());
			}
			SetEntry changed = edit.apply(old);
			PlaceSets edited = place.withQuickChanges(quick.putEntry(changed));
			if (QuickActions.switchesQuickOn(edit)) {
				// The edit must take effect even if the player switched this place's Quick changes off (FR-8.6).
				edited = edited.setEnabled(SoundSet.QUICK_ID, true);
			}
			if (edited.equals(place)) {
				return applied(ctx, ws, ws, TOAST_CHANGED, changed.pattern().text());
			}
			return applied(ctx, ws, new Workspace(ws.profile().withPlace(where.place(), edited), ws.library()), TOAST_CHANGED,
					changed.pattern().text());
		}
		SoundSet set = ws.library().get(where.setId());
		SetEntry old = set == null ? null : set.entry(where.entryId());
		if (old == null) {
			return refused(ctx, ws, TraceEditError.RULE_NOT_FOUND.message());
		}
		SetEntry changed = edit.apply(old);
		if (changed.equals(old)) {
			return applied(ctx, ws, ws, TOAST_CHANGED, changed.pattern().text());
		}
		Workspace after = new Workspace(ws.profile(), ws.library().put(set.putEntry(changed)));
		return applied(ctx, ws, after, TOAST_CHANGED, changed.pattern().text());
	}

	/** A zone line's on/off control. */
	public static TraceEditResult setZoneEnabled(TraceEditContext ctx, Workspace ws, String zoneId, boolean enabled) {
		Zone zone = ws.profile().zone(zoneId);
		if (zone == null) {
			return refused(ctx, ws, TraceEditError.ZONE_NOT_FOUND.message());
		}
		Workspace after = new Workspace(ws.profile().putZone(zone.withEnabled(enabled)), ws.library());
		return applied(ctx, ws, after, TOAST_CHANGED, zone.name());
	}

	/**
	 * Add to Quick changes: applies {@code edit} to the sound's single-sound entry in the Quick changes of Everywhere or
	 * of a zone shown in the trace, creating the set or the entry when needed, exactly as the Sounds tab's quick actions
	 * do (FR-1.5, FR-4.10). Quick changes is pinned first in its place, so the edit takes effect there.
	 */
	public static TraceEditResult addToQuickChanges(TraceEditContext ctx, Workspace ws, PlaceRef where, EntryEdit edit) {
		QuickActionResult result = QuickActions.apply(ws, where, ctx.soundId(), edit);
		if (!result.ok()) {
			return refused(ctx, ws, result.error());
		}
		boolean existed = QuickActions.quickEntry(ws, where, ctx.soundId()) != null;
		return applied(ctx, ws, result.after(), existed ? TOAST_CHANGED : TOAST_ADDED, ctx.soundId());
	}

	private static TraceEditResult refused(TraceEditContext ctx, Workspace ws, Message error) {
		return new TraceEditResult(error, ws, ws, ctx.trace(ws), ctx.play() != null, null, List.of());
	}

	private static TraceEditResult applied(TraceEditContext ctx, Workspace before, Workspace after, String toastKey,
			String toastArg) {
		return new TraceEditResult(null, before, after, ctx.trace(after), ctx.play() != null, toastKey, List.of(toastArg));
	}
}
