package soundsculptor.mc;

import soundsculptor.core.Message;
import soundsculptor.core.Translator;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.editor.FieldError;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetUsage;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.search.SoundSearch;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.core.trace.EntryLocation;
import soundsculptor.core.trace.LastPlay;
import soundsculptor.core.trace.PlaceTrace;
import soundsculptor.core.trace.SetLine;
import soundsculptor.core.trace.SoundTrace;
import soundsculptor.core.trace.TraceEditContext;
import soundsculptor.core.trace.TraceEditError;
import soundsculptor.core.trace.TraceEditResult;
import soundsculptor.core.trace.TraceEditor;
import soundsculptor.core.trace.TraceText;
import soundsculptor.core.trace.ZoneLine;
import soundsculptor.core.ui.SliderModel;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * The "Why does this sound like this?" panel (PRD FR-14). Built when it opens from the sound's last play (FR-14.6);
 * the lines on screen are the same text "Copy trace" puts on the clipboard (FR-14.5), so the two never disagree.
 *
 * <p>Under each place the lines are grouped by set, the place's Quick changes first. A set's line opens its full
 * editor; a library set's line lists the places that use it in its tooltip ("Farm animals · used in 3 places"), so
 * the player knows an edit there changes every one (FR-4.9).
 *
 * <p>Inline editing (FR-14.4): entry lines have On/Off, Mute, Level… and "Open full editor" (the set editor or the
 * place's Quick changes, at that entry); zone lines have On/Off and open the zone editor. Every place whose Quick
 * changes has no entry matching the sound offers Add to Quick changes, which offers Mute or Level… for the sound's
 * single-sound entry in that place's Quick changes (FR-4.10): on its "No entry matches" line, or, when a library set
 * there matches, on the place's own line (the Everywhere heading or the zone's line), so a library set's mute can be
 * overridden in one place without changing the set. An entry of a library set is edited in the library, so every place
 * using the set changes, and its tooltips say so. Each line's Mute edits the line's own entry, except on the sound's
 * single-sound entry in Quick changes and on Add to Quick changes, where it shows and toggles the sound's actual state
 * at the place, as the Sounds tab's quick actions do (FR-1.5). Edits save at once through {@link ProfileManager#change},
 * recompute the trace from the sound's last play (labelled "Predicted for next play" until it plays again) and show a
 * 5-second Undo toast. Level changes made in one visit to the Level panel are undone together; returning from a full
 * editor recomputes the trace. Limit and Cue controls come with their milestones (M11, M9), like in the entry editor.
 */
public final class TraceScreen extends SculptScreen {
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final int ON_OFF_WIDTH = 30;
	private static final int MUTE_WIDTH = 50;
	private static final int VOLUME_WIDTH = 60;
	private static final int PANEL_WIDTH = 240;
	private static final int FIELD_WIDTH = 40;
	private static final String ADD_QUICK_KEY = "soundsculptor.trace.edit.add_quick";

	private final String soundId;
	private final boolean vanillaCovered;
	/** The play the trace is worked out from; a new play of the sound replaces it and ends the prediction. */
	private LastPlay play;
	/** The profile and library the shown trace was worked out from. */
	private Workspace shownWorkspace;
	private SoundTrace trace;
	private boolean predicted;
	private List<TraceText.Line> traceLines = List.of();
	private String text;
	public SculptList lines;
	public SculptButton copyButton;

	// The Add to Quick changes choice, while it is open.
	public SculptButton addQuickMute;
	public SculptButton addQuickLevel;

	// The Level panel: one visit is one change for Undo.
	public SculptSlider volumeSlider;
	public SculptTextField volumeField;
	/** Applies a level from the Level panel, or {@code null} for an empty field. */
	private Function<Integer, TraceEditResult> volumeApply;
	private Workspace volumeBefore;
	private TraceEditResult volumeLast;

	public TraceScreen(Screen parent, String soundId) {
		super(Component.translatable("soundsculptor.trace.title"), parent);
		this.soundId = soundId;
		this.vanillaCovered = SoundSearch.isVanillaCovered(soundId) || KnownSounds.seenExcluded().contains(soundId);
		this.play = SoundHook.lastPlays().get(soundId);
		recompute();
	}

