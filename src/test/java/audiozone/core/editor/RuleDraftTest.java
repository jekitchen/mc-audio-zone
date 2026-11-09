package audiozone.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import audiozone.core.editor.RuleDraft.Field;
import audiozone.core.rules.Limit;
import audiozone.core.rules.PatternError;
import audiozone.core.rules.Rule;

import org.junit.jupiter.api.Test;

/** PRD FR-12.4, FR-12.6, FR-12.7 and section 6.2 for the rule editor (FR-4, FR-5, FR-6 limits). */
class RuleDraftTest {
	private static final boolean BOOST = true;

	@Test
	void blankDraftCannotSaveButShowsNoErrorUntilEditedOrSaveIsPressed() {
		// 6.2: Save stays disabled from the start; the error shows once the field has been edited, or Save is pressed.
		RuleDraft d = RuleDraft.blank("r1", BOOST);
		assertFalse(d.canSave());
		assertEquals(PatternError.EMPTY.translationKey(), d.error(Field.PATTERN).translationKey());
		assertNull(d.shownError(Field.PATTERN));
		assertFalse(d.attemptSave());
		assertEquals(PatternError.EMPTY.translationKey(), d.shownError(Field.PATTERN).translationKey());
	}

	@Test
	void editingAFieldShowsItsErrorAtOnce() {
		RuleDraft d = RuleDraft.blank("r1", BOOST);
		d.set(Field.PATTERN, "entity.cow.#");
		assertEquals(PatternError.INVALID_CHARACTER.translationKey(), d.shownError(Field.PATTERN).translationKey());
		assertNull(d.shownError(Field.VOLUME), "untouched fields stay quiet");
	}

	@Test
	void uppercasePatternIsAcceptedAndSavedLowercase() {
		RuleDraft d = RuleDraft.blank("r1", BOOST);
		d.set(Field.PATTERN, "Minecraft:Entity.Cow.*");
		d.setMute(true);
		assertTrue(d.canSave());
		Rule rule = d.build();
		assertEquals("minecraft:entity.cow.*", rule.pattern().text());
		assertEquals(Boolean.TRUE, rule.mute());
		assertEquals("r1", rule.id());
	}

