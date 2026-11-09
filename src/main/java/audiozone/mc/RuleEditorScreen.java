package audiozone.mc;

import audiozone.core.editor.FieldError;
import audiozone.core.editor.PatternPreview;
import audiozone.core.editor.RuleDraft;
import audiozone.core.editor.RuleDraft.Field;
import audiozone.core.profile.Profile;
import audiozone.core.rules.Rule;
import audiozone.core.rules.RuleResolver;
import audiozone.core.ui.SliderModel;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzDropdown;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzSlider;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.AzToggle;
import audiozone.mc.ui.Theme;
import audiozone.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The rule editor (PRD 6.1 Rules, FR-4, FR-5 MVP): pattern with a live "Matches N sounds" preview and the matching
 * IDs (FR-4.3), "Partly overridden by" (FR-4.6), enabled (FR-4.7), Mute and Volume 0–100% (FR-5.1–5.2). Save and
 * Cancel, with the unsaved-changes dialog (FR-12.4), a reset button per value (FR-12.6) and inline errors that disable
 * Save (FR-12.7). Limit and Cue arrive with their milestones (M11, M9); a rule's existing limit and cue are kept.
 */
public final class RuleEditorScreen extends AzScreen {
	private static final Identifier RESET_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/reset");
	private static final int ENABLED_WIDTH = 110;
	private static final int MUTE_WIDTH = 170;
	private static final int SLIDER_WIDTH = 200;
	private static final int VOLUME_FIELD_WIDTH = 50;

	/** Mute is "not set" unless the rule sets it, so a rule can also say "do not mute" over a broader one. */
	public enum MuteChoice {
		NOT_SET(null), MUTE(true), DO_NOT_MUTE(false);

		final Boolean value;

		MuteChoice(Boolean value) {
			this.value = value;
		}

		static MuteChoice of(Boolean value) {
			return value == null ? NOT_SET : value ? MUTE : DO_NOT_MUTE;
		}
	}

	private final RuleDraft draft;
	private PatternPreview preview;
	private Component overridden = Component.empty();

	public AzTextField patternField;
	public AzToggle enabledToggle;
	public AzDropdown<MuteChoice> muteDropdown;
	public AzSlider volumeSlider;
	public AzTextField volumeField;
	public AzList matchList;
	public AzButton saveButton;

	private RuleEditorScreen(Screen parent, RuleDraft draft, boolean isNew) {
		super(Component.translatable(isNew ? "audiozone.rule_editor.title.new" : "audiozone.rule_editor.title.edit"), parent);
		this.draft = draft;
		updatePreview();
	}

	/** Opens the editor for a new rule. */
	public static RuleEditorScreen create(Screen parent) {
		return new RuleEditorScreen(parent, RuleDraft.blank(ProfileManager.profile().newRuleId(), false), true);
	}

	/** Opens the editor for an existing rule. */
	public static RuleEditorScreen edit(Screen parent, Rule rule) {
		return new RuleEditorScreen(parent, RuleDraft.of(rule, false), false);
	}

	/** Opens the editor on a sound's exact-ID rule, or a new exact-ID rule for it. */
	public static RuleEditorScreen forSound(Screen parent, String soundId) {
		Rule exact = ProfileManager.profile().exactRule(soundId);
		if (exact != null) {
			return edit(parent, exact);
		}
		RuleEditorScreen screen = create(parent);
		screen.draft.set(Field.PATTERN, soundId);
		screen.updatePreview();
		return screen;
	}

	public RuleDraft draft() {
		return draft;
	}

	public PatternPreview preview() {
		return preview;
	}

