package soundsculptor.mc;

import soundsculptor.core.editor.FieldError;
import soundsculptor.core.browser.LikeThis;
import soundsculptor.core.editor.PatternPreview;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundSearch;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.editor.EntryDraft.Field;
import soundsculptor.core.editor.SetDraft;
import soundsculptor.core.Message;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.ui.SliderModel;
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
 * The entry editor (PRD FR-4.3): one entry of a Sound Set being edited in the set editor. Target (a sound ID or a
 * group pattern) with a live "Matches N sounds" preview and the matching IDs, "Partly overridden by" a more specific
 * entry of the same set (FR-4.6), On/Off, Mute (Unchanged, Muted, Not muted) and Level 0–100% (FR-5.1–5.2). Save and
 * Cancel, with the unsaved-changes dialog (FR-12.4), a reset button per value (FR-12.6) and inline errors that disable
 * Save (FR-12.7). Save puts the entry into the set's draft only; nothing changes in game until the set editor saves.
 * Limit and Cue arrive with their milestones (M11, M9); an entry's existing limit and cue are kept.
 */
public final class EntryEditorScreen extends SculptScreen {
	private static final Identifier RESET_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/reset");
	private static final int ENABLED_WIDTH = 110;
	private static final int MUTE_WIDTH = 170;
	private static final int SLIDER_WIDTH = 200;
	private static final int VOLUME_FIELD_WIDTH = 50;
	private static final int PICK_WIDTH = 70;
	/** Where the slider rests while the entry leaves the level unchanged. */
	private static final int UNCHANGED_VOLUME = 100;

	/** Mute is "Unchanged" unless the entry sets it, so an entry can also say "Not muted" over a broader one. */
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
	private final boolean isNew;
	/** The set the entry belongs to; Save puts the entry into it. */
	private final SetDraft set;
	private final Component subtitle;
	/**
	 * For Edit… on a sound row and the trace: the place whose Quick changes the entry is saved into at once, since there
	 * is no set editor to save it; {@code null} when the entry belongs to a set being edited.
	 */
	private final PlaceRef quickPlace;
	private PatternPreview preview;
	private Component overridden = Component.empty();
	/** True while the slider is moved to its resting place for "Unchanged", so that move does not set a level. */
	private boolean showingUnchanged;

	public SculptTextField patternField;
	public SculptToggle enabledToggle;
	public SculptDropdown<MuteChoice> muteDropdown;
	public SculptSlider volumeSlider;
	public SculptTextField volumeField;
	public SculptList matchList;
	public SculptButton saveButton;

	private EntryEditorScreen(Screen parent, SetDraft set, EntryDraft draft, boolean isNew, Component subtitle, PlaceRef quickPlace) {
		super(Component.translatable(isNew ? "soundsculptor.rule_editor.title.new" : "soundsculptor.rule_editor.title.edit"), parent);
		this.isNew = isNew;
		this.set = set;
		this.draft = draft;
		this.subtitle = subtitle;
		this.quickPlace = quickPlace;
		updatePreview();
	}

	/** Opens the editor on an entry of the set being edited. */
	public static EntryEditorScreen edit(Screen parent, SetDraft set, String entryId) {
		SetEntry entry = set.entry(entryId);
		if (entry == null) {
			throw new IllegalArgumentException("the set has no entry " + entryId);
		}
		return new EntryEditorScreen(parent, set, EntryDraft.of(entry, false), false, setSubtitle(set), null);
	}

	/** Opens the editor for a new entry of the set being edited (Add sounds…, until the Sounds picker replaces it). */
	public static EntryEditorScreen add(Screen parent, SetDraft set) {
		return new EntryEditorScreen(parent, set, EntryDraft.blank(set.newEntryId(), false), true, setSubtitle(set), null);
	}

	private static Component setSubtitle(SetDraft set) {
		return Component.translatable("soundsculptor.set_editor.entry_subtitle", SoundSet.QUICK_ID.equals(set.id())
				? Component.translatable("soundsculptor.set_editor.quick.title") : Component.literal(set.name()));
	}

