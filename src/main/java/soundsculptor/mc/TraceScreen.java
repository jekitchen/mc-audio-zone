package soundsculptor.mc;

import soundsculptor.core.Translator;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.editor.FieldError;
import soundsculptor.core.editor.RuleDraft;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.rules.Rule;
import soundsculptor.core.search.SoundSearch;
import soundsculptor.core.trace.EditableRules;
import soundsculptor.core.trace.LastPlay;
import soundsculptor.core.trace.RuleEdit;
import soundsculptor.core.trace.RuleLocation;
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
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The "Why does this sound like this?" panel (PRD FR-14). Built when it opens from the sound's last play (FR-14.6);
 * the lines on screen are the same text "Copy trace" puts on the clipboard (FR-14.5), so the two never disagree.
 *
 * <p>Inline editing (v1, FR-14.4): rule lines have On/Off, Mute and Volume, zone lines have On/Off, and a "No rule
 * matches" line has "Add rule". Each line also opens its full editor. Edits save at once, recompute the trace from
 * the sound's last play (labelled "Predicted for next play" until it plays again) and show a 5-second Undo toast.
 * Volume changes made in one visit to the Volume panel are undone together. Limit and Cue controls come with their
 * milestones (M11, M9), like in the rule editor.
 */
public final class TraceScreen extends SculptScreen {
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final int ON_OFF_WIDTH = 30;
	private static final int MUTE_WIDTH = 50;
	private static final int VOLUME_WIDTH = 60;
	private static final int ADD_WIDTH = 70;
	private static final int PANEL_WIDTH = 240;
	private static final int FIELD_WIDTH = 40;

	private final String soundId;
	private final boolean vanillaCovered;
	/** The play the trace is worked out from; a new play of the sound replaces it and ends the prediction. */
	private LastPlay play;
	/** The rules and zones the shown trace was worked out from. */
	private EditableRules shownRules;
	private SoundTrace trace;
	private boolean predicted;
	private List<TraceText.Line> traceLines = List.of();
	private String text;
	public SculptList lines;
	public SculptButton copyButton;

