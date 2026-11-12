package soundsculptor.gametest;

import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileCodec;
import soundsculptor.core.profile.ProfileSettings;
import soundsculptor.core.profile.WorldIds;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileListPanel;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.ProfileRenameScreen;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

/**
 * M14 Profiles tab (PRD FR-11.2, FR-11.3, FR-11.1 "Give this copy its own profile"): the list shows every profile;
 * Copy brings over Everywhere's Sound Sets (library sets linked, Quick changes copied), bubbles with their sets and
 * settings but not fixed zones, and is a one-time copy; Rename changes the
 * display name only; Reset empties a profile without touching the others; Delete removes another server's profile but is
 * not offered for the world you are in.
 */
public final class ProfilesClientGameTest implements FabricClientGameTest {
	private static final String A = "a.example.net";
	private static final SoundSet FARM = new SoundSet("set-gametest-farm", "Gametest farm",
			List.of(SetEntry.of("e1", "*sheep*").withVolume(50)));

	@Override
	public void runTest(ClientGameTestContext context) {
		// Outside a world (e.g. from Mod Menu): the Profiles tab still lists and manages profiles.
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_PROFILES));
		check(context.computeOnClient(mc -> main(mc).profilesPanel() != null && main(mc).profilesPanel().list != null),
				"the Profiles tab works outside a world");
		context.setScreen(() -> null);

		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			Profile serverA = Profile.empty("Server A")
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true),
							SetEntry.of("r2", "*cow*").withVolume(40))).attach(FARM.id()))
					.withZones(List.of(zone("fa", A), zone("ba", A).asBubble().withSets(PlaceSets.EMPTY.attach(FARM.id()))))
					.withSettings(new ProfileSettings(false))
					.withLastJoined(Instant.parse("2026-11-01T12:00:00Z"));
			context.runOnClient(mc -> {
				try {
					ProfileManager.store().save(A, serverA);
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withZones(List.of(zone("home", key))),
						ws.library().put(FARM)));
			});

			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_PROFILES));

			// The list: this world and server A, with name, key, counts and last joined date.
			List<String> rowA = context.computeOnClient(mc -> row(mc, A).columnTexts());
			check(rowA.equals(List.of("Server A", "Joined 2026-11-01", A, "Rules: 2 · Zones: 2")), "server A's row, was " + rowA);
			check(context.computeOnClient(mc -> row(mc, key).columnTexts().get(0).endsWith("(this world)")), "this world is marked");
			// Delete is not offered for the world you are in; it is for server A.
			check(!context.computeOnClient(mc -> deleteButton(row(mc, key)).active), "Delete is off for this world's profile");
			check(context.computeOnClient(mc -> deleteButton(row(mc, A)).active), "Delete is on for server A");
			check(context.computeOnClient(mc -> buttonLabels(row(mc, key)).stream().noneMatch(l -> l.equals("Copy"))),
					"this world's row has no Copy");
			context.takeScreenshot("profiles-tab");

			// Copy from A (FR-11.2): the dialog says what happens; A's rules, bubble and settings arrive, its fixed zone does not.
			context.runOnClient(mc -> panel(mc).confirmCopy(panel(mc).find(A)));
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString().equals("Copy from Server A?")), "the copy dialog");
			String copyMessage = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
			check(copyMessage.contains("Its 2 Sound Sets on Everywhere, 1 bubble zones and settings"), "the dialog lists what is copied, was " + copyMessage);
			int librarySize = context.computeOnClient(mc -> ProfileManager.library().size());
			context.takeScreenshot("profiles-copy-dialog");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			Profile copied = context.computeOnClient(mc -> ProfileManager.profile());
			check(RuleTarget.GLOBAL.rules(copied).size() == 2, "A's global rules are copied, was " + RuleTarget.GLOBAL.rules(copied));
			check(copied.zones().stream().map(Zone::id).toList().equals(List.of("home", "ba")),
					"this world's fixed zone stays and A's bubble arrives; A's fixed zone does not, was " + copied.zones());
			check(!copied.settings().showDistance(), "A's settings are copied");
			check(copied.everywhere().uses(FARM.id()) && copied.zone("ba").sets().uses(FARM.id()),
					"Everywhere and the bubble attach A's library set, was " + copied);
			check(context.computeOnClient(mc -> ProfileManager.library().size()) == librarySize, "library sets are linked, not copied");
			check(context.computeOnClient(mc -> main(mc).toast() != null
					&& main(mc).toast().message().getString().equals("Copied from Server A")), "a toast confirms the copy");

			// A one-time copy: changing A later does not change this world.
			context.runOnClient(mc -> ProfileManager.saveOther(A, RuleTarget.GLOBAL.remove(serverA, "r1")));
			check(context.computeOnClient(mc -> RuleTarget.GLOBAL.rules(ProfileManager.profile()).size()) == 2, "this world keeps its copy");

			// Rename A (FR-11.3): an empty name is refused; a new name changes only the display name.
			context.runOnClient(mc -> row(mc, A).buttons().stream()
					.filter(b -> b.getMessage().getString().equals("Rename")).findFirst().orElseThrow().onPress(null));
			context.waitForScreen(ProfileRenameScreen.class);
			context.runOnClient(mc -> ((ProfileRenameScreen) mc.gui.screen()).setName("  "));
			check(!context.computeOnClient(mc -> ((ProfileRenameScreen) mc.gui.screen()).saveButton.active), "an empty name disables Save");
			context.takeScreenshot("profiles-rename-error");
			context.runOnClient(mc -> {
				ProfileRenameScreen rename = (ProfileRenameScreen) mc.gui.screen();
				rename.setName("Farm server");
				rename.save();
			});
			context.waitForScreen(SoundSculptorScreen.class);
			Profile renamed = context.computeOnClient(mc -> stored(A));
			check(renamed.name().equals("Farm server") && RuleTarget.GLOBAL.rules(renamed).size() == 1, "A is renamed and keeps its rules, was " + renamed);
			check(context.computeOnClient(mc -> row(mc, A).columnTexts().get(0)).equals("Farm server"), "the list shows the new name");

			// Reset A: back to empty defaults; this world is unaffected.
			context.runOnClient(mc -> panel(mc).confirmReset(panel(mc).find(A)));
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			Profile reset = context.computeOnClient(mc -> stored(A));
			check(RuleTarget.GLOBAL.rules(reset).isEmpty() && reset.zones().isEmpty() && reset.settings().equals(ProfileSettings.DEFAULTS)
					&& reset.name().equals("Farm server"), "A is reset to empty defaults, keeping its name, was " + reset);
			check(context.computeOnClient(mc -> RuleTarget.GLOBAL.rules(ProfileManager.profile()).size()) == 2, "this world is unaffected by the reset");

			// Delete: never this world's profile; A's goes (kept as .bak).
			context.runOnClient(mc -> panel(mc).delete(key));
			check(context.computeOnClient(mc -> panel(mc).find(key)) != null, "this world's profile cannot be deleted");
			context.runOnClient(mc -> panel(mc).confirmDelete(panel(mc).find(A), "Farm server"));
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> panel(mc).find(A)) == null, "A is gone from the list");
			context.runOnClient(mc -> ProfileManager.flushSaves());
			Path fileA = context.computeOnClient(mc -> ProfileManager.store().fileFor(A));
			check(!Files.exists(fileA) && Files.exists(fileA.resolveSibling(fileA.getFileName() + ".bak")), "A's file is deleted and kept as .bak");

			// Rename this world's profile: the header follows.
			context.runOnClient(mc -> mc.gui.setScreen(new ProfileRenameScreen(mc.gui.screen(), key, ProfileManager.profile())));
			context.waitForScreen(ProfileRenameScreen.class);
			context.runOnClient(mc -> {
				ProfileRenameScreen rename = (ProfileRenameScreen) mc.gui.screen();
				rename.setName("My test world");
				rename.save();
			});
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().name()).equals("My test world"), "this world's profile is renamed");

			// Give this copy its own profile (FR-11.1): a new key and ID file; the fixed zone follows; the old profile stays.
			check(context.computeOnClient(mc -> panel(mc).ownProfileButton != null), "singleplayer offers Give this copy its own profile");
			context.runOnClient(mc -> panel(mc).confirmOwnProfile());
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			String newKey = context.computeOnClient(mc -> ProfileManager.key());
			check(!newKey.equals(key) && newKey.startsWith("world:"), "a new world key, was " + newKey);
			String idText = context.computeOnClient(mc -> read(mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve(WorldIds.FILE_NAME)));
			check(newKey.equals("world:" + WorldIds.readId(idText).orElse("")), "the ID file holds the new ID, was " + idText);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("home").worldKey()).equals(newKey), "the fixed zone moves to the new key");
			check(RuleTarget.GLOBAL.rules(context.computeOnClient(mc -> stored(key))).size() == 2, "the original profile is kept");

			// Copying boosted rules into a profile that never confirmed boost asks first (FR-5.4), then saves it as confirmed.
			String boostKey = "boost.example.net";
			context.runOnClient(mc -> {
				try {
					ProfileManager.store().save(boostKey, RuleTarget.GLOBAL.put(Profile.empty("Boost server"), SetEntry.of("b1", "*bell*").withVolume(150)));
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
				main(mc).selectTab(SoundSculptorScreen.TAB_PROFILES);
			});
			check(!context.computeOnClient(mc -> ProfileManager.profile().boostConfirmed()), "this profile has not confirmed boost");
			context.runOnClient(mc -> panel(mc).confirmCopy(panel(mc).find(boostKey)));
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Louder than vanilla?"), "the boost question");
			context.takeScreenshot("profiles-copy-boost");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().boostConfirmed()
					&& RuleTarget.GLOBAL.rule(ProfileManager.profile(), "b1") != null), "the boosted rule is copied and boost is confirmed");

			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.delete(boostKey);
				ProfileManager.delete(key);
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library().remove(FARM.id())));
				ProfileManager.flushSaves();
			});
		}
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static ProfileListPanel panel(Minecraft mc) {
		return main(mc).profilesPanel();
	}

	private static SculptList.Row row(Minecraft mc, String key) {
		SculptList.Row row = panel(mc).list.findRow(key);
		check(row != null, "no row for " + key);
		return row;
	}

	private static SculptButton deleteButton(SculptList.Row row) {
		return row.buttons().stream().filter(b -> b.getMessage().getString().equals("Delete")).findFirst().orElseThrow();
	}

	private static List<String> buttonLabels(SculptList.Row row) {
		return row.buttons().stream().map(b -> b.getMessage().getString()).toList();
	}

	private static Profile stored(String key) {
		ProfileManager.flushSaves();
		try {
			return ProfileCodec.fromJson(read(ProfileManager.store().fileFor(key)));
		} catch (soundsculptor.core.profile.ProfileFormatException e) {
			throw new AssertionError(e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static Zone zone(String id, String key) {
		return new Zone(id, "Zone " + id, true, key, "minecraft:overworld", new Point(0, -60, 0), new Shape.Sphere(8), PlaceSets.EMPTY);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