	/**
	 * Edit… on a sound row (FR-1.5, FR-3.7): opens the sound's single-sound entry in the place's Quick changes, or a new
	 * single-sound entry for it there. Save writes the entry into Quick changes, creating the set if needed; an existing
	 * entry for the sound is always reused, never duplicated.
	 */
	public static EntryEditorScreen forSound(Screen parent, PlaceRef where, String soundId) {
		SetEntry exact = QuickActions.quickEntry(ProfileManager.workspace(), where, soundId);
		if (exact != null) {
			return forQuickEntry(parent, where, exact.id());
		}
		SetDraft quick = SetEditorScreen.quickDraft(where);
		EntryEditorScreen screen = new EntryEditorScreen(parent, quick, EntryDraft.blank(quick.newEntryId(), false), true, placeSubtitle(where), where);
		screen.draft.set(Field.TARGET, soundId);
		screen.updatePreview();
		return screen;
	}

	/**
	 * An entry of the place's Quick changes, saved straight back into it: {@link #forSound} uses it when the sound
	 * already has its single-sound entry there. (The trace's "Open full editor" on a Quick changes line opens the place's
	 * Quick changes in the set editor instead, at that entry.) {@code null} if the place no longer has that entry.
	 */
	public static EntryEditorScreen forQuickEntry(Screen parent, PlaceRef where, String entryId) {
		SetDraft quick = SetEditorScreen.quickDraft(where);
		SetEntry entry = quick.entry(entryId);
		return entry == null ? null : new EntryEditorScreen(parent, quick, EntryDraft.of(entry, false), false, placeSubtitle(where), where);
	}

	private static Component placeSubtitle(PlaceRef where) {
		return Component.translatable("soundsculptor.entry_editor.quick_subtitle", SetDeleteDialog.placeName(ProfileManager.profile(), where));
	}

	/** The place whose Quick changes the entry is saved into, or {@code null} when it belongs to a set being edited. */
	public PlaceRef quickPlace() {
		return quickPlace;
	}

	public EntryDraft draft() {
		return draft;
	}

