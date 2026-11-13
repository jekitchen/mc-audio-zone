package soundsculptor.mc;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.browser.BrowserRow;
import soundsculptor.core.browser.SoundBrowser;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.editor.FieldError;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.ApplyTo;
import soundsculptor.core.quick.QuickActionResult;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundNames;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.core.ui.SliderModel;
import soundsculptor.core.zones.Point;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * The Sounds tab (PRD FR-1, FR-3, FR-12.1): one browser for every sound, replacing the Recently Played and Search
 * tabs. One search field with a "?" help (FR-3.5), the <b>Recently played</b> filter (FR-1.0) and the result count
 * ("42 sounds", FR-3.4). With the filter on, the list holds the recently played sounds that match, with Pause (P while
 * the list has focus) and Sort (FR-1.3); with it off, every known sound that matches (FR-3.1). The rows come from core
 * {@link SoundBrowser}; a row with recent data shows the FR-1.2 columns, one without shows "Seen as" (FR-3.2).
 *
 * <p>Quick actions on each row (FR-1.5, FR-3.7): Mute/Unmute (the sound's actual state at the place), Level… (a
 * slider pop-up), Edit entry, Add to set… ({@link AddToSetScreen}) and Why? ({@link TraceScreen}). Mute, Level and Edit
 * change the sound's single-sound entry in the Quick changes of the place "Apply to" chose (FR-4.10), at once with a
 * 5-second Undo toast (FR-12.5).
 *
 * <p>The filter, search text, sort, pause and Apply to choice are remembered while the game runs: the filter starts on
 * the first time the tab opens and then stays as the player left it. The list refreshes at most 4 times a second
 * (FR-1.6), each refresh building one recent snapshot and one mute predicate for every row; search waits 150 ms after
 * typing stops (FR-3.4).
 */
public final class SoundsPanel {
	/** FR-3.4: results update 150 ms after the player stops typing. */
	public static final long SEARCH_DELAY_MS = 150;
	/** FR-1.6: the list refreshes at most 4 times per second (every 5 ticks). */
	public static final int REFRESH_TICKS = 5;
	/** The row buttons, in PRD 6.1 order. */
	public static final int BUTTON_MUTE = 0;
	public static final int BUTTON_LEVEL = 1;
	public static final int BUTTON_EDIT = 2;
	public static final int BUTTON_ADD_TO_SET = 3;
	public static final int BUTTON_WHY = 4;

	private static final int MAX_APPLY_TO_WIDTH = 200;
	private static final int MIN_APPLY_TO_WIDTH = 120;
	private static final int STATUS_WIDTH = 70;
	private static final int CATEGORY_WIDTH = 95;
	private static final int PLAYS_WIDTH = 75;
	private static final int BAND_WIDTH = 60;
	private static final int ZONE_WIDTH = 110;
	private static final int SEEN_WIDTH = 150;
	private static final int HELP_WIDTH = 360;
	private static final int LEVEL_PANEL_WIDTH = 240;
	private static final int LEVEL_FIELD_WIDTH = 40;
	/** Room around a toggle's or dropdown's text (the dropdown also draws its arrow). */
	private static final int TEXT_PADDING = 12;
	private static final int DROPDOWN_PADDING = 28;
	private static final int HELP_LINES = 7;
	private static final Identifier MUTE_ICON = icon("mute");
	private static final Identifier VOLUME_ICON = icon("volume");
	private static final Identifier LEVEL_ICON = icon("level");
	private static final Identifier EDIT_ICON = icon("edit");
	private static final Identifier ADD_ICON = icon("add");
	private static final Identifier HELP_ICON = icon("help");

	// Remembered while the game runs (FR-1.0, FR-1.3, FR-1.5).
	private static boolean recentOnly = true;
	private static String searchText = "";
	private static SortOrder sort = SortOrder.MOST_RECENT;
	private static boolean paused;
	/** The zone "Apply to" chose last, or null for Everywhere. */
	private static String applyZoneId;