	// The Volume panel: one visit is one change for Undo.
	public SculptSlider volumeSlider;
	public SculptTextField volumeField;
	private RuleLocation volumeTarget;
	private Profile volumeBefore;
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
		// Back from a full editor (or any other change): show the trace for the rules as they are now.
		if (!Objects.equals(currentRules(), shownRules)) {
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

	private TraceEditContext context() {
		return new TraceEditContext(soundId, play, ProfileManager.profile().settings().defaultEdge(), ProfileManager.isBypassed(),
				vanillaCovered);
	}

	private static EditableRules currentRules() {
		Profile profile = ProfileManager.profile();
		return new EditableRules(profile.globalRules(), profile.zones());
	}

	private void recompute() {
		shownRules = currentRules();
		show(context().trace(shownRules));
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
		if (target instanceof TraceText.RuleTarget rule) {
			return "rule:" + rule.where().zoneId() + "/" + rule.where().ruleId();
		}
		if (target instanceof TraceText.ZoneTarget zone) {
			return "zone:" + zone.zone().id();
		}
		if (target instanceof TraceText.NoRuleTarget none) {
			return "none:" + none.zoneId();
		}
		return "line:" + index;
	}

	private List<SculptButton> buttons(TraceText.Target target) {
		List<SculptButton> buttons = new ArrayList<>();
		if (target instanceof TraceText.RuleTarget ruleTarget) {
			Rule rule = ruleTarget.rule();
			RuleLocation where = ruleTarget.where();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(rule.enabled()), ON_OFF_WIDTH,
					() -> edit(where, new RuleEdit.Enabled(!rule.enabled()))),
					Component.translatable(rule.enabled() ? "soundsculptor.rules.turn_off" : "soundsculptor.rules.turn_on")));
			boolean muted = Boolean.TRUE.equals(rule.mute());
			boolean cueDefault = ProfileManager.profile().settings().cueDefault();
			buttons.add(SculptButton.text(Component.translatable(muted ? "soundsculptor.recent.unmute" : "soundsculptor.recent.mute"), MUTE_WIDTH,
					() -> edit(where, muted ? new RuleEdit.UnmuteWithCue(cueDefault) : new RuleEdit.MuteWithCue(cueDefault))));
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.edit.volume"), VOLUME_WIDTH,
					() -> openVolume(where, rule)), Component.translatable("soundsculptor.trace.edit.volume.tip")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.trace.edit.open_rule"), () -> openRuleEditor(where)));
		} else if (target instanceof TraceText.ZoneTarget zoneTarget) {
			Zone zone = zoneTarget.zone();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(zone.enabled()), ON_OFF_WIDTH,
					() -> setZoneEnabled(zone.id(), !zone.enabled())),
					Component.translatable(zone.enabled() ? "soundsculptor.zones.turn_off" : "soundsculptor.zones.turn_on")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.trace.edit.open_zone"), () -> openZoneEditor(zone.id())));
		} else if (target instanceof TraceText.NoRuleTarget none) {
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.trace.edit.add_rule"), ADD_WIDTH,
					() -> addRule(none.zoneId())), Component.translatable(none.zoneId() == null
							? "soundsculptor.trace.edit.add_rule.tip" : "soundsculptor.trace.edit.add_rule.zone_tip")));
		}
		return buttons;
	}

	// ---- Edits ----

	/** An On/Off or Mute button on a rule line. */
	public void edit(RuleLocation where, RuleEdit edit) {
		applyOne(TraceEditor.edit(context(), currentRules(), where, edit));
	}

	/** A zone line's On/Off button. */
	public void setZoneEnabled(String zoneId, boolean enabled) {
		applyOne(TraceEditor.setZoneEnabled(context(), currentRules(), zoneId, enabled));
	}

	/**
	 * "Add rule for this sound" in the global list ({@code zoneId} null) or a zone: an exact-ID rule that sets nothing
	 * yet, so its line appears with the same controls. Focus moves to it.
	 */
	public void addRule(String zoneId) {
		TraceEditResult result = TraceEditor.addRuleForSound(context(), currentRules(), zoneId, new RuleEdit.Enabled(true));
		applyOne(result);
		if (result.ok() && lines != null) {
			Rule added = soundsculptor.core.trace.ExactRules.find(result.after(), zoneId, soundId);
			SculptList.Row row = added == null ? null : lines.findRow(key(new TraceText.RuleTarget(new RuleLocation(zoneId, added.id()), added), -1));
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
		Profile before = ProfileManager.profile();
		save(result);
		offerUndo(before, result);
	}

	/** Saves the edit at once (no Save button) and shows the recomputed trace. */
	private void save(TraceEditResult result) {
		ProfileManager.update(profile -> withRules(profile, result.after()));
		predicted = result.predictedForNextPlay();
		shownRules = result.after();
		show(result.predicted());
		refreshRows();
	}

	/**
	 * The shared quick-action Undo toast (FR-12.5, {@link QuickUndo}): Ctrl+Z, the countdown pausing while Undo has
	 * focus, the same narration, and Undo refused if the profile was changed again since (then nothing is lost).
	 * {@code before} is the profile before the change; for the Volume panel, before the panel opened.
	 */
	private void offerUndo(Profile before, TraceEditResult result) {
		Profile after = ProfileManager.profile();
		if (before.equals(after)) {
			return;
		}
		QuickUndo.offer(this, this::afterUndo, before, after, result.toastKey(), result.toastArgs());
	}

	/** After Undo: the trace for the restored rules, still predicted from the last play. */
	private void afterUndo() {
		predicted = play != null;
		recompute();
		refreshRows();
	}

	private static Profile withRules(Profile profile, EditableRules rules) {
		return profile.withGlobalRules(rules.globalRules()).withZones(rules.zones());
	}

	private static Component errorText(TraceEditError error) {
		return Component.translatable(error.translationKey(), error.min(), error.max());
	}

	// ---- Volume panel ----

	/** Opens the Volume panel for a rule line: a slider and a number field that apply as they change. */
	public void openVolume(RuleLocation where, Rule rule) {
		finishVolume();
		volumeTarget = where;
		volumeBefore = ProfileManager.profile();
		volumeLast = null;
		int max = BoostWarning.sliderMax(false);
		int panelWidth = Math.min(PANEL_WIDTH, width - 2 * Theme.PADDING);
		int sliderWidth = panelWidth - FIELD_WIDTH - 3 * Theme.GAP;
		double initial = rule.volume() != null ? rule.volume() : 100;
		volumeField = Tooltips.describe(new SculptTextField(font, FIELD_WIDTH, Component.translatable("soundsculptor.rule_editor.volume_typed")),
				Component.translatable("soundsculptor.trace.edit.volume_typed.tip"));
		volumeField.setMaxLength(3);
		volumeField.setValue(rule.volume() == null ? "" : rule.volume().toString());
		volumeField.placeholder(Component.translatable("soundsculptor.trace.edit.volume_not_set"));
		// Checked exactly like the rule editor's Volume field (FR-12.7), e.g. "250" is refused and nothing changes.
		RuleDraft check = RuleDraft.of(rule, false);
		volumeField.validator(value -> {
			check.set(RuleDraft.Field.VOLUME, value);
			FieldError error = check.error(RuleDraft.Field.VOLUME);
			return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
		});
		volumeSlider = Tooltips.describe(new SculptSlider(sliderWidth, Component.translatable("soundsculptor.rule_editor.volume"),
				new SliderModel(Rule.MIN_VOLUME, max, 5, 1, 100, Math.min(max, initial)),
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
		TraceEditResult result = TraceEditor.edit(context(), currentRules(), volumeTarget, new RuleEdit.Volume(volume));
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
		Profile before = volumeBefore;
		volumeTarget = null;
		volumeBefore = null;
		volumeLast = null;
		if (last != null && before != null) {
			offerUndo(before, last);
		}
	}

	// ---- Full editors ----

	/** "Open full editor" on a rule line: for changes the panel does not offer, such as the pattern. */
	public void openRuleEditor(RuleLocation where) {
		RuleTarget target = where.isGlobal() ? RuleTarget.GLOBAL : RuleTarget.zone(where.zoneId());
		Rule rule = target.rule(ProfileManager.profile(), where.ruleId());
		if (rule == null) {
			showToast(errorText(TraceEditError.RULE_NOT_FOUND), null);
			return;
		}
		minecraft.gui.setScreen(RuleEditorScreen.edit(this, target, rule));
	}

	/** "Open full editor" on a zone line: shape, size and anchor. */
	public void openZoneEditor(String zoneId) {
		Zone zone = ProfileManager.profile().zone(zoneId);
		if (zone == null) {
			showToast(errorText(TraceEditError.ZONE_NOT_FOUND), null);
			return;
		}
		minecraft.gui.setScreen(ZoneEditorScreen.edit(this, zone));
	}

	/** The row for a line's target, for tests. */
	public SculptList.Row row(TraceText.Target target) {
		return lines == null ? null : lines.findRow(key(target, -1));
	}
}
