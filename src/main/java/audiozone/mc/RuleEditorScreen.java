package audiozone.mc;

import audiozone.core.editor.FieldError;
import audiozone.core.editor.PatternPreview;
import audiozone.core.editor.RuleDraft;
import audiozone.core.editor.RuleDraft.Field;
import audiozone.core.profile.Profile;
import audiozone.core.profile.RuleTarget;
import audiozone.core.rules.Rule;
import audiozone.core.rules.RuleResolver;
import audiozone.core.ui.SliderModel;
import audiozone.core.zones.Zone;
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
	/** The global list, or one zone's list. */
	private final RuleTarget target;
	private PatternPreview preview;
	private Component overridden = Component.empty();
	/**
	 * What the sound gets when this rule leaves volume or mute unset (M8 review: show the current value instead of
	 * "Not set"). For an exact sound ID, the other rules of the same list decide; otherwise 100% and not muted.
	 */
	private int inheritedVolume = 100;
	private boolean inheritedMuted;
	/** True while the slider is moved to show the unchanged value, so that move does not set a volume. */
	private boolean showingInherited;

	public AzTextField patternField;
	public AzToggle enabledToggle;
	public AzDropdown<MuteChoice> muteDropdown;
	public AzSlider volumeSlider;
	public AzTextField volumeField;
	public AzList matchList;
	public AzButton saveButton;

	private RuleEditorScreen(Screen parent, RuleTarget target, RuleDraft draft, boolean isNew) {
		super(Component.translatable(isNew ? "audiozone.rule_editor.title.new" : "audiozone.rule_editor.title.edit"), parent);
		this.target = target;
		this.draft = draft;
		updatePreview();
	}

	/** Opens the editor for a new rule. */
	public static RuleEditorScreen create(Screen parent) {
		return create(parent, RuleTarget.GLOBAL);
	}

	/** Opens the editor for a new rule in a zone's list or the global list (FR-8.6). */
	public static RuleEditorScreen create(Screen parent, RuleTarget target) {
		return new RuleEditorScreen(parent, target, RuleDraft.blank(target.newRuleId(ProfileManager.profile()), false), true);
	}

	/** Opens the editor for an existing rule. */
	public static RuleEditorScreen edit(Screen parent, Rule rule) {
		return edit(parent, RuleTarget.GLOBAL, rule);
	}

	/** Opens the editor for an existing rule of a zone's list or the global list. */
	public static RuleEditorScreen edit(Screen parent, RuleTarget target, Rule rule) {
		return new RuleEditorScreen(parent, target, RuleDraft.of(rule, false), false);
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
		if (!target.isGlobal()) {
			Zone zone = ProfileManager.profile().zone(target.zoneId());
			String name = zone == null || zone.name() == null || zone.name().isBlank() ? target.zoneId() : zone.name();
			return Component.translatable("audiozone.rule_editor.zone", name);
		}
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
				this::muteChoiceName,
				MuteChoice.of(draft.mute()), this::setMute), Component.translatable("audiozone.rule_editor.mute.tip"));
		body.row(enabledToggle, muteDropdown, resetButton(Field.MUTE, () -> muteDropdown.setValue(MuteChoice.of(draft.mute()))));

		String volumeText = draft.text(Field.VOLUME);
		double initialVolume = draft.error(Field.VOLUME) == null && !volumeText.isBlank() ? Double.parseDouble(volumeText.trim()) : inheritedVolume;
		volumeSlider = Tooltips.describe(new AzSlider(SLIDER_WIDTH, Component.translatable("audiozone.rule_editor.volume"),
				new SliderModel(Rule.MIN_VOLUME, 100, 5, 1, 100, initialVolume),
				value -> draft.text(Field.VOLUME).isBlank()
						? Component.translatable("audiozone.rule_editor.volume.not_set", inheritedText())
						: Component.literal((int) value + "%"),
				null,
				value -> {
					// Only the player's slider moves set a volume; showing the unchanged value must not.
					if (volumeField != null && !volumeField.isFocused() && !showingInherited) {
						volumeField.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("audiozone.rule_editor.volume.tip"));
		volumeField = Tooltips.describe(new AzTextField(font, VOLUME_FIELD_WIDTH, Component.translatable("audiozone.rule_editor.volume_typed")),
				Component.translatable("audiozone.rule_editor.volume_typed.tip"));
		volumeField.placeholder(Component.literal(Integer.toString(inheritedVolume)));
		volumeField.setMaxLength(3);
		volumeField.setValue(volumeText);
		volumeField.validator(text -> {
			setText(Field.VOLUME, text);
			return message(draft.error(Field.VOLUME));
		});
		volumeField.onChange(text -> {
			if (draft.error(Field.VOLUME) == null) {
				if (text.isBlank()) {
					showInheritedOnSlider();
				} else {
					volumeSlider.setTypedValue(Double.parseDouble(text.trim()));
				}
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
		return Component.translatable(preview.count() == 1 ? "audiozone.rule_editor.preview.one" : "audiozone.rule_editor.preview", preview.count());
	}

	private Component muteChoiceName(MuteChoice choice) {
		if (choice == MuteChoice.NOT_SET) {
			return Component.translatable("audiozone.rule_editor.mute.not_set",
					Component.translatable(inheritedMuted ? "audiozone.rule_editor.inherited.muted" : "audiozone.rule_editor.inherited.not_muted"));
		}
		return Component.translatable("audiozone.rule_editor.mute." + choice.name().toLowerCase(Locale.ROOT));
	}

	/** "100%" or "muted": what the sound gets now where this rule leaves loudness unset. */
	private Component inheritedText() {
		return inheritedMuted
				? Component.translatable("audiozone.rule_editor.inherited.muted")
				: Component.literal(inheritedVolume + "%");
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
		// The "unchanged" values follow the pattern (an exact ID can inherit from other rules).
		if (muteDropdown != null) {
			muteDropdown.setValue(muteDropdown.value());
		}
		if (volumeField != null) {
			volumeField.placeholder(Component.literal(Integer.toString(inheritedVolume)));
			if (draft.text(Field.VOLUME).isBlank()) {
				showInheritedOnSlider();
			}
		}
	}

	private void updatePreview() {
		preview = PatternPreview.of(draft.text(Field.PATTERN), KnownSounds.ids(), KnownSounds.seenExcluded());
		overridden = Component.empty();
		updateInherited();
		if (!draft.canSave()) {
			return;
		}
		Rule candidate = draft.build();
		Profile withCandidate = target.put(ProfileManager.profile(), candidate);
		Set<Rule> winners = new RuleResolver(target.rules(withCandidate)).overriddenBy(candidate, preview.matches());
		if (!winners.isEmpty()) {
			List<String> patterns = new ArrayList<>();
			for (Rule winner : winners) {
				patterns.add(winner.pattern().text());
			}
			overridden = Component.translatable("audiozone.rule_editor.overridden", String.join(", ", patterns));
		}
	}

	private void showInheritedOnSlider() {
		showingInherited = true;
		try {
			volumeSlider.setTypedValue(inheritedVolume);
		} finally {
			showingInherited = false;
		}
	}

	/** For an exact sound ID: what the list's other rules give it; for a wildcard, 100% and not muted. */
	private void updateInherited() {
		inheritedVolume = 100;
		inheritedMuted = false;
		String pattern = preview.normalizedPattern();
		if (pattern == null || pattern.contains("*")) {
			return;
		}
		String id = draft.id();
		List<Rule> others = new ArrayList<>();
		for (Rule rule : target.rules(ProfileManager.profile())) {
			if (!rule.id().equals(id)) {
				others.add(rule);
			}
		}
		audiozone.core.rules.RuleResolution resolution = new RuleResolver(others).resolve(pattern);
		inheritedMuted = resolution.muted();
		inheritedVolume = resolution.volumePercent();
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
		ProfileManager.update(profile -> target.put(profile, rule));
		return true;
	}
}