	private final SculptScreen host;
	public SculptTextField searchField;
	public SculptButton helpButton;
	public SculptToggle recentToggle;
	/** "Apply to" (FR-1.5, FR-3.7), with the filter on or off. */
	public SculptDropdown<ApplyTarget> applyToDropdown;
	/** Shown only while the filter is on (FR-1.3). */
	public SculptToggle pauseToggle;
	/** Shown only while the filter is on (FR-1.3). */
	public SculptDropdown<SortOrder> sortDropdown;
	public SculptList list;
	/** The search examples, while the "?" pop-up is open. */
	public SculptList helpList;
	/** The Level pop-up, while it is open. */
	public SculptSlider levelSlider;
	public SculptTextField levelField;
	/** How many times the rows were rebuilt, for tests of the refresh rate. */
	public int refreshCount;

	private List<ApplyTarget> applyChoices = List.of(ApplyTarget.EVERYWHERE);
	/** Every known sound, built when the tab opens so names follow the language and resource packs (FR-3.1). */
	private List<SoundEntry> entries = List.of();
	private SearchQuery query = SearchQuery.parse("");
	private int count;
	private boolean searchPending;
	private long searchChangedAt;
	private int ticks;

	// The Level pop-up: one visit is one change for Undo.
	private String levelSound;
	private ApplyTarget levelTarget;
	private Workspace levelBefore;

	public SoundsPanel(SculptScreen host) {
		this.host = host;
	}

	/** Back to how a new game run starts: the filter on, no search text, most recent first, Everywhere. For tests. */
	public static void resetRememberedState() {
		recentOnly = true;
		searchText = "";
		sort = SortOrder.MOST_RECENT;
		paused = false;
		applyZoneId = null;
	}

	// ---- Building ----

	public void build(SculptScreen.Body body) {
		Font font = Minecraft.getInstance().font;
		recentToggle = Tooltips.describe(new SculptToggle(toggleWidth(font, "soundsculptor.sounds.recent_only"),
				Component.translatable("soundsculptor.sounds.recent_only"), recentOnly, this::setRecentOnly),
				Component.translatable("soundsculptor.sounds.recent_only.tip"));
		helpButton = SculptButton.icon(HELP_ICON, Component.translatable("soundsculptor.sounds.help.show"), this::openHelp);
		searchField = new SculptTextField(font, Math.max(60, body.width() - Theme.ICON_BUTTON_SIZE - recentToggle.getWidth() - 2 * Theme.GAP),
				Component.translatable("soundsculptor.sounds.field"))
				.placeholder(Component.translatable("soundsculptor.sounds.hint"));
		searchField.setMaxLength(200);
		searchField.setValue(searchText);
		searchField.onChange(text -> {
			searchText = text;
			searchPending = true;
			searchChangedAt = Util.getMillis();
		});
		body.row(searchField, helpButton, recentToggle);

		pauseToggle = Tooltips.describe(new SculptToggle(toggleWidth(font, "soundsculptor.sounds.pause"),
				Component.translatable("soundsculptor.sounds.pause"), paused, this::setPaused),
				Component.translatable("soundsculptor.sounds.pause.tip"));
		sortDropdown = Tooltips.describe(new SculptDropdown<>(sortWidth(font), Component.translatable("soundsculptor.sounds.sort"),
				List.of(SortOrder.values()), SoundsPanel::sortName, sort, this::setSort),
				Component.translatable("soundsculptor.sounds.sort.tip"));
		applyChoices = computeApplyChoices();
		int applyWidth = Math.max(MIN_APPLY_TO_WIDTH,
				Math.min(MAX_APPLY_TO_WIDTH, body.width() - pauseToggle.getWidth() - sortDropdown.getWidth() - 2 * Theme.GAP));
		applyToDropdown = Tooltips.describe(new SculptDropdown<>(applyWidth, Component.translatable("soundsculptor.sounds.apply_to"),
				applyChoices, choice -> Component.translatable(choice.translationKey(), choice.args().toArray()),
				applyTarget(), this::setApplyTarget), Component.translatable("soundsculptor.sounds.apply_to.tip"));
		// Pause and Sort keep their place while hidden, so turning the filter on or off moves nothing else.
		body.row(applyToDropdown, pauseToggle, sortDropdown);
		showFilterControls();

		body.label(() -> Component.translatable(count == 1 ? "soundsculptor.sounds.count.one" : "soundsculptor.sounds.count", count));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.sounds.list"));
		list.setEmptyText(() -> Component.translatable(!recentOnly ? "soundsculptor.sounds.empty"
				: query.isEmpty() ? "soundsculptor.sounds.empty_recent" : "soundsculptor.sounds.empty_recent_filtered"));
		body.fill(list);

		entries = KnownSounds.entries();
		query = SearchQuery.parse(searchText);
		refresh();
	}

