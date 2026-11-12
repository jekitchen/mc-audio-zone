package soundsculptor.mc;

import soundsculptor.core.diag.DiagnosticsReport;
import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.ApplyTo;
import soundsculptor.core.quick.QuickActionResult;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.core.zones.Point;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.search.SoundSearch;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundNames;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptTabButton;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;
import soundsculptor.mc.ui.demo.UiKitDemoScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * The main Sound Sculptor screen (FR-12.1). Header: active profile and the Bypass toggle (FR-13.1). Tabs: Recently
 * Played (FR-1, with the Mute and Edit quick actions), Search, Rules (the global rules), Zones, Profiles (FR-11.2,
 * FR-11.3) and Settings; Inspect arrives with M12. Lives in {@code soundsculptor.mc} because it uses Minecraft's sound manager and GUI
 * classes.
 */
public final class SoundSculptorScreen extends SculptScreen {
	public static final int TAB_RECENT = 0;
	public static final int TAB_SEARCH = 1;
	public static final int TAB_RULES = 2;
	public static final int TAB_ZONES = 3;
	public static final int TAB_PROFILES = 4;
	public static final int TAB_SETTINGS = 5;
	/** FR-3.4: results update 150 ms after the player stops typing. */
	public static final long SEARCH_DELAY_MS = 150;
	/** FR-1.6: the list refreshes at most 4 times per second (every 5 ticks). */
	public static final int REFRESH_TICKS = 5;

	private static final int PAUSE_WIDTH = 80;
	private static final int SORT_WIDTH = 150;
	private static final int STATUS_WIDTH = 70;
	private static final int CATEGORY_WIDTH = 95;
	private static final int PLAYS_WIDTH = 75;
	private static final int BAND_WIDTH = 60;
	private static final int ZONE_WIDTH = 110;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier WHY_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/help");
	private static final int PAGE_TAB_WIDTH = 100;
	private static final int OUTLINE_MODE_WIDTH = 240;
	private static final int SEEN_WIDTH = 150;
	private static final int APPLY_TO_WIDTH = 200;

	// Remembered while the game runs, like the last used tab.
	private static SortOrder sort = SortOrder.MOST_RECENT;
	private static boolean paused;
	/** The zone "Apply to" chose last (FR-1.5, FR-3.7), or null for Everywhere; shared by Recently Played and Search. */
	private static String applyZoneId;
	private static SettingsPage settingsPage = SettingsPage.GENERAL;

	/** Settings pages (PRD 6.1); Zones and Accessibility arrive with their milestones. */
	public enum SettingsPage {
		GENERAL, ZONES, DIAGNOSTICS
	}
	private static String filter = "";
	private static String searchText = "";
	private static boolean searchHelp;

	public SculptList recentList;
	public SculptToggle pauseToggle;
	public SculptDropdown<SortOrder> sortDropdown;
	public SculptTextField filterField;
	public SculptToggle bypassToggle;
	public SculptList rulesList;
	private RuleListPanel rulesPanel;
	private ZoneListPanel zonesPanel;
	private ProfileListPanel profilesPanel;
	public SculptTextField searchField;
	public SculptButton searchHelpButton;
	public SculptList searchList;
	/** "Apply to" on Recently Played and Search (FR-1.5, FR-3.7). */
	public SculptDropdown<ApplyTarget> applyToDropdown;
	private List<ApplyTarget> applyChoices = List.of(ApplyTarget.EVERYWHERE);
	public int searchCount;
	private boolean searchPending;
	private long searchChangedAt;
	private List<SoundEntry> searchEntries;
	public SculptButton addRuleButton;
	/** The Settings tab's page tabs, in {@link SettingsPage} order. */
	public final List<SculptTabButton> pageTabs = new ArrayList<>();
	public SculptButton copyReportButton;
	public SculptDropdown<OutlineMode> outlineModeDropdown;
	public SculptToggle throughBlocksToggle;
	private DiagnosticsReport.Data diagnostics;
	public int refreshCount;
	private int ticks;

	public SoundSculptorScreen() {
		this(null);
	}

	public SoundSculptorScreen(Screen parent) {
		super(Component.translatable("screen.soundsculptor.title"), parent);
	}