	/** The set the entry belongs to. */
	public SetDraft set() {
		return set;
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
		return subtitle;
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
		patternField.setValue(draft.text(Field.TARGET));
		patternField.validator(text -> {
			setText(Field.TARGET, text);
			// Plain words search for sounds (FR-4.8); they are not a pattern, so they show no pattern error.
			return isSearch(text) ? null : message(draft.error(Field.TARGET));
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
		body.row(patternField, resetButton(Field.TARGET, () -> patternField.setValue(draft.text(Field.TARGET))));
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
		double initialVolume = draft.error(Field.VOLUME) == null && !volumeText.isBlank() ? Double.parseDouble(volumeText.trim()) : UNCHANGED_VOLUME;
		volumeSlider = Tooltips.describe(new SculptSlider(SLIDER_WIDTH, Component.translatable("soundsculptor.rule_editor.volume"),
				new SliderModel(SetEntry.MIN_VOLUME, 100, 5, 1, 100, initialVolume),
				value -> draft.text(Field.VOLUME).isBlank()
						? Component.translatable("soundsculptor.rule_editor.volume.not_set")
						: Component.literal((int) value + "%"),
				null,
				value -> {
					// Only the player's slider moves set a level; resting the slider for "Unchanged" must not.
					if (volumeField != null && !volumeField.isFocused() && !showingUnchanged) {
						volumeField.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("soundsculptor.rule_editor.volume.tip"));
		volumeField = Tooltips.describe(new SculptTextField(font, VOLUME_FIELD_WIDTH, Component.translatable("soundsculptor.rule_editor.volume_typed")),
				Component.translatable("soundsculptor.rule_editor.volume_typed.tip"));
		volumeField.setMaxLength(3);
		volumeField.setValue(volumeText);
		volumeField.validator(text -> {
			setText(Field.VOLUME, text);
			return message(draft.error(Field.VOLUME));
		});
		volumeField.onChange(text -> {
			if (draft.error(Field.VOLUME) == null) {
				if (text.isBlank()) {
					showUnchangedOnSlider();
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
		if (draft.shownError(Field.TARGET) != null) {
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

	/** The preview line under the field ("Matches N sounds" or the search summary), for tests. */
	public Component previewLine() {
		return previewText();
	}

	private Component previewText() {
		if (searching()) {
			String words = draft.text(Field.TARGET).strip();
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
		return Component.translatable("soundsculptor.rule_editor.mute." + choice.name().toLowerCase(Locale.ROOT));
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
		if (volumeField != null && draft.text(Field.VOLUME).isBlank()) {
			showUnchangedOnSlider();
		}
	}

	private void updatePreview() {
		preview = PatternPreview.of(draft.text(Field.TARGET), KnownSounds.ids(), KnownSounds.seenExcluded());
		overridden = Component.empty();
		if (searching()) {
			if (matchList != null && searchEntries != null) {
				searchResults = SoundSearch.search(searchEntries, SearchQuery.parse(draft.text(Field.TARGET)), KnownSounds.seenExcluded());
			}
			return;
		}
		if (!draft.canSave()) {
			return;
		}
		// FR-4.6: overrides within this set only; the trace shows the ones between sets and places.
		SetEntry candidate = draft.build();
		List<SetEntry> entries = new ArrayList<>(set.entries());
		int at = indexOf(entries, candidate.id());
		if (at >= 0) {
			entries.set(at, candidate);
		} else {
			entries.add(candidate);
		}
		Set<SetEntry> winners = EntryResolver.of(entries).overriddenBy(candidate, preview.matches());
		if (!winners.isEmpty()) {
			List<String> patterns = new ArrayList<>();
			for (SetEntry winner : winners) {
				patterns.add(winner.pattern().text());
			}
			overridden = Component.translatable("soundsculptor.rule_editor.overridden", String.join(", ", patterns));
		}
	}

	private static int indexOf(List<SetEntry> entries, String id) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private void showUnchangedOnSlider() {
		showingUnchanged = true;
		try {
			volumeSlider.setTypedValue(UNCHANGED_VOLUME);
		} finally {
			showingUnchanged = false;
		}
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
		return isSearch(draft.text(Field.TARGET));
	}

	/** What the list shows: the search words, or the pattern being previewed. */
	private String listKey() {
		if (searching()) {
			return "search:" + draft.text(Field.TARGET).strip();
		}
		return preview == null ? null : preview.normalizedPattern();
	}

	private List<SoundEntry> searchResults = List.of();
	/** What the list was last filled for (see {@link #listKey}), so it is refilled only when that changes. */
	private String shownListKey;
	private List<SoundEntry> searchEntries;

	/** The sounds matching the words, with the Sounds tab's search syntax, each with This sound and Like this. */
	private void fillSearchResults() {
		if (searchEntries == null) {
			searchEntries = KnownSounds.entries();
		}
		searchResults = SoundSearch.search(searchEntries, SearchQuery.parse(draft.text(Field.TARGET)), KnownSounds.seenExcluded());
		List<SculptList.Row> rows = new ArrayList<>(searchResults.size());
		for (SoundEntry entry : searchResults) {
			String id = entry.id();
			String name = entry.subtitle() == null ? id : entry.subtitle();
			SculptButton exact = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.rule_editor.this_sound"), PICK_WIDTH,
					() -> usePattern(id)).narrationContext(() -> Component.literal(name)),
					Component.translatable("soundsculptor.rule_editor.this_sound.tip"));
			SculptButton[] like = new SculptButton[1];
			like[0] = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.rule_editor.like_this"), PICK_WIDTH,
					() -> showFamilies(id, like[0])).narrationContext(() -> Component.literal(name)),
					Component.translatable("soundsculptor.rule_editor.like_this.tip"));
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
		List<SculptButton> buttons = new ArrayList<>();
		int width = 0;
		for (LikeThis.Group group : LikeThis.groups(soundId, KnownSounds.ids())) {
			String family = group.pattern();
			Component label = ProfileManager.component(group.label());
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
		SetEntry entry = draft.build();
		Message full = set.canPut(entry);
		if (full == null && quickPlace != null) {
			Workspace ws = ProfileManager.workspace();
			full = QuickActions.canPutQuickEntry(ws, quickPlace, withFreeId(ws, entry));
		}
		if (full != null) {
			// The set is full, or the zone is gone: say so and keep the editor open, so nothing typed is lost.
			showToast(ProfileManager.component(full), null);
			return false;
		}
		set.putEntry(entry);
		if (quickPlace != null) {
			// Only this entry is written, so changes made to the place's Quick changes since the editor opened are kept.
			ProfileManager.change(ws -> QuickActions.putQuickEntry(ws, quickPlace, withFreeId(ws, entry)));
		}
		return true;
	}

	/** A new entry keeps its ID unless another entry took it in Quick changes since the editor opened. */
	private SetEntry withFreeId(Workspace ws, SetEntry entry) {
		if (!isNew) {
			return entry;
		}
		PlaceSets place = ws.profile().place(quickPlace);
		SoundSet live = place == null ? null : place.quickChanges();
		return live == null || live.entry(entry.id()) == null ? entry : entry.withId(live.newEntryId());
	}
}