	private static int toggleWidth(Font font, String labelKey) {
		Component label = Component.translatable(labelKey);
		int on = font.width(Component.translatable("soundsculptor.ui.toggle", label, SculptToggle.stateText(true)));
		int off = font.width(Component.translatable("soundsculptor.ui.toggle", label, SculptToggle.stateText(false)));
		return Math.max(on, off) + TEXT_PADDING;
	}

	private static int sortWidth(Font font) {
		int widest = 0;
		for (SortOrder order : SortOrder.values()) {
			widest = Math.max(widest, font.width(Component.translatable("soundsculptor.ui.toggle",
					Component.translatable("soundsculptor.sounds.sort"), sortName(order))));
		}
		return widest + DROPDOWN_PADDING;
	}

	private static Component sortName(SortOrder order) {
		return Component.translatable("soundsculptor.sounds.sort." + order.name().toLowerCase(Locale.ROOT));
	}

	private void showFilterControls() {
		pauseToggle.visible = recentOnly;
		sortDropdown.visible = recentOnly;
	}

	// ---- State ----

	public boolean isRecentOnly() {
		return recentOnly;
	}

	/** The Recently played filter (FR-1.0); remembered while the game runs. */
	public void setRecentOnly(boolean value) {
		recentOnly = value;
		if (recentToggle != null) {
			recentToggle.setValue(value);
			showFilterControls();
		}
		refresh();
	}

	public boolean isPaused() {
		return paused;
	}

	/** Pause freezes the recently played list for reading (FR-1.3); actions and search still update it. */
	public void setPaused(boolean value) {
		paused = value;
		if (pauseToggle != null) {
			pauseToggle.setValue(value);
		}
		if (!value) {
			refresh();
		}
	}

	public void setSort(SortOrder value) {
		sort = value;
		if (sortDropdown != null) {
			sortDropdown.setValue(value);
		}
		refresh();
	}

	public String searchText() {
		return searchText;
	}

	/** How many sounds the list holds ("42 sounds"). */
	public int count() {
		return count;
	}

	// ---- Refresh ----

	/**
	 * Every tick while the tab is open: runs a search 150 ms after typing stops, and refreshes the rows every
	 * {@value #REFRESH_TICKS} ticks unless the recently played list is paused.
	 *
	 * @return whether the player walked into or out of a zone, so "Apply to" needs other choices: the screen rebuilds
	 */
	public boolean tick() {
		ticks++;
		if (searchPending && Util.getMillis() - searchChangedAt >= SEARCH_DELAY_MS) {
			runSearch();
		}
		if (ticks % REFRESH_TICKS != 0) {
			return false;
		}
		if (!(recentOnly && paused)) {
			refresh();
		}
		return !host.hasOverlay() && !computeApplyChoices().equals(applyChoices);
	}

	/** Runs the search now (normally 150 ms after the last keystroke). */
	public void runSearch() {
		searchPending = false;
		query = SearchQuery.parse(searchText);
		refresh();
	}