	@Test
	void volumeIsCheckedAgainstTheSliderRange() {
		RuleDraft d = draft("*villager*");
		d.set(Field.VOLUME, "250");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("0", "200")), d.error(Field.VOLUME));
		d.set(Field.VOLUME, "150");
		assertNull(d.error(Field.VOLUME));
		assertTrue(d.showsBoostWarning());

		RuleDraft noBoost = RuleDraft.blank("r2", false);
		noBoost.set(Field.PATTERN, "*");
		noBoost.set(Field.VOLUME, "150");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("0", "100")), noBoost.error(Field.VOLUME));
	}

	@Test
	void volumeMustBeAWholeNumberOrEmpty() {
		RuleDraft d = draft("*");
		d.set(Field.VOLUME, "50.5");
		assertEquals(FieldError.NOT_WHOLE_NUMBER, d.error(Field.VOLUME).translationKey());
		d.set(Field.VOLUME, "loud");
		assertEquals(FieldError.NOT_A_NUMBER, d.error(Field.VOLUME).translationKey());
		d.set(Field.VOLUME, " 60 ");
		assertEquals(60, d.build().volume());
		d.set(Field.VOLUME, "");
		assertNull(d.build().volume(), "an empty field means the rule does not set volume");
		assertFalse(d.showsBoostWarning());
	}

	@Test
	void limitIsSetByItsCountAndNeedsAWindow() {
		RuleDraft d = draft("minecraft:entity.villager.ambient");
		d.set(Field.LIMIT_WINDOW, "nonsense");
		assertTrue(d.canSave(), "with no count there is no limit, so the window is not checked");
		assertNull(d.build().limit());

		d.set(Field.LIMIT_COUNT, "3");
		assertEquals(FieldError.NOT_A_NUMBER, d.error(Field.LIMIT_WINDOW).translationKey());
		d.set(Field.LIMIT_WINDOW, "");
		assertEquals(FieldError.REQUIRED, d.error(Field.LIMIT_WINDOW).translationKey());
		d.resetToDefault(Field.LIMIT_WINDOW);
		d.setPerSource(true);
		assertEquals(new Limit(3, 1000, true), d.build().limit());
	}

	@Test
	void limitRangesComeFromFr6() {
		RuleDraft d = draft("*");
		d.set(Field.LIMIT_COUNT, "0");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("1", "100")), d.error(Field.LIMIT_COUNT));
		d.set(Field.LIMIT_COUNT, "5");
		d.set(Field.LIMIT_WINDOW, "20");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("50", "10000")), d.error(Field.LIMIT_WINDOW));
		d.set(Field.LIMIT_WINDOW, "99999999999999999999");
		assertEquals(FieldError.OUT_OF_RANGE, d.error(Field.LIMIT_WINDOW).translationKey());
	}

	@Test
	void anExistingRuleRoundTrips() {
		Rule rule = new Rule("r7", false, audiozone.core.rules.SoundPattern.of("*:block.*.step"), false, 40,
				new Limit(2, 500, true), true);
		RuleDraft d = RuleDraft.of(rule, BOOST);
		assertEquals("40", d.text(Field.VOLUME));
		assertFalse(d.enabled());
		assertEquals(Boolean.FALSE, d.mute());
		assertEquals(Boolean.TRUE, d.cue());
		assertTrue(d.canSave());
		assertEquals(rule, d.build());
		assertEquals(Rule.of("r8", "*"), RuleDraft.of(Rule.of("r8", "*"), BOOST).build());
	}

	@Test
	void dirtyMeansAValueReallyChanged() {
		// FR-12.4: Esc with unsaved changes opens Save / Discard / Keep editing.
		RuleDraft d = RuleDraft.of(Rule.of("r1", "*villager*").withVolume(50), BOOST);
		assertFalse(d.isDirty());
		d.set(Field.VOLUME, "050");
		assertFalse(d.isDirty(), "the same number typed differently");
		d.set(Field.VOLUME, "20");
		assertTrue(d.isDirty());
		d.set(Field.VOLUME, "50");
		assertFalse(d.isDirty());
		d.set(Field.LIMIT_WINDOW, "2000");
		assertFalse(d.isDirty(), "the window does nothing without a count");
		d.set(Field.PATTERN, "*VILLAGER*");
		assertFalse(d.isDirty(), "same pattern after lowercasing");
		d.setCue(false);
		assertTrue(d.isDirty());
	}

	@Test
	void resetToDefaultPerValue() {
		// FR-12.6. Values with no app-wide default (the pattern) go back to what they were when the editor opened.
		RuleDraft d = RuleDraft.of(new Rule("r1", true, audiozone.core.rules.SoundPattern.of("*cow*"), true, 30,
				new Limit(4, 2000, true), true), BOOST);
		d.set(Field.PATTERN, "*pig*");
		d.resetToDefault(Field.PATTERN);
		assertEquals("*:*cow*", d.text(Field.PATTERN));
		for (Field f : List.of(Field.MUTE, Field.VOLUME, Field.LIMIT_COUNT, Field.LIMIT_WINDOW, Field.PER_SOURCE, Field.CUE)) {
			assertFalse(d.isDefault(f), f.name());
			d.resetToDefault(f);
			assertTrue(d.isDefault(f), f.name());
		}
		Rule reset = d.build();
		assertNull(reset.mute());
		assertNull(reset.volume());
		assertNull(reset.limit());
		assertNull(reset.cue());
		assertEquals("1000", d.text(Field.LIMIT_WINDOW));
		assertFalse(d.perSource());
	}

	@Test
	void togglesAreSetThroughTheirOwnMethods() {
		RuleDraft d = draft("*");
		assertThrows(IllegalArgumentException.class, () -> d.set(Field.MUTE, "maybe"));
		d.setMute(null);
		assertNull(d.mute());
		d.setEnabled(false);
		assertFalse(d.build().enabled());
	}

	@Test
	void buildRefusesAnInvalidDraft() {
		RuleDraft d = draft("*");
		d.set(Field.VOLUME, "999");
		assertThrows(IllegalStateException.class, d::build);
	}

	private static RuleDraft draft(String pattern) {
		RuleDraft d = RuleDraft.blank("r1", BOOST);
		d.set(Field.PATTERN, pattern);
		return d;
	}
}
