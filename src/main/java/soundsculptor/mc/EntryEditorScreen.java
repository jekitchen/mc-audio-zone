package soundsculptor.mc;

import soundsculptor.core.editor.FieldError;
import soundsculptor.core.editor.PatternPreview;
import soundsculptor.core.editor.PatternSuggestions;
import soundsculptor.core.sets.SoundPattern;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundSearch;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.editor.EntryDraft.Field;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.ui.SliderModel;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;
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
public final class EntryEditorScreen extends SculptScreen {
	private static final Identifier RESET_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/reset");
	private static final int ENABLED_WIDTH = 110;
	private static final int MUTE_WIDTH = 170;
	private static final int SLIDER_WIDTH = 200;
	private static final int VOLUME_FIELD_WIDTH = 50;
	private static final int PICK_WIDTH = 70;

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

	private final EntryDraft draft;
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

	public SculptTextField patternField;
	public SculptToggle enabledToggle;
	public SculptDropdown<MuteChoice> muteDropdown;
	public SculptSlider volumeSlider;
	public SculptTextField volumeField;
	public SculptList matchList;
	public SculptButton saveButton;

	private EntryEditorScreen(Screen parent, RuleTarget target, EntryDraft draft, boolean isNew) {
		super(Component.translatable(isNew ? "soundsculptor.rule_editor.title.new" : "soundsculptor.rule_editor.title.edit"), parent);
		this.target = target;
		this.draft = draft;
		updatePreview();
	}

	/** Opens the editor for a new rule. */
	public static EntryEditorScreen create(Screen parent) {
		return create(parent, RuleTarget.GLOBAL);
	}

	/** Opens the editor for a new rule in a zone's list or the global list (FR-8.6). */
	public static EntryEditorScreen create(Screen parent, RuleTarget target) {
		return new EntryEditorScreen(parent, target, EntryDraft.blank(target.newRuleId(ProfileManager.profile()), false), true);
	}

	/** Opens the editor for an existing rule. */
	public static EntryEditorScreen edit(Screen parent, SetEntry rule) {
		return edit(parent, RuleTarget.GLOBAL, rule);
	}

	/** Opens the editor for an existing rule of a zone's list or the global list. */
	public static EntryEditorScreen edit(Screen parent, RuleTarget target, SetEntry rule) {
		return new EntryEditorScreen(parent, target, EntryDraft.of(rule, false), false);
	}

	/** Opens the editor on a sound's exact-ID rule, or a new exact-ID rule for it. */
	public static EntryEditorScreen forSound(Screen parent, String soundId) {
		return forSound(parent, ApplyTarget.EVERYWHERE, soundId);
	}

	/**
	 * Opens the editor on the sound's exact-ID rule in the list "Apply to" chose (FR-1.5, FR-3.7), or a new exact-ID
	 * rule for it there.
	 */
	public static EntryEditorScreen forSound(Screen parent, ApplyTarget applyTo, String soundId) {
		RuleTarget target = applyTo.isEverywhere() ? RuleTarget.GLOBAL : RuleTarget.zone(applyTo.zoneId());
		SetEntry exact = QuickActions.exactRule(ProfileManager.profile(), applyTo, soundId);
		if (exact != null) {
			return edit(parent, target, exact);
		}
		EntryEditorScreen screen = create(parent, target);
		screen.draft.set(Field.PATTERN, soundId);
		screen.updatePreview();
		return screen;
	}

	public EntryDraft draft() {
		return draft;
	}

	/** The list the rule is saved in: the global rules or a zone's. */
	public RuleTarget target() {
		return target;
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
			return Component.translatable("soundsculptor.rule_editor.zone", name);
		}
		return Component.translatable("soundsculptor.header.profile", ProfileManager.profile().name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
		saveButton = SculptButton.text(Component.translatable("soundsculptor.ui.save"), this::save);
		saveButton.active = draft.canSave();
		footer.add(saveButton);
	}

