package audiozone.core.trace;

import java.util.ArrayList;
import java.util.List;

import audiozone.core.rules.Rule;
import audiozone.core.zones.Zone;

/**
 * Edits made straight from the sound trace panel (PRD FR-14.4). Each edit is validated like the full editors, then
 * returns the new rules and zones (saved at once, no Save button), the before-state for the 5-second Undo toast, and
 * the trace recomputed from the sound's last play. Not on the hot path.
 */
public final class TraceEditor {
	/** The most rules a profile may hold, global and zone rules together (PRD FR-11.6). */
	public static final int MAX_RULES = 1000;

	/** Toast after changing a rule or zone: "Changed %s", with the rule's pattern or the zone's name. */
	public static final String TOAST_CHANGED = "audiozone.trace.edit.toast.changed";
	/** Toast after "Add rule for this sound": "Added a rule for %s", with the sound ID. */
	public static final String TOAST_ADDED = "audiozone.trace.edit.toast.added";

	private TraceEditor() {
	}

	/** Applies one inline control to a rule line: a global rule, or the zone rule shown on a zone line. */
	public static TraceEditResult edit(TraceEditContext ctx, EditableRules rules, RuleLocation where, RuleEdit edit) {
		TraceEditError invalid = edit.validate();
		if (invalid != null) {
			return refused(ctx, rules, invalid);
		}
		if (where.isGlobal()) {
			int i = indexOfRule(rules.globalRules(), where.ruleId());
			if (i < 0) {
				return refused(ctx, rules, TraceEditError.RULE_NOT_FOUND);
			}
			Rule changed = edit.apply(rules.globalRules().get(i));
			EditableRules after = new EditableRules(replace(rules.globalRules(), i, changed), rules.zones());
			return applied(ctx, rules, after, TOAST_CHANGED, changed.pattern().text());
		}
		int z = indexOfZone(rules.zones(), where.zoneId());
		if (z < 0) {
			return refused(ctx, rules, TraceEditError.ZONE_NOT_FOUND);
		}
		Zone zone = rules.zones().get(z);
		int i = indexOfRule(zone.rules(), where.ruleId());
		if (i < 0) {
			return refused(ctx, rules, TraceEditError.RULE_NOT_FOUND);
		}
		Rule changed = edit.apply(zone.rules().get(i));
		Zone edited = withRules(zone, replace(zone.rules(), i, changed));
		EditableRules after = new EditableRules(rules.globalRules(), replace(rules.zones(), z, edited));
		return applied(ctx, rules, after, TOAST_CHANGED, changed.pattern().text());
	}

	/** A zone line's enable/disable control. */
	public static TraceEditResult setZoneEnabled(TraceEditContext ctx, EditableRules rules, String zoneId, boolean enabled) {
		int z = indexOfZone(rules.zones(), zoneId);
		if (z < 0) {
			return refused(ctx, rules, TraceEditError.ZONE_NOT_FOUND);
		}
		Zone zone = rules.zones().get(z);
		Zone edited = new Zone(zone.id(), zone.name(), enabled, zone.worldKey(), zone.dimension(), zone.anchor(), zone.shape(),
				zone.rules(), zone.mode(), zone.edge(), zone.anchorType());
		EditableRules after = new EditableRules(rules.globalRules(), replace(rules.zones(), z, edited));
		return applied(ctx, rules, after, TOAST_CHANGED, zone.name());
	}

	/**
	 * "Add rule for this sound": an exact-ID rule for the trace's sound, in the global list ({@code zoneId} null) or
	 * in a zone shown in the trace, with {@code edit} applied. An exact rule for the sound already in that list is
	 * reused, and turned on, instead of adding a second one.
	 */
	public static TraceEditResult addRuleForSound(TraceEditContext ctx, EditableRules rules, String zoneId, RuleEdit edit) {
		TraceEditError invalid = edit.validate();
		if (invalid != null) {
			return refused(ctx, rules, invalid);
		}
		int z = -1;
		List<Rule> list = rules.globalRules();
		if (zoneId != null) {
			z = indexOfZone(rules.zones(), zoneId);
			if (z < 0) {
				return refused(ctx, rules, TraceEditError.ZONE_NOT_FOUND);
			}
			list = rules.zones().get(z).rules();
		}
		String soundId = ctx.soundId();
		int existing = indexOfExact(list, soundId);
		List<Rule> newList;
		if (existing >= 0) {
			newList = replace(list, existing, edit.apply(list.get(existing).withEnabled(true)));
		} else {
			if (ruleCount(rules) >= MAX_RULES) {
				return refused(ctx, rules, TraceEditError.TOO_MANY_RULES);
			}
			newList = new ArrayList<>(list);
			newList.add(edit.apply(Rule.of(freeId(rules, exactRuleId(zoneId, soundId)), soundId)));
		}
		EditableRules after = z < 0
				? new EditableRules(newList, rules.zones())
				: new EditableRules(rules.globalRules(), replace(rules.zones(), z, withRules(rules.zones().get(z), newList)));
		return applied(ctx, rules, after, existing >= 0 ? TOAST_CHANGED : TOAST_ADDED, soundId);
	}

