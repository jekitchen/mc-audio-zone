package audiozone.mc;

import audiozone.core.profile.Profile;
import audiozone.core.profile.ProfileOps;
import audiozone.core.profile.ProfileSummary;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzDialog;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.Tooltips;
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
 * rule and zone counts and last joined date. Actions: Copy (into this world's profile), Rename, Reset and Delete; the
 * profile of the world you are in can be reset but not deleted. In a singleplayer world, "Give this copy its own
 * profile" (FR-11.1). Works outside a world too (from Mod Menu), without Copy.
 */
public final class ProfileListPanel {
	private static final int WHEN_WIDTH = 110;
	private static final int COPY_WIDTH = 44;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/edit");
	private static final Identifier RESET_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/reset");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/delete");

	private final AzScreen host;
	public AzList list;
	public AzButton ownProfileButton;
	private List<ProfileManager.Stored> stored = List.of();

	public ProfileListPanel(AzScreen host) {
		this.host = host;
	}

	public void build(AzScreen.Body body) {
		Minecraft minecraft = Minecraft.getInstance();
		ownProfileButton = null;
		if (WorldSession.canGiveOwnProfile(minecraft)) {
			ownProfileButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.profiles.own"), this::confirmOwnProfile),
					Component.translatable("audiozone.profiles.own.tip"));
			body.row(ownProfileButton);
		}
		if (ProfileManager.isLoaded() && WorldSession.usesFolderFallback()) {
			body.label(() -> Component.translatable("audiozone.profiles.folder_fallback"));
		}
		body.label(() -> Component.translatable("audiozone.profiles.hint"));
		list = new AzList(body.width(), 100, Component.translatable("audiozone.profiles.list"));
		list.setEmptyText(() -> Component.translatable("audiozone.profiles.empty"));
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
		List<AzList.Row> rows = new ArrayList<>(sorted.size());
		for (ProfileManager.Stored s : sorted) {
			rows.add(row(s, connected));
		}
		list.setRows(rows);
	}

	private static ProfileSummary summary(ProfileManager.Stored s, String connected) {
		return ProfileSummary.of(s.key(), s.profile(), connected);
	}

	private AzList.Row row(ProfileManager.Stored s, String connected) {
		List<AzButton> buttons = new ArrayList<>();
		String keyText = s.keyKnown() ? s.key()
				: Component.translatable("audiozone.profiles.key_unknown", s.key()).getString();
		if (s.profile() == null) {
			// A file that cannot be read: its problem, and Delete (it is kept as .bak).
			String name = Component.translatable("audiozone.profiles.broken", keyText).getString();
			String problem = ProfileManager.text(s.problem());
			buttons.add(deleteButton(s, keyText));
			return new AzList.Row(s.key(), List.of(
					new AzList.Row.Column(() -> name, 0, 0),
					new AzList.Row.Column(() -> problem, 0, 1)), buttons);
		}
		ProfileSummary summary = summary(s, connected);
		String name = summary.connected()
				? Component.translatable("audiozone.profiles.this_world", summary.displayName()).getString()
				: summary.displayName();
		String when = summary.lastJoined() == null
				? Component.translatable("audiozone.profiles.never_joined").getString()
				: Component.translatable("audiozone.profiles.joined",
						LocalDate.ofInstant(summary.lastJoined(), ZoneId.systemDefault()).toString()).getString();
		String counts = Component.translatable("audiozone.profiles.counts", summary.ruleCount(), summary.zoneCount()).getString();
		Component rowName = Component.literal(summary.displayName());
		if (ProfileManager.isLoaded() && !summary.connected() && s.keyKnown()) {
			buttons.add(Tooltips.describe(AzButton.text(Component.translatable("audiozone.profiles.copy"), COPY_WIDTH, () -> confirmCopy(s))
					.narrationContext(() -> rowName), Component.translatable("audiozone.profiles.copy.tip")));
		}
		if (s.keyKnown()) {
			buttons.add(AzButton.icon(EDIT_ICON, Component.translatable("audiozone.profiles.rename"),
					() -> setScreen(new ProfileRenameScreen(host, s.key(), s.profile()))).narrationContext(() -> rowName));
			buttons.add(AzButton.icon(RESET_ICON, Component.translatable("audiozone.profiles.reset"), () -> confirmReset(s))
					.narrationContext(() -> rowName));
		}
		AzButton delete = deleteButton(s, summary.displayName());
		if (!summary.canDelete()) {
			delete.active = false;
			delete.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("audiozone.profiles.delete.current")));
		}
		buttons.add(delete);
		return new AzList.Row(s.key(), List.of(
				new AzList.Row.Column(() -> name, 0, 0),
				new AzList.Row.Column(() -> when, WHEN_WIDTH, 0),
				new AzList.Row.Column(() -> keyText, 0, 1),
				new AzList.Row.Column(() -> counts, WHEN_WIDTH, 1)), buttons);
	}

	private AzButton deleteButton(ProfileManager.Stored s, String name) {
		return AzButton.icon(DELETE_ICON, Component.translatable("audiozone.profiles.delete"), () -> confirmDelete(s, name))
				.narrationContext(() -> Component.literal(name));
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
		ProfileOps.Copy copy = ProfileOps.copyFrom(source.profile(), ProfileManager.profile());
		if (copy.problem() != null) {
			host.showToast(Component.literal(ProfileManager.text(copy.problem())), null);
			return;
		}
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.profiles.copy.title", source.profile().name()),
				Component.translatable("audiozone.profiles.copy.message", copy.ruleCount(), copy.bubbleCount(), copy.fixedKept()),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.profiles.copy.confirm"),
						() -> confirmBoostThen(host, copy.needsBoostConfirm(), () -> copy(source))),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
				1, 1));
	}

	/** Copies the source's global rules, bubbles and settings into this world's profile; fixed zones stay. */
	public void copy(ProfileManager.Stored source) {
		Profile latest = ProfileManager.storedProfile(source.key());
		Profile from = latest != null ? latest : source.profile();
		// A copy that brings boosted rules was confirmed in the boost dialog (FR-5.4), so it is saved as confirmed.
		ProfileManager.update(target -> {
			ProfileOps.Copy copy = ProfileOps.copyFrom(from, target);
			if (copy.problem() != null) {
				return target;
			}
			return copy.needsBoostConfirm() ? copy.result().withBoostConfirmed(true) : copy.result();
		});
		refresh();
		host.showToast(Component.translatable("audiozone.profiles.copied", source.profile().name()), null);
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
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.boost.confirm.title"),
				Component.translatable("audiozone.boost.confirm.body"),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.profiles.boost.confirm"), then),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
				1, 1));
	}

	// ---- Reset and delete (FR-11.3) ----

	public void confirmReset(ProfileManager.Stored s) {
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.profiles.reset.title"),
				Component.translatable("audiozone.profiles.reset.message", s.profile().name()),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.profiles.reset.confirm"), () -> reset(s.key())),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
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
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.profiles.delete.title"),
				Component.translatable("audiozone.profiles.delete.message", name),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.profiles.delete.confirm"), () -> delete(s.key())),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
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
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.profiles.own.title"),
				Component.translatable("audiozone.profiles.own.message"),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.profiles.own.confirm"), this::giveOwnProfile),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
				1, 1));
	}

	public void giveOwnProfile() {
		boolean done = WorldSession.giveOwnProfile(Minecraft.getInstance());
		refresh();
		host.showToast(Component.translatable(done ? "audiozone.profiles.own.done" : "audiozone.profiles.own.failed"), null);
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
