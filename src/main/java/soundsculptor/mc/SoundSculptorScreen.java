package soundsculptor.mc;

import soundsculptor.core.diag.DiagnosticsReport;
import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptTabButton;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;
import soundsculptor.mc.ui.demo.UiKitDemoScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The main Sound Sculptor screen (FR-12.1). Header: active profile and the Bypass toggle (FR-13.1). Tabs: Sounds (the
 * one sound browser, FR-1 and FR-3, {@link SoundsPanel}), Sound Sets (the library, FR-4.7), Zones (Everywhere first,
 * FR-8.10), Profiles (FR-11.2, FR-11.3) and Settings; Inspect arrives with M12. Sounds replaced the earlier Recently
 * Played and Search tabs. Lives in {@code soundsculptor.mc} because it uses Minecraft's GUI classes.
 */
public final class SoundSculptorScreen extends SculptScreen {
	public static final int TAB_SOUNDS = 0;
	public static final int TAB_SETS = 1;
	public static final int TAB_ZONES = 2;
	public static final int TAB_PROFILES = 3;
	public static final int TAB_SETTINGS = 4;

	private static final int PAGE_TAB_WIDTH = 100;
	/** The least width of the Settings › Zones controls; they grow to fit a longer label. */
	private static final int OUTLINE_MODE_WIDTH = 240;

	// Remembered while the game runs, like the last used tab.
	private static SettingsPage settingsPage = SettingsPage.GENERAL;

	/** Settings pages (PRD 6.1); Zones and Accessibility arrive with their milestones. */
	public enum SettingsPage {
		GENERAL, ZONES, DIAGNOSTICS
	}

	public SculptToggle bypassToggle;
	private SoundsPanel soundsPanel;
	private SetLibraryPanel setsPanel;
	private ZoneListPanel zonesPanel;
	private ProfileListPanel profilesPanel;
	/** The Settings tab's page tabs, in {@link SettingsPage} order. */
	public final List<SculptTabButton> pageTabs = new ArrayList<>();
	public SculptButton copyReportButton;
	/** Whether Copy report sits in the footer: the Diagnostics page's text left no room for it in the body. */
	private boolean copyReportInFooter;
	public SculptDropdown<OutlineMode> outlineModeDropdown;
	public SculptToggle throughBlocksToggle;
	private DiagnosticsReport.Data diagnostics;
	private int ticks;

	public SoundSculptorScreen() {
		this(null);
	}

