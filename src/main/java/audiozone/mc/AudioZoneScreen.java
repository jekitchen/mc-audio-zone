package audiozone.mc;

import audiozone.core.diag.DiagnosticsReport;
import audiozone.core.diag.MemoryEstimate;
import audiozone.core.diag.TimingWindow;
import audiozone.core.outline.OutlineMode;
import audiozone.core.recent.RecentRow;
import audiozone.core.recent.SortOrder;
import audiozone.core.profile.Profile;
import audiozone.core.profile.RuleTarget;
import audiozone.core.quick.ApplyTarget;
import audiozone.core.quick.ApplyTo;
import audiozone.core.quick.QuickActionResult;
import audiozone.core.quick.QuickActions;
import audiozone.core.trace.RuleEdit;
import audiozone.core.trace.TraceEditError;
import audiozone.core.zones.Point;
import audiozone.core.rules.Rule;
import audiozone.core.search.SoundSearch;
import audiozone.core.search.SoundEntry;
import audiozone.core.search.SoundNames;
import audiozone.core.search.SearchQuery;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzTabButton;
import audiozone.mc.ui.AzDropdown;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.AzToggle;
import audiozone.mc.ui.Theme;
import audiozone.mc.ui.Tooltips;
import audiozone.mc.ui.demo.UiKitDemoScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The main Audio Zone screen (FR-12.1). Header: active profile and the Bypass toggle (FR-13.1). Tabs: Recently
 * Played (FR-1, with the Mute and Edit quick actions), Search, Rules (the global rules), Zones, Profiles (FR-11.2,
 * FR-11.3) and Settings; Inspect arrives with M12. Lives in {@code audiozone.mc} because it uses Minecraft's sound manager and GUI
 * classes.
 */
public final class AudioZoneScreen extends AzScreen {
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
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/edit");
	private static final Identifier WHY_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/help");
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

	public AzList recentList;
	public AzToggle pauseToggle;
	public AzDropdown<SortOrder> sortDropdown;
	public AzTextField filterField;
	public AzToggle bypassToggle;
	public AzList rulesList;
	private RuleListPanel rulesPanel;
	private ZoneListPanel zonesPanel;
	private ProfileListPanel profilesPanel;
	public AzTextField searchField;
	public AzButton searchHelpButton;
	public AzList searchList;
	/** "Apply to" on Recently Played and Search (FR-1.5, FR-3.7). */
	public AzDropdown<ApplyTarget> applyToDropdown;
	private List<ApplyTarget> applyChoices = List.of(ApplyTarget.EVERYWHERE);
	public int searchCount;
	private boolean searchPending;
	private long searchChangedAt;
	private List<SoundEntry> searchEntries;
	public AzButton addRuleButton;
	/** The Settings tab's page tabs, in {@link SettingsPage} order. */
	public final List<AzTabButton> pageTabs = new ArrayList<>();
	public AzButton copyReportButton;
	public AzDropdown<OutlineMode> outlineModeDropdown;
	public AzToggle throughBlocksToggle;
	private DiagnosticsReport.Data diagnostics;
	public int refreshCount;
	private int ticks;

	public AudioZoneScreen() {
		this(null);
	}

	public AudioZoneScreen(Screen parent) {
		super(Component.translatable("screen.audiozone.title"), parent);
	}