	/** Rebuilds the rows. The list keeps the focused row and the scroll position. */
	public void refresh() {
		if (list == null) {
			return;
		}
		long now = Util.getMillis();
		List<RecentRow> recent = RecentLog.get().sounds().snapshot(now, SortOrder.MOST_RECENT, null);
		List<BrowserRow> found = SoundBrowser.rows(recent, entries, query, recentOnly, sort, KnownSounds.seenExcluded());
		Predicate<String> muted = mutedAtApplyTarget();
		List<SculptList.Row> rows = new ArrayList<>(found.size());
		for (BrowserRow row : found) {
			rows.add(toListRow(row, now, muted));
		}
		count = rows.size();
		list.setRows(rows);
		refreshCount++;
	}

	private SculptList.Row toListRow(BrowserRow row, long now, Predicate<String> muted) {
		String id = row.soundId();
		String name = row.subtitle() == null ? SoundNames.readable(id) : row.subtitle();
		List<SculptList.Row.Column> columns = new ArrayList<>();
		columns.add(new SculptList.Row.Column(() -> name, 0, 0));
		RecentRow recent = row.recent();
		if (recent == null) {
			// FR-3.2: "Seen as" when the sound played this session, outside the last 3 minutes.
			String category = RecentLog.get().sounds().seenCategory(id);
			String seen = category == null ? "" : Component.translatable("soundsculptor.sounds.seen_as",
					Component.translatable("soundCategory." + category)).getString();
			columns.add(new SculptList.Row.Column(() -> seen, SEEN_WIDTH, 0));
			columns.add(new SculptList.Row.Column(() -> id, 0, 1));
		} else {
			recentColumns(columns, recent, now);
		}
		boolean isMuted = muted.test(id);
		return new SculptList.Row(id, columns, List.of(muteButton(id, isMuted), levelButton(id), editButton(id),
				addToSetButton(id), whyButton(id)));
	}

	/**
	 * FR-1.2: subtitle, category and status (text plus an icon character, never color alone) on top; ID, the zone that
	 * decided the latest play (FR-9), plays with time since the last, and the distance band (unless hidden) below.
	 */
	private static void recentColumns(List<SculptList.Row.Column> columns, RecentRow row, long now) {
		String category = row.category() == null ? "?" : Component.translatable("soundCategory." + row.category()).getString();
		String status = Component.translatable("soundsculptor.sounds.status_line." + row.status().name().toLowerCase(Locale.ROOT),
				Component.translatable(row.status().translationKey())).getString();
		String plays = Component.translatable("soundsculptor.sounds.plays", row.count(), ago(now - row.lastPlayedMs())).getString();
		columns.add(new SculptList.Row.Column(() -> category, CATEGORY_WIDTH, 0));
		columns.add(new SculptList.Row.Column(() -> status, STATUS_WIDTH, 0));
		columns.add(new SculptList.Row.Column(row::id, 0, 1));
		if (row.zoneName() != null) {
			String zone = Component.translatable("soundsculptor.sounds.zone", row.zoneName()).getString();
			columns.add(new SculptList.Row.Column(() -> zone, ZONE_WIDTH, 1));
		}
		columns.add(new SculptList.Row.Column(() -> plays, PLAYS_WIDTH, 1));
		if (SoundSculptorSettings.showDistance()) {
			String band = Component.translatable(row.band().translationKey()).getString();
			columns.add(new SculptList.Row.Column(() -> band, BAND_WIDTH, 1));
		}
	}

	private static String ago(long millis) {
		long seconds = Math.max(0, millis / 1000);
		return seconds < 60
				? Component.translatable("soundsculptor.sounds.ago.seconds", seconds).getString()
				: Component.translatable("soundsculptor.sounds.ago.minutes", seconds / 60).getString();
	}

	// ---- Row buttons ----

	/**
	 * Mute / Unmute: the icon shows the sound's actual state at the place "Apply to" chose, whichever set decides it
	 * (crossed-out speaker when muted); the label, tooltip and narration say what pressing it does.
	 */
	private SculptButton muteButton(String soundId, boolean muted) {
		return SculptButton.icon(muted ? MUTE_ICON : VOLUME_ICON,
				Component.translatable(muted ? "soundsculptor.sounds.unmute" : "soundsculptor.sounds.mute"), () -> toggleMute(soundId));
	}