	/**
	 * The zone rule a zone line's controls edit: the zone's winning rule for {@code property}, or else its most
	 * specific matching rule. {@code null} when the zone is not in the trace or none of its rules match; the panel
	 * then offers "Add rule for this sound".
	 */
	public static Rule matchedZoneRule(SoundTrace trace, String zoneId, TraceProperty property) {
		for (ZoneLine line : trace.zones()) {
			if (!line.zone().id().equals(zoneId)) {
				continue;
			}
			RuleWinner winner = line.rules().winners().get(property);
			if (winner != null) {
				return winner.rule();
			}
			return line.rules().matched().isEmpty() ? null : line.rules().matched().get(0);
		}
		return null;
	}

	/**
	 * The stable ID of the exact-ID rule for a sound: {@code exact:<id>} in the global list, the same ID quick actions
	 * use, and {@code <zone id>/exact:<id>} in a zone, so IDs stay unique across the profile.
	 */
	public static String exactRuleId(String zoneId, String soundId) {
		return zoneId == null ? "exact:" + soundId : zoneId + "/exact:" + soundId;
	}

	private static TraceEditResult refused(TraceEditContext ctx, EditableRules rules, TraceEditError error) {
		return new TraceEditResult(error, rules, rules, ctx.trace(rules), ctx.play() != null, null, List.of());
	}

	private static TraceEditResult applied(TraceEditContext ctx, EditableRules before, EditableRules after, String toastKey,
			String toastArg) {
		return new TraceEditResult(null, before, after, ctx.trace(after), ctx.play() != null, toastKey, List.of(toastArg));
	}

	private static Zone withRules(Zone zone, List<Rule> rules) {
		return new Zone(zone.id(), zone.name(), zone.enabled(), zone.worldKey(), zone.dimension(), zone.anchor(), zone.shape(),
				rules, zone.mode(), zone.edge(), zone.anchorType());
	}

	private static <T> List<T> replace(List<T> list, int i, T value) {
		List<T> copy = new ArrayList<>(list);
		copy.set(i, value);
		return copy;
	}

	private static int indexOfRule(List<Rule> rules, String id) {
		for (int i = 0; i < rules.size(); i++) {
			if (rules.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfZone(List<Zone> zones, String id) {
		for (int i = 0; i < zones.size(); i++) {
			if (zones.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfExact(List<Rule> rules, String soundId) {
		for (int i = 0; i < rules.size(); i++) {
			Rule rule = rules.get(i);
			if (rule.pattern().isExact() && rule.pattern().text().equals(soundId)) {
				return i;
			}
		}
		return -1;
	}

	private static int ruleCount(EditableRules rules) {
		int count = rules.globalRules().size();
		for (Zone zone : rules.zones()) {
			count += zone.rules().size();
		}
		return count;
	}

	/** {@code id}, or {@code id#2}, {@code id#3}... if another rule already uses it. */
	private static String freeId(EditableRules rules, String id) {
		String candidate = id;
		for (int n = 2; isTaken(rules, candidate); n++) {
			candidate = id + "#" + n;
		}
		return candidate;
	}

	private static boolean isTaken(EditableRules rules, String id) {
		if (indexOfRule(rules.globalRules(), id) >= 0) {
			return true;
		}
		for (Zone zone : rules.zones()) {
			if (indexOfRule(zone.rules(), id) >= 0) {
				return true;
			}
		}
		return false;
	}
}
