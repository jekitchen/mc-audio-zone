package audiozone.core.quick;

import java.util.List;

import audiozone.core.profile.Profile;
import audiozone.core.rules.Rule;
import audiozone.core.trace.EditableRules;
import audiozone.core.trace.ExactRules;
import audiozone.core.trace.RuleEdit;
import audiozone.core.trace.TraceEditor;

/**
 * Recently Played quick actions (PRD FR-1.5): Mute/Unmute, Volume, Limit and Cue create or edit the sound's exact-ID
 * rule in the list the "Apply to" selector chose. Values are checked like the full editors (FR-12.7), and the result
 * carries the before-state for the 5-second Undo toast. Not on the hot path.
 *
 * <p>Rule IDs match the trace panel's ({@link TraceEditor#exactRuleId}): {@code exact:<id>} in the global list, the
 * ID quick actions have always used, and {@code <zone id>/exact:<id>} in a zone.
 *
 * <p>An edit that clears a value never adds a rule or turns one on, and a rule left setting nothing is removed, so
 * Mute followed by Unmute leaves the profile as it was.
 */
public final class QuickActions {
	/** Toast after a quick action removed a rule that no longer set anything: "Removed the rule for %s". */
	public static final String TOAST_REMOVED = "audiozone.recent.toast.removed";

	private QuickActions() {
	}

	/** The sound's exact-ID rule in the target's list, or null (also when the zone no longer exists). */
	public static Rule exactRule(Profile profile, ApplyTarget target, String soundId) {
		return ExactRules.find(rules(profile), target.zoneId(), soundId);
	}

	/** Whether the target's exact-ID rule for the sound is on and mutes it: the row then offers Unmute. */
	public static boolean isMuted(Profile profile, ApplyTarget target, String soundId) {
		Rule rule = exactRule(profile, target, soundId);
		return rule != null && rule.enabled() && Boolean.TRUE.equals(rule.mute());
	}

	/**
	 * The row's Mute/Unmute button: clears mute when {@link #isMuted}, else mutes. Muting starts a rule that does not
	 * set Cue with the profile's Cue default (FR-7.6); unmuting takes that Cue away again.
	 */
	public static RuleEdit toggleMute(Profile profile, ApplyTarget target, String soundId) {
		boolean cueDefault = profile.settings().cueDefault();
		return isMuted(profile, target, soundId) ? new RuleEdit.UnmuteWithCue(cueDefault) : new RuleEdit.MuteWithCue(cueDefault);
	}

	/** Applies one quick action to the sound's exact-ID rule in the target's list. */
	public static QuickActionResult apply(Profile profile, ApplyTarget target, String soundId, RuleEdit edit) {
		ExactRules.Change change = ExactRules.edit(rules(profile), target.zoneId(), soundId, edit, true);
		if (change.error() != null) {
			return new QuickActionResult(change.error(), profile, profile, null, List.of());
		}
		String toastKey = switch (change.outcome()) {
			case ADDED -> TraceEditor.TOAST_ADDED;
			case CHANGED -> TraceEditor.TOAST_CHANGED;
			case REMOVED -> TOAST_REMOVED;
			case UNCHANGED -> null;
		};
		if (toastKey == null) {
			return new QuickActionResult(null, profile, profile, null, List.of());
		}
		Profile after = profile.withGlobalRules(change.after().globalRules()).withZones(change.after().zones());
		return new QuickActionResult(null, profile, after, toastKey, List.of(soundId));
	}

	private static EditableRules rules(Profile profile) {
		return new EditableRules(profile.globalRules(), profile.zones());
	}
}