	public SoundTrace trace() {
		return trace;
	}

	/** The plain text that "Copy trace" copies. */
	public String text() {
		return text;
	}

	/** Whether the trace is worked out for the sound's next play after an edit, not its last one. */
	public boolean isPredicted() {
		return predicted;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.literal(soundId);
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		copyButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.copy"), this::copy),
				Component.translatable("soundsculptor.trace.copy.tip"));
		footer.add(copyButton);
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		// A relayout drops an open overlay without its close callback: finish the Level panel's change so Undo still
		// covers it, and forget the Add to Quick changes choice.
		finishVolume();
		addQuickMute = null;
		addQuickLevel = null;
		// Back from a full editor (or any other change): show the trace for the sets and zones as they are now.
		if (!Objects.equals(ProfileManager.workspace(), shownWorkspace)) {
			predicted = predicted || play != null;
			recompute();
		}
		lines = new SculptList(body.width(), SculptList.ROW_HEIGHT, Component.translatable("soundsculptor.trace.lines"));
		body.fill(lines);
		refreshRows();
	}

	@Override
	public void tick() {
		super.tick();
		// The sound played again: show what actually happened, no longer a prediction.
		LastPlay latest = SoundHook.lastPlays().get(soundId);
		if (!Objects.equals(latest, play) && !hasOverlay()) {
			play = latest;
			predicted = false;
			recompute();
			refreshRows();
		}
	}

	public void copy() {
		minecraft.keyboardHandler.setClipboard(text);
		showToast(Component.translatable("soundsculptor.trace.copied"), null);
	}

	// ---- Trace and rows ----

	/**
	 * The context for this sound. "Used in N places" counts this profile's places plus the places in the other profiles
	 * the usage index has found; before its first rebuild has finished there are none, so only the local count shows.
	 */
	private TraceEditContext context() {
		SetUsage usage = SetUsageIndex.current();
		String key = ProfileManager.key();
		return new TraceEditContext(soundId, play, ProfileManager.isBypassed(), vanillaCovered,
				setId -> usage == null ? 0 : usage.otherPlaceCount(setId, key));
	}

	private void recompute() {
		shownWorkspace = ProfileManager.workspace();
		show(context().trace(shownWorkspace));
	}

	private void show(SoundTrace newTrace) {
		trace = newTrace;
		List<TraceText.Line> built = new ArrayList<>(TraceText.lines(trace, TRANSLATOR));
		if (predicted) {
			// Right under the header, so it is read before the result it qualifies.
			built.add(1, new TraceText.Line("  " + TRANSLATOR.translate(TraceEditResult.PREDICTED_KEY), null));
		}
		traceLines = List.copyOf(built);
		StringBuilder out = new StringBuilder();
		for (TraceText.Line line : traceLines) {
			out.append(line.text()).append('\n');
		}
		text = out.toString();
	}

	private void refreshRows() {
		if (lines == null) {
			return;
		}
		List<SculptList.Row> rows = new ArrayList<>(traceLines.size());
		for (int i = 0; i < traceLines.size(); i++) {
			TraceText.Line line = traceLines.get(i);
			String lineText = line.text();
			Component places = line.target() instanceof TraceText.SetTarget set && !set.set().quick() ? placesText(set.set().setId()) : null;
			rows.add(new SculptList.Row(key(line.target(), i), List.of(new SculptList.Row.Column(() -> lineText, 0)), buttons(line.target(), places))
					.tooltip(places));
		}
		lines.setRows(rows);
	}

	private static Object key(TraceText.Target target, int index) {
		if (target instanceof TraceText.SetTarget set) {
			return setKey(set.place(), set.set().setId());
		}
		if (target instanceof TraceText.EntryTarget entry) {
			EntryLocation where = entry.where();
			return "entry:" + where.place().zoneId() + "/" + where.setId() + "/" + where.entryId();
		}
		if (target instanceof TraceText.ZoneTarget zone) {
			return "zone:" + zone.zone().id();
		}
		if (target instanceof TraceText.NoEntryTarget none) {
			return "none:" + none.place().zoneId();
		}
		if (target instanceof TraceText.PlaceTarget place) {
			return "place:" + place.place().zoneId();
		}
		return "line:" + index;
	}

	private static String setKey(PlaceRef place, String setId) {
		return "set:" + place.zoneId() + "/" + setId;
	}

	/**
	 * The line's inline controls.
	 *
	 * @param places for a library set's line, the places that use it ({@link #placesText})
	 */
	private List<SculptButton> buttons(TraceText.Target target, Component places) {
		List<SculptButton> buttons = new ArrayList<>();
		if (target instanceof TraceText.SetTarget setTarget) {
			buttons.add(setEditorButton(setTarget, places));
		} else if (target instanceof TraceText.EntryTarget entryTarget) {
			SetEntry entry = entryTarget.entry();
			EntryLocation where = entryTarget.where();
			String setName = where.isQuick() ? null : setName(where.setId());
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(entry.enabled()), ON_OFF_WIDTH,
					() -> edit(where, new EntryEdit.Enabled(!entry.enabled()))),
					Component.translatable(entry.enabled() ? "soundsculptor.trace.edit.turn_off" : "soundsculptor.trace.edit.turn_on")));
			buttons.add(muteButton(where, entry, setName));
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.edit.volume"), VOLUME_WIDTH,
					() -> openVolume(where, entry)), setName == null ? Component.translatable("soundsculptor.trace.edit.volume.tip")
							: Component.translatable("soundsculptor.trace.edit.volume.linked_tip", setName)));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable(where.isQuick() ? "soundsculptor.trace.edit.open_quick_entry"
					: "soundsculptor.trace.edit.open_entry"), () -> openSetEditor(where.place(), where.setId(), where.entryId())));
		} else if (target instanceof TraceText.ZoneTarget zoneTarget) {
			Zone zone = zoneTarget.zone();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(zone.enabled()), ON_OFF_WIDTH,
					() -> setZoneEnabled(zone.id(), !zone.enabled())),
					Component.translatable(zone.enabled() ? "soundsculptor.zones.turn_off" : "soundsculptor.zones.turn_on")));
			if (offersAddOnPlaceLine(zonePlace(zone.id()))) {
				buttons.add(addToQuickButton(PlaceRef.zone(zone.id())));
			}
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.trace.edit.open_zone"), () -> openZoneEditor(zone.id())));
		} else if (target instanceof TraceText.PlaceTarget placeTarget) {
			if (placeTarget.place().isEverywhere() && offersAddOnPlaceLine(trace.everywhere())) {
				buttons.add(addToQuickButton(PlaceRef.EVERYWHERE));
			}
		} else if (target instanceof TraceText.NoEntryTarget none) {
			buttons.add(addToQuickButton(none.place()));
		}
		return buttons;
	}

	/** Add to Quick changes for {@code place}: opens the Mute / Level… choice. */
	private SculptButton addToQuickButton(PlaceRef place) {
		return Tooltips.describe(SculptButton.text(Component.translatable(ADD_QUICK_KEY), () -> openAddToQuickChanges(place)),
				Component.translatable("soundsculptor.trace.edit.add_quick.tip"));
	}

	/**
	 * Whether the place's own line offers Add to Quick changes: a library set there matches the sound, but its Quick
	 * changes has no matching entry. (With no matching entry at all, the "No entry matches" line offers it instead.)
	 */
	private static boolean offersAddOnPlaceLine(PlaceTrace place) {
		return place != null && !place.sets().isEmpty() && place.sets().stream().noneMatch(SetLine::quick);
	}

	/** How the zone treated the sound, or {@code null} if the trace does not list it. */
	private PlaceTrace zonePlace(String zoneId) {
		for (ZoneLine line : trace.zones()) {
			if (line.zone().id().equals(zoneId)) {
				return line.sets();
			}
		}
		return null;
	}

	/**
	 * A set line's "Open full editor": the set editor, or the place's Quick changes. For a library set its tooltip, which
	 * the narrator reads too, lists the places that use the set.
	 */
	private SculptButton setEditorButton(TraceText.SetTarget target, Component places) {
		String setId = target.set().setId();
		Component label = Component.translatable(target.set().quick() ? "soundsculptor.trace.edit.open_quick" : "soundsculptor.trace.edit.open_set");
		SculptButton button = SculptButton.icon(EDIT_ICON, label, () -> openSetEditor(target.place(), setId, null));
		if (places != null) {
			button.setTooltip(Tooltip.create(Component.empty().append(label).append("\n").append(places), places));
		}
		return button;
	}

	/**
	 * A line's Mute or Unmute. On the sound's single-sound entry in Quick changes it shows and toggles the sound's actual
	 * state at the place, from every set attached there (FR-1.5): Unmute over a library set's mute writes "Not muted".
	 * On any other line (a group in Quick changes, or an entry of a library set) it edits that line's own entry.
	 *
	 * @param setName the library set's name, or {@code null} for Quick changes
	 */
	private SculptButton muteButton(EntryLocation where, SetEntry entry, String setName) {
		PlaceRef place = where.place();
		if (where.isQuick() && isSingleSoundEntry(place, entry)) {
			boolean muted = QuickActions.isMuted(ProfileManager.workspace(), place, soundId);
			return SculptButton.text(muteLabel(muted), MUTE_WIDTH,
					() -> addToQuickChanges(place, QuickActions.toggleMute(ProfileManager.workspace(), place, soundId, cueDefault())));
		}
		boolean muted = Boolean.TRUE.equals(entry.mute());
		SculptButton button = SculptButton.text(muteLabel(muted), MUTE_WIDTH,
				() -> edit(where, muted ? new EntryEdit.UnmuteWithCue(cueDefault()) : new EntryEdit.MuteWithCue(cueDefault())));
		return setName == null ? button : Tooltips.describe(button, Component.translatable("soundsculptor.trace.edit.mute.linked_tip", setName));
	}

	/** Whether {@code entry} is the sound's single-sound entry in the place's Quick changes. */
	private boolean isSingleSoundEntry(PlaceRef place, SetEntry entry) {
		SetEntry single = QuickActions.quickEntry(ProfileManager.workspace(), place, soundId);
		return single != null && single.id().equals(entry.id());
	}

	private static Component muteLabel(boolean muted) {
		return Component.translatable(muted ? "soundsculptor.sounds.unmute" : "soundsculptor.sounds.mute");
	}

	private static boolean cueDefault() {
		return ProfileManager.profile().settings().cueDefault();
	}

	/** The library set's name, or its ID if the library no longer has it. */
	private static String setName(String setId) {
		SoundSet set = ProfileManager.library().get(setId);
		return set == null ? setId : set.name();
	}

	/**
	 * A library set's line tooltip: this world's places that use it, by name, then how many other worlds do ("Used in
	 * Everywhere, Pen and 1 other world."); used in more than one place, it says an edit changes every one. While the
	 * other worlds are still being counted it says so.
	 */
	static Component placesText(String setId) {
		Profile profile = ProfileManager.profile();
		List<String> here = new ArrayList<>();
		for (PlaceRef where : profile.placesUsing(setId)) {
			here.add(SetDeleteDialog.placeName(profile, where));
		}
		SetUsage usage = SetUsageIndex.current();
		String key = ProfileManager.key();
		int otherWorlds = usage == null ? 0 : usage.otherCount(setId, key);
		int places = here.size() + (usage == null ? 0 : usage.otherPlaceCount(setId, key));
		MutableComponent text = Component.translatable(places > 1 ? "soundsculptor.trace.edit.set.places.linked" : "soundsculptor.trace.edit.set.places",
				SetEditorScreen.placeList(here, otherWorlds));
		if (usage == null) {
			text.append("\n").append(Component.translatable("soundsculptor.set_editor.confirm.counting"));
		}
		return text;
	}

	// ---- Edits ----

	/** An On/Off or Mute button on an entry line: the entry in its library set, or in the place's Quick changes. */
	public void edit(EntryLocation where, EntryEdit edit) {
		applyOne(TraceEditor.edit(context(), ProfileManager.workspace(), where, edit));
	}

	/** A zone line's On/Off button. */
	public void setZoneEnabled(String zoneId, boolean enabled) {
		applyOne(TraceEditor.setZoneEnabled(context(), ProfileManager.workspace(), zoneId, enabled));
	}

	/**
	 * Add to Quick changes, and the Mute of the sound's single-sound entry line in Quick changes: the edit goes to that
	 * entry in the place's Quick changes, made if needed (FR-4.10). Focus moves to that entry's line.
	 */
	public void addToQuickChanges(PlaceRef where, EntryEdit edit) {
		TraceEditResult result = TraceEditor.addToQuickChanges(context(), ProfileManager.workspace(), where, edit);
		applyOne(result);
		if (result.ok() && lines != null) {
			SetEntry added = QuickActions.quickEntry(result.after(), where, soundId);
			SculptList.Row row = added == null ? null
					: lines.findRow(key(new TraceText.EntryTarget(new EntryLocation(where, SoundSet.QUICK_ID, added.id()), added), -1));
			if (row != null && !row.buttons().isEmpty()) {
				focusPath(ComponentPath.path(lines, ComponentPath.path(row, ComponentPath.leaf(row.buttons().get(0)))));
			}
		}
	}

	/** One change with its own Undo toast; a refused change shows why and changes nothing. */
	private void applyOne(TraceEditResult result) {
		if (!result.ok()) {
			showToast(errorText(result.error()), null);
			return;
		}
		save(result);
		offerUndo(result.before(), result);
	}

	/** Saves the edit at once (no Save button) and shows the recomputed trace. */
	private void save(TraceEditResult result) {
		ProfileManager.change(current -> result.after());
		predicted = result.predictedForNextPlay();
		shownWorkspace = ProfileManager.workspace();
		show(result.predicted());
		refreshRows();
	}

	/**
	 * The shared quick-action Undo toast (FR-12.5, {@link QuickUndo}): Ctrl+Z, the countdown pausing while Undo has
	 * focus, the same narration, and Undo refused if the profile or the library was changed again since (then nothing
	 * is lost). {@code before} is the workspace before the change; for the Level panel, before the panel opened. Undo
	 * puts a library set back too, so a linked edit is undone in every place that uses the set.
	 */
	private void offerUndo(Workspace before, TraceEditResult result) {
		Workspace after = ProfileManager.workspace();
		if (before.equals(after)) {
			return;
		}
		QuickUndo.offer(this, this::afterUndo, before, after, result.toastKey(), result.toastArgs());
	}

	/** After Undo: the trace for the restored sets and zones, still predicted from the last play. */
	private void afterUndo() {
		predicted = play != null;
		recompute();
		refreshRows();
	}

	private static Component errorText(Message error) {
		return ProfileManager.component(error);
	}

	// ---- Add to Quick changes ----

	/**
	 * Add to Quick changes (on a "No entry matches" line or a place's own line): a small choice above the screen, Mute
	 * (or Unmute, the sound's actual state at the place, FR-1.5) and Level…, each read with where it goes ("Add to Quick
	 * changes in Pen"). Mute applies at once; Level… opens the Level panel. Esc closes the choice and returns focus to the
	 * button.
	 */
	public void openAddToQuickChanges(PlaceRef where) {
		Component context = Component.translatable("soundsculptor.trace.edit.add_quick.choice", SetDeleteDialog.placeName(ProfileManager.profile(), where));
		boolean muted = QuickActions.isMuted(ProfileManager.workspace(), where, soundId);
		SculptButton mute = Tooltips.describe(SculptButton.text(muteLabel(muted), () -> {
			closeOverlay();
			addToQuickChanges(where, QuickActions.toggleMute(ProfileManager.workspace(), where, soundId, cueDefault()));
		}), Component.translatable("soundsculptor.trace.edit.add_quick.mute_tip")).narrationContext(() -> context);
		SculptButton level = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.edit.volume"), () -> {
			closeOverlay();
			openQuickVolume(where);
		}), Component.translatable("soundsculptor.trace.edit.add_quick.volume_tip")).narrationContext(() -> context);
		int panelWidth = mute.getWidth() + level.getWidth() + 3 * Theme.GAP;
		int panelHeight = Theme.WIDGET_HEIGHT + 2 * Theme.GAP;
		int x = Math.max(0, (width - panelWidth) / 2);
		int y = Math.max(Theme.PADDING, (height - panelHeight) / 2);
		mute.setPosition(x + Theme.GAP, y + Theme.GAP);
		level.setPosition(mute.getRight() + Theme.GAP, y + Theme.GAP);
		addQuickMute = mute;
		addQuickLevel = level;
		openOverlay(x, y, panelWidth, panelHeight, List.of(mute, level), mute, () -> {
			addQuickMute = null;
			addQuickLevel = null;
			ComponentPath button = addToQuickButtonPath(where);
			if (button != null) {
				focusPath(button);
			}
		});
	}

	/** The path to the place's Add to Quick changes button, on its "No entry matches" line or its own line, if shown. */
	private ComponentPath addToQuickButtonPath(PlaceRef where) {
		if (lines == null) {
			return null;
		}
		Object placeLine = where.isEverywhere() ? key(new TraceText.PlaceTarget(where), -1) : "zone:" + where.zoneId();
		String label = Component.translatable(ADD_QUICK_KEY).getString();
		for (Object rowKey : List.of(key(new TraceText.NoEntryTarget(where), -1), placeLine)) {
			SculptList.Row row = lines.findRow(rowKey);
			for (int i = 0; row != null && i < row.buttons().size(); i++) {
				if (row.buttons().get(i).getMessage().getString().equals(label)) {
					return lines.revealRow(row, i);
				}
			}
		}
		return null;
	}

	// ---- Level panel ----

	/** Opens the Level panel for an entry line: a slider and a number field that apply as they change. */
	public void openVolume(EntryLocation where, SetEntry entry) {
		openVolumePanel(entry, volume -> TraceEditor.edit(context(), ProfileManager.workspace(), where, new EntryEdit.Volume(volume)));
	}

	/**
	 * Opens the Level panel for Add to Quick changes: the first change adds the sound's single-sound entry to the place's
	 * Quick changes, and later ones change it.
	 */
	public void openQuickVolume(PlaceRef where) {
		openVolumePanel(SetEntry.of("new", soundId),
				volume -> TraceEditor.addToQuickChanges(context(), ProfileManager.workspace(), where, new EntryEdit.Volume(volume)));
	}

	private void openVolumePanel(SetEntry entry, Function<Integer, TraceEditResult> apply) {
		finishVolume();
		volumeApply = apply;
		volumeBefore = ProfileManager.workspace();
		volumeLast = null;
		int max = BoostWarning.sliderMax(false);
		int panelWidth = Math.min(PANEL_WIDTH, width - 2 * Theme.PADDING);
		int sliderWidth = panelWidth - FIELD_WIDTH - 3 * Theme.GAP;
		double initial = entry.volume() != null ? entry.volume() : 100;
		volumeField = Tooltips.describe(new SculptTextField(font, FIELD_WIDTH, Component.translatable("soundsculptor.rule_editor.volume_typed")),
				Component.translatable("soundsculptor.trace.edit.volume_typed.tip"));
		volumeField.setMaxLength(3);
		volumeField.setValue(entry.volume() == null ? "" : entry.volume().toString());
		volumeField.placeholder(Component.translatable("soundsculptor.trace.edit.volume_not_set"));
		// Checked exactly like the entry editor's Level field (FR-12.7), e.g. "250" is refused and nothing changes.
		EntryDraft check = EntryDraft.of(entry, false);
		volumeField.validator(value -> {
			check.set(EntryDraft.Field.VOLUME, value);
			FieldError error = check.error(EntryDraft.Field.VOLUME);
			return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
		});
		volumeSlider = Tooltips.describe(new SculptSlider(sliderWidth, Component.translatable("soundsculptor.rule_editor.volume"),
				new SliderModel(SetEntry.MIN_VOLUME, max, 5, 1, 100, Math.min(max, initial)),
				value -> Component.literal((int) value + "%"), null,
				value -> {
					if (!volumeField.isFocused()) {
						volumeField.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("soundsculptor.rule_editor.volume.tip"));
		volumeField.onChange(value -> {
			if (!volumeField.isValid()) {
				return;
			}
			Integer volume = value.isBlank() ? null : Integer.valueOf(value.trim());
			if (volume != null && volumeField.isFocused()) {
				volumeSlider.setTypedValue(volume);
			}
			applyVolume(volume);
		});
		SculptButton done = SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::closeOverlay);

		int panelHeight = 2 * Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT + 3 * Theme.GAP;
		int x = (width - panelWidth) / 2;
		int y = Math.max(Theme.PADDING, (height - panelHeight) / 2);
		volumeSlider.setPosition(x + Theme.GAP, y + Theme.GAP);
		volumeField.setPosition(volumeSlider.getRight() + Theme.GAP, y + Theme.GAP);
		done.setPosition(x + panelWidth - Theme.GAP - done.getWidth(),
				y + 2 * Theme.GAP + Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT);
		openOverlay(x, y, panelWidth, panelHeight, List.of(volumeSlider, volumeField, done), volumeSlider, this::finishVolume);
	}

	private void applyVolume(Integer volume) {
		TraceEditResult result = volumeApply.apply(volume);
		if (!result.ok()) {
			showToast(errorText(result.error()), null);
			return;
		}
		save(result);
		volumeLast = result;
	}

	/** The Level panel closed: one Undo toast for everything changed in it. */
	private void finishVolume() {
		TraceEditResult last = volumeLast;
		Workspace before = volumeBefore;
		volumeApply = null;
		volumeBefore = null;
		volumeLast = null;
		if (last != null && before != null) {
			offerUndo(before, last);
		}
	}

	// ---- Full editors ----

	/**
	 * "Open full editor" on a set or entry line, for changes the panel does not offer, such as the pattern: the library
	 * set's editor, or the place's Quick changes, at the entry if one is given. Returning recomputes the trace.
	 *
	 * @param setId   a library set's ID, or {@link SoundSet#QUICK_ID} for the place's Quick changes
	 * @param entryId the entry to open at, or {@code null}
	 */
	public void openSetEditor(PlaceRef place, String setId, String entryId) {
		if (SoundSet.QUICK_ID.equals(setId)) {
			if (ProfileManager.profile().place(place) == null) {
				showToast(errorText(TraceEditError.ZONE_NOT_FOUND.message()), null);
				return;
			}
			minecraft.gui.setScreen(SetEditorScreen.forQuickChanges(this, place, entryId));
			return;
		}
		if (ProfileManager.library().get(setId) == null) {
			showToast(errorText(TraceEditError.ENTRY_NOT_FOUND.message()), null);
			return;
		}
		minecraft.gui.setScreen(SetEditorScreen.edit(this, setId, entryId));
	}

	/** "Open full editor" on a zone line: shape, size and anchor. */
	public void openZoneEditor(String zoneId) {
		Zone zone = ProfileManager.profile().zone(zoneId);
		if (zone == null) {
			showToast(errorText(TraceEditError.ZONE_NOT_FOUND.message()), null);
			return;
		}
		minecraft.gui.setScreen(ZoneEditorScreen.edit(this, zone));
	}

	/** The row for a line's target, for tests. */
	public SculptList.Row row(TraceText.Target target) {
		return lines == null ? null : lines.findRow(key(target, -1));
	}

	/** The line of a set listed under a place, for tests. */
	public SculptList.Row setRow(PlaceRef place, String setId) {
		return lines == null ? null : lines.findRow(setKey(place, setId));
	}
}
