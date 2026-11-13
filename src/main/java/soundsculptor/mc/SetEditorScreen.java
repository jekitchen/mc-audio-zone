package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import soundsculptor.core.Message;
import soundsculptor.core.Translator;
import soundsculptor.core.editor.SetDraft;
import soundsculptor.core.lists.EntrySummary;
import soundsculptor.core.lists.SummaryPart;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SetUsage;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * The set editor (PRD FR-4.3, FR-4.9): a library Sound Set's name, its entries as a list (each row: the sound's name
 * or the group's pattern, a summary such as "✖ Muted", and On/Off, Edit, Move up, Move down and Remove), Add sounds…
 * (the {@link SoundPickerScreen}), Type a pattern… (the entry editor for a new entry) and the {@link AttachedToPanel}.
 * Opened on a place's Quick changes ({@link #forQuickChanges}) it has the entries only: no name and no Attached to
 * panel.
 *
 * <p>Everything is a draft until Save (FR-12.4): entry edits, the name, and Attach and Detach in the panel. Save applies
 * them together in one {@link ProfileManager#change}; Cancel or Esc with changes asks Save, Discard or Keep editing.
 * Because sets are linked, a changed set used in more than one place (here or in other worlds) first asks to confirm
 * with the list of places ("This changes Main base, Mob farm and 1 other world"). An editor has no Undo toast.
 */
public final class SetEditorScreen extends SculptScreen {
	private static final int NAME_WIDTH = 220;
	private static final int STATUS_WIDTH = 110;
	private static final int ON_OFF_WIDTH = 30;
	/** An entry row's buttons: On/Off, then Edit. */
	private static final int EDIT_BUTTON = 1;
	/** How often the Attached to panel checks whether the other worlds have been counted. */
	private static final int USAGE_REFRESH_TICKS = 5;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier UP_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/up");
	private static final Identifier DOWN_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/down");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/delete");
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();
	/** Summary parts the status column already shows. */
	private static final Set<String> LOUDNESS_PARTS = Set.of(EntrySummary.DISABLED, EntrySummary.MUTED, EntrySummary.NOT_MUTED,
			EntrySummary.VOLUME);

	private final SetDraft draft;
	/** The place whose Quick changes this edits, or {@code null} for a library set. */
	private final PlaceRef quickPlace;
	/** Where the library set is attached in this world; {@code null} for Quick changes. */
	private final AttachedToPanel attached;
	private int ticks;
	/** The entry to focus when the editor opens, or {@code null}; used once. */
	private String openAt;

	public SculptTextField nameField;
	/** Add sounds…: the Sounds picker (FR-1.7). */
	public SculptButton addButton;
	/** Type a pattern…: the entry editor for a new entry, for a typed group pattern or a plain-word search (FR-4.8). */
	public SculptButton typeButton;
	public SculptList entryList;
	public SculptButton saveButton;

	private SetEditorScreen(Screen parent, SetDraft draft, PlaceRef quickPlace, Component title) {
		super(title, parent);
		this.draft = draft;
		this.quickPlace = quickPlace;
		this.attached = quickPlace == null ? new AttachedToPanel(this, draft.id()) : null;
	}

	/**
	 * Opens the editor on a library set.
	 *
	 * @throws IllegalArgumentException if the library has no such set
	 */
	public static SetEditorScreen edit(Screen parent, String setId) {
		return edit(parent, setId, null);
	}

	/**
	 * Opens the editor on a library set at one of its entries: that entry's row is scrolled into view with its Edit button
	 * focused (the sound trace's "Open full editor", FR-14.4). An entry the set no longer has opens the editor as usual.
	 *
	 * @param entryId the entry to open at, or {@code null}
	 * @throws IllegalArgumentException if the library has no such set
	 */
	public static SetEditorScreen edit(Screen parent, String setId, String entryId) {
		SoundSet set = ProfileManager.library().get(setId);
		if (set == null) {
			throw new IllegalArgumentException("the library has no Sound Set " + setId);
		}
		return new SetEditorScreen(parent, SetDraft.of(set), null, Component.translatable("soundsculptor.set_editor.title")).openAt(entryId);
	}

	/** Opens the editor on a place's Quick changes (FR-4.10); Save removes the set if it was left empty. */
	public static SetEditorScreen forQuickChanges(Screen parent, PlaceRef where) {
		return forQuickChanges(parent, where, null);
	}

	/** {@link #forQuickChanges(Screen, PlaceRef)} at one of its entries, as {@link #edit(Screen, String, String)} does. */
	public static SetEditorScreen forQuickChanges(Screen parent, PlaceRef where, String entryId) {
		return new SetEditorScreen(parent, quickDraft(where), where, Component.translatable("soundsculptor.set_editor.quick.title")).openAt(entryId);
	}

	private SetEditorScreen openAt(String entryId) {
		this.openAt = entryId;
		return this;
	}

	/** The place's Quick changes as a draft; an empty one if it has none yet. */
	static SetDraft quickDraft(PlaceRef where) {
		PlaceSets sets = ProfileManager.profile().place(where);
		SoundSet quick = sets == null ? null : sets.quickChanges();
		return quick == null ? SetDraft.blank(SoundSet.QUICK_ID, "") : SetDraft.of(quick);
	}

	/**
	 * The profile with the place's Quick changes replaced by {@code quick}'s entries, pinned first; no entries removes
	 * the set. Unchanged for a zone the profile no longer has.
	 */
	private static Profile withQuickChanges(Profile profile, PlaceRef where, SoundSet quick) {
		PlaceSets sets = profile.place(where);
		return sets == null ? profile : profile.withPlace(where, sets.withQuickEntries(quick.entries()));
	}

	public SetDraft draft() {
		return draft;
	}

	/** The Attached to panel, or {@code null} when editing Quick changes. */
	public AttachedToPanel attachedPanel() {
		return attached;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		if (quickPlace != null) {
			return Component.translatable("soundsculptor.set_editor.quick.subtitle", SetDeleteDialog.placeName(ProfileManager.profile(), quickPlace));
		}
		SoundSet saved = ProfileManager.library().get(draft.id());
		return Component.literal(saved == null ? draft.name() : saved.name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
		saveButton = SculptButton.text(Component.translatable("soundsculptor.ui.save"), this::save);
		saveButton.active = nameProblem(draft.name()) == null;
		footer.add(saveButton);
	}

	@Override
	protected void buildTab(int index, Body body) {
		addButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.set_editor.add"), this::addSounds),
				Component.translatable("soundsculptor.set_editor.add.tip"));
		typeButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.set_editor.type"), this::typePattern),
				Component.translatable("soundsculptor.set_editor.type.tip"));
		entryList = new SculptList(body.width(), SculptList.ROW_HEIGHT, Component.translatable("soundsculptor.set_editor.entries"));
		entryList.setEmptyText(() -> Component.translatable("soundsculptor.set_editor.entries.empty"));
		if (attached == null) {
			body.row(addButton, typeButton);
			body.label(() -> Component.translatable("soundsculptor.set_editor.quick.hint"));
			body.fill(entryList);
		} else {
			nameField = Tooltips.describe(new SculptTextField(font,
					Math.min(NAME_WIDTH, body.width() - addButton.getWidth() - typeButton.getWidth() - 2 * Theme.GAP),
					Component.translatable("soundsculptor.sets.name")), Component.translatable("soundsculptor.sets.name.tip"));
			nameField.setMaxLength(SoundSet.MAX_NAME_LENGTH + 16);
			nameField.setValue(draft.name());
			nameField.validator(this::nameProblem);
			nameField.onChange(text -> {
				draft.setName(text);
				if (saveButton != null) {
					saveButton.active = nameProblem(text) == null;
				}
			});
			body.row(nameField, addButton, typeButton);
			// The entries get three fifths of the room the Attached to panel leaves; the panel's list takes the rest.
			int panelFixed = AttachedToPanel.fixedHeight(font);
			entryList.setHeight(Math.max(SculptList.ROW_HEIGHT, (body.remaining() - panelFixed - Theme.GAP) * 3 / 5));
			body.row(entryList);
			attached.build(body);
		}
		refreshEntries();
	}

	/** Opened at an entry: focus its Edit button, once; afterwards (e.g. back from the entry editor) as usual. */
	@Override
	protected void setInitialFocus() {
		SculptList.Row row = openAt == null || entryList == null ? null : entryList.findRow(openAt);
		openAt = null;
		if (row != null) {
			focusPath(entryList.revealRow(row, EDIT_BUTTON));
			return;
		}
		super.setInitialFocus();
	}

	@Override
	public void tick() {
		super.tick();
		if (attached != null && ++ticks % USAGE_REFRESH_TICKS == 0) {
			attached.refreshIfUsageChanged();
		}
	}

	/** Rebuilds the entry rows from the draft. The list keeps the focused row and button. */
	public void refreshEntries() {
		if (entryList == null) {
			return;
		}
		List<String> known = KnownSounds.ids();
		List<SetEntry> entries = draft.entries();
		List<SculptList.Row> rows = new ArrayList<>(entries.size());
		for (int i = 0; i < entries.size(); i++) {
			SetEntry entry = entries.get(i);
			String id = entry.id();
			String target = entry.pattern().text();
			String title = entry.pattern().isExact() ? KnownSounds.name(target) : target;
			String status = status(entry);
			String details = entry.pattern().isExact() ? join(target, details(entry, known)) : details(entry, known);
			List<SculptButton> buttons = new ArrayList<>();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(entry.enabled()), ON_OFF_WIDTH,
					() -> setEntryEnabled(id, !entry.enabled())).narrationContext(() -> Component.translatable("soundsculptor.set_editor.entry")),
					Component.translatable(entry.enabled() ? "soundsculptor.set_editor.turn_off" : "soundsculptor.set_editor.turn_on")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.set_editor.edit"), () -> editEntry(id)));
			SculptButton up = SculptButton.icon(UP_ICON, Component.translatable("soundsculptor.set_editor.move_up"), () -> moveEntry(id, -1));
			up.active = i > 0;
			buttons.add(up);
			SculptButton down = SculptButton.icon(DOWN_ICON, Component.translatable("soundsculptor.set_editor.move_down"), () -> moveEntry(id, 1));
			down.active = i < entries.size() - 1;
			buttons.add(down);
			buttons.add(SculptButton.icon(DELETE_ICON, Component.translatable("soundsculptor.set_editor.remove"), () -> removeEntry(id)));
			rows.add(new SculptList.Row(id, List.of(
					new SculptList.Row.Column(() -> title, 0, 0),
					new SculptList.Row.Column(() -> status, STATUS_WIDTH, 0),
					new SculptList.Row.Column(() -> details, 0, 1)), buttons));
		}
		entryList.setRows(rows);
	}

	private static String join(String a, String b) {
		return b.isEmpty() ? a : a + " · " + b;
	}

	/**
	 * What the entry does to loudness, in the same style as the Sounds tab's status: "✖ Muted", "▶ Level 30%",
	 * "▶ Not muted", or "○ Off" for an entry that is turned off. Empty if it does not set loudness.
	 */
	static String status(SetEntry entry) {
		if (!entry.enabled()) {
			return Component.translatable("soundsculptor.set_editor.status.off").getString();
		}
		if (Boolean.TRUE.equals(entry.mute())) {
			return Component.translatable("soundsculptor.set_editor.status.muted").getString();
		}
		if (entry.volume() != null) {
			return Component.translatable("soundsculptor.set_editor.status.volume", entry.volume()).getString();
		}
		if (Boolean.FALSE.equals(entry.mute())) {
			return Component.translatable("soundsculptor.set_editor.status.not_muted").getString();
		}
		return "";
	}

	/**
	 * The second line: the entry's other settings (limit, cue, or "Sets nothing yet"), then for a group how many known
	 * sounds it matches, so a typo shows up as "No known sound matches" (FR-4.3). A single-sound entry always matches its
	 * one sound, so it has no count.
	 */
	static String details(SetEntry entry, List<String> known) {
		List<SummaryPart> parts = new ArrayList<>();
		for (SummaryPart part : EntrySummary.parts(entry)) {
			if (!LOUDNESS_PARTS.contains(part.key())) {
				parts.add(part);
			}
		}
		if (!entry.pattern().isExact()) {
			int count = EntrySummary.matchCount(entry, known);
			parts.add(SummaryPart.of(count == 0 ? "soundsculptor.set_editor.matches.none"
					: count == 1 ? "soundsculptor.set_editor.matches.one" : "soundsculptor.set_editor.matches", Integer.toString(count)));
		}
		return EntrySummary.join(parts, TRANSLATOR);
	}

	// ---- Entry actions: they change the draft only ----

	/** Add sounds…: the Sounds picker, which adds its picks to the draft (FR-1.7); refused when the set is full. */
	public void addSounds() {
		if (!refuseWhenFull()) {
			minecraft.gui.setScreen(new SoundPickerScreen(this, draft));
		}
	}

	/** Type a pattern…: the entry editor for a new entry (FR-4.8); refused when the set is full. */
	public void typePattern() {
		if (!refuseWhenFull()) {
			minecraft.gui.setScreen(EntryEditorScreen.add(this, draft));
		}
	}

	/** Shows the entry limit and returns true when the draft has no room for another entry. */
	private boolean refuseWhenFull() {
		Message full = draft.canAdd();
		if (full != null) {
			showToast(ProfileManager.component(full), null);
		}
		return full != null;
	}

	public void editEntry(String entryId) {
		if (draft.entry(entryId) != null) {
			minecraft.gui.setScreen(EntryEditorScreen.edit(this, draft, entryId));
		}
	}

	/** FR-4.1: an entry can be turned off without deleting it; the effect stops once the set is saved. */
	public void setEntryEnabled(String entryId, boolean enabled) {
		draft.setEntryEnabled(entryId, enabled);
		refreshEntries();
	}

	public void moveEntry(String entryId, int delta) {
		draft.moveEntry(entryId, delta);
		refreshEntries();
	}

	public void removeEntry(String entryId) {
		draft.removeEntry(entryId);
		refreshEntries();
	}

	// ---- Save, Cancel ----

	/** The error under the name field, or {@code null}; Quick changes has no name. */
	private Component nameProblem(String name) {
		if (attached == null) {
			return null;
		}
		Message problem = LibraryOps.canRename(ProfileManager.workspace(), draft.id(), name);
		return problem == null ? null : ProfileManager.component(problem);
	}

	public void save() {
		if (saveChanges()) {
			closeNow();
		}
	}

	@Override
	protected boolean hasUnsavedChanges() {
		return draft.changed() || (attached != null && attached.hasChanges());
	}

	/**
	 * Saves at once, or, for a changed set used in more than one place, first shows the confirmation with those places
	 * (FR-4.9) and returns false: the confirmation's Save then saves and closes.
	 */
	@Override
	protected boolean saveChanges() {
		if (nameProblem(draft.name()) != null) {
			nameField.revealError();
			return false;
		}
		Component linked = attached != null && draft.changed() ? linkedConfirmation() : null;
		if (linked != null) {
			minecraft.gui.setScreen(linkedSaveDialog(this, draft.build().name(), linked, () -> {
				commit();
				closeNow();
			}));
			return false;
		}
		commit();
		return true;
	}

	/**
	 * The confirmation before saving a set used in more than one place (FR-4.9): Save and Keep editing, Save focused, Esc
	 * keeps editing. A long list of places scrolls inside it.
	 *
	 * @param message the places, from {@link #linkedMessage}
	 */
	public static SculptDialog linkedSaveDialog(Screen parent, String setName, Component message, Runnable save) {
		return new SculptDialog(parent, Component.translatable("soundsculptor.set_editor.confirm.title", setName), message,
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.ui.save"), save),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.keep_editing"), () -> { })),
				0, 1);
	}

	/**
	 * The confirmation text when the set may be used in more than one place after this save (this world's places, as
	 * the panel will leave them, plus the places in other worlds), else {@code null}.
	 */
	private Component linkedConfirmation() {
		Profile profile = ProfileManager.profile();
		List<String> here = new ArrayList<>();
		for (PlaceRef where : attached.places()) {
			here.add(SetDeleteDialog.placeName(profile, where));
		}
		SetUsage usage = SetUsageIndex.current();
		String key = ProfileManager.key();
		int otherPlaces = usage == null ? 0 : usage.otherPlaceCount(draft.id(), key);
		if (!confirms(here.size(), otherPlaces, usage != null)) {
			return null;
		}
		return linkedMessage(here, usage == null ? 0 : usage.otherCount(draft.id(), key), usage != null);
	}

	/**
	 * Whether Save on a changed set must confirm first (FR-4.9): it is used in more than one place, counting this world's
	 * places and the other worlds' places. While the other worlds are still being counted nobody can tell that they don't
	 * use it, so Save always confirms then, whether or not the set is used here.
	 */
	public static boolean confirms(int placesHere, int otherPlaces, boolean othersCounted) {
		return !othersCounted || placesHere + otherPlaces > 1;
	}

	/**
	 * "This changes Main base, Mob farm and 1 other world." and, while the other worlds are still being counted, a
	 * line saying any of them that use the set change too. With no place to name (none here, and the other worlds not
	 * counted yet) the first line says the set is not attached in this world.
	 *
	 * @param placesHere  the names of this world's places that use the set
	 * @param otherWorlds how many other worlds or servers use it
	 */
	public static Component linkedMessage(List<String> placesHere, int otherWorlds, boolean othersCounted) {
		String list = placeList(placesHere, otherWorlds);
		MutableComponent text = list.isEmpty() ? Component.translatable("soundsculptor.set_editor.confirm.not_here")
				: Component.translatable("soundsculptor.set_editor.confirm.message", list);
		if (!othersCounted) {
			text.append("\n").append(Component.translatable("soundsculptor.set_editor.confirm.counting"));
		}
		return text;
	}

	/**
	 * "Main base, Mob farm and 1 other world": this world's places by name, then how many other worlds or servers use the
	 * set; {@code ""} when there are none. Also the sound trace's set-line tooltip (FR-14.4).
	 */
	static String placeList(List<String> placesHere, int otherWorlds) {
		List<String> parts = new ArrayList<>(placesHere);
		if (otherWorlds > 0) {
			parts.add(Component.translatable(otherWorlds == 1 ? "soundsculptor.set_editor.confirm.other_worlds.one"
					: "soundsculptor.set_editor.confirm.other_worlds", otherWorlds).getString());
		}
		if (parts.size() <= 1) {
			return parts.isEmpty() ? "" : parts.get(0);
		}
		return Component.translatable("soundsculptor.set_editor.confirm.and", String.join(", ", parts.subList(0, parts.size() - 1)),
				parts.get(parts.size() - 1)).getString();
	}

	/**
	 * Writes the set, and the panel's Attach and Detach, in one change. The set is written only if the draft changed:
	 * otherwise the draft is still the copy taken when the editor opened, and writing it would undo any change made to
	 * the set since (for example by a quick action).
	 */
	private void commit() {
		SoundSet built = draft.build();
		boolean setChanged = draft.changed();
		if (quickPlace != null) {
			if (setChanged) {
				ProfileManager.update(profile -> withQuickChanges(profile, quickPlace, built));
			}
			return;
		}
		ProfileManager.change(ws -> {
			SetLibrary library = ws.library();
			SoundSet live = library.get(built.id());
			if (live == null) {
				// Deleted meanwhile: nothing to save the edits into.
				return ws;
			}
			SoundSet saved = built.withName(library.uniqueName(built.name(), built.id()));
			return new Workspace(attached.apply(ws.profile()), setChanged && !saved.equals(live) ? library.put(saved) : library);
		});
	}
}