	public SoundSculptorScreen(Screen parent) {
		super(Component.translatable("screen.soundsculptor.title"), parent);
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				// FR-1.6: the Sounds tab does not pause a singleplayer game, so sounds keep arriving.
				new Tab(Component.translatable("soundsculptor.tab.sounds"), false),
				new Tab(Component.translatable("soundsculptor.tab.sets"), true),
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
		Component label = Component.translatable("soundsculptor.bypass");
		bypassToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(label), label, ProfileManager.isBypassed(), ProfileManager::setBypass),
				Component.translatable("soundsculptor.bypass.tip", bypassKeyName()));
		header.add(bypassToggle);
	}

	private static Component bypassKeyName() {
		return soundsculptor.SoundSculptorClient.bypassKey.getTranslatedKeyMessage();
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		if (copyReportInFooter) {
			footer.add(copyReportButton);
		}
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			footer.add(SculptButton.text(Component.translatable("screen.soundsculptor.ui_demo"),
					() -> minecraft.gui.setScreen(new UiKitDemoScreen(this))));
		}
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		copyReportInFooter = false;
		if (index != TAB_SETTINGS && index != TAB_PROFILES && !ProfileManager.isLoaded()) {
			// Opened outside a world (e.g. from Mod Menu on the title screen): sounds, Sound Sets and zones belong to a
			// world's profile (FR-11.1), so there is nothing to show or change yet.
			body.label(() -> Component.translatable("soundsculptor.no_world"));
			return;
		}
		if (index == TAB_SOUNDS) {
			soundsPanel = new SoundsPanel(this);
			soundsPanel.build(body);
		} else if (index == TAB_SETS) {
			setsPanel = new SetLibraryPanel(this);
			setsPanel.build(body);
		} else if (index == TAB_ZONES) {
			buildZones(body);
		} else if (index == TAB_PROFILES) {
			profilesPanel = new ProfileListPanel(this);
			profilesPanel.build(body);
		} else {
			buildSettings(body);
		}
	}

	// ---- Sounds (FR-1, FR-3) ----

	/** The Sounds tab's panel, while that tab is shown. */
	public SoundsPanel sounds() {
		return soundsPanel;
	}

	// ---- Sound Sets (FR-4.7) ----

	public SetLibraryPanel setsPanel() {
		return setsPanel;
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
		Component throughBlocks = Component.translatable("soundsculptor.settings.through_blocks");
		throughBlocksToggle = Tooltips.describe(new SculptToggle(Math.max(OUTLINE_MODE_WIDTH, SculptToggle.widthFor(throughBlocks)), throughBlocks,
				SoundSculptorSettings.outlinesThroughBlocks(), SoundSculptorSettings::setOutlinesThroughBlocks),
				Component.translatable("soundsculptor.settings.through_blocks.tip"));
		if (!ProfileManager.isLoaded()) {
			body.row(throughBlocksToggle);
			body.label(() -> Component.translatable("soundsculptor.settings.no_world"));
			return;
		}
		Component outlines = Component.translatable("soundsculptor.settings.outlines");
		List<Component> modeNames = new ArrayList<>();
		for (OutlineMode mode : OutlineMode.values()) {
			modeNames.add(outlineModeName(mode));
		}
		outlineModeDropdown = Tooltips.describe(new SculptDropdown<>(Math.max(OUTLINE_MODE_WIDTH, SculptDropdown.widthFor(outlines, modeNames)),
				outlines, List.of(OutlineMode.values()), SoundSculptorScreen::outlineModeName,
				SoundSculptorSettings.outlineMode(), SoundSculptorSettings::setOutlineMode),
				Component.translatable("soundsculptor.settings.outlines.tip", outlinesKeyName()));
		body.row(outlineModeDropdown);
		body.row(throughBlocksToggle);
	}

	private static Component outlineModeName(OutlineMode mode) {
		return Component.translatable("soundsculptor.settings.outlines." + mode.name().toLowerCase(Locale.ROOT));
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
		// The long lines wrap rather than being cut (PRD 9.3).
		body.wrappedLabel(() -> Component.translatable("soundsculptor.diag.per_sound", micros(diagnostics.sounds().averageMicros()),
				micros(diagnostics.sounds().maxMicros()), micros(diagnostics.peakSoundMicros())));
		body.wrappedLabel(() -> timing("soundsculptor.diag.tick", diagnostics.tick()));
		body.wrappedLabel(() -> timing("soundsculptor.diag.outlines", diagnostics.outlines()));
		body.wrappedLabel(() -> Component.translatable("soundsculptor.diag.active", diagnostics.activeSets(),
				diagnostics.librarySets(), diagnostics.activeEntries(), diagnostics.zones()));
		body.label(() -> Double.isNaN(diagnostics.cacheHitRate())
				? Component.translatable("soundsculptor.diag.cache.none")
				: Component.translatable("soundsculptor.diag.cache", String.format(Locale.ROOT, "%.1f", diagnostics.cacheHitRate() * 100)));
		body.label(() -> Component.translatable("soundsculptor.diag.errors", diagnostics.errors()));
		body.label(() -> Component.translatable("soundsculptor.diag.memory", MemoryEstimate.megabytes(Diagnostics.memoryBytes()),
				MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES)));
		copyReportButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.diag.copy"), this::copyReport),
				Component.translatable("soundsculptor.diag.copy.tip"));
		// In a small window, wrapped lines can leave no room below them: Copy report then goes in the footer, still after
		// the page in focus order, instead of under the footer's edge.
		copyReportInFooter = body.remaining() < Theme.WIDGET_HEIGHT;
		if (!copyReportInFooter) {
			body.row(copyReportButton);
		}
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

	// ---- Ticks and keys ----

	@Override
	public void tick() {
		super.tick();
		// Bypass can also change through the hotkey or the error budget; keep the header toggle in step.
		if (bypassToggle != null && bypassToggle.value() != ProfileManager.isBypassed()) {
			bypassToggle.setValue(ProfileManager.isBypassed());
		}
		ticks++;
		if (currentTab() == TAB_SETTINGS && settingsPage == SettingsPage.DIAGNOSTICS) {
			diagnostics = Diagnostics.data();
		}
		if (currentTab() == TAB_SETS && setsPanel != null && ticks % SoundsPanel.REFRESH_TICKS == 0) {
			// The usage index reads other worlds' profiles in the background; show its counts once it has them.
			setsPanel.refreshIfUsageChanged();
		}
		if (currentTab() == TAB_SOUNDS && soundsPanel != null && soundsPanel.tick()) {
			// The player walked into or out of a zone: offer the zones they now stand in.
			boolean focused = getFocused() == soundsPanel.applyToDropdown;
			rebuildWidgets();
			if (focused) {
				setInitialFocus(soundsPanel.applyToDropdown);
			}
		}
	}

	/** Rebuilds the open tab's list from the profile, e.g. after a quick action or Undo. */
	public void refreshLists() {
		if (soundsPanel != null && currentTab() == TAB_SOUNDS) {
			soundsPanel.refresh();
		}
		if (setsPanel != null && currentTab() == TAB_SETS) {
			setsPanel.refresh();
		}
		if (zonesPanel != null && currentTab() == TAB_ZONES) {
			zonesPanel.refresh();
		}
	}

	/** The Bypass hotkey works here too (FR-13.1); P pauses the Sounds list (FR-1.3). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (soundsculptor.SoundSculptorClient.bypassKey.matches(event) && !hasOverlay() && !(getFocused() instanceof SculptTextField)) {
			ProfileManager.toggleBypass();
			bypassToggle.setValue(ProfileManager.isBypassed());
			return true;
		}
		if (currentTab() == TAB_SOUNDS && soundsPanel != null && soundsPanel.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}
}
