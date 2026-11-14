package soundsculptor.mc;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileOps;
import soundsculptor.core.profile.ProfileSummary;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Tooltips;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Profiles tab (PRD FR-11.2, FR-11.3): every server and world with a profile, with its display name, world key,
 * Sound Set and zone counts and last joined date. Actions: Copy (into this world's profile), Rename, Reset and Delete; the
 * profile of the world you are in can be reset but not deleted. In a singleplayer world, "Give this copy its own
 * profile" (FR-11.1). Export on every row and Import for this world (FR-11.4, FR-11.5; files can also be dropped on
 * the tab). Works outside a world too (from Mod Menu), without Copy and Import.
 */
public final class ProfileListPanel {
	/** A date as the rows show it ("Joined 2026-11-12"): every date is as long. */
	private static final String SAMPLE_DATE = "2026-11-12";
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier RESET_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/reset");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/delete");

	private final SculptScreen host;
	public SculptList list;
	public SculptButton ownProfileButton;
	public SculptButton importButton;
	private List<ProfileManager.Stored> stored = List.of();

	public ProfileListPanel(SculptScreen host) {
		this.host = host;
	}

	public void build(SculptScreen.Body body) {
		Minecraft minecraft = Minecraft.getInstance();
		ownProfileButton = null;
		importButton = null;
		List<SculptButton> top = new ArrayList<>();
		if (ProfileManager.isLoaded()) {
			// Imports always go into the profile of the world you are in (FR-11.5).
			importButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.transfer.import_button"),
					() -> setScreen(new ProfileImportScreen(host))), Component.translatable("soundsculptor.transfer.import_button.tip"));
			top.add(importButton);
		}
		if (WorldSession.canGiveOwnProfile(minecraft)) {
			ownProfileButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.profiles.own"), this::confirmOwnProfile),
					Component.translatable("soundsculptor.profiles.own.tip"));
			top.add(ownProfileButton);
		}
		if (!top.isEmpty()) {
			body.row(top.toArray(new SculptButton[0]));
		}
		if (ProfileManager.isLoaded() && WorldSession.usesFolderFallback()) {
			body.label(() -> Component.translatable("soundsculptor.profiles.folder_fallback"));
		}
		body.label(() -> Component.translatable("soundsculptor.profiles.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.profiles.list"));
		list.setEmptyText(() -> Component.translatable("soundsculptor.profiles.empty"));
		body.fill(list);
		refresh();
	}

	/** Reads the profiles folder again and rebuilds the rows. */
	public void refresh() {
		if (list == null) {
			return;
		}
		stored = ProfileManager.storedProfiles();
		String connected = ProfileManager.key();
		List<ProfileManager.Stored> sorted = new ArrayList<>(stored);
		sorted.sort(Comparator.comparing((ProfileManager.Stored s) -> s.profile() == null)
				.thenComparing(s -> s.profile() == null ? null : summary(s, connected), Comparator.nullsLast(ProfileSummary.ORDER))
				.thenComparing(ProfileManager.Stored::key));
		// The counts column is as wide as the widest count in this list, so the key beside it keeps its room.
		int countsWidth = 0;
		for (ProfileManager.Stored s : sorted) {
			if (s.profile() != null) {
				countsWidth = Math.max(countsWidth, SculptList.Row.Column.widthFor(Component.literal(countsText(summary(s, connected)))));
			}
		}
		List<SculptList.Row> rows = new ArrayList<>(sorted.size());
		for (ProfileManager.Stored s : sorted) {
			rows.add(row(s, connected, countsWidth));
		}
		list.setRows(rows);
	}

	private static ProfileSummary summary(ProfileManager.Stored s, String connected) {
		return ProfileSummary.of(s.key(), s.profile(), connected);
	}

	private SculptList.Row row(ProfileManager.Stored s, String connected, int countsWidth) {
		List<SculptButton> buttons = new ArrayList<>();
		String keyText = s.keyKnown() ? s.key()
				: Component.translatable("soundsculptor.profiles.key_unknown", s.key()).getString();
		if (s.profile() == null) {
			// A file that cannot be read: its problem, and Delete (it is kept as .bak).
			String name = Component.translatable("soundsculptor.profiles.broken", keyText).getString();
			String problem = ProfileManager.text(s.problem());
			buttons.add(deleteButton(s, keyText));
			return new SculptList.Row(s.key(), List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> problem, 0, 1)), buttons);
		}
		ProfileSummary summary = summary(s, connected);
		String name = summary.connected()
				? Component.translatable("soundsculptor.profiles.this_world", summary.displayName()).getString()
				: summary.displayName();
		String when = summary.lastJoined() == null
				? Component.translatable("soundsculptor.profiles.never_joined").getString()
				: Component.translatable("soundsculptor.profiles.joined",
						LocalDate.ofInstant(summary.lastJoined(), ZoneId.systemDefault()).toString()).getString();
		String counts = countsText(summary);
		if (ProfileManager.isLoaded() && !summary.connected() && s.keyKnown()) {
			Component copy = Component.translatable("soundsculptor.profiles.copy");
			buttons.add(Tooltips.describe(SculptButton.text(copy, SculptButton.widthFor(copy), () -> confirmCopy(s)),
					Component.translatable("soundsculptor.profiles.copy.tip")));
		}
		if (s.keyKnown()) {
			Component export = Component.translatable("soundsculptor.transfer.export");
			buttons.add(Tooltips.describe(SculptButton.text(export, SculptButton.widthFor(export),
					() -> ProfileTransfer.exportWithDialog(host, s.key(), s.profile())),
					Component.translatable("soundsculptor.transfer.export.tip")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.profiles.rename"),
					() -> setScreen(new ProfileRenameScreen(host, s.key(), s.profile()))));
			buttons.add(SculptButton.icon(RESET_ICON, Component.translatable("soundsculptor.profiles.reset"), () -> confirmReset(s)));
		}
		SculptButton delete = deleteButton(s, summary.displayName());
		if (!summary.canDelete()) {
			delete.active = false;
			delete.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("soundsculptor.profiles.delete.current")));
		}
		buttons.add(delete);
		return new SculptList.Row(s.key(), List.of(
				new SculptList.Row.Column(() -> name, 0, 0),
				new SculptList.Row.Column(() -> when, whenWidth(), 0),
				new SculptList.Row.Column(() -> keyText, 0, 1),
				new SculptList.Row.Column(() -> counts, countsWidth, 1)), buttons);
	}

	/** Fits "Never joined" and "Joined 2026-11-12". */
	private static int whenWidth() {
		return SculptList.Row.Column.widthFor(Component.translatable("soundsculptor.profiles.never_joined"),
				Component.translatable("soundsculptor.profiles.joined", SAMPLE_DATE));
	}

	/** "Sound Sets: 4 · Zones: 2". */
	private static String countsText(ProfileSummary summary) {
		return Component.translatable("soundsculptor.profiles.counts", summary.setCount(), summary.zoneCount()).getString();
	}

	private SculptButton deleteButton(ProfileManager.Stored s, String name) {
		return SculptButton.icon(DELETE_ICON, Component.translatable("soundsculptor.profiles.delete"), () -> confirmDelete(s, name));
	}

	/** The profiles as last listed (tests). */
	public List<ProfileManager.Stored> stored() {
		return stored;
	}

	public ProfileManager.Stored find(String key) {
		for (ProfileManager.Stored s : stored) {
			if (s.key().equals(key)) {
				return s;
			}
		}
		return null;
	}

	// ---- Copy from another server (FR-11.2) ----

	public void confirmCopy(ProfileManager.Stored source) {
		ProfileOps.Copy copy = ProfileOps.copyFrom(source.profile(), ProfileManager.profile(), ProfileManager.library());
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.profiles.copy.title", source.profile().name()),
				Component.translatable("soundsculptor.profiles.copy.message", setsOnEverywhere(copy.setCount())),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.copy.confirm"),
						() -> confirmBoostThen(host, copy.needsBoostConfirm(ProfileManager.library()), () -> copy(source))),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	/**
	 * Copies the source's Sound Sets on Everywhere (library sets linked, Quick changes copied) and settings into this
	 * world's profile; its zones stay.
	 */
	public void copy(ProfileManager.Stored source) {
		Profile latest = ProfileManager.storedProfile(source.key());
		Profile from = latest != null ? latest : source.profile();
		// A copy that brings boosted rules was confirmed in the boost dialog (FR-5.4), so it is saved as confirmed.
		ProfileManager.change(ws -> {
			ProfileOps.Copy copy = ProfileOps.copyFrom(from, ws.profile(), ws.library());
			Profile result = copy.needsBoostConfirm(ws.library()) ? copy.result().withBoostConfirmed(true) : copy.result();
			return new Workspace(result, ws.library());
		});
		refresh();
		host.showToast(Component.translatable("soundsculptor.profiles.copied", source.profile().name()), null);
	}

	/** "1 Sound Set on Everywhere" or "4 Sound Sets on Everywhere" (Copy from… and Import). */
	static Component setsOnEverywhere(int count) {
		return plural("soundsculptor.profiles.count.sets_everywhere", count);
	}

	/** The text {@code key + ".one"} for 1, else {@code key} with the count. */
	static Component plural(String key, int count) {
		return count == 1 ? Component.translatable(key + ".one") : Component.translatable(key, count);
	}

	/**
	 * Runs {@code then}, first asking the boost question (FR-5.4) if the change brings rules above 100% into a profile
	 * that has not confirmed boost yet. Used by Copy from… and Import.
	 */
	public static void confirmBoostThen(net.minecraft.client.gui.screens.Screen host, boolean needed, Runnable then) {
		if (!needed) {
			then.run();
			return;
		}
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.boost.confirm.title"),
				Component.translatable("soundsculptor.boost.confirm.body"),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.boost.confirm"), then),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	// ---- Reset and delete (FR-11.3); library Sound Sets are never deleted ----

	public void confirmReset(ProfileManager.Stored s) {
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.profiles.reset.title"),
				Component.translatable("soundsculptor.profiles.reset.message", s.profile().name()),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.reset.confirm"), () -> reset(s.key())),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	public void reset(String key) {
		ProfileManager.Stored s = find(key);
		if (s == null || s.profile() == null) {
			return;
		}
		if (s.connected()) {
			ProfileManager.update(ProfileOps::reset);
		} else {
			ProfileManager.saveOther(key, ProfileOps.reset(s.profile()));
		}
		refresh();
	}

	public void confirmDelete(ProfileManager.Stored s, String name) {
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.profiles.delete.title"),
				Component.translatable("soundsculptor.profiles.delete.message", name),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.delete.confirm"), () -> delete(s.key())),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	public void delete(String key) {
		ProfileManager.Stored s = find(key);
		if (s == null || s.connected()) {
			return;
		}
		ProfileManager.delete(key);
		refresh();
	}

	// ---- Give this copy its own profile (FR-11.1) ----

	public void confirmOwnProfile() {
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.profiles.own.title"),
				Component.translatable("soundsculptor.profiles.own.message"),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.own.confirm"), this::giveOwnProfile),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	public void giveOwnProfile() {
		boolean done = WorldSession.giveOwnProfile(Minecraft.getInstance());
		refresh();
		host.showToast(Component.translatable(done ? "soundsculptor.profiles.own.done" : "soundsculptor.profiles.own.failed"), null);
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
