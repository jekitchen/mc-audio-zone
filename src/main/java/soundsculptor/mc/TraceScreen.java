package soundsculptor.mc;

import soundsculptor.core.Message;
import soundsculptor.core.Translator;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.editor.FieldError;
import soundsculptor.core.editor.EntryDraft;
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
import soundsculptor.core.trace.SoundTrace;
import soundsculptor.core.trace.TraceEditContext;
import soundsculptor.core.trace.TraceEditError;
import soundsculptor.core.trace.TraceEditResult;
import soundsculptor.core.trace.TraceEditor;
import soundsculptor.core.trace.TraceText;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The "Why does this sound like this?" panel (PRD FR-14). Built when it opens from the sound's last play (FR-14.6);
 * the lines on screen are the same text "Copy trace" puts on the clipboard (FR-14.5), so the two never disagree.
 *
 * <p>Inline editing (FR-14.4): entry lines have On/Off, Mute and Volume, zone lines have On/Off, and a "No entry
 * matches" line adds to the place's Quick changes with Mute or Volume. An entry of a library set is edited in the
 * library, so every place using the set changes (FR-4.9); a Quick changes line's Mute shows and toggles the sound's
 * actual state at its place, as the Sounds tab's quick actions do (FR-1.5). Quick changes and zone lines also open
 * their full editor. Edits save at once through {@link ProfileManager#change}, recompute the trace from the sound's
 * last play (labelled "Predicted for next play" until it plays again) and show a 5-second Undo toast. Volume changes
 * made in one visit to the Volume panel are undone together. Limit and Cue controls come with their milestones (M11,
 * M9), like in the entry editor.
 */
public final class TraceScreen extends SculptScreen {
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final int ON_OFF_WIDTH = 30;
	private static final int MUTE_WIDTH = 50;
	private static final int VOLUME_WIDTH = 60;
	private static final int PANEL_WIDTH = 240;
	private static final int FIELD_WIDTH = 40;

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

	// The Volume panel: one visit is one change for Undo.
	public SculptSlider volumeSlider;
	public SculptTextField volumeField;
	/** Applies a level from the Volume panel, or {@code null} for an empty field. */
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
		// A relayout drops an open Volume panel: finish its change so Undo still covers it.
		finishVolume();
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
			rows.add(new SculptList.Row(key(line.target(), i), List.of(new SculptList.Row.Column(() -> lineText, 0)), buttons(line.target())));
		}
		lines.setRows(rows);
	}

	private static Object key(TraceText.Target target, int index) {
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
		return "line:" + index;
	}

	private List<SculptButton> buttons(TraceText.Target target) {
		List<SculptButton> buttons = new ArrayList<>();
		boolean cueDefault = ProfileManager.profile().settings().cueDefault();
		if (target instanceof TraceText.EntryTarget entryTarget) {
			SetEntry entry = entryTarget.entry();
			EntryLocation where = entryTarget.where();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(entry.enabled()), ON_OFF_WIDTH,
					() -> edit(where, new EntryEdit.Enabled(!entry.enabled()))),
					Component.translatable(entry.enabled() ? "soundsculptor.trace.edit.turn_off" : "soundsculptor.trace.edit.turn_on")));
			if (where.isQuick()) {
				// As in FR-1.5: the sound's actual state at the place, from every set attached there.
				PlaceRef place = where.place();
				boolean muted = QuickActions.isMuted(ProfileManager.workspace(), place, soundId);
				buttons.add(SculptButton.text(Component.translatable(muted ? "soundsculptor.recent.unmute" : "soundsculptor.recent.mute"),
						MUTE_WIDTH, () -> addToQuickChanges(place, QuickActions.toggleMute(ProfileManager.workspace(), place, soundId, cueDefault))));
			} else {
				boolean muted = Boolean.TRUE.equals(entry.mute());
				buttons.add(SculptButton.text(Component.translatable(muted ? "soundsculptor.recent.unmute" : "soundsculptor.recent.mute"),
						MUTE_WIDTH, () -> edit(where, muted ? new EntryEdit.UnmuteWithCue(cueDefault) : new EntryEdit.MuteWithCue(cueDefault))));
			}
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.edit.volume"), VOLUME_WIDTH,
					() -> openVolume(where, entry)), Component.translatable("soundsculptor.trace.edit.volume.tip")));
			if (where.isQuick()) {
				buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.trace.edit.open_rule"), () -> openEntryEditor(where)));
			}
		} else if (target instanceof TraceText.ZoneTarget zoneTarget) {
			Zone zone = zoneTarget.zone();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(zone.enabled()), ON_OFF_WIDTH,
					() -> setZoneEnabled(zone.id(), !zone.enabled())),
					Component.translatable(zone.enabled() ? "soundsculptor.zones.turn_off" : "soundsculptor.zones.turn_on")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.trace.edit.open_zone"), () -> openZoneEditor(zone.id())));
		} else if (target instanceof TraceText.NoEntryTarget none) {
			PlaceRef place = none.place();
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.recent.mute"), MUTE_WIDTH,
					() -> addToQuickChanges(place, new EntryEdit.MuteWithCue(cueDefault))),
					Component.translatable("soundsculptor.trace.edit.add_quick.mute_tip")));
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.edit.volume"), VOLUME_WIDTH,
					() -> openQuickVolume(place)), Component.translatable("soundsculptor.trace.edit.add_quick.volume_tip")));
		}
		return buttons;
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
	 * Add to Quick changes, and a Quick changes line's Mute: the edit goes to the sound's single-sound entry in the
	 * place's Quick changes, made if needed (FR-4.10). Focus moves to that entry's line.
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
	 * is lost). {@code before} is the workspace before the change; for the Volume panel, before the panel opened. Undo
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

	// ---- Volume panel ----

	/** Opens the Volume panel for an entry line: a slider and a number field that apply as they change. */
	public void openVolume(EntryLocation where, SetEntry entry) {
		openVolumePanel(entry, volume -> TraceEditor.edit(context(), ProfileManager.workspace(), where, new EntryEdit.Volume(volume)));
	}

	/**
	 * Opens the Volume panel for a "No entry matches" line: the first change adds the sound's single-sound entry to the
	 * place's Quick changes, and later ones change it.
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
		// Checked exactly like the entry editor's Volume field (FR-12.7), e.g. "250" is refused and nothing changes.
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

	/** The Volume panel closed: one Undo toast for everything changed in it. */
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

	/** "Open full editor" on a Quick changes line: for changes the panel does not offer, such as the pattern. */
	public void openEntryEditor(EntryLocation where) {
		EntryEditorScreen editor = where.isQuick() ? EntryEditorScreen.forQuickEntry(this, where.place(), where.entryId()) : null;
		if (editor == null) {
			showToast(errorText(TraceEditError.RULE_NOT_FOUND.message()), null);
			return;
		}
		minecraft.gui.setScreen(editor);
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
}
