package soundsculptor.core.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileSettings;
import soundsculptor.core.rules.Limit;
import soundsculptor.core.rules.Rule;
import soundsculptor.core.trace.RuleEdit;
import soundsculptor.core.trace.TraceEditError;
import soundsculptor.core.trace.TraceEditor;
import soundsculptor.core.undo.UndoSlot;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-1.5: quick actions create or edit an exact-ID rule in the list "Apply to" chose. */
class QuickActionsTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final Point HOME = new Point(0, 64, 0);

	private static Zone pen(Rule... rules) {
		return new Zone("pen", "Villager pen", true, WORLD, OVERWORLD, HOME, new Shape.Sphere(10), List.of(rules));
	}

	private static Profile profile(List<Rule> global, Zone... zones) {
		Profile profile = new Profile("p", ProfileSettings.DEFAULTS, global).withZones(List.of(zones));
		return zones.length == 0 ? profile : profile.withZoneColor("pen", 0xE69F00);
	}

	private static ApplyTarget penTarget(Profile profile) {
		return ApplyTo.select(ApplyTo.choices(profile, WORLD, OVERWORLD, HOME), "pen");
	}

	private static QuickActionResult ok(QuickActionResult result) {
		assertTrue(result.ok(), () -> "refused: " + result.error());
		return result;
	}

	@Test
	void muteEverywhereCreatesTheSameGlobalRuleAsTheOldQuickAction() {
		Profile start = profile(List.of(), pen());
		QuickActionResult result = ok(QuickActions.apply(start, ApplyTarget.EVERYWHERE, AMBIENT, new RuleEdit.Mute(true)));
		assertEquals(start.muteExact(AMBIENT).globalRules(), result.after().globalRules());
		assertEquals("exact:" + AMBIENT, result.after().globalRules().get(0).id());
		assertTrue(QuickActions.isMuted(result.after(), ApplyTarget.EVERYWHERE, AMBIENT));
		assertEquals(TraceEditor.TOAST_ADDED, result.toastKey());
		assertEquals(List.of(AMBIENT), result.toastArgs());
	}

	@Test
	void muteInAZoneAddsAnExactRuleToThatZoneOnly() {
		Profile start = profile(List.of(), pen());
		QuickActionResult result = ok(QuickActions.apply(start, penTarget(start), AMBIENT, new RuleEdit.Mute(true)));
		assertEquals(List.of(), result.after().globalRules());
		Rule added = result.after().zones().get(0).rules().get(0);
		assertEquals("pen/exact:" + AMBIENT, added.id());
		assertEquals(AMBIENT, added.pattern().text());
		assertEquals(Boolean.TRUE, added.mute());
		assertTrue(QuickActions.isMuted(result.after(), penTarget(start), AMBIENT));
		assertFalse(QuickActions.isMuted(result.after(), ApplyTarget.EVERYWHERE, AMBIENT));
		assertEquals(0xE69F00, result.after().colorOf("pen"), "the zone keeps its color");
	}

	@Test
	void anExistingExactRuleInTheTargetIsEditedAndTurnedOnInstead() {
		Rule old = Rule.of("mine", AMBIENT).withVolume(50).withEnabled(false);
		Profile start = profile(List.of(), pen(old));
		QuickActionResult result = ok(QuickActions.apply(start, penTarget(start), AMBIENT, new RuleEdit.Mute(true)));
		assertEquals(List.of(old.withEnabled(true).withMute(true)), result.after().zones().get(0).rules());
		assertEquals(TraceEditor.TOAST_CHANGED, result.toastKey());
	}

	@Test
	void volumeAndLimitAndCueEditTheSameRule() {
		Profile p = profile(List.of(), pen());
		ApplyTarget pen = penTarget(p);
		p = ok(QuickActions.apply(p, pen, AMBIENT, new RuleEdit.Volume(30))).after();
		p = ok(QuickActions.apply(p, pen, AMBIENT, new RuleEdit.SetLimit(2, 1000, false))).after();
		p = ok(QuickActions.apply(p, pen, AMBIENT, new RuleEdit.Cue(true))).after();
		Rule rule = QuickActions.exactRule(p, pen, AMBIENT);
		assertEquals(30, rule.volume());
		assertEquals(new Limit(2, 1000, false), rule.limit());
		assertEquals(Boolean.TRUE, rule.cue());
		assertEquals(1, p.zones().get(0).rules().size());
	}

	@Test
	void unmuteRemovesARuleLeftSettingNothingSoMuteThenUnmuteLeavesTheProfileAsItWas() {
		for (boolean inZone : new boolean[] {false, true}) {
			Profile start = profile(List.of(Rule.of("r1", "*villager*").withVolume(40)), pen());
			ApplyTarget target = inZone ? penTarget(start) : ApplyTarget.EVERYWHERE;
			Profile muted = ok(QuickActions.apply(start, target, AMBIENT, new RuleEdit.Mute(true))).after();
			QuickActionResult unmuted = ok(QuickActions.apply(muted, target, AMBIENT, new RuleEdit.Mute(null)));
			assertEquals(start, unmuted.after());
			assertEquals(QuickActions.TOAST_REMOVED, unmuted.toastKey());
			assertEquals(List.of(AMBIENT), unmuted.toastArgs());
		}
	}

	@Test
	void unmuteKeepsARuleThatStillSetsSomethingAndDoesNotTurnItOn() {
		Rule old = Rule.of("mine", AMBIENT).withMute(true).withVolume(50).withEnabled(false);
		Profile start = profile(List.of(), pen(old));
		QuickActionResult result = ok(QuickActions.apply(start, penTarget(start), AMBIENT, new RuleEdit.Mute(null)));
		assertEquals(List.of(Rule.of("mine", AMBIENT).withVolume(50).withEnabled(false)), result.after().zones().get(0).rules());
		assertEquals(TraceEditor.TOAST_CHANGED, result.toastKey());
	}

	@Test
	void clearingWhenThereIsNoRuleChangesNothing() {
		Profile start = profile(List.of(), pen());
		QuickActionResult result = ok(QuickActions.apply(start, penTarget(start), AMBIENT, new RuleEdit.Mute(null)));
		assertSame(start, result.after());
		assertSame(start, result.before());
		assertNull(result.toastKey(), "no change, so no Undo toast");
		assertFalse(result.changed());
	}

	@Test
	void repeatingAnActionThatChangesNothingOffersNoUndo() {
		Profile muted = ok(QuickActions.apply(profile(List.of()), ApplyTarget.EVERYWHERE, AMBIENT, new RuleEdit.Mute(true))).after();
		QuickActionResult again = ok(QuickActions.apply(muted, ApplyTarget.EVERYWHERE, AMBIENT, new RuleEdit.Mute(true)));
		assertSame(muted, again.after());
		assertNull(again.toastKey());
	}

	@Test
	void toggleMuteFlipsBetweenMuteAndClear() {
		Profile p = profile(List.of(), pen());
		ApplyTarget pen = penTarget(p);
		assertEquals(new RuleEdit.MuteWithCue(true), QuickActions.toggleMute(p, pen, AMBIENT));
		p = ok(QuickActions.apply(p, pen, AMBIENT, QuickActions.toggleMute(p, pen, AMBIENT))).after();
		assertTrue(QuickActions.isMuted(p, pen, AMBIENT));
		assertEquals(new RuleEdit.UnmuteWithCue(true), QuickActions.toggleMute(p, pen, AMBIENT));
	}

	@Test
	void aDisabledMutingRuleDoesNotCountAsMuted() {
		Profile p = profile(List.of(Rule.of("exact:" + AMBIENT, AMBIENT).withMute(true).withEnabled(false)));
		assertFalse(QuickActions.isMuted(p, ApplyTarget.EVERYWHERE, AMBIENT));
		assertEquals(new RuleEdit.MuteWithCue(true), QuickActions.toggleMute(p, ApplyTarget.EVERYWHERE, AMBIENT));
	}

	@Test
	void quickMuteStartsTheRuleWithTheProfilesCueDefault() {
		// FR-7.6: new mute rules have Cue on by default; the default can be changed in Settings.
		for (boolean cueDefault : new boolean[] {true, false}) {
			Profile p = profile(List.of(), pen()).withSettings(ProfileSettings.DEFAULTS.withCueDefault(cueDefault));
			p = ok(QuickActions.apply(p, ApplyTarget.EVERYWHERE, AMBIENT, QuickActions.toggleMute(p, ApplyTarget.EVERYWHERE,
					AMBIENT))).after();
			Rule rule = QuickActions.exactRule(p, ApplyTarget.EVERYWHERE, AMBIENT);
			assertEquals(Boolean.TRUE, rule.mute());
			assertEquals(cueDefault, rule.cue());
		}
	}

	@Test
	void quickMuteKeepsACueTheRuleAlreadySets() {
		Profile p = profile(List.of(Rule.of("exact:" + AMBIENT, AMBIENT).withCue(false).withVolume(50)));
		p = ok(QuickActions.apply(p, ApplyTarget.EVERYWHERE, AMBIENT, QuickActions.toggleMute(p, ApplyTarget.EVERYWHERE,
				AMBIENT))).after();
		assertEquals(Boolean.FALSE, QuickActions.exactRule(p, ApplyTarget.EVERYWHERE, AMBIENT).cue());
	}

	@Test
	void toggledMuteThenUnmuteLeavesTheProfileAsItWas() {
		for (boolean inZone : new boolean[] {false, true}) {
			Profile start = profile(List.of(Rule.of("r1", "*villager*").withVolume(40)), pen());
			ApplyTarget target = inZone ? penTarget(start) : ApplyTarget.EVERYWHERE;
			Profile muted = ok(QuickActions.apply(start, target, AMBIENT, QuickActions.toggleMute(start, target, AMBIENT))).after();
			QuickActionResult unmuted = ok(QuickActions.apply(muted, target, AMBIENT, QuickActions.toggleMute(muted, target,
					AMBIENT)));
			assertEquals(start, unmuted.after(), "the cue the mute added goes with it");
			assertEquals(QuickActions.TOAST_REMOVED, unmuted.toastKey());
		}
	}

	@Test
	void unmuteKeepsACueThatDiffersFromTheDefaultOrARuleThatSetsMore() {
		Profile cueOff = profile(List.of(Rule.of("exact:" + AMBIENT, AMBIENT).withMute(true).withCue(false)));
		cueOff = ok(QuickActions.apply(cueOff, ApplyTarget.EVERYWHERE, AMBIENT, QuickActions.toggleMute(cueOff,
				ApplyTarget.EVERYWHERE, AMBIENT))).after();
		assertEquals(Rule.of("exact:" + AMBIENT, AMBIENT).withCue(false), QuickActions.exactRule(cueOff, ApplyTarget.EVERYWHERE,
				AMBIENT), "the player turned Cue off on purpose");
		Profile more = profile(List.of(Rule.of("exact:" + AMBIENT, AMBIENT).withMute(true).withCue(true).withVolume(50)));
		more = ok(QuickActions.apply(more, ApplyTarget.EVERYWHERE, AMBIENT, QuickActions.toggleMute(more,
				ApplyTarget.EVERYWHERE, AMBIENT))).after();
		assertEquals(Rule.of("exact:" + AMBIENT, AMBIENT).withCue(true).withVolume(50), QuickActions.exactRule(more,
				ApplyTarget.EVERYWHERE, AMBIENT));
	}

	@Test
	void outOfRangeValuesAreRefusedWithTheEditorMessage() {
		Profile start = profile(List.of(), pen());
		QuickActionResult result = QuickActions.apply(start, penTarget(start), AMBIENT, new RuleEdit.Volume(250));
		assertEquals(TraceEditError.VOLUME_OUT_OF_RANGE, result.error());
		assertSame(start, result.after());
		assertNull(result.toastKey());
	}

	@Test
	void aZoneDeletedSinceTheListOpenedIsRefused() {
		Profile withPen = profile(List.of(), pen());
		ApplyTarget pen = penTarget(withPen);
		Profile without = withPen.withZones(List.of());
		assertEquals(TraceEditError.ZONE_NOT_FOUND, QuickActions.apply(without, pen, AMBIENT, new RuleEdit.Mute(true)).error());
		assertNull(QuickActions.exactRule(without, pen, AMBIENT));
		assertFalse(QuickActions.isMuted(without, pen, AMBIENT));
	}

	@Test
	void aFullProfileRefusesNewRulesButStillEditsExistingOnes() {
		List<Rule> rules = new ArrayList<>();
		for (int i = 0; i < TraceEditor.MAX_RULES - 1; i++) {
			rules.add(Rule.of("g" + i, "minecraft:sound." + i));
		}
		Profile full = profile(rules, pen(Rule.of("mine", AMBIENT)));
		assertEquals(TraceEditError.TOO_MANY_RULES,
				QuickActions.apply(full, ApplyTarget.EVERYWHERE, AMBIENT, new RuleEdit.Mute(true)).error());
		assertTrue(QuickActions.apply(full, penTarget(full), AMBIENT, new RuleEdit.Mute(true)).ok());
	}

	@Test
	void undoRestoresTheProfileBeforeTheQuickAction() {
		Profile start = profile(List.of(), pen());
		QuickActionResult result = ok(QuickActions.apply(start, penTarget(start), AMBIENT, new RuleEdit.Volume(20)));
		UndoSlot<Profile> undo = new UndoSlot<>();
		undo.offer(result.before(), result.after(), result.toastKey(), result.toastArgs(), 1000);
		assertEquals(start, undo.undo(result.after(), 2000).orElseThrow());
	}
}
