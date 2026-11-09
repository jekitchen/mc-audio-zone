package audiozone.mc;

import audiozone.core.recent.RecentRow;
import audiozone.core.recent.SortOrder;
import audiozone.mc.ui.AzButton;
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

/**
 * The main Audio Zone screen (FR-12.1). M3 has the Recently Played tab (FR-1, read-only) and a Settings tab with
 * "Show distance"; the other tabs arrive with their milestones. Lives in {@code audiozone.mc} because it uses
 * Minecraft's sound manager and GUI classes.
 */
public final class AudioZoneScreen extends AzScreen {
	public static final int TAB_RECENT = 0;
	public static final int TAB_SETTINGS = 1;
	/** FR-1.6: the list refreshes at most 4 times per second (every 5 ticks). */
	public static final int REFRESH_TICKS = 5;

	private static final int PAUSE_WIDTH = 80;
	private static final int SORT_WIDTH = 150;
	private static final int STATUS_WIDTH = 70;
	private static final int CATEGORY_WIDTH = 95;
	private static final int PLAYS_WIDTH = 75;
	private static final int BAND_WIDTH = 60;

	// Remembered while the game runs, like the last used tab.
	private static SortOrder sort = SortOrder.MOST_RECENT;
	private static boolean paused;
	private static String filter = "";

	public AzList recentList;
	public AzToggle pauseToggle;
	public AzDropdown<SortOrder> sortDropdown;
	public AzTextField filterField;
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
				new Tab(Component.translatable("audiozone.tab.settings"), true));
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
		if (index == TAB_RECENT) {
			buildRecent(body);
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
		ticks++;
		if (currentTab() == TAB_RECENT && !paused && ticks % REFRESH_TICKS == 0) {
			refresh();
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
			String shownName = name == null ? row.id() : name;
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
		String status = Component.translatable("audiozone.recent.status_line",
				Component.translatable(row.status().translationKey())).getString();
		String plays = Component.translatable("audiozone.recent.plays", row.count(), ago(now - row.lastPlayedMs())).getString();
		List<AzList.Row.Column> columns = new ArrayList<>();
		columns.add(new AzList.Row.Column(() -> name, 0, 0));
		columns.add(new AzList.Row.Column(() -> status, STATUS_WIDTH, 0));
		columns.add(new AzList.Row.Column(row::id, 0, 1));
		columns.add(new AzList.Row.Column(() -> category, CATEGORY_WIDTH, 1));
		columns.add(new AzList.Row.Column(() -> plays, PLAYS_WIDTH, 1));
		if (AudioZoneSettings.showDistance) {
			String band = Component.translatable(row.band().translationKey()).getString();
			columns.add(new AzList.Row.Column(() -> band, BAND_WIDTH, 1));
		}
		return new AzList.Row(row.id(), columns, List.of());
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

	// ---- Settings ----

	private void buildSettings(Body body) {
		body.label(() -> Component.translatable("audiozone.settings.general"));
		body.row(Tooltips.describe(new AzToggle(160, Component.translatable("audiozone.settings.show_distance"),
				AudioZoneSettings.showDistance, value -> AudioZoneSettings.showDistance = value),
				Component.translatable("audiozone.settings.show_distance.tip")));
	}

	// ---- Keys ----

	/** P toggles Pause while the list has focus (FR-1.3). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_P && getFocused() == recentList && recentList != null && !hasOverlay()) {
			setPaused(!paused);
			return true;
		}
		return super.keyPressed(event);
	}
}
