package soundsculptor.core.trace;

import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.undo.UndoSlot;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;

import org.junit.jupiter.api.Test;

/** PRD FR-14.4: editing rules and zones straight from the sound trace. */
class TraceEditorTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final LastPlay PLAY = new LastPlay(true, WORLD, OVERWORLD, 2, 0, 0, 50, false, false);

	private static Zone sphere(String id, double radius, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(radius), quick(List.of(rules)));
	}

	private static TraceEditContext context(LastPlay play) {
		return new TraceEditContext(AMBIENT, play, ZoneEdge.hard(), false, false);
	}

	private static TraceEditResult ok(TraceEditResult result) {
		assertTrue(result.ok(), () -> "edit refused: " + result.error());
		return result;
	}

	// --- FR-14 v1 acceptance checks ---

	@Test
	void draggingTheWinningZoneSliderPredictsTheNewVolumeAndUndoRestoresIt() {
		Zone hall = sphere("hall", 10, SetEntry.of("hr", "*villager*").withVolume(50));
		EditableRules start = new EditableRules(List.of(), List.of(hall));
		TraceEditContext ctx = context(PLAY);

		SetEntry matched = TraceEditor.matchedZoneRule(ctx.trace(start), "hall", TraceProperty.LOUDNESS);
		assertEquals("hr", matched.id());
		TraceEditResult result = ok(TraceEditor.edit(ctx, start, RuleLocation.inZone("hall", matched.id()), new EntryEdit.Volume(20)));

		assertEquals(20, result.predicted().volumePercent(), 1e-6);
		assertTrue(result.predictedForNextPlay(), "labelled 'Predicted for next play' until the sound plays again");
		assertEquals(20, result.after().zones().get(0).sets().quickEntries().get(0).volume());

		UndoSlot<EditableRules> undo = new UndoSlot<>();
		undo.offer(result.before(), result.after(), result.toastKey(), result.toastArgs(), 1000);
		EditableRules restored = undo.undo(result.after(), 2000).orElseThrow();
		assertEquals(start, restored);
		assertEquals(50, ctx.trace(restored).volumePercent(), 1e-6);
	}

	@Test
	void disablingTheWinningGlobalRuleMakesTheNextRuleTheWinner() {
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		SetEntry wildcard = SetEntry.of("w", "*villager*").withVolume(30);
		EditableRules start = new EditableRules(List.of(wildcard, exact), List.of());

		TraceEditResult result = ok(TraceEditor.edit(context(PLAY), start, RuleLocation.global("e"), new EntryEdit.Enabled(false)));

		assertEquals("w", result.predicted().global().winners().get(TraceProperty.LOUDNESS).rule().id());
		assertEquals(30, result.predicted().volumePercent(), 1e-6);
		assertFalse(result.after().globalRules().get(1).enabled(), "the rule is kept, only disabled");
	}

	@Test
	void addRuleForThisSoundWithMuteMutesItAndAddsItToTheGlobalList() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("other", "minecraft:block.*").withMute(true)), List.of());

		TraceEditResult result = ok(TraceEditor.addRuleForSound(context(PLAY), start, null, new EntryEdit.Mute(true)));

		assertEquals(0, result.predicted().volumePercent(), 1e-6);
		assertEquals(2, result.after().globalRules().size());
		SetEntry added = result.after().globalRules().get(1);
		assertEquals("exact:" + AMBIENT, added.id(), "same ID as the quick action's exact rule");
		assertEquals(AMBIENT, added.pattern().text());
		assertTrue(added.pattern().isExact());
		assertEquals(Boolean.TRUE, added.mute());
	}

	@Test
	void typing250IntoAVolumeFieldIsRefusedAndNothingChanges() {
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		EditableRules start = new EditableRules(List.of(exact), List.of());

		TraceEditResult result = TraceEditor.edit(context(PLAY), start, RuleLocation.global("e"), new EntryEdit.Volume(250));

		assertFalse(result.ok());
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE, result.error());
		assertEquals("soundsculptor.trace.edit.error.volume_out_of_range", result.error().translationKey());
		assertSame(start, result.after(), "nothing changes");
		assertSame(start, result.before());
		assertEquals(60, result.predicted().volumePercent(), 1e-6);
	}

	// --- SetEntry line controls ---

	@Test
	void ruleLineControlsSetEachProperty() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of());
		TraceEditContext ctx = context(PLAY);
		RuleLocation e = RuleLocation.global("e");

		EditableRules r = ok(TraceEditor.edit(ctx, start, e, new EntryEdit.Mute(true))).after();
		assertEquals(Boolean.TRUE, r.globalRules().get(0).mute());
		r = ok(TraceEditor.edit(ctx, r, e, new EntryEdit.Cue(false))).after();
		assertEquals(Boolean.FALSE, r.globalRules().get(0).cue());
		r = ok(TraceEditor.edit(ctx, r, e, new EntryEdit.SetLimit(3, 1000, true))).after();
		assertEquals(new Limit(3, 1000, true), r.globalRules().get(0).limit());
		TraceEditResult limited = TraceEditor.edit(ctx, r, e, new EntryEdit.Volume(70));
		assertEquals(new Limit(3, 1000, true), limited.predicted().limit());
		r = ok(TraceEditor.edit(ctx, r, e, EntryEdit.ClearLimit.INSTANCE)).after();
		assertNull(r.globalRules().get(0).limit());
		r = ok(TraceEditor.edit(ctx, r, e, new EntryEdit.Mute(null))).after();
		assertNull(r.globalRules().get(0).mute(), "Mute off clears it, so the rule no longer sets loudness");
	}

	@Test
	void limitOutOfRangeIsRefused() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of());
		RuleLocation e = RuleLocation.global("e");
		assertEquals(TraceEditError.LIMIT_COUNT_OUT_OF_RANGE,
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.SetLimit(0, 1000, false)).error());
		assertEquals(TraceEditError.LIMIT_COUNT_OUT_OF_RANGE,
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.SetLimit(Limit.MAX_COUNT + 1, 1000, false)).error());
		assertEquals(TraceEditError.LIMIT_WINDOW_OUT_OF_RANGE,
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.SetLimit(3, 10, false)).error());
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE,
				TraceEditor.edit(context(PLAY), start, e, new EntryEdit.Volume(-1)).error());
	}

	@Test
	void volumeEdgesAreAccepted() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of());
		RuleLocation e = RuleLocation.global("e");
		assertEquals(0, ok(TraceEditor.edit(context(PLAY), start, e, new EntryEdit.Volume(0))).after().globalRules().get(0).volume());
		assertEquals(200, ok(TraceEditor.edit(context(PLAY), start, e, new EntryEdit.Volume(200))).after().globalRules().get(0).volume());
	}

	@Test
	void unknownRuleOrZoneIsRefused() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of(sphere("hall", 10)));
		assertEquals(TraceEditError.RULE_NOT_FOUND,
				TraceEditor.edit(context(PLAY), start, RuleLocation.global("nope"), new EntryEdit.Mute(true)).error());
		assertEquals(TraceEditError.RULE_NOT_FOUND,
				TraceEditor.edit(context(PLAY), start, RuleLocation.inZone("hall", "e"), new EntryEdit.Mute(true)).error());
		assertEquals(TraceEditError.ZONE_NOT_FOUND,
				TraceEditor.edit(context(PLAY), start, RuleLocation.inZone("nope", "e"), new EntryEdit.Mute(true)).error());
		assertEquals(TraceEditError.ZONE_NOT_FOUND, TraceEditor.setZoneEnabled(context(PLAY), start, "nope", false).error());
	}

	@Test
	void editsLeaveTheOtherRulesAndZonesAlone() {
		SetEntry a = SetEntry.of("a", AMBIENT).withVolume(60);
		SetEntry b = SetEntry.of("b", "*villager*").withVolume(30);
		Zone hall = sphere("hall", 10, SetEntry.of("hr", "*villager*").withVolume(50), SetEntry.of("hx", "*cow*").withMute(true));
		Zone yard = sphere("yard", 20, SetEntry.of("yr", "*").withVolume(90));
		EditableRules start = new EditableRules(List.of(a, b), List.of(hall, yard));

		EditableRules after = ok(TraceEditor.edit(context(PLAY), start, RuleLocation.inZone("hall", "hr"),
				new EntryEdit.Volume(20))).after();

		assertEquals(List.of(a, b), after.globalRules());
		assertEquals(yard, after.zones().get(1));
		assertEquals(hall.sets().quickEntries().get(1), after.zones().get(0).sets().quickEntries().get(1));
		Zone edited = after.zones().get(0);
		assertEquals(hall.shape(), edited.shape());
		assertEquals(hall.anchor(), edited.anchor());
		assertEquals(hall.name(), edited.name());
	}

	// --- Zone line controls ---

	@Test
	void disablingAZoneLineDropsItFromThePrediction() {
		Zone hall = sphere("hall", 10, SetEntry.of("hr", "*villager*").withVolume(50));
		EditableRules start = new EditableRules(List.of(), List.of(hall));

		TraceEditResult result = ok(TraceEditor.setZoneEnabled(context(PLAY), start, "hall", false));

		assertFalse(result.after().zones().get(0).enabled());
		assertTrue(result.predicted().zones().isEmpty());
		assertEquals(100, result.predicted().volumePercent(), 1e-6);
		assertEquals(hall.sets().quickEntries(), result.after().zones().get(0).sets().quickEntries());
	}

	@Test
	void disabledZoneKeepsItsKind() {
		Zone bubble = sphere("bubble", 8, SetEntry.of("br", "*").withVolume(30)).asBubble();
		EditableRules start = new EditableRules(List.of(), List.of(bubble));
		Zone off = ok(TraceEditor.setZoneEnabled(context(PLAY), start, "bubble", false)).after().zones().get(0);
		assertTrue(off.isBubble());
		assertEquals(bubble.mode(), off.mode());
		assertEquals(bubble.edge(), off.edge());
	}

	@Test
	void matchedZoneRuleIsTheWinnerForThatSettingOrElseTheFirstMatch() {
		SetEntry loud = SetEntry.of("loud", "*villager*").withVolume(50);
		SetEntry cue = SetEntry.of("cue", AMBIENT).withCue(true);
		Zone hall = sphere("hall", 10, loud, cue, SetEntry.of("cow", "*cow*").withMute(true));
		SoundTrace t = context(PLAY).trace(new EditableRules(List.of(), List.of(hall)));

		assertEquals("loud", TraceEditor.matchedZoneRule(t, "hall", TraceProperty.LOUDNESS).id());
		assertEquals("cue", TraceEditor.matchedZoneRule(t, "hall", TraceProperty.CUE).id());
		assertEquals("cue", TraceEditor.matchedZoneRule(t, "hall", TraceProperty.LIMIT).id(),
				"no rule sets the limit, so the first (most specific) matched rule is edited");
		assertNull(TraceEditor.matchedZoneRule(t, "elsewhere", TraceProperty.LOUDNESS), "zone not in the trace");
	}

	@Test
	void matchedZoneRuleIsNullWhenTheZoneHasNoMatchingRule() {
		Zone hall = sphere("hall", 10, SetEntry.of("cow", "*cow*").withMute(true));
		SoundTrace t = context(PLAY).trace(new EditableRules(List.of(), List.of(hall)));
		assertNull(TraceEditor.matchedZoneRule(t, "hall", TraceProperty.LOUDNESS), "the panel offers Add rule instead");
	}

	// --- Add rule for this sound ---

	@Test
	void addRuleInAZoneCreatesAnExactRuleThere() {
		Zone hall = sphere("hall", 10, SetEntry.of("cow", "*cow*").withMute(true));
		EditableRules start = new EditableRules(List.of(), List.of(hall));

		TraceEditResult result = ok(TraceEditor.addRuleForSound(context(PLAY), start, "hall", new EntryEdit.Volume(25)));

		List<SetEntry> rules = result.after().zones().get(0).sets().quickEntries();
		assertEquals(2, rules.size());
		SetEntry added = rules.get(1);
		assertEquals(AMBIENT, added.pattern().text());
		assertEquals(25, added.volume());
		assertTrue(result.after().globalRules().isEmpty());
		assertEquals(25, result.predicted().volumePercent(), 1e-6);
	}

	@Test
	void addRuleReusesAnExistingExactRuleInsteadOfDuplicatingIt() {
		SetEntry existing = new SetEntry("mine", false, soundsculptor.core.sets.SoundPattern.of(AMBIENT), null, 60, null, null);
		EditableRules start = new EditableRules(List.of(existing), List.of());

		TraceEditResult result = ok(TraceEditor.addRuleForSound(context(PLAY), start, null, new EntryEdit.Mute(true)));

		assertEquals(1, result.after().globalRules().size());
		SetEntry r = result.after().globalRules().get(0);
		assertEquals("mine", r.id());
		assertTrue(r.enabled(), "the reused rule is turned on, or the edit would have no effect");
		assertEquals(Boolean.TRUE, r.mute());
		assertEquals(60, r.volume());
		assertEquals(0, result.predicted().volumePercent(), 1e-6);
	}

	@Test
	void addRuleDoesNotReuseAWildcardThatHappensToMatch() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("w", "*villager*").withVolume(30)), List.of());
		TraceEditResult result = ok(TraceEditor.addRuleForSound(context(PLAY), start, null, new EntryEdit.Volume(80)));
		assertEquals(2, result.after().globalRules().size());
		assertEquals(30, result.after().globalRules().get(0).volume());
	}

	@Test
	void addRuleGivesZoneRulesTheirOwnStableId() {
		Zone hall = sphere("hall", 10);
		Zone yard = sphere("yard", 20);
		EditableRules start = new EditableRules(List.of(), List.of(hall, yard));
		TraceEditContext ctx = context(PLAY);

		EditableRules r = ok(TraceEditor.addRuleForSound(ctx, start, null, new EntryEdit.Volume(80))).after();
		r = ok(TraceEditor.addRuleForSound(ctx, r, "hall", new EntryEdit.Volume(40))).after();
		r = ok(TraceEditor.addRuleForSound(ctx, r, "yard", new EntryEdit.Volume(60))).after();

		List<String> ids = new ArrayList<>();
		ids.add(r.globalRules().get(0).id());
		ids.add(r.zones().get(0).sets().quickEntries().get(0).id());
		ids.add(r.zones().get(1).sets().quickEntries().get(0).id());
		assertEquals(3, ids.stream().distinct().count(), "rule IDs stay unique across the profile: " + ids);
		assertEquals(ids, List.of(
				TraceEditor.exactRuleId(null, AMBIENT),
				TraceEditor.exactRuleId("hall", AMBIENT),
				TraceEditor.exactRuleId("yard", AMBIENT)), "and are the same every time");
	}

	@Test
	void addRuleSkipsAnIdAlreadyTakenByAnotherRule() {
		SetEntry squatter = SetEntry.of("exact:" + AMBIENT, "*villager*").withVolume(30);
		EditableRules start = new EditableRules(List.of(squatter), List.of());
		SetEntry added = ok(TraceEditor.addRuleForSound(context(PLAY), start, null, new EntryEdit.Mute(true))).after().globalRules().get(1);
		assertFalse(added.id().equals(squatter.id()));
		assertEquals(AMBIENT, added.pattern().text());
	}

	@Test
	void addRuleRefusesAnInvalidValueOrUnknownZone() {
		EditableRules start = new EditableRules(List.of(), List.of(sphere("hall", 10)));
		TraceEditResult bad = TraceEditor.addRuleForSound(context(PLAY), start, null, new EntryEdit.Volume(250));
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE, bad.error());
		assertSame(start, bad.after());
		assertEquals(TraceEditError.ZONE_NOT_FOUND,
				TraceEditor.addRuleForSound(context(PLAY), start, "nope", new EntryEdit.Mute(true)).error());
	}

	@Test
	void addRuleRefusesPastTheQuickChangesLimit() {
		List<SetEntry> many = new ArrayList<>();
		for (int i = 0; i < TraceEditor.MAX_RULES; i++) {
			many.add(SetEntry.of("r" + i, "minecraft:block.b" + i));
		}
		EditableRules full = new EditableRules(many, List.of());
		assertEquals(TraceEditError.TOO_MANY_RULES,
				TraceEditor.addRuleForSound(context(PLAY), full, null, new EntryEdit.Mute(true)).error());
	}

	// --- Prediction and Undo ---

	@Test
	void notHeardYetEditsAreNotLabelledPredicted() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of());
		TraceEditResult result = ok(TraceEditor.edit(context(null), start, RuleLocation.global("e"), new EntryEdit.Volume(40)));
		assertFalse(result.predictedForNextPlay());
		assertTrue(result.predicted().notices().contains(TraceNotice.NOT_HEARD_YET));
		assertEquals(40, result.predicted().volumePercent(), 1e-6);
	}

	@Test
	void predictionUsesThePlayersPositionForBubbles() {
		LastPlay play = new LastPlay(true, WORLD, OVERWORLD, 102, 0, 0, 100, 0, 0, 100, false, false);
		Zone bubble = sphere("bubble", 8, SetEntry.of("br", "*villager*").withVolume(100)).asBubble();
		EditableRules start = new EditableRules(List.of(), List.of(bubble));
		TraceEditResult result = ok(TraceEditor.edit(context(play), start, RuleLocation.inZone("bubble", "br"), new EntryEdit.Volume(30)));
		assertEquals(30, result.predicted().volumePercent(), 1e-6, "the bubble sits on the player, 2 blocks from the sound");
	}

	@Test
	void toastNamesTheEditedPatternWithoutJoiningSentenceFragments() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of());
		TraceEditResult edited = ok(TraceEditor.edit(context(PLAY), start, RuleLocation.global("e"), new EntryEdit.Volume(40)));
		assertEquals("soundsculptor.trace.edit.toast.changed", edited.toastKey());
		assertEquals(List.of(AMBIENT), edited.toastArgs());
		TraceEditResult added = ok(TraceEditor.addRuleForSound(context(PLAY), new EditableRules(List.of(), List.of()), null,
				new EntryEdit.Mute(true)));
		assertEquals("soundsculptor.trace.edit.toast.added", added.toastKey());
		assertEquals(List.of(AMBIENT), added.toastArgs());
	}

	@Test
	void refusedEditOffersNoToast() {
		EditableRules start = new EditableRules(List.of(SetEntry.of("e", AMBIENT)), List.of());
		TraceEditResult refused = TraceEditor.edit(context(PLAY), start, RuleLocation.global("e"), new EntryEdit.Volume(999));
		assertNull(refused.toastKey());
		assertTrue(refused.toastArgs().isEmpty());
	}

	@Test
	void editableRulesAreImmutableCopies() {
		List<SetEntry> rules = new ArrayList<>(List.of(SetEntry.of("e", AMBIENT)));
		EditableRules r = new EditableRules(rules, List.of());
		rules.clear();
		assertEquals(1, r.globalRules().size());
	}
}