	@Override
	protected void buildTab(int index, Body body) {
		int resetWidth = Theme.ICON_BUTTON_SIZE + Theme.GAP;

		patternField = Tooltips.describe(new SculptTextField(font, body.width() - resetWidth,
				Component.translatable("soundsculptor.rule_editor.pattern")), Component.translatable("soundsculptor.rule_editor.pattern.tip"));
		patternField.placeholder(Component.translatable("soundsculptor.rule_editor.pattern.hint"));
		patternField.setMaxLength(200);
		patternField.setValue(draft.text(Field.PATTERN));
		patternField.validator(text -> {
			setText(Field.PATTERN, text);
			// Plain words search for sounds (FR-4.8); they are not a pattern, so they show no pattern error.
			return isSearch(text) ? null : message(draft.error(Field.PATTERN));
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

		enabledToggle = Tooltips.describe(new SculptToggle(ENABLED_WIDTH, Component.translatable("soundsculptor.rule_editor.enabled"),
				draft.enabled(), value -> {
					draft.setEnabled(value);
					changed();
				}), Component.translatable("soundsculptor.rule_editor.enabled.tip"));
		muteDropdown = Tooltips.describe(new SculptDropdown<>(MUTE_WIDTH, Component.translatable("soundsculptor.rule_editor.mute"),
				List.of(MuteChoice.values()),
				this::muteChoiceName,
				MuteChoice.of(draft.mute()), this::setMute), Component.translatable("soundsculptor.rule_editor.mute.tip"));
		body.row(enabledToggle, muteDropdown, resetButton(Field.MUTE, () -> muteDropdown.setValue(MuteChoice.of(draft.mute()))));

		String volumeText = draft.text(Field.VOLUME);
		double initialVolume = draft.error(Field.VOLUME) == null && !volumeText.isBlank() ? Double.parseDouble(volumeText.trim()) : inheritedVolume;
		volumeSlider = Tooltips.describe(new SculptSlider(SLIDER_WIDTH, Component.translatable("soundsculptor.rule_editor.volume"),
				new SliderModel(SetEntry.MIN_VOLUME, 100, 5, 1, 100, initialVolume),
				value -> draft.text(Field.VOLUME).isBlank()
						? Component.translatable("soundsculptor.rule_editor.volume.not_set", inheritedText())
						: Component.literal((int) value + "%"),
				null,
				value -> {
					// Only the player's slider moves set a volume; showing the unchanged value must not.
					if (volumeField != null && !volumeField.isFocused() && !showingInherited) {
						volumeField.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("soundsculptor.rule_editor.volume.tip"));
		volumeField = Tooltips.describe(new SculptTextField(font, VOLUME_FIELD_WIDTH, Component.translatable("soundsculptor.rule_editor.volume_typed")),
				Component.translatable("soundsculptor.rule_editor.volume_typed.tip"));
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

		matchList = new SculptList(body.width(), SculptList.ROW_HEIGHT, Component.translatable("soundsculptor.rule_editor.matches"));
		matchList.setEmptyText(() -> Component.translatable(preview.error() != null
				? "soundsculptor.rule_editor.matches.invalid" : "soundsculptor.rule_editor.matches.none"));
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

	private SculptButton resetButton(Field field, Runnable syncWidget) {
		// The icon button's tooltip is its label, e.g. "Reset volume".
		return SculptButton.icon(RESET_ICON, Component.translatable("soundsculptor.rule_editor.reset." + field.name().toLowerCase(Locale.ROOT)),
				() -> {
					draft.resetToDefault(field);
					syncWidget.run();
					changed();
				});
	}

	private Component previewText() {
		if (searching()) {
			String words = draft.text(Field.PATTERN).strip();
			return Component.translatable(searchResults.isEmpty() ? "soundsculptor.rule_editor.search.none"
					: searchResults.size() == 1 ? "soundsculptor.rule_editor.search.one" : "soundsculptor.rule_editor.search.many",
					searchResults.size(), words);
		}
		if (preview.error() != null) {
			return Component.empty();
		}
		return Component.translatable(preview.count() == 1 ? "soundsculptor.rule_editor.preview.one" : "soundsculptor.rule_editor.preview", preview.count());
	}

	private Component muteChoiceName(MuteChoice choice) {
		if (choice == MuteChoice.NOT_SET) {
			return Component.translatable("soundsculptor.rule_editor.mute.not_set",
					Component.translatable(inheritedMuted ? "soundsculptor.rule_editor.inherited.muted" : "soundsculptor.rule_editor.inherited.not_muted"));
		}
		return Component.translatable("soundsculptor.rule_editor.mute." + choice.name().toLowerCase(Locale.ROOT));
	}

	/** "100%" or "muted": what the sound gets now where this rule leaves loudness unset. */
	private Component inheritedText() {
		return inheritedMuted
				? Component.translatable("soundsculptor.rule_editor.inherited.muted")
				: Component.literal(inheritedVolume + "%");
	}

	private static Component message(FieldError error) {
		return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
	}

	/** Any value changed: refresh the preview, the overridden line, the match list and Save. */
	private void changed() {
		updatePreview();
		if (matchList != null && !java.util.Objects.equals(shownListKey, listKey())) {
			fillMatches();
		}
		if (saveButton != null) {
			saveButton.active = draft.canSave() && !searching();
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
		if (searching()) {
			inheritedVolume = 100;
			inheritedMuted = false;
			if (matchList != null && searchEntries != null) {
				searchResults = SoundSearch.search(searchEntries, SearchQuery.parse(draft.text(Field.PATTERN)), KnownSounds.seenExcluded());
			}
			return;
		}
		updateInherited();
		if (!draft.canSave()) {
			return;
		}
		SetEntry candidate = draft.build();
		Profile withCandidate = target.put(ProfileManager.profile(), candidate);
		Set<SetEntry> winners = new EntryResolver(target.rules(withCandidate)).overriddenBy(candidate, preview.matches());
		if (!winners.isEmpty()) {
			List<String> patterns = new ArrayList<>();
			for (SetEntry winner : winners) {
				patterns.add(winner.pattern().text());
			}
			overridden = Component.translatable("soundsculptor.rule_editor.overridden", String.join(", ", patterns));
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
		List<SetEntry> others = new ArrayList<>();
		for (SetEntry rule : target.rules(ProfileManager.profile())) {
			if (!rule.id().equals(id)) {
				others.add(rule);
			}
		}
		soundsculptor.core.sets.EntryResolution resolution = new EntryResolver(others).resolve(pattern);
		inheritedMuted = resolution.muted();
		inheritedVolume = resolution.volumePercent();
	}

	private void fillMatches() {
		shownListKey = listKey();
		if (searching()) {
			fillSearchResults();
			return;
		}
		List<SculptList.Row> rows = new ArrayList<>(preview.count());
		for (String id : preview.matches()) {
			rows.add(new SculptList.Row(id, List.of(new SculptList.Row.Column(() -> id, 0)), List.of()));
		}
		matchList.setRows(rows);
	}

	// ---- Plain-word search (FR-4.8) ----

	/**
	 * Whether the pattern field holds plain words to search for rather than a pattern: text with neither {@code :} nor
	 * {@code *}. Such text could never be a useful pattern (FR-4.1: a pattern must match the whole ID), so nothing is
	 * lost; {@code minecraft:entity.villager.*} or {@code *villager*} still work as patterns.
	 */
	static boolean isSearch(String text) {
		String t = text.strip();
		return !t.isEmpty() && t.indexOf(':') < 0 && t.indexOf('*') < 0;
	}

	public boolean searching() {
		return isSearch(draft.text(Field.PATTERN));
	}

	/** What the list shows: the search words, or the pattern being previewed. */
	private String listKey() {
		if (searching()) {
			return "search:" + draft.text(Field.PATTERN).strip();
		}
		return preview == null ? null : preview.normalizedPattern();
	}

	private List<SoundEntry> searchResults = List.of();
	/** What the list was last filled for (see {@link #listKey}), so it is refilled only when that changes. */
	private String shownListKey;
	private List<SoundEntry> searchEntries;

	/** The sounds matching the words, with the Search tab's syntax, each with This sound and Like this. */
	private void fillSearchResults() {
		if (searchEntries == null) {
			searchEntries = KnownSounds.entries();
		}
		searchResults = SoundSearch.search(searchEntries, SearchQuery.parse(draft.text(Field.PATTERN)), KnownSounds.seenExcluded());
		List<SculptList.Row> rows = new ArrayList<>(searchResults.size());
		for (SoundEntry entry : searchResults) {
			String id = entry.id();
			String name = entry.subtitle() == null ? id : entry.subtitle();
			SculptButton exact = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.rule_editor.this_sound"), PICK_WIDTH,
					() -> usePattern(id)), Component.translatable("soundsculptor.rule_editor.this_sound.tip"));
			SculptButton[] like = new SculptButton[1];
			like[0] = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.rule_editor.like_this"), PICK_WIDTH,
					() -> showFamilies(id, like[0])), Component.translatable("soundsculptor.rule_editor.like_this.tip"));
			rows.add(new SculptList.Row(id, List.of(new SculptList.Row.Column(() -> name, 0, 0), new SculptList.Row.Column(() -> id, 0, 1)),
					List.of(exact, like[0])));
		}
		matchList.setRows(rows);
	}

	/** Puts a pattern into the field; the editor then previews it as usual. */
	public void usePattern(String pattern) {
		closeOverlay();
		patternField.setValue(pattern);
		setFocused(patternField);
	}

	/** "Like this": the sound's families, closest first (FR-1.5 "Make pattern…"), each with how many sounds it matches. */
	public void showFamilies(String soundId, AbstractWidget below) {
		List<String> families = PatternSuggestions.suggest(soundId);
		List<SculptButton> buttons = new ArrayList<>();
		int width = 0;
		for (String family : families) {
			SoundPattern pattern = SoundPattern.of(family);
			int count = 0;
			for (String id : KnownSounds.ids()) {
				if (pattern.matches(id)) {
					count++;
				}
			}
			Component label = Component.translatable("soundsculptor.rule_editor.family", family, count);
			width = Math.max(width, font.width(label) + 16);
			buttons.add(SculptButton.text(label, () -> usePattern(family)));
		}
		width = Math.min(width, this.width - 2 * Theme.PADDING);
		int height = buttons.size() * (Theme.WIDGET_HEIGHT + Theme.GAP) + Theme.GAP;
		int x = Math.max(Theme.PADDING, Math.min(below.getRight() - width, this.width - width - Theme.PADDING));
		int y = below.getBottom() + 2;
		if (y + height > this.height - Theme.PADDING) {
			y = Math.max(Theme.PADDING, below.getY() - height - 2);
		}
		for (int i = 0; i < buttons.size(); i++) {
			SculptButton button = buttons.get(i);
			button.setWidth(width - 2 * Theme.GAP);
			button.setPosition(x + Theme.GAP, y + Theme.GAP + i * (Theme.WIDGET_HEIGHT + Theme.GAP));
		}
		openOverlay(x, y, width, height, buttons, buttons.isEmpty() ? null : buttons.get(0), null);
	}

	/** The family buttons shown by {@link #showFamilies}, for tests. */
	public List<AbstractWidget> familyButtons() {
		return overlayWidgets();
	}

	/** The sounds found for the words in the field, for tests. */
	public List<SoundEntry> searchResults() {
		return searchResults;
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
		if (searching()) {
			// Words are a search, not a pattern: pick a sound or family first.
			return false;
		}
		if (!draft.attemptSave()) {
			patternField.revealError();
			volumeField.revealError();
			return false;
		}
		SetEntry rule = draft.build();
		ProfileManager.update(profile -> target.put(profile, rule));
		return true;
	}
}