	/** The "Partly overridden by" line, or empty. */
	public Component overriddenText() {
		return overridden;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("audiozone.header.profile", ProfileManager.profile().name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(AzButton.text(Component.translatable("audiozone.ui.cancel"), this::onClose));
		saveButton = AzButton.text(Component.translatable("audiozone.ui.save"), this::save);
		saveButton.active = draft.canSave();
		footer.add(saveButton);
	}

	@Override
	protected void buildTab(int index, Body body) {
		int resetWidth = Theme.ICON_BUTTON_SIZE + Theme.GAP;

		patternField = Tooltips.describe(new AzTextField(font, body.width() - resetWidth,
				Component.translatable("audiozone.rule_editor.pattern")), Component.translatable("audiozone.rule_editor.pattern.tip"));
		patternField.placeholder(Component.translatable("audiozone.rule_editor.pattern.hint"));
		patternField.setMaxLength(200);
		patternField.setValue(draft.text(Field.PATTERN));
		patternField.validator(text -> {
			setText(Field.PATTERN, text);
			return message(draft.error(Field.PATTERN));
		});
		patternField.onChange(text -> {
			// FR-4.2: uppercase becomes lowercase as it is typed.
			String lower = text.toLowerCase(Locale.ROOT);
			if (!lower.equals(text)) {
				patternField.setValue(lower);
				return;
			}
			changed();
		});
		body.row(patternField, resetButton(Field.PATTERN, () -> patternField.setValue(draft.text(Field.PATTERN))));
		body.label(this::previewText);

		enabledToggle = Tooltips.describe(new AzToggle(ENABLED_WIDTH, Component.translatable("audiozone.rule_editor.enabled"),
				draft.enabled(), value -> {
					draft.setEnabled(value);
					changed();
				}), Component.translatable("audiozone.rule_editor.enabled.tip"));
		muteDropdown = Tooltips.describe(new AzDropdown<>(MUTE_WIDTH, Component.translatable("audiozone.rule_editor.mute"),
				List.of(MuteChoice.values()),
				choice -> Component.translatable("audiozone.rule_editor.mute." + choice.name().toLowerCase(Locale.ROOT)),
				MuteChoice.of(draft.mute()), this::setMute), Component.translatable("audiozone.rule_editor.mute.tip"));
		body.row(enabledToggle, muteDropdown, resetButton(Field.MUTE, () -> muteDropdown.setValue(MuteChoice.of(draft.mute()))));

		String volumeText = draft.text(Field.VOLUME);
		double initialVolume = draft.error(Field.VOLUME) == null && !volumeText.isBlank() ? Double.parseDouble(volumeText.trim()) : 100;
		volumeSlider = Tooltips.describe(new AzSlider(SLIDER_WIDTH, Component.translatable("audiozone.rule_editor.volume"),
				new SliderModel(Rule.MIN_VOLUME, 100, 5, 1, 100, initialVolume),
				value -> draft.text(Field.VOLUME).isBlank()
						? Component.translatable("audiozone.rule_editor.volume.not_set")
						: Component.literal((int) value + "%"),
				null,
				value -> {
					if (volumeField != null && !volumeField.isFocused()) {
						volumeField.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("audiozone.rule_editor.volume.tip"));
		volumeField = Tooltips.describe(new AzTextField(font, VOLUME_FIELD_WIDTH, Component.translatable("audiozone.rule_editor.volume_typed")),
				Component.translatable("audiozone.rule_editor.volume_typed.tip"));
		volumeField.placeholder(Component.translatable("audiozone.rule_editor.volume.not_set_short"));
		volumeField.setMaxLength(3);
		volumeField.setValue(volumeText);
		volumeField.validator(text -> {
			setText(Field.VOLUME, text);
			return message(draft.error(Field.VOLUME));
		});
		volumeField.onChange(text -> {
			if (draft.error(Field.VOLUME) == null) {
				volumeSlider.setTypedValue(text.isBlank() ? 100 : Double.parseDouble(text.trim()));
			}
			changed();
		});
		body.row(volumeSlider, volumeField, resetButton(Field.VOLUME, () -> volumeField.setValue(draft.text(Field.VOLUME))));
		body.label(() -> overridden);

		matchList = new AzList(body.width(), AzList.ROW_HEIGHT, Component.translatable("audiozone.rule_editor.matches"));
		matchList.setEmptyText(() -> Component.translatable(preview.error() != null
				? "audiozone.rule_editor.matches.invalid" : "audiozone.rule_editor.matches.none"));
		body.fill(matchList);
		fillMatches();

		// After Save was refused (for example from the unsaved-changes dialog), keep every error visible.
		if (draft.shownError(Field.PATTERN) != null) {
			patternField.revealError();
		}
		if (draft.shownError(Field.VOLUME) != null) {
			volumeField.revealError();
		}
	}

	/** Only a real change counts as an edit, so an untouched field shows no error (6.2). */
	private void setText(Field field, String text) {
		if (!text.equals(draft.text(field))) {
			draft.set(field, text);
		}
	}

	public void setMute(MuteChoice choice) {
		draft.setMute(choice.value);
		if (muteDropdown != null && muteDropdown.value() != choice) {
			muteDropdown.setValue(choice);
		}
		changed();
	}

	private AzButton resetButton(Field field, Runnable syncWidget) {
		// The icon button's tooltip is its label, e.g. "Reset volume".
		return AzButton.icon(RESET_ICON, Component.translatable("audiozone.rule_editor.reset." + field.name().toLowerCase(Locale.ROOT)),
				() -> {
					draft.resetToDefault(field);
					syncWidget.run();
					changed();
				});
	}

	private Component previewText() {
		if (preview.error() != null) {
			return Component.empty();
		}
		return Component.translatable("audiozone.rule_editor.preview", preview.count());
	}

	private static Component message(FieldError error) {
		return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
	}

	/** Any value changed: refresh the preview, the overridden line, the match list and Save. */
	private void changed() {
		String before = preview == null ? null : preview.normalizedPattern();
		updatePreview();
		if (matchList != null && !java.util.Objects.equals(before, preview.normalizedPattern())) {
			fillMatches();
		}
		if (saveButton != null) {
			saveButton.active = draft.canSave();
		}
	}

	private void updatePreview() {
		preview = PatternPreview.of(draft.text(Field.PATTERN), KnownSounds.ids(), KnownSounds.seenExcluded());
		overridden = Component.empty();
		if (!draft.canSave()) {
			return;
		}
		Rule candidate = draft.build();
		Profile withCandidate = ProfileManager.profile().putRule(candidate);
		Set<Rule> winners = new RuleResolver(withCandidate.globalRules()).overriddenBy(candidate, preview.matches());
		if (!winners.isEmpty()) {
			List<String> patterns = new ArrayList<>();
			for (Rule winner : winners) {
				patterns.add(winner.pattern().text());
			}
			overridden = Component.translatable("audiozone.rule_editor.overridden", String.join(", ", patterns));
		}
	}

	private void fillMatches() {
		List<AzList.Row> rows = new ArrayList<>(preview.count());
		for (String id : preview.matches()) {
			rows.add(new AzList.Row(id, List.of(new AzList.Row.Column(() -> id, 0)), List.of()));
		}
		matchList.setRows(rows);
	}

	// ---- Save, Cancel ----

	public void save() {
		if (saveChanges()) {
			closeNow();
		}
	}

	@Override
	protected boolean hasUnsavedChanges() {
		return draft.isDirty();
	}

	@Override
	protected boolean saveChanges() {
		if (!draft.attemptSave()) {
			patternField.revealError();
			volumeField.revealError();
			return false;
		}
		Rule rule = draft.build();
		ProfileManager.update(profile -> profile.putRule(rule));
		return true;
	}
}
