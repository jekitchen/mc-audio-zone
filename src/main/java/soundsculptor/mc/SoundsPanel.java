package soundsculptor.mc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import soundsculptor.core.browser.BrowserRow;
import soundsculptor.core.browser.LikeThis;
import soundsculptor.core.browser.PickerModel;
import soundsculptor.core.browser.SoundBrowser;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.ApplyTo;
import soundsculptor.core.quick.QuickActionResult;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.recent.DistanceBand;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.RecentSounds;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundNames;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.core.zones.Point;
import soundsculptor.mc.compat.Keys;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptCheckbox;
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
 * <p>In picker mode ({@link #picker}, FR-1.7) the same browser serves {@link SoundPickerScreen}: each row has a checkbox
 * (or "✔ In this set" and no checkbox) and Like this… instead of the quick actions, and there is no Apply to, Pause or
 * Sort.
 *
 * <p>The filter, search text, sort, pause and Apply to choice are remembered while the game runs, apart for the tab and
 * the picker, so the picker never changes the tab's filter: the filter starts on the first time each opens and then
 * stays as the player left it. The list refreshes at most 4 times a second
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
	/** The most a recent row's "Zone: <name>" gets; a long zone name is cut, and gives way in a narrow row. */
	private static final int ZONE_WIDTH = 110;
	/** The play count the plays column is measured for ("×999 · 59s ago"). */
	private static final int PLAYS_MEASURED = 999;
	private static final int HELP_WIDTH = 360;
	private static final int HELP_LINES = 7;
	private static final Identifier MUTE_ICON = icon("mute");
	private static final Identifier VOLUME_ICON = icon("volume");
	private static final Identifier LEVEL_ICON = icon("level");
	private static final Identifier EDIT_ICON = icon("edit");
	private static final Identifier ADD_ICON = icon("add");
	private static final Identifier HELP_ICON = icon("help");
	/** "✔ In this set", for what the set already has (FR-1.7); Add to set… uses the same text. */
	static final Component IN_SET = Component.translatable("soundsculptor.picker.in_set");

	/** The Sounds tab's remembered state; the picker has its own, so opening it changes nothing on the tab. */
	private static final Remembered TAB_STATE = new Remembered();
	private static final Remembered PICKER_STATE = new Remembered();

	private final SculptScreen host;
	private final Remembered state;
	/** Picker mode (FR-1.7): what the rows tick, or {@code null} on the Sounds tab. */
	private final PickerModel picker;
	/** Told after every tick or untick, so the picker's footer follows. */
	private final Runnable picksChanged;
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
	/** The Like this… groups (FR-4.8), while the pop-up is open (picker mode). */
	public SculptList likeThisList;
	/** The Level pop-up, while it is open. */
	public SculptSlider levelSlider;
	public SculptTextField levelField;
	/** How many times a refresh ran (it may find nothing to do), for tests of the refresh rate. */
	public int refreshCount;
	/** How many rows were built, for tests that a refresh rebuilds only what changed. */
	public int rowsBuilt;

	private List<ApplyTarget> applyChoices = List.of(ApplyTarget.EVERYWHERE);
	/** Every known sound, built when the tab opens so names follow the language and resource packs (FR-3.1). */
	private List<SoundEntry> entries = List.of();
	private SearchQuery query = SearchQuery.parse("");
	private int count;
	private boolean searchPending;
	private long searchChangedAt;
	private int ticks;

	// What the shown rows were built from (see refresh).
	private long shownGeneration = -1;
	private Profile shownProfile;
	private SetLibrary shownLibrary;
	private ApplyTarget shownTarget;
	private boolean shownDistance;
	private SearchQuery shownQuery;
	private boolean shownRecentOnly;
	private SortOrder shownSort;
	private long shownSecond;
	private boolean shownAnyRecent;
	private int shownPickVersion;
	/** Counts ticks and unticks in picker mode, so a refresh after one is not skipped. */
	private int pickVersion;
	/** The Like this… pop-up's sound and its groups (counted once when it opens). */
	private String likeThisSound;
	private List<LikeThis.Group> likeThisGroups = List.of();
	/** The row columns' and Like this…'s widths, measured once per panel rather than for each of up to 2,000 rows. */
	private Widths widths;
	/** Built rows by sound ID; cleared when the workspace, Apply to or Show distance changes. */
	private final Map<String, CachedRow> rowCache = new HashMap<>();

	// The Level pop-up: one visit is one change for Undo.
	private String levelSound;
	private ApplyTarget levelTarget;
	private Workspace levelBefore;

	/** The Sounds tab. */
	public SoundsPanel(SculptScreen host) {
		this(host, TAB_STATE, null, null);
	}

	private SoundsPanel(SculptScreen host, Remembered state, PickerModel picker, Runnable picksChanged) {
		this.host = host;
		this.state = state;
		this.picker = picker;
		this.picksChanged = picksChanged;
	}

	/**
	 * The browser in picker mode (FR-1.7): a checkbox on each row (or "In this set" when the set has the sound) and Like
	 * this…, instead of the quick actions; no Apply to, Pause or Sort, which act on places. Its filter and search text
	 * are remembered apart from the Sounds tab's.
	 *
	 * @param picksChanged runs after every tick or untick
	 */
	public static SoundsPanel picker(SculptScreen host, PickerModel picker, Runnable picksChanged) {
		return new SoundsPanel(host, PICKER_STATE, picker, picksChanged);
	}

	/**
	 * Back to how a new game run starts, for the tab and the picker: the filter on, no search text, most recent first,
	 * Everywhere. For tests.
	 */
	public static void resetRememberedState() {
		TAB_STATE.reset();
		PICKER_STATE.reset();
	}

	/** Remembered while the game runs (FR-1.0, FR-1.3, FR-1.5). */
	private static final class Remembered {
		boolean recentOnly;
		String searchText;
		SortOrder sort;
		boolean paused;
		/** The zone "Apply to" chose last, or null for Everywhere. Zone ids restart in every profile, so it counts only in {@link #applyWorldKey}. */
		String applyZoneId;
		/** The world (profile key) {@link #applyZoneId} was chosen in. */
		String applyWorldKey;

		Remembered() {
			reset();
		}

		void reset() {
			recentOnly = true;
			searchText = "";
			sort = SortOrder.MOST_RECENT;
			paused = false;
			applyZoneId = null;
			applyWorldKey = null;
		}
	}

	// ---- Building ----

	public void build(SculptScreen.Body body) {
		Font font = Minecraft.getInstance().font;
		recentToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(Component.translatable("soundsculptor.sounds.recent_only")),
				Component.translatable("soundsculptor.sounds.recent_only"), state.recentOnly, this::setRecentOnly),
				Component.translatable("soundsculptor.sounds.recent_only.tip"));
		helpButton = SculptButton.icon(HELP_ICON, Component.translatable("soundsculptor.sounds.help.show"), this::openHelp);
		searchField = new SculptTextField(font, Math.max(60, body.width() - Theme.ICON_BUTTON_SIZE - recentToggle.getWidth() - 2 * Theme.GAP),
				Component.translatable("soundsculptor.sounds.field"))
				.placeholder(Component.translatable("soundsculptor.sounds.hint"));
		searchField.setMaxLength(200);
		searchField.setValue(state.searchText);
		searchField.onChange(text -> {
			state.searchText = text;
			searchPending = true;
			searchChangedAt = Util.getMillis();
		});
		body.row(searchField, helpButton, recentToggle);

		// The picker has no Apply to, Pause or Sort: they act on places, and its rows have no quick actions.
		if (picker == null) {
			buildPlaceRow(body);
		}

		body.label(() -> Component.translatable(count == 1 ? "soundsculptor.sounds.count.one" : "soundsculptor.sounds.count", count));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.sounds.list"));
		list.setEmptyText(() -> Component.translatable(!state.recentOnly ? "soundsculptor.sounds.empty"
				: query.isEmpty() ? "soundsculptor.sounds.empty_recent" : "soundsculptor.sounds.empty_recent_filtered"));
		body.fill(list);

		entries = KnownSounds.entries();
		query = SearchQuery.parse(state.searchText);
		refresh();
	}

	/** Apply to, Pause and Sort (the Sounds tab only). */
	private void buildPlaceRow(SculptScreen.Body body) {
		pauseToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(Component.translatable("soundsculptor.sounds.pause")),
				Component.translatable("soundsculptor.sounds.pause"), state.paused, this::setPaused),
				Component.translatable("soundsculptor.sounds.pause.tip"));
		sortDropdown = Tooltips.describe(new SculptDropdown<>(sortWidth(), Component.translatable("soundsculptor.sounds.sort"),
				List.of(SortOrder.values()), SoundsPanel::sortName, state.sort, this::setSort),
				Component.translatable("soundsculptor.sounds.sort.tip"));
		applyChoices = computeApplyChoices();
		Component applyLabel = Component.translatable("soundsculptor.sounds.apply_to");
		int share = Math.max(MIN_APPLY_TO_WIDTH,
				Math.min(MAX_APPLY_TO_WIDTH, body.width() - pauseToggle.getWidth() - sortDropdown.getWidth() - 2 * Theme.GAP));
		// Never narrower than "Apply to: Everywhere" in full; a long zone name is cut instead.
		int applyWidth = Math.max(share, SculptDropdown.widthFor(applyLabel, List.of(applyName(ApplyTarget.EVERYWHERE))));
		applyToDropdown = Tooltips.describe(new SculptDropdown<>(applyWidth, applyLabel, applyChoices, SoundsPanel::applyName,
				applyTarget(), this::setApplyTarget), Component.translatable("soundsculptor.sounds.apply_to.tip"));
		// Pause and Sort keep their place while hidden, so turning the filter on or off moves nothing else. When the
		// three do not fit on one line (a narrow window, a longer translation), Sort and then Pause move to the next.
		body.flow(applyToDropdown, pauseToggle, sortDropdown);
		showFilterControls();
	}

	private static Component applyName(ApplyTarget choice) {
		return Component.translatable(choice.translationKey(), choice.args().toArray());
	}

	private static int sortWidth() {
		List<Component> names = new ArrayList<>();
		for (SortOrder order : SortOrder.values()) {
			names.add(sortName(order));
		}
		return SculptDropdown.widthFor(Component.translatable("soundsculptor.sounds.sort"), names);
	}

	private static Component sortName(SortOrder order) {
		return Component.translatable("soundsculptor.sounds.sort." + order.name().toLowerCase(Locale.ROOT));
	}

	private void showFilterControls() {
		if (pauseToggle != null) {
			pauseToggle.visible = state.recentOnly;
			sortDropdown.visible = state.recentOnly;
		}
	}

	// ---- State ----

	public boolean isRecentOnly() {
		return state.recentOnly;
	}

	/** The Recently played filter (FR-1.0); remembered while the game runs. */
	public void setRecentOnly(boolean value) {
		state.recentOnly = value;
		if (recentToggle != null) {
			recentToggle.setValue(value);
			showFilterControls();
		}
		refresh();
	}

	public boolean isPaused() {
		return state.paused;
	}

	/** Pause freezes the recently played list for reading (FR-1.3); actions and search still update it. */
	public void setPaused(boolean value) {
		state.paused = value;
		if (pauseToggle != null) {
			pauseToggle.setValue(value);
		}
		if (!value) {
			refresh();
		}
	}

	public void setSort(SortOrder value) {
		state.sort = value;
		if (sortDropdown != null) {
			sortDropdown.setValue(value);
		}
		refresh();
	}

	public String searchText() {
		return state.searchText;
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
	 *         (never in picker mode, which has no Apply to)
	 */
	public boolean tick() {
		ticks++;
		if (searchPending && Util.getMillis() - searchChangedAt >= SEARCH_DELAY_MS) {
			runSearch();
		}
		if (ticks % REFRESH_TICKS != 0) {
			return false;
		}
		if (!(state.recentOnly && state.paused)) {
			refresh();
		}
		return picker == null && !host.hasOverlay() && !computeApplyChoices().equals(applyChoices);
	}

	/** Runs the search now (normally 150 ms after the last keystroke). */
	public void runSearch() {
		searchPending = false;
		query = SearchQuery.parse(state.searchText);
		refresh();
	}

	/**
	 * Brings the rows up to date. The list keeps the focused row and the scroll position.
	 *
	 * <p>The full list has about 2,000 rows, so a refresh does only what changed (PRD 9.1, 11.2). It does nothing when
	 * the log's {@linkplain RecentSounds#generation generation}, the workspace, the query, the filter, the sort, Apply to
	 * and Show distance are as they were, and the clock has not reached the next second while a row shows recent data
	 * (its "2s ago" or play count may change). Otherwise each row is rebuilt only if what it shows changed (its recent
	 * values to the "ago" granularity, or its "Seen as" category); the others are reused, and the list is replaced only if
	 * a row or the order changed. A new workspace, Apply to choice or Show distance setting rebuilds every row, because
	 * the Mute buttons and the columns depend on them. Does nothing while a pop-up is open, so focus stays in it.
	 *
	 * <p>In picker mode a tick or untick also counts as a change, and a row's inputs include whether it is picked and
	 * whether the set has it, so a tick rebuilds only its own row.
	 */
	public void refresh() {
		if (list == null || host.hasOverlay()) {
			return;
		}
		refreshCount++;
		long now = Util.getMillis();
		RecentSounds log = RecentLog.get().sounds();
		Workspace ws = ProfileManager.workspace();
		ApplyTarget target = applyTarget();
		boolean distance = SoundSculptorSettings.showDistance();
		long second = now / 1000;
		// workspace() is a new record each call: compare its parts, which change identity only when changed.
		if (ws.profile() != shownProfile || ws.library() != shownLibrary || !target.equals(shownTarget) || distance != shownDistance) {
			rowCache.clear();
		} else if (log.generation() == shownGeneration && query == shownQuery && state.recentOnly == shownRecentOnly && state.sort == shownSort
				&& pickVersion == shownPickVersion && (!shownAnyRecent || second == shownSecond)) {
			return;
		}
		shownProfile = ws.profile();
		shownLibrary = ws.library();
		shownTarget = target;
		shownDistance = distance;
		shownQuery = query;
		shownRecentOnly = state.recentOnly;
		shownSort = state.sort;
		shownPickVersion = pickVersion;
		shownSecond = second;
		List<RecentRow> recent = log.snapshot(now, SortOrder.MOST_RECENT, null);
		// Read after the snapshot, which drops expired rows.
		shownGeneration = log.generation();
		List<BrowserRow> found = SoundBrowser.rows(recent, entries, query, state.recentOnly, state.sort, KnownSounds.seenExcluded());
		Predicate<String> muted = null;
		boolean anyRecent = false;
		List<SculptList.Row> rows = new ArrayList<>(found.size());
		for (BrowserRow row : found) {
			Object inputs = rowInputs(row, now, log);
			if (picker != null) {
				inputs = new PickInputs(inputs, picker.inSet(row.soundId()), picker.picked(row.soundId()));
			}
			CachedRow cached = rowCache.get(row.soundId());
			if (cached == null || !cached.inputs().equals(inputs)) {
				if (muted == null && picker == null) {
					muted = mutedAtApplyTarget();
				}
				cached = new CachedRow(inputs, toListRow(row, now, muted));
				rowCache.put(row.soundId(), cached);
				rowsBuilt++;
			}
			anyRecent |= row.recent() != null;
			rows.add(cached.row());
		}
		shownAnyRecent = anyRecent;
		count = rows.size();
		if (!sameRows(rows, list.children())) {
			list.setRows(rows);
		}
	}

	/** What a row shows besides its name and ID: equal inputs mean the row would be built the same. */
	private static Object rowInputs(BrowserRow row, long now, RecentSounds log) {
		RecentRow recent = row.recent();
		if (recent == null) {
			String category = log.seenCategory(row.soundId());
			return category == null ? "" : category;
		}
		return new RecentInputs(recent.category(), recent.count(), agoBucket(now - recent.lastPlayedMs()), recent.band(),
				recent.status(), recent.zoneName());
	}

	/** The time since the last play as {@link #ago} shows it: seconds up to a minute, then minutes. */
	private static long agoBucket(long millis) {
		long seconds = Math.max(0, millis / 1000);
		return seconds < 60 ? seconds : 60 + seconds / 60;
	}

	private static boolean sameRows(List<SculptList.Row> a, List<SculptList.Row> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i)) {
				return false;
			}
		}
		return true;
	}

	/** A row with recent data, by what it shows (FR-1.2). */
	private record RecentInputs(String category, int count, long ago, DistanceBand band, PlayStatus status, String zoneName) {
	}

	/** A picker row's inputs: what it shows, whether the set has the sound, and whether it is ticked. */
	private record PickInputs(Object shown, boolean inSet, boolean picked) {
	}

	/** A built row and what it was built from. */
	private record CachedRow(Object inputs, SculptList.Row row) {
	}

	/** @param muted which sounds are muted at the Apply to place; {@code null} in picker mode */
	private SculptList.Row toListRow(BrowserRow row, long now, Predicate<String> muted) {
		String id = row.soundId();
		String name = row.subtitle() == null ? SoundNames.readable(id) : row.subtitle();
		boolean inSet = picker != null && picker.inSet(id);
		// Picker mode (FR-1.7): a sound the set has says so where its status would be.
		String mark = inSet ? IN_SET.getString() : null;
		Widths widths = widths();
		List<SculptList.Row.Column> columns = new ArrayList<>();
		columns.add(new SculptList.Row.Column(() -> name, 0, 0));
		RecentRow recent = row.recent();
		if (recent == null) {
			String seen = mark != null ? mark : seenText(id);
			columns.add(new SculptList.Row.Column(() -> seen, widths.seen(), 0));
			columns.add(new SculptList.Row.Column(() -> id, 0, 1));
		} else {
			recentColumns(columns, recent, now, mark, widths);
		}
		if (picker != null) {
			return new SculptList.Row(id, columns, pickButtons(id, inSet));
		}
		boolean isMuted = muted.test(id);
		return new SculptList.Row(id, columns, List.of(muteButton(id, isMuted), levelButton(id), editButton(id),
				addToSetButton(id), whyButton(id)));
	}

	/**
	 * FR-1.2: subtitle, category and status (text plus an icon character, never color alone) on top; ID, the zone that
	 * decided the latest play (FR-9), plays with time since the last, and the distance band (unless hidden) below.
	 *
	 * <p>The zone name may be long: its column is at most {@value #ZONE_WIDTH} px, cut with "…", and gives way in a
	 * narrow row so the ID stays readable.
	 *
	 * @param mark shown instead of the status in picker mode ("In this set"), or {@code null}
	 */
	private static void recentColumns(List<SculptList.Row.Column> columns, RecentRow row, long now, String mark, Widths widths) {
		String category = row.category() == null ? "?" : categoryName(row.category()).getString();
		String status = mark != null ? mark : statusText(row.status()).getString();
		String plays = Component.translatable("soundsculptor.sounds.plays", row.count(), ago(now - row.lastPlayedMs())).getString();
		columns.add(new SculptList.Row.Column(() -> category, widths.category(), 0));
		columns.add(new SculptList.Row.Column(() -> status, widths.status(), 0));
		columns.add(new SculptList.Row.Column(row::id, 0, 1));
		if (row.zoneName() != null) {
			Component zone = Component.translatable("soundsculptor.sounds.zone", row.zoneName());
			String zoneText = zone.getString();
			columns.add(new SculptList.Row.Column(() -> zoneText, Math.min(ZONE_WIDTH, SculptList.Row.Column.widthFor(zone)), 1, true));
		}
		columns.add(new SculptList.Row.Column(() -> plays, widths.plays(), 1));
		if (SoundSculptorSettings.showDistance()) {
			String band = Component.translatable(row.band().translationKey()).getString();
			columns.add(new SculptList.Row.Column(() -> band, widths.band(), 1));
		}
	}

	/** FR-3.2: "Seen as: Friendly Creatures" when the sound played this session outside the last 3 minutes, else "". */
	private static String seenText(String soundId) {
		String category = RecentLog.get().sounds().seenCategory(soundId);
		return category == null ? "" : seenAs(category).getString();
	}

	private static Component seenAs(String category) {
		return Component.translatable("soundsculptor.sounds.seen_as", categoryName(category));
	}

	private static Component categoryName(String category) {
		return Component.translatable("soundCategory." + category);
	}

	/** The status with its icon character, e.g. "▶ Playing" (FR-1.2: text and an icon, never color alone). */
	private static Component statusText(PlayStatus status) {
		return Component.translatable("soundsculptor.sounds.status_line." + status.name().toLowerCase(Locale.ROOT),
				Component.translatable(status.translationKey()));
	}

	/**
	 * The fixed row columns' widths, each fitting every text the column can show, so rows line up and nothing is cut;
	 * and the Like this… button's.
	 */
	private record Widths(int category, int status, int seen, int plays, int band, int likeThis) {
	}

	/** Measured the first time a row is built, in the language shown now. */
	private Widths widths() {
		if (widths != null) {
			return widths;
		}
		List<Component> categories = new ArrayList<>();
		List<Component> seen = new ArrayList<>();
		for (SoundSource source : SoundSource.values()) {
			categories.add(categoryName(source.getName()));
			seen.add(seenAs(source.getName()));
		}
		List<Component> statuses = new ArrayList<>();
		for (PlayStatus status : PlayStatus.values()) {
			statuses.add(statusText(status));
		}
		List<Component> bands = new ArrayList<>();
		for (DistanceBand band : DistanceBand.values()) {
			bands.add(Component.translatable(band.translationKey()));
		}
		if (picker != null) {
			statuses.add(IN_SET);
			seen.add(IN_SET);
		}
		Component plays = Component.translatable("soundsculptor.sounds.plays", PLAYS_MEASURED,
				Component.translatable("soundsculptor.sounds.ago.seconds", 59));
		widths = new Widths(widthFor(categories), widthFor(statuses), widthFor(seen), SculptList.Row.Column.widthFor(plays),
				widthFor(bands), SculptButton.widthFor(Component.translatable("soundsculptor.picker.like_this")));
		return widths;
	}

	private static int widthFor(List<Component> texts) {
		return SculptList.Row.Column.widthFor(texts.toArray(Component[]::new));
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
		String worldKey = ProfileManager.key();
		// Through the memory, so Unmute right after Mute puts the entry back exactly as it was.
		EntryEdit edit = ProfileManager.mutes().toggleMute(worldKey, ws, where, soundId, ws.profile().settings().cueDefault());
		QuickActionResult result = QuickActions.apply(ws, where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
			return;
		}
		if (result.changed()) {
			ProfileManager.mutes().applied(worldKey, where, soundId, edit, result.before(), result.after());
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
		LevelPopup popup = LevelPopup.open(host, entry == null ? SetEntry.of("new", soundId) : entry, this::applyLevel, this::finishLevel);
		levelSlider = popup.slider;
		levelField = popup.field;
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
		if (result.changed()) {
			ProfileManager.change(current -> QuickActions.apply(current, where, soundId, edit).after());
		}
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

	// ---- Picker mode (FR-1.7, FR-4.8) ----

	/** A picker row's buttons: the checkbox (none when the set has the sound), then Like this…. */
	private List<SculptButton> pickButtons(String soundId, boolean inSet) {
		Component likeLabel = Component.translatable("soundsculptor.picker.like_this");
		SculptButton likeThis = Tooltips.describe(SculptButton.text(likeLabel, widths().likeThis(), () -> openLikeThis(soundId)),
				Component.translatable("soundsculptor.picker.like_this.tip"));
		if (inSet) {
			return List.of(likeThis);
		}
		SculptCheckbox box = new SculptCheckbox(Component.translatable("soundsculptor.picker.pick"),
				Component.translatable("soundsculptor.picker.pick.tip"), picker.picked(soundId), () -> togglePick(soundId));
		return List.of(box, likeThis);
	}

	/** Ticks or unticks a sound; nothing for a sound the set has. Rebuilds only its row, which keeps keyboard focus. */
	public void togglePick(String soundId) {
		if (picker == null) {
			return;
		}
		picker.toggleSound(soundId);
		pickVersion++;
		refresh();
		picksChanged.run();
	}

	/**
	 * Like this… (FR-4.8): the sound's groups in a pop-up, closest first, each with how many known sounds it matches and
	 * a checkbox, or "In this set" when the set has the group. Esc or Done closes it and returns to the row's Like this….
	 */
	public void openLikeThis(String soundId) {
		host.closeOverlay();
		likeThisSound = soundId;
		likeThisGroups = LikeThis.groups(soundId, KnownSounds.ids());
		int x = list.getX();
		int y = list.getY();
		// Over the whole list, so no row behind it shows at the side.
		int width = Math.min(list.getWidth(), host.width - x - Theme.PADDING);
		int height = Math.max(SculptList.ROW_HEIGHT + Theme.WIDGET_HEIGHT + 3 * Theme.GAP,
				host.height - SculptScreen.FOOTER_HEIGHT - Theme.PADDING - y);
		SculptButton done = SculptButton.text(Component.translatable("soundsculptor.ui.done"), host::closeOverlay);
		int listHeight = height - done.getHeight() - 3 * Theme.GAP;
		SculptList groups = new SculptList(width - 2 * Theme.GAP, listHeight,
				Component.translatable("soundsculptor.picker.like_this.list", soundName(soundId)));
		groups.setPosition(x + Theme.GAP, y + Theme.GAP);
		groups.setWidth(width - 2 * Theme.GAP);
		groups.setHeight(listHeight);
		done.setPosition(x + width - Theme.GAP - done.getWidth(), groups.getBottom() + Theme.GAP);
		likeThisList = groups;
		groups.setRows(groupRows());
		host.openOverlay(x, y, width, height, List.of(groups, done), groups, () -> {
			String sound = likeThisSound;
			likeThisList = null;
			likeThisSound = null;
			likeThisGroups = List.of();
			SculptList.Row row = list.findRow(sound);
			if (row != null) {
				focusRowButton(sound, row.buttons().size() - 1);
			}
		});
		if (!groups.children().isEmpty()) {
			groups.focusRow(host, groups.children().get(0));
		}
	}

	/** Ticks or unticks a Like this group; nothing for a group the set has. */
	public void toggleGroup(String pattern) {
		if (picker == null || likeThisList == null) {
			return;
		}
		picker.toggleGroup(pattern);
		likeThisList.setRows(groupRows());
		picksChanged.run();
	}

	private List<SculptList.Row> groupRows() {
		List<SculptList.Row> rows = new ArrayList<>(likeThisGroups.size());
		for (LikeThis.Group group : likeThisGroups) {
			String pattern = group.pattern();
			boolean inSet = picker.groupInSet(pattern);
			String mark = inSet ? IN_SET.getString() : "";
			String matches = ProfileManager.component(group.matches()).getString();
			List<SculptList.Row.Column> columns = List.of(
					new SculptList.Row.Column(() -> pattern, 0, 0),
					new SculptList.Row.Column(() -> mark, SculptList.Row.Column.widthFor(IN_SET), 0),
					new SculptList.Row.Column(() -> matches, 0, 1));
			List<SculptButton> buttons = inSet ? List.of() : List.of(new SculptCheckbox(Component.translatable("soundsculptor.picker.pick"),
					Component.translatable("soundsculptor.picker.pick_group.tip"), picker.groupPicked(pattern), () -> toggleGroup(pattern)));
			rows.add(new SculptList.Row(pattern, columns, buttons));
		}
		return rows;
	}

	// ---- Apply to ----

	/** Where quick actions put their change (FR-1.5, FR-3.7): Everywhere, or the chosen zone while the player stands in it. */
	public ApplyTarget applyTarget() {
		boolean sameWorld = state.applyWorldKey != null && state.applyWorldKey.equals(ProfileManager.key());
		return ApplyTo.select(applyChoices, sameWorld ? state.applyZoneId : null);
	}

	/** Chooses where quick actions put their change; refreshes the rows, whose Mute/Unmute follows the choice. */
	public void setApplyTarget(ApplyTarget target) {
		state.applyZoneId = target.zoneId();
		state.applyWorldKey = ProfileManager.key();
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
		examples.focusRow(host, rows.get(0));
	}

	public boolean isHelpShown() {
		return helpList != null;
	}

	// ---- Keys ----

	/** P toggles Pause while the list has focus and the Recently played filter is on (FR-1.3). */
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == Keys.P && picker == null && state.recentOnly && list != null && host.getFocused() == list
				&& !host.hasOverlay()) {
			setPaused(!state.paused);
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