	public boolean isListPaused() {
		return paused;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				// FR-1.6: the Recently Played tab does not pause a singleplayer game, so sounds keep arriving.
				new Tab(Component.translatable("audiozone.tab.recent"), false),
				new Tab(Component.translatable("audiozone.tab.search"), true),
				new Tab(Component.translatable("audiozone.tab.rules"), true),
				new Tab(Component.translatable("audiozone.tab.zones"), true),
				new Tab(Component.translatable("audiozone.tab.profiles"), true),
				new Tab(Component.translatable("audiozone.tab.settings"), true));
	}

	@Override
	protected Component headerSubtitle() {
		return ProfileManager.isLoaded()
				? Component.translatable("audiozone.header.profile", ProfileManager.profile().name())
				: Component.translatable("audiozone.header.no_world");
	}

	@Override
	protected void addHeaderWidgets(List<AbstractWidget> header) {
		bypassToggle = Tooltips.describe(new AzToggle(100, Component.translatable("audiozone.bypass"), ProfileManager.isBypassed(),
				ProfileManager::setBypass), Component.translatable("audiozone.bypass.tip", bypassKeyName()));
		header.add(bypassToggle);
	}

	private static Component bypassKeyName() {
		return audiozone.AudioZoneClient.bypassKey.getTranslatedKeyMessage();
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			footer.add(AzButton.text(Component.translatable("screen.audiozone.ui_demo"),
					() -> minecraft.gui.setScreen(new UiKitDemoScreen(this))));
		}
		footer.add(AzButton.text(Component.translatable("audiozone.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		if (index != TAB_SETTINGS && index != TAB_PROFILES && !ProfileManager.isLoaded()) {
			// Opened outside a world (e.g. from Mod Menu on the title screen): sounds, rules and zones belong to a
			// world's profile (FR-11.1), so there is nothing to show or change yet.
			body.label(() -> Component.translatable("audiozone.no_world"));
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
		pauseToggle = Tooltips.describe(new AzToggle(PAUSE_WIDTH, Component.translatable("audiozone.recent.pause"), paused,
				value -> setPaused(value)), Component.translatable("audiozone.recent.pause.tip"));
		sortDropdown = Tooltips.describe(new AzDropdown<>(SORT_WIDTH, Component.translatable("audiozone.recent.sort"),
				List.of(SortOrder.values()), order -> Component.translatable("audiozone.recent.sort." + order.name().toLowerCase(Locale.ROOT)),
				sort, this::setSort), Component.translatable("audiozone.recent.sort.tip"));
		int filterWidth = Math.max(60, body.width() - PAUSE_WIDTH - SORT_WIDTH - 2 * Theme.GAP);
		filterField = new AzTextField(font, filterWidth, Component.translatable("audiozone.recent.filter"))
				.placeholder(Component.translatable("audiozone.recent.filter.hint"));
		filterField.setValue(filter);
		filterField.onChange(text -> {
			filter = text;
			refresh();
		});
		// "Apply to" goes first, so Tab still moves from the filter straight into the list.
		body.row(buildApplyTo());
		body.row(pauseToggle, sortDropdown, filterField);

		recentList = new AzList(body.width(), 100, Component.translatable("audiozone.recent.list"));
		recentList.setEmptyText(() -> Component.translatable(filter.isBlank() ? "audiozone.recent.empty" : "audiozone.recent.empty_filtered"));
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
		List<AzList.Row> rows = new ArrayList<>(snapshot.size());
		for (RecentRow row : snapshot) {
			String name = subtitle(row.id());
			String shownName = name == null ? SoundNames.readable(row.id()) : name;
			String category = row.category() == null ? "?" : Component.translatable("soundCategory." + row.category()).getString();
			if (!needle.isEmpty() && !(shownName + " " + row.id() + " " + category).toLowerCase(Locale.ROOT).contains(needle)) {
				continue;
			}
			rows.add(toListRow(row, shownName, category, now));
		}
		recentList.setRows(rows);
		refreshCount++;
	}

	private AzList.Row toListRow(RecentRow row, String name, String category, long now) {
		// Status as text plus an icon character, never color alone (FR-1.2).
		String status = Component.translatable("audiozone.recent.status_line." + row.status().name().toLowerCase(Locale.ROOT),
				Component.translatable(row.status().translationKey())).getString();
		String plays = Component.translatable("audiozone.recent.plays", row.count(), ago(now - row.lastPlayedMs())).getString();
		List<AzList.Row.Column> columns = new ArrayList<>();
		columns.add(new AzList.Row.Column(() -> name, 0, 0));
		if (row.zoneName() != null) {
			// FR-9 AC: which zone decided the latest play.
			String zone = Component.translatable("audiozone.recent.zone", row.zoneName()).getString();
			columns.add(new AzList.Row.Column(() -> zone, ZONE_WIDTH, 0));
		}
		columns.add(new AzList.Row.Column(() -> status, STATUS_WIDTH, 0));
		columns.add(new AzList.Row.Column(row::id, 0, 1));
		columns.add(new AzList.Row.Column(() -> category, CATEGORY_WIDTH, 1));
		columns.add(new AzList.Row.Column(() -> plays, PLAYS_WIDTH, 1));
		if (AudioZoneSettings.showDistance()) {
			String band = Component.translatable(row.band().translationKey()).getString();
			columns.add(new AzList.Row.Column(() -> band, BAND_WIDTH, 1));
		}
		return new AzList.Row(row.id(), columns, List.of(muteButton(row.id()), editSoundButton(row.id()), whyButton(row.id())));
	}

	/**
	 * Quick action Mute / Unmute: sets mute on the sound's exact-ID rule (FR-1.5). The icon shows the sound's current
	 * state (crossed-out speaker when muted, speaker with waves when not); the label, tooltip and narration say what
	 * pressing it does.
	 */
	private AzButton muteButton(String soundId) {
		boolean muted = QuickActions.isMuted(ProfileManager.profile(), applyTarget(), soundId);
		return AzButton.icon(
				Identifier.fromNamespaceAndPath("audiozone", muted ? "icon/mute" : "icon/volume"),
				Component.translatable(muted ? "audiozone.recent.unmute" : "audiozone.recent.mute"),
				() -> toggleMute(soundId));
	}

	/** Quick action Edit: opens the rule editor on the sound's exact-ID rule (a new one if it has none). */
	private AzButton editSoundButton(String soundId) {
		return AzButton.icon(EDIT_ICON, Component.translatable("audiozone.recent.edit"),
				() -> minecraft.gui.setScreen(RuleEditorScreen.forSound(this, applyTarget(), soundId)));
	}

	/** Why?: the sound trace for this sound's most recent play (FR-14.1). */
	private AzButton whyButton(String soundId) {
		return AzButton.icon(WHY_ICON, Component.translatable("audiozone.recent.why"),
				() -> minecraft.gui.setScreen(new TraceScreen(this, soundId)));
	}

	/**
	 * Mute/Unmute on a Recently Played or Search row (FR-1.5): edits the sound's exact-ID rule in the list "Apply to"
	 * chose, saves at once and shows a 5-second toast with Undo (FR-12.5). A new mute takes the profile's Cue default.
	 */
	public void toggleMute(String soundId) {
		ApplyTarget target = applyTarget();
		Profile profile = ProfileManager.profile();
		RuleEdit edit = QuickActions.toggleMute(profile, target, soundId);
		QuickActionResult result = QuickActions.apply(profile, target, soundId, edit);
		if (!result.ok()) {
			showToast(errorText(result.error()), null);
			return;
		}
		if (result.changed()) {
			ProfileManager.update(current -> result.after());
			String toastKey = (edit instanceof RuleEdit.MuteWithCue ? "audiozone.recent.toast.muted" : "audiozone.recent.toast.unmuted")
					+ (target.isEverywhere() ? "" : "_in");
			List<String> args = target.isEverywhere() ? List.of(soundName(soundId)) : List.of(soundName(soundId), target.zoneName());
			QuickUndo.offer(this, this::refreshLists, result.before(), result.after(), toastKey, args);
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

	private AzDropdown<ApplyTarget> buildApplyTo() {
		applyChoices = computeApplyChoices();
		applyToDropdown = Tooltips.describe(new AzDropdown<>(APPLY_TO_WIDTH, Component.translatable("audiozone.recent.apply_to"),
				applyChoices, choice -> Component.translatable(choice.translationKey(), choice.args().toArray()),
				applyTarget(), this::setApplyTarget), Component.translatable("audiozone.recent.apply_to.tip"));
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

	static Component errorText(TraceEditError error) {
		return error == TraceEditError.TOO_MANY_RULES
				? Component.translatable(error.translationKey(), error.max())
				: Component.translatable(error.translationKey(), error.min(), error.max());
	}

	private static String ago(long millis) {
		long seconds = Math.max(0, millis / 1000);
		return seconds < 60
				? Component.translatable("audiozone.recent.ago.seconds", seconds).getString()
				: Component.translatable("audiozone.recent.ago.minutes", seconds / 60).getString();
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
		AzDropdown<ApplyTarget> applyTo = buildApplyTo();
		searchField = new AzTextField(font, body.width() - Theme.ICON_BUTTON_SIZE - applyTo.getWidth() - 2 * Theme.GAP,
				Component.translatable("audiozone.search.field"))
				.placeholder(Component.translatable("audiozone.search.hint"));
		searchField.setMaxLength(200);
		searchField.setValue(searchText);
		searchField.onChange(text -> {
			searchText = text;
			searchPending = true;
			searchChangedAt = Util.getMillis();
		});
		searchHelpButton = AzButton.icon(WHY_ICON,
				Component.translatable(searchHelp ? "audiozone.search.help.hide" : "audiozone.search.help.show"), this::toggleSearchHelp);
		body.row(applyTo, searchField, searchHelpButton);
		if (searchHelp) {
			for (int i = 1; i <= SEARCH_HELP_LINES; i++) {
				String key = "audiozone.search.help." + i;
				body.label(() -> Component.translatable(key));
			}
		}
		body.label(() -> Component.translatable(searchCount == 1 ? "audiozone.search.count.one" : "audiozone.search.count", searchCount));
		searchList = new AzList(body.width(), 100, Component.translatable("audiozone.search.list"));
		searchList.setEmptyText(() -> Component.translatable("audiozone.search.empty"));
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
		List<AzList.Row> rows = new ArrayList<>(found.size());
		for (SoundEntry entry : found) {
			String id = entry.id();
			String name = entry.subtitle() == null ? SoundNames.readable(id) : entry.subtitle();
			String category = RecentLog.get().sounds().seenCategory(id);
			String seen = category == null ? "" : Component.translatable("audiozone.search.seen_as",
					Component.translatable("soundCategory." + category)).getString();
			List<AzList.Row.Column> columns = List.of(
					new AzList.Row.Column(() -> name, 0, 0),
					new AzList.Row.Column(() -> seen, SEEN_WIDTH, 0),
					new AzList.Row.Column(() -> id, 0, 1));
			rows.add(new AzList.Row(id, columns, List.of(muteButton(id), editSoundButton(id), whyButton(id))));
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

	public void confirmDelete(Rule rule) {
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
			pageTabs.add(new AzTabButton(PAGE_TAB_WIDTH,
					Component.translatable("audiozone.settings.page." + page.name().toLowerCase(Locale.ROOT)), i, pages.length,
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
		body.label(() -> Component.translatable("audiozone.settings.bypass_hint", bypassKeyName()));
		if (!ProfileManager.isLoaded()) {
			body.label(() -> Component.translatable("audiozone.settings.no_world"));
			return;
		}
		body.row(Tooltips.describe(new AzToggle(160, Component.translatable("audiozone.settings.show_distance"),
				AudioZoneSettings.showDistance(), AudioZoneSettings::setShowDistance),
				Component.translatable("audiozone.settings.show_distance.tip")));
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
		throughBlocksToggle = Tooltips.describe(new AzToggle(OUTLINE_MODE_WIDTH, Component.translatable("audiozone.settings.through_blocks"),
				AudioZoneSettings.outlinesThroughBlocks(), AudioZoneSettings::setOutlinesThroughBlocks),
				Component.translatable("audiozone.settings.through_blocks.tip"));
		if (!ProfileManager.isLoaded()) {
			body.row(throughBlocksToggle);
			body.label(() -> Component.translatable("audiozone.settings.no_world"));
			return;
		}
		outlineModeDropdown = Tooltips.describe(new AzDropdown<>(OUTLINE_MODE_WIDTH, Component.translatable("audiozone.settings.outlines"),
				List.of(OutlineMode.values()),
				mode -> Component.translatable("audiozone.settings.outlines." + mode.name().toLowerCase(Locale.ROOT)),
				AudioZoneSettings.outlineMode(), AudioZoneSettings::setOutlineMode),
				Component.translatable("audiozone.settings.outlines.tip", outlinesKeyName()));
		body.row(outlineModeDropdown);
		body.row(throughBlocksToggle);
	}

	private static Component outlinesKeyName() {
		return audiozone.AudioZoneClient.outlinesKey.isUnbound()
				? Component.translatable("audiozone.settings.outlines.key_unbound")
				: audiozone.AudioZoneClient.outlinesKey.getTranslatedKeyMessage();
	}

	/** FR-13.3: live numbers, refreshed every tick, and Copy report. */
	private void buildDiagnostics(Body body) {
		diagnostics = Diagnostics.data();
		body.label(() -> Component.translatable("audiozone.diag.sounds", diagnostics.sounds().count()));
		body.label(() -> Component.translatable("audiozone.diag.per_sound", micros(diagnostics.sounds().averageMicros()),
				micros(diagnostics.sounds().maxMicros()), micros(diagnostics.peakSoundMicros())));
		body.label(() -> timing("audiozone.diag.tick", diagnostics.tick()));
		body.label(() -> timing("audiozone.diag.outlines", diagnostics.outlines()));
		body.label(() -> Component.translatable("audiozone.diag.active", diagnostics.globalRules(), diagnostics.zones()));
		body.label(() -> Double.isNaN(diagnostics.cacheHitRate())
				? Component.translatable("audiozone.diag.cache.none")
				: Component.translatable("audiozone.diag.cache", String.format(Locale.ROOT, "%.1f", diagnostics.cacheHitRate() * 100)));
		body.label(() -> Component.translatable("audiozone.diag.errors", diagnostics.errors()));
		body.label(() -> Component.translatable("audiozone.diag.memory", MemoryEstimate.megabytes(Diagnostics.memoryBytes()),
				MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES)));
		copyReportButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.diag.copy"), this::copyReport),
				Component.translatable("audiozone.diag.copy.tip"));
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
		showToast(Component.translatable("audiozone.diag.copied"), null);
	}

	// ---- Keys ----

	/** P toggles Pause while the list has focus (FR-1.3); the Bypass hotkey works here too (FR-13.1). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (audiozone.AudioZoneClient.bypassKey.matches(event) && !hasOverlay() && !(getFocused() instanceof AzTextField)) {
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