	private SculptButton levelButton(String soundId) {
		return SculptButton.icon(LEVEL_ICON, Component.translatable("soundsculptor.sounds.level"), () -> openLevel(soundId));
	}

	/** Edit entry: the sound's single-sound entry in the Quick changes of the place "Apply to" chose (a new one if none). */
	private SculptButton editButton(String soundId) {
		return SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.sounds.edit"),
				() -> setScreen(EntryEditorScreen.forSound(host, applyTarget().place(), soundId)));
	}

	private SculptButton addToSetButton(String soundId) {
		return SculptButton.icon(ADD_ICON, Component.translatable("soundsculptor.sounds.add_to_set"), () -> openAddToSet(soundId));
	}

	/** Why?: the sound trace for the sound's most recent play (FR-14.1). */
	private SculptButton whyButton(String soundId) {
		return SculptButton.icon(HELP_ICON, Component.translatable("soundsculptor.sounds.why"), () -> setScreen(new TraceScreen(host, soundId)));
	}

	/** Which sounds are muted at the place "Apply to" chose: one resolver for a whole rebuild, not one per row. */
	private Predicate<String> mutedAtApplyTarget() {
		return QuickActions.mutedAt(ProfileManager.workspace(), applyTarget().place());
	}

	// ---- Quick actions ----

	/**
	 * Mute/Unmute (FR-1.5, FR-4.10): edits the sound's single-sound entry in the Quick changes of the place "Apply to"
	 * chose, saves at once and shows a 5-second toast with Undo. A new mute takes the profile's Cue default; Unmute on a
	 * sound another set mutes writes "Not muted".
	 */
	public void toggleMute(String soundId) {
		ApplyTarget target = applyTarget();
		PlaceRef where = target.place();
		Workspace ws = ProfileManager.workspace();
		EntryEdit edit = QuickActions.toggleMute(ws, where, soundId, ws.profile().settings().cueDefault());
		QuickActionResult result = QuickActions.apply(ws, where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
			return;
		}
		if (result.changed()) {
			String toastKey = edit instanceof EntryEdit.MuteWithCue ? "soundsculptor.sounds.toast.muted" : "soundsculptor.sounds.toast.unmuted";
			QuickUndo.applyWorkspace(host, this::refreshAfterUndo, current -> QuickActions.apply(current, where, soundId, edit).after(),
					placeKey(toastKey, target), placeArgs(soundId, target));
		}
		refresh();
	}

	/**
	 * Level… (FR-1.5): a slider and a number field for the sound's level in the Quick changes of the place "Apply to"
	 * chose. Changes apply as they are made; closing the pop-up offers one Undo for everything changed in it. An empty
	 * field clears the level. Values are checked like the entry editor's (FR-12.7).
	 */
	public void openLevel(String soundId) {
		host.closeOverlay();
		levelSound = soundId;
		levelTarget = applyTarget();
		levelBefore = ProfileManager.workspace();
		SetEntry entry = QuickActions.quickEntry(levelBefore, levelTarget.place(), soundId);
		Integer current = entry == null ? null : entry.volume();
		Font font = Minecraft.getInstance().font;
		int max = BoostWarning.sliderMax(false);
		int panelWidth = Math.min(LEVEL_PANEL_WIDTH, host.width - 2 * Theme.PADDING);
		int sliderWidth = panelWidth - LEVEL_FIELD_WIDTH - 3 * Theme.GAP;
		levelField = Tooltips.describe(new SculptTextField(font, LEVEL_FIELD_WIDTH, Component.translatable("soundsculptor.rule_editor.volume_typed")),
				Component.translatable("soundsculptor.rule_editor.volume_typed.tip"));
		levelField.setMaxLength(3);
		levelField.setValue(current == null ? "" : current.toString());
		levelField.placeholder(Component.translatable("soundsculptor.trace.edit.volume_not_set"));
		EntryDraft check = EntryDraft.of(entry == null ? SetEntry.of("new", soundId) : entry, false);
		levelField.validator(value -> {
			check.set(EntryDraft.Field.VOLUME, value);
			FieldError error = check.error(EntryDraft.Field.VOLUME);
			return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
		});
		levelSlider = Tooltips.describe(new SculptSlider(sliderWidth, Component.translatable("soundsculptor.rule_editor.volume"),
				new SliderModel(SetEntry.MIN_VOLUME, max, 5, 1, 100, Math.min(max, current == null ? 100 : current)),
				value -> Component.literal((int) value + "%"), null,
				value -> {
					if (!levelField.isFocused()) {
						levelField.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("soundsculptor.rule_editor.volume.tip"));
		levelField.onChange(value -> {
			if (!levelField.isValid()) {
				return;
			}
			Integer level = value.isBlank() ? null : Integer.valueOf(value.trim());
			if (level != null && levelField.isFocused()) {
				levelSlider.setTypedValue(level);
			}
			applyLevel(level);
		});
		SculptButton done = SculptButton.text(Component.translatable("soundsculptor.ui.done"), host::closeOverlay);

		int panelHeight = 2 * Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT + 3 * Theme.GAP;
		int x = (host.width - panelWidth) / 2;
		int y = Math.max(Theme.PADDING, (host.height - panelHeight) / 2);
		levelSlider.setPosition(x + Theme.GAP, y + Theme.GAP);
		levelField.setPosition(levelSlider.getRight() + Theme.GAP, y + Theme.GAP);
		done.setPosition(x + panelWidth - Theme.GAP - done.getWidth(), y + 2 * Theme.GAP + Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT);
		host.openOverlay(x, y, panelWidth, panelHeight, List.of(levelSlider, levelField, done), levelSlider, this::finishLevel);
	}

	/** Sets the level from the pop-up, or clears it ({@code null}); saved at once, without its own toast. */
	private void applyLevel(Integer level) {
		if (levelSound == null) {
			return;
		}
		PlaceRef where = levelTarget.place();
		String soundId = levelSound;
		EntryEdit edit = new EntryEdit.Volume(level);
		QuickActionResult result = QuickActions.apply(ProfileManager.workspace(), where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
			return;
		}
		ProfileManager.change(current -> QuickActions.apply(current, where, soundId, edit).after());
	}

	/** The Level pop-up closed: one Undo toast for everything changed in it. */
	private void finishLevel() {
		String soundId = levelSound;
		ApplyTarget target = levelTarget;
		Workspace before = levelBefore;
		levelSound = null;
		levelTarget = null;
		levelBefore = null;
		levelSlider = null;
		levelField = null;
		if (soundId == null) {
			return;
		}
		Workspace after = ProfileManager.workspace();
		if (!after.equals(before)) {
			QuickUndo.offer(host, this::refreshAfterUndo, before, after, placeKey("soundsculptor.sounds.toast.level", target),
					List.of(placeArgs(soundId, target)));
		}
		refresh();
		focusRowButton(soundId, BUTTON_LEVEL);
	}

	/** Add to set…: choose a library set or a new one, then this sound or one of its Like this groups (FR-1.5, FR-4.8). */
	public void openAddToSet(String soundId) {
		setScreen(new AddToSetScreen(host, soundId, soundName(soundId), this::refreshAfterUndo));
	}

	/** A toast key for Everywhere, or its "_in" form naming the zone. */
	private static String placeKey(String key, ApplyTarget target) {
		return target.isEverywhere() ? key : key + "_in";
	}

	private static String[] placeArgs(String soundId, ApplyTarget target) {
		return target.isEverywhere() ? new String[] {soundName(soundId)} : new String[] {soundName(soundId), target.zoneName()};
	}

	/** After Undo, the screen may show a newer panel (e.g. after returning from Add to set…): refresh what it shows. */
	private void refreshAfterUndo() {
		if (host instanceof SoundSculptorScreen main) {
			main.refreshLists();
		} else {
			refresh();
		}
	}

	private void focusRowButton(String soundId, int button) {
		SculptList.Row row = list == null ? null : list.findRow(soundId);
		if (row != null) {
			host.focusPath(list.revealRow(row, button));
		}
	}

	// ---- Apply to ----

	/** Where quick actions put their change (FR-1.5, FR-3.7): Everywhere, or the chosen zone while the player stands in it. */
	public ApplyTarget applyTarget() {
		return ApplyTo.select(applyChoices, applyZoneId);
	}

	/** Chooses where quick actions put their change; refreshes the rows, whose Mute/Unmute follows the choice. */
	public void setApplyTarget(ApplyTarget target) {
		applyZoneId = target.zoneId();
		if (applyToDropdown != null && !applyToDropdown.value().equals(target)) {
			applyToDropdown.setValue(target);
		}
		refresh();
	}

	/** The "Apply to" choices offered now. */
	public List<ApplyTarget> applyChoices() {
		return applyChoices;
	}

	/** Everywhere, then each zone the player is standing in (core {@link ApplyTo}). */
	private static List<ApplyTarget> computeApplyChoices() {
		Minecraft mc = Minecraft.getInstance();
		if (!ProfileManager.isLoaded() || mc.player == null) {
			return List.of(ApplyTarget.EVERYWHERE);
		}
		Vec3 eye = mc.player.getEyePosition();
		String key = ProfileManager.key() == null ? "" : ProfileManager.key();
		return ApplyTo.choices(ProfileManager.profile(), key, WorldSession.dimension(mc), new Point(eye.x, eye.y, eye.z));
	}

	// ---- Search help ----

	/**
	 * The "?" button (FR-3.5): the search syntax examples in a pop-up list, one per row with what it means, so each is
	 * read by the narrator. Esc or Hide closes it and returns to the button.
	 */
	public void openHelp() {
		int x = list.getX();
		int y = helpButton.getBottom() + 2;
		int width = Math.min(HELP_WIDTH, host.width - x - Theme.PADDING);
		int height = Math.max(SculptList.ROW_HEIGHT + Theme.WIDGET_HEIGHT + 3 * Theme.GAP,
				host.height - SculptScreen.FOOTER_HEIGHT - Theme.PADDING - y);
		SculptButton hide = SculptButton.text(Component.translatable("soundsculptor.sounds.help.hide"), host::closeOverlay);
		int listHeight = height - hide.getHeight() - 3 * Theme.GAP;
		SculptList examples = new SculptList(width - 2 * Theme.GAP, listHeight, Component.translatable("soundsculptor.sounds.help.list"));
		examples.setPosition(x + Theme.GAP, y + Theme.GAP);
		examples.setWidth(width - 2 * Theme.GAP);
		examples.setHeight(listHeight);
		List<SculptList.Row> rows = new ArrayList<>();
		for (int i = 1; i <= HELP_LINES; i++) {
			String example = Component.translatable("soundsculptor.sounds.help." + i + ".example").getString();
			String meaning = Component.translatable("soundsculptor.sounds.help." + i + ".meaning").getString();
			rows.add(new SculptList.Row(i, List.of(new SculptList.Row.Column(() -> example, 0, 0),
					new SculptList.Row.Column(() -> meaning, 0, 1)), List.of()));
		}
		examples.setRows(rows);
		hide.setPosition(x + width - Theme.GAP - hide.getWidth(), examples.getBottom() + Theme.GAP);
		helpList = examples;
		host.openOverlay(x, y, width, height, List.of(examples, hide), examples, () -> {
			helpList = null;
			host.setFocused(helpButton);
		});
		examples.focusRow(rows.get(0));
	}

	public boolean isHelpShown() {
		return helpList != null;
	}

	// ---- Keys ----

	/** P toggles Pause while the list has focus and the Recently played filter is on (FR-1.3). */
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_P && recentOnly && list != null && host.getFocused() == list && !host.hasOverlay()) {
			setPaused(!paused);
			return true;
		}
		return false;
	}

	// ---- Helpers ----

	/** The sound's subtitle, or a readable name made from its ID, for toasts. */
	private static String soundName(String soundId) {
		return KnownSounds.name(soundId);
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("soundsculptor", "icon/" + name);
	}
}