	public boolean isListPaused() {
		return paused;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				// FR-1.6: the Recently Played tab does not pause a singleplayer game, so sounds keep arriving.
				new Tab(Component.translatable("soundsculptor.tab.recent"), false),
				new Tab(Component.translatable("soundsculptor.tab.search"), true),
				new Tab(Component.translatable("soundsculptor.tab.rules"), true),
				new Tab(Component.translatable("soundsculptor.tab.zones"), true),
				new Tab(Component.translatable("soundsculptor.tab.profiles"), true),
				new Tab(Component.translatable("soundsculptor.tab.settings"), true));
	}

	@Override
	protected Component headerSubtitle() {
		return ProfileManager.isLoaded()
				? Component.translatable("soundsculptor.header.profile", ProfileManager.profile().name())
				: Component.translatable("soundsculptor.header.no_world");
	}

	@Override
	protected void addHeaderWidgets(List<AbstractWidget> header) {
		bypassToggle = Tooltips.describe(new SculptToggle(100, Component.translatable("soundsculptor.bypass"), ProfileManager.isBypassed(),
				ProfileManager::setBypass), Component.translatable("soundsculptor.bypass.tip", bypassKeyName()));
		header.add(bypassToggle);
	}

	private static Component bypassKeyName() {
		return soundsculptor.SoundSculptorClient.bypassKey.getTranslatedKeyMessage();
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			footer.add(SculptButton.text(Component.translatable("screen.soundsculptor.ui_demo"),
					() -> minecraft.gui.setScreen(new UiKitDemoScreen(this))));
		}
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		if (index != TAB_SETTINGS && index != TAB_PROFILES && !ProfileManager.isLoaded()) {
			// Opened outside a world (e.g. from Mod Menu on the title screen): sounds, rules and zones belong to a
			// world's profile (FR-11.1), so there is nothing to show or change yet.
			body.label(() -> Component.translatable("soundsculptor.no_world"));
			return;
		}
		if (index == TAB_RECENT) {
			buildRecent(body);
		} else if (index == TAB_SEARCH) {
			buildSearch(body);
		} else if (index == TAB_RULES) {
			buildRules(body);
		} else if (index == TAB_ZONES) {
			buildZones(body);
		} else if (index == TAB_PROFILES) {
			profilesPanel = new ProfileListPanel(this);
			profilesPanel.build(body);
		} else {
			buildSettings(body);
		}
	}

	// ---- Recently Played (FR-1) ----

	private void buildRecent(Body body) {
		pauseToggle = Tooltips.describe(new SculptToggle(PAUSE_WIDTH, Component.translatable("soundsculptor.recent.pause"), paused,
				value -> setPaused(value)), Component.translatable("soundsculptor.recent.pause.tip"));
		sortDropdown = Tooltips.describe(new SculptDropdown<>(SORT_WIDTH, Component.translatable("soundsculptor.recent.sort"),
				List.of(SortOrder.values()), order -> Component.translatable("soundsculptor.recent.sort." + order.name().toLowerCase(Locale.ROOT)),
				sort, this::setSort), Component.translatable("soundsculptor.recent.sort.tip"));
		int filterWidth = Math.max(60, body.width() - PAUSE_WIDTH - SORT_WIDTH - 2 * Theme.GAP);
		filterField = new SculptTextField(font, filterWidth, Component.translatable("soundsculptor.recent.filter"))
				.placeholder(Component.translatable("soundsculptor.recent.filter.hint"));
		filterField.setValue(filter);
		filterField.onChange(text -> {
			filter = text;
			refresh();
		});
		// "Apply to" goes first, so Tab still moves from the filter straight into the list.
		body.row(buildApplyTo());
		body.row(pauseToggle, sortDropdown, filterField);

		recentList = new SculptList(body.width(), 100, Component.translatable("soundsculptor.recent.list"));
		recentList.setEmptyText(() -> Component.translatable(filter.isBlank() ? "soundsculptor.recent.empty" : "soundsculptor.recent.empty_filtered"));
		body.fill(recentList);
		refresh();
	}

	public void setSort(SortOrder value) {
		sort = value;
		if (sortDropdown != null) {
			sortDropdown.setValue(value);
		}
		refresh();
	}

	public void setPaused(boolean value) {
		paused = value;
		if (pauseToggle != null) {
			pauseToggle.setValue(value);
		}
		if (!value) {
			refresh();
		}
	}

	@Override
	public void tick() {
		super.tick();
		// Bypass can also change through the hotkey or the error budget; keep the header toggle in step.
		if (bypassToggle != null && bypassToggle.value() != ProfileManager.isBypassed()) {
			bypassToggle.setValue(ProfileManager.isBypassed());
		}
		ticks++;
		if (currentTab() == TAB_SEARCH && searchPending && Util.getMillis() - searchChangedAt >= SEARCH_DELAY_MS) {
			runSearch();
		}
		if (currentTab() == TAB_SETTINGS && settingsPage == SettingsPage.DIAGNOSTICS) {
			diagnostics = Diagnostics.data();
		}
		if (currentTab() == TAB_RECENT && !paused && ticks % REFRESH_TICKS == 0) {
			refresh();
		}
		if ((currentTab() == TAB_RECENT || currentTab() == TAB_SEARCH) && ticks % REFRESH_TICKS == 0 && !hasOverlay()
				&& applyToDropdown != null && !computeApplyChoices().equals(applyChoices)) {
			// The player walked into or out of a zone: offer the zones they now stand in.
			boolean focused = getFocused() == applyToDropdown;
			rebuildWidgets();
			if (focused) {
				setInitialFocus(applyToDropdown);
			}
		}
	}

	/** Rebuilds the rows from the log. The list keeps the focused row and the scroll position. */
	public void refresh() {
		if (recentList == null || currentTab() != TAB_RECENT) {
			return;
		}
		long now = Util.getMillis();
		List<RecentRow> snapshot = RecentLog.get().sounds().snapshot(now, sort, this::subtitle);
		String needle = filter.strip().toLowerCase(Locale.ROOT);
		List<SculptList.Row> rows = new ArrayList<>(snapshot.size());
		Predicate<String> muted = mutedAtApplyTarget();
		for (RecentRow row : snapshot) {
			String name = subtitle(row.id());
			String shownName = name == null ? SoundNames.readable(row.id()) : name;
			String category = row.category() == null ? "?" : Component.translatable("soundCategory." + row.category()).getString();
			if (!needle.isEmpty() && !(shownName + " " + row.id() + " " + category).toLowerCase(Locale.ROOT).contains(needle)) {
				continue;
			}
			rows.add(toListRow(row, shownName, category, now, muted));
		}
		recentList.setRows(rows);
		refreshCount++;
	}

	private SculptList.Row toListRow(RecentRow row, String name, String category, long now, Predicate<String> muted) {
		// Status as text plus an icon character, never color alone (FR-1.2).
		String status = Component.translatable("soundsculptor.recent.status_line." + row.status().name().toLowerCase(Locale.ROOT),
				Component.translatable(row.status().translationKey())).getString();
		String plays = Component.translatable("soundsculptor.recent.plays", row.count(), ago(now - row.lastPlayedMs())).getString();
		List<SculptList.Row.Column> columns = new ArrayList<>();
		columns.add(new SculptList.Row.Column(() -> name, 0, 0));
		if (row.zoneName() != null) {
			// FR-9 AC: which zone decided the latest play.
			String zone = Component.translatable("soundsculptor.recent.zone", row.zoneName()).getString();
			columns.add(new SculptList.Row.Column(() -> zone, ZONE_WIDTH, 0));
		}
		columns.add(new SculptList.Row.Column(() -> status, STATUS_WIDTH, 0));
		columns.add(new SculptList.Row.Column(row::id, 0, 1));
		columns.add(new SculptList.Row.Column(() -> category, CATEGORY_WIDTH, 1));
		columns.add(new SculptList.Row.Column(() -> plays, PLAYS_WIDTH, 1));
		if (SoundSculptorSettings.showDistance()) {
			String band = Component.translatable(row.band().translationKey()).getString();
			columns.add(new SculptList.Row.Column(() -> band, BAND_WIDTH, 1));
		}
		return new SculptList.Row(row.id(), columns, List.of(muteButton(row.id(), muted), editSoundButton(row.id()), whyButton(row.id())));
	}

	/**
	 * Quick action Mute / Unmute (FR-1.5, FR-4.10): edits the sound's single-sound entry in the Quick changes of the
	 * place "Apply to" chose. The icon shows the sound's actual state at that place, whichever set decides it
	 * (crossed-out speaker when muted, speaker with waves when not); the label, tooltip and narration say what pressing
	 * it does.
	 *
	 * @param mutedAt the mute state at that place, from {@link #mutedAtApplyTarget}, shared by every row of one rebuild
	 */
	private SculptButton muteButton(String soundId, Predicate<String> mutedAt) {
		boolean muted = mutedAt.test(soundId);
		return SculptButton.icon(
				Identifier.fromNamespaceAndPath("soundsculptor", muted ? "icon/mute" : "icon/volume"),
				Component.translatable(muted ? "soundsculptor.recent.unmute" : "soundsculptor.recent.mute"),
				() -> toggleMute(soundId));
	}

	/**
	 * Which sounds are muted at the place "Apply to" chose: one resolver for a whole list rebuild, not one per row
	 * (Recently Played rebuilds 4 times a second, FR-1.6).
	 */
	private Predicate<String> mutedAtApplyTarget() {
		return QuickActions.mutedAt(ProfileManager.workspace(), applyTarget().place());
	}

	/** Quick action Edit: opens the rule editor on the sound's exact-ID rule (a new one if it has none). */
	private SculptButton editSoundButton(String soundId) {
		return SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.recent.edit"),
				() -> minecraft.gui.setScreen(EntryEditorScreen.forSound(this, applyTarget(), soundId)));
	}

	/** Why?: the sound trace for this sound's most recent play (FR-14.1). */
	private SculptButton whyButton(String soundId) {
		return SculptButton.icon(WHY_ICON, Component.translatable("soundsculptor.recent.why"),
				() -> minecraft.gui.setScreen(new TraceScreen(this, soundId)));
	}

	/**
	 * Mute/Unmute on a Recently Played or Search row (FR-1.5, FR-4.10): edits the sound's single-sound entry in the
	 * Quick changes of the place "Apply to" chose, saves at once and shows a 5-second toast with Undo (FR-12.5), which
	 * puts back the profile and the library together. A new mute takes the profile's Cue default; Unmute on a sound
	 * another set mutes writes "Not muted".
	 */
	public void toggleMute(String soundId) {
		ApplyTarget target = applyTarget();
		PlaceRef where = target.place();
		Workspace ws = ProfileManager.workspace();
		EntryEdit edit = QuickActions.toggleMute(ws, where, soundId, ws.profile().settings().cueDefault());
		QuickActionResult result = QuickActions.apply(ws, where, soundId, edit);
		if (!result.ok()) {
			showToast(ProfileManager.component(result.error()), null);
			return;
		}
		if (result.changed()) {
			String toastKey = (edit instanceof EntryEdit.MuteWithCue ? "soundsculptor.recent.toast.muted" : "soundsculptor.recent.toast.unmuted")
					+ (target.isEverywhere() ? "" : "_in");
			String[] args = target.isEverywhere()
					? new String[] {soundName(soundId)}
					: new String[] {soundName(soundId), target.zoneName()};
			QuickUndo.applyWorkspace(this, this::refreshLists, current -> QuickActions.apply(current, where, soundId, edit).after(),
					toastKey, args);
		}
		refreshLists();
	}

	/**
	 * Where quick actions on Recently Played and Search rows put their rule (FR-1.5, FR-3.7): Everywhere, or the zone
	 * chosen in "Apply to" while the player still stands in it.
	 */
	public ApplyTarget applyTarget() {
		return ApplyTo.select(applyChoices, applyZoneId);
	}

	/** Chooses where quick actions put their rule; refreshes the rows, whose Mute/Unmute follows the choice. */
	public void setApplyTarget(ApplyTarget target) {
		applyZoneId = target.zoneId();
		if (applyToDropdown != null && !applyToDropdown.value().equals(target)) {
			applyToDropdown.setValue(target);
		}
		refreshLists();
	}

	/** The "Apply to" choices offered now. */
	public List<ApplyTarget> applyChoices() {
		return applyChoices;
	}

	private SculptDropdown<ApplyTarget> buildApplyTo() {
		applyChoices = computeApplyChoices();
		applyToDropdown = Tooltips.describe(new SculptDropdown<>(APPLY_TO_WIDTH, Component.translatable("soundsculptor.recent.apply_to"),
				applyChoices, choice -> Component.translatable(choice.translationKey(), choice.args().toArray()),
				applyTarget(), this::setApplyTarget), Component.translatable("soundsculptor.recent.apply_to.tip"));
		return applyToDropdown;
	}

	/** Everywhere, then each zone the player is standing in (core {@link ApplyTo}). */
	private List<ApplyTarget> computeApplyChoices() {
		if (!ProfileManager.isLoaded() || minecraft == null || minecraft.player == null) {
			return List.of(ApplyTarget.EVERYWHERE);
		}
		Vec3 eye = minecraft.player.getEyePosition();
		String key = ProfileManager.key() == null ? "" : ProfileManager.key();
		return ApplyTo.choices(ProfileManager.profile(), key, WorldSession.dimension(minecraft), new Point(eye.x, eye.y, eye.z));
	}

	/** Rebuilds the open tab's list from the profile, e.g. after a quick action or Undo. */
	public void refreshLists() {
		refresh();
		if (currentTab() == TAB_SEARCH) {
			runSearch();
		}
		refreshRules();
		if (zonesPanel != null && currentTab() == TAB_ZONES) {
			zonesPanel.refresh();
		}
	}

	/** The sound's subtitle, or a readable name made from its ID, for toasts. */
	private String soundName(String soundId) {
		String name = subtitle(soundId);
		return name == null ? SoundNames.readable(soundId) : name;
	}

	private static String ago(long millis) {
		long seconds = Math.max(0, millis / 1000);
		return seconds < 60
				? Component.translatable("soundsculptor.recent.ago.seconds", seconds).getString()
				: Component.translatable("soundsculptor.recent.ago.minutes", seconds / 60).getString();
	}

	/** The sound's subtitle in the current language, or null if it has none (FR-1.2). */
	private String subtitle(String soundId) {
		Identifier id = Identifier.tryParse(soundId);
		if (id == null || minecraft == null) {
			return null;
		}
		WeighedSoundEvents events = minecraft.getSoundManager().getSoundEvent(id);
		Component subtitle = events == null ? null : events.getSubtitle();
		return subtitle == null ? null : subtitle.getString();
	}

	// ---- Search (FR-3) ----

	private void buildSearch(Body body) {
		// "Apply to" shares the search field's row, so the results keep their height in small windows.
		SculptDropdown<ApplyTarget> applyTo = buildApplyTo();
		searchField = new SculptTextField(font, body.width() - Theme.ICON_BUTTON_SIZE - applyTo.getWidth() - 2 * Theme.GAP,
				Component.translatable("soundsculptor.search.field"))
				.placeholder(Component.translatable("soundsculptor.search.hint"));
		searchField.setMaxLength(200);
		searchField.setValue(searchText);
		searchField.onChange(text -> {
			searchText = text;
			searchPending = true;
			searchChangedAt = Util.getMillis();
		});
		searchHelpButton = SculptButton.icon(WHY_ICON,
				Component.translatable(searchHelp ? "soundsculptor.search.help.hide" : "soundsculptor.search.help.show"), this::toggleSearchHelp);
		body.row(applyTo, searchField, searchHelpButton);
		if (searchHelp) {
			for (int i = 1; i <= SEARCH_HELP_LINES; i++) {
				String key = "soundsculptor.search.help." + i;
				body.label(() -> Component.translatable(key));
			}
		}
		body.label(() -> Component.translatable(searchCount == 1 ? "soundsculptor.search.count.one" : "soundsculptor.search.count", searchCount));
		searchList = new SculptList(body.width(), 100, Component.translatable("soundsculptor.search.list"));
		searchList.setEmptyText(() -> Component.translatable("soundsculptor.search.empty"));
		body.fill(searchList);
		searchEntries = null;
		runSearch();
	}

	private static final int SEARCH_HELP_LINES = 7;

	/** The "?" button shows or hides the syntax examples (FR-3.5). */
	public void toggleSearchHelp() {
		searchHelp = !searchHelp;
		rebuildWidgets();
		setInitialFocus(searchHelpButton);
	}

	public boolean isSearchHelpShown() {
		return searchHelp;
	}

	/** Runs the query now (normally 150 ms after the last keystroke). */
	public void runSearch() {
		searchPending = false;
		if (searchList == null || currentTab() != TAB_SEARCH) {
			return;
		}
		if (searchEntries == null) {
			// Built when the tab opens, so subtitles are in the current language and new resource packs are included.
			// Names are subtitles, or readable names for sounds without one, so plain words find both.
			searchEntries = KnownSounds.entries();
		}
		List<SoundEntry> found = SoundSearch.search(searchEntries, SearchQuery.parse(searchText), KnownSounds.seenExcluded());
		List<SculptList.Row> rows = new ArrayList<>(found.size());
		Predicate<String> muted = mutedAtApplyTarget();
		for (SoundEntry entry : found) {
			String id = entry.id();
			String name = entry.subtitle() == null ? SoundNames.readable(id) : entry.subtitle();
			String category = RecentLog.get().sounds().seenCategory(id);
			String seen = category == null ? "" : Component.translatable("soundsculptor.search.seen_as",
					Component.translatable("soundCategory." + category)).getString();
			List<SculptList.Row.Column> columns = List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> seen, SEEN_WIDTH, 0),
					new SculptList.Row.Column(() -> id, 0, 1));
			rows.add(new SculptList.Row(id, columns, List.of(muteButton(id, muted), editSoundButton(id), whyButton(id))));
		}
		searchCount = found.size();
		searchList.setRows(rows);
	}

	public String searchText() {
		return searchText;
	}

	// ---- Rules (PRD 6.1, FR-4) ----

	private void buildRules(Body body) {
		rulesPanel = new RuleListPanel(this, RuleTarget.GLOBAL);
		rulesPanel.build(body);
		addRuleButton = rulesPanel.addButton;
		rulesList = rulesPanel.list;
	}

	/** Rebuilds the rule rows from the profile. The list keeps the focused row and button. */
	public void refreshRules() {
		if (rulesPanel != null && currentTab() == TAB_RULES) {
			rulesPanel.refresh();
		}
	}

	public void setRuleEnabled(String ruleId, boolean enabled) {
		rulesPanel.setEnabled(ruleId, enabled);
	}

	public void moveRule(String ruleId, int delta) {
		rulesPanel.move(ruleId, delta);
	}

	public void confirmDelete(SetEntry rule) {
		rulesPanel.confirmDelete(rule);
	}

	public void deleteRule(String ruleId) {
		rulesPanel.delete(ruleId);
	}

	// ---- Zones (PRD 6.1, FR-8) ----

	private void buildZones(Body body) {
		zonesPanel = new ZoneListPanel(this);
		zonesPanel.build(body);
	}

	public ZoneListPanel zonesPanel() {
		return zonesPanel;
	}

	// ---- Profiles (FR-11.2, FR-11.3) ----

	public ProfileListPanel profilesPanel() {
		return profilesPanel;
	}

	/** FR-11.5: a profile file dropped on the Profiles tab is imported into this world's profile (the first one, if several). */
	@Override
	public void onFilesDrop(List<java.nio.file.Path> paths) {
		if (currentTab() != TAB_PROFILES || paths.isEmpty() || hasOverlay()) {
			return;
		}
		ProfileTransfer.importWithDialog(this, paths.get(0));
	}

	// ---- Settings ----

	private void buildSettings(Body body) {
		// Page tabs (M8 review): every page visible, the current one underlined, like the main tab bar.
		SettingsPage[] pages = SettingsPage.values();
		pageTabs.clear();
		for (int i = 0; i < pages.length; i++) {
			SettingsPage page = pages[i];
			pageTabs.add(new SculptTabButton(PAGE_TAB_WIDTH,
					Component.translatable("soundsculptor.settings.page." + page.name().toLowerCase(Locale.ROOT)), i, pages.length,
					page == settingsPage, () -> setSettingsPage(page)));
		}
		body.row(pageTabs.toArray(new AbstractWidget[0]));
		if (settingsPage == SettingsPage.DIAGNOSTICS) {
			buildDiagnostics(body);
			return;
		}
		if (settingsPage == SettingsPage.ZONES) {
			buildZoneSettings(body);
			return;
		}
		body.label(() -> Component.translatable("soundsculptor.settings.bypass_hint", bypassKeyName()));
		if (!ProfileManager.isLoaded()) {
			body.label(() -> Component.translatable("soundsculptor.settings.no_world"));
			return;
		}
		body.row(Tooltips.describe(new SculptToggle(160, Component.translatable("soundsculptor.settings.show_distance"),
				SoundSculptorSettings.showDistance(), SoundSculptorSettings::setShowDistance),
				Component.translatable("soundsculptor.settings.show_distance.tip")));
	}

	public void setSettingsPage(SettingsPage page) {
		if (page == settingsPage) {
			return;
		}
		settingsPage = page;
		rebuildWidgets();
		setInitialFocus(pageTabs.get(page.ordinal()));
	}

	public SettingsPage settingsPage() {
		return settingsPage;
	}

	/** Settings → Zones (PRD 6.1): FR-10.1 "Show zone outlines" and FR-10.5 "Outlines visible through blocks". */
	private void buildZoneSettings(Body body) {
		// "Outlines visible through blocks" is app-wide; "Show zone outlines" belongs to a world's profile.
		throughBlocksToggle = Tooltips.describe(new SculptToggle(OUTLINE_MODE_WIDTH, Component.translatable("soundsculptor.settings.through_blocks"),
				SoundSculptorSettings.outlinesThroughBlocks(), SoundSculptorSettings::setOutlinesThroughBlocks),
				Component.translatable("soundsculptor.settings.through_blocks.tip"));
		if (!ProfileManager.isLoaded()) {
			body.row(throughBlocksToggle);
			body.label(() -> Component.translatable("soundsculptor.settings.no_world"));
			return;
		}
		outlineModeDropdown = Tooltips.describe(new SculptDropdown<>(OUTLINE_MODE_WIDTH, Component.translatable("soundsculptor.settings.outlines"),
				List.of(OutlineMode.values()),
				mode -> Component.translatable("soundsculptor.settings.outlines." + mode.name().toLowerCase(Locale.ROOT)),
				SoundSculptorSettings.outlineMode(), SoundSculptorSettings::setOutlineMode),
				Component.translatable("soundsculptor.settings.outlines.tip", outlinesKeyName()));
		body.row(outlineModeDropdown);
		body.row(throughBlocksToggle);
	}

	private static Component outlinesKeyName() {
		return soundsculptor.SoundSculptorClient.outlinesKey.isUnbound()
				? Component.translatable("soundsculptor.settings.outlines.key_unbound")
				: soundsculptor.SoundSculptorClient.outlinesKey.getTranslatedKeyMessage();
	}

	/** FR-13.3: live numbers, refreshed every tick, and Copy report. */
	private void buildDiagnostics(Body body) {
		diagnostics = Diagnostics.data();
		body.label(() -> Component.translatable("soundsculptor.diag.sounds", diagnostics.sounds().count()));
		body.label(() -> Component.translatable("soundsculptor.diag.per_sound", micros(diagnostics.sounds().averageMicros()),
				micros(diagnostics.sounds().maxMicros()), micros(diagnostics.peakSoundMicros())));
		body.label(() -> timing("soundsculptor.diag.tick", diagnostics.tick()));
		body.label(() -> timing("soundsculptor.diag.outlines", diagnostics.outlines()));
		body.label(() -> Component.translatable("soundsculptor.diag.active", diagnostics.activeSets(),
				diagnostics.librarySets(), diagnostics.activeEntries(), diagnostics.zones()));
		body.label(() -> Double.isNaN(diagnostics.cacheHitRate())
				? Component.translatable("soundsculptor.diag.cache.none")
				: Component.translatable("soundsculptor.diag.cache", String.format(Locale.ROOT, "%.1f", diagnostics.cacheHitRate() * 100)));
		body.label(() -> Component.translatable("soundsculptor.diag.errors", diagnostics.errors()));
		body.label(() -> Component.translatable("soundsculptor.diag.memory", MemoryEstimate.megabytes(Diagnostics.memoryBytes()),
				MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES)));
		copyReportButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.diag.copy"), this::copyReport),
				Component.translatable("soundsculptor.diag.copy.tip"));
		body.row(copyReportButton);
	}

	private static Component timing(String key, TimingWindow.Stats stats) {
		return Component.translatable(key, micros(stats.averageMicros()), micros(stats.maxMicros()));
	}

	private static String micros(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** The numbers on the Diagnostics page, as last refreshed. */
	public DiagnosticsReport.Data diagnostics() {
		return diagnostics;
	}

	public void copyReport() {
		minecraft.keyboardHandler.setClipboard(Diagnostics.report());
		showToast(Component.translatable("soundsculptor.diag.copied"), null);
	}

	// ---- Keys ----

	/** P toggles Pause while the list has focus (FR-1.3); the Bypass hotkey works here too (FR-13.1). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (soundsculptor.SoundSculptorClient.bypassKey.matches(event) && !hasOverlay() && !(getFocused() instanceof SculptTextField)) {
			ProfileManager.toggleBypass();
			bypassToggle.setValue(ProfileManager.isBypassed());
			return true;
		}
		if (event.key() == InputConstants.KEY_P && getFocused() == recentList && recentList != null && !hasOverlay()) {
			setPaused(!paused);
			return true;
		}
		return super.keyPressed(event);
	}
}
