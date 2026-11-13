package soundsculptor.gametest;

import soundsculptor.core.profile.ExportCodec;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileCodec;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileImportScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.ProfileTransfer;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * M15 export and import (PRD FR-11.4 to FR-11.7), with Sound Sets (M8c): Export writes the whole profile with exact
 * fixed-zone coordinates and a copy of every library set it attaches; importing it back into an empty library restores
 * the zones and the set; importing it over a library whose set has changed keeps both ("(imported)"); another server's
 * export brings its sets, bubble and settings but skips its fixed zones and a set it does not have, and says so; a file
 * with a JSON typo, a radius of 900 or nothing in it fails with a clear message and changes nothing; a file dropped on
 * the Profiles tab is imported the same way.
 */
public final class ImportExportClientGameTest implements FabricClientGameTest {
	private static final String A = "a.example.net";
	private static final SoundSet VILLAGERS = new SoundSet("set-gametest-villagers", "Gametest villagers",
			List.of(SetEntry.of("e1", "minecraft:entity.villager.*").withVolume(30)));

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			Set<String> libraryBefore = context.computeOnClient(mc -> Set.copyOf(ProfileManager.library().ids()));
			Profile original = Profile.empty("Export test")
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true)))
							.attach(VILLAGERS.id()))
					.withZones(List.of(
							new Zone("hall", "Trading hall", true, key, "minecraft:overworld", new Point(120.5, 64, -33.25),
									new Shape.Box(20, 8, 12), PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("z1", "*cow*").withVolume(40)))),
							new Zone("near", "Near", true, key, "minecraft:the_nether", new Point(-7, 30, 9), new Shape.Sphere(16), PlaceSets.EMPTY)));
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(original, ws.library().put(VILLAGERS))));
			Path imports = context.computeOnClient(mc -> ProfileTransfer.importsDir());
			Path exports = context.computeOnClient(mc -> ProfileTransfer.exportsDir());

			// Export (FR-11.4): a dialog names the file and offers Open folder; the file holds the whole profile.
			openProfiles(context);
			context.runOnClient(mc -> main(mc).profilesPanel().list.findRow(key).buttons().stream()
					.filter(b -> b.getMessage().getString().equals("Export")).findFirst().orElseThrow().onPress(null));
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Profile exported"), "the export dialog");
			check(context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).buttons().get(0).getMessage().getString()).equals("Open folder"),
					"the export dialog offers Open folder");
			context.takeScreenshot("export-dialog");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
			context.waitForScreen(SoundSculptorScreen.class);
			Path exported = exports.resolve("Export test-" + java.time.LocalDate.now() + ".json");
			check(Files.exists(exported), "the export file " + exported);
			ExportCodec.Read fromFile = parse(read(exported));
			check(fromFile.profile().zones().equals(original.zones()), "the export keeps fixed zones with exact coordinates and dimension");
			check(read(exported).contains("\"worldKey\": \"" + key + "\""), "the export records its world key");
			check(fromFile.sets().equals(List.of(VILLAGERS)), "the export carries a copy of the attached library set, was " + fromFile.sets());

			// Import it back after a reset into a library without the set (FR-11.4): both fixed zones return exactly and are
			// active here (FR-11.7), and the set is added to the library and attached again (FR-11.5a, not in the library).
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()),
					ws.library().remove(VILLAGERS.id()))));
			copy(exported, imports.resolve("backup.json"));
			importFromScreen(context, "backup.json");
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Import backup.json?"), "the import dialog");
			String backupMessage = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
			check(backupMessage.contains("1 Sound Set will be added to the library."), "the dialog says the set is added, was " + backupMessage);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			Profile restored = context.computeOnClient(mc -> ProfileManager.profile());
			check(restored.zones().equals(original.zones()) && QuickEntries.of(restored).equals(QuickEntries.of(original))
					&& restored.everywhere().equals(original.everywhere()),
					"importing the export restores rules, the attached set and fixed zones exactly, was " + restored);
			check(VILLAGERS.equals(context.computeOnClient(mc -> ProfileManager.library().get(VILLAGERS.id()))),
					"the set is back in the library");

			// The library's copy has changed since: importing again keeps both (the default until the clash dialog, FR-11.5a).
			// The library's version is unchanged; the file's is added as "(imported)" with a new ID and attached.
			SoundSet changed = VILLAGERS.withEntries(List.of(SetEntry.of("e1", "minecraft:entity.villager.*").withVolume(80)));
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(changed))));
			importFromScreen(context, "backup.json");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			SetLibrary keptBoth = context.computeOnClient(mc -> ProfileManager.library());
			List<String> attached = context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().stream()
					.map(Attachment::setId).toList());
			check(changed.equals(keptBoth.get(VILLAGERS.id())), "Keep both leaves the library's version alone");
			check(attached.size() == 2 && !attached.get(1).equals(VILLAGERS.id()), "Everywhere attaches the imported copy, was " + attached);
			SoundSet copyOfFile = keptBoth.get(attached.get(1));
			check(copyOfFile != null && copyOfFile.name().equals("Gametest villagers (imported)")
					&& copyOfFile.entries().equals(VILLAGERS.entries()), "the file's version is added as (imported), was " + copyOfFile);

			// Another server's export (FR-11.5): rules, bubble and settings come in; its 2 fixed zones are skipped, and both
			// the dialog and the toast say so; this world's own fixed zones stay.
			// A attaches a set that is neither in its file nor in this library: it is skipped and counted (FR-11.6).
			Profile serverA = Profile.empty("Server A")
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("a1", "*bee*").withVolume(20),
							SetEntry.of("a2", "*zombie*").withMute(true))).attach("set-gametest-gone"))
					.withZones(List.of(zoneFor("fa1", A), zoneFor("fa2", A), zoneFor("ba", A).asBubble()));
			write(imports.resolve("server-a.json"), ProfileCodec.toJson(serverA, A));
			importFromScreen(context, "server-a.json");
			String message = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
			check(message.contains("2 fixed zones made for another server will be skipped"), "the dialog says 2 fixed zones are skipped, was " + message);
			check(message.contains("1 Sound Set not found, skipped."), "the dialog counts the missing set, was " + message);
			context.takeScreenshot("import-dialog-skipped");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> main(mc).toast().message().getString())
					.equals("Imported server-a.json. 2 fixed zones were skipped (another server)."), "the toast says 2 were skipped");
			Profile afterA = context.computeOnClient(mc -> ProfileManager.profile());
			check(QuickEntries.of(afterA).size() == 2 && QuickEntries.get(afterA, "a1") != null, "A's rules are imported, was " + QuickEntries.of(afterA));
			check(afterA.everywhere().attached().size() == 1, "the missing set is not attached, was " + afterA.everywhere().attached());
			check(afterA.zones().stream().map(Zone::id).toList().equals(List.of("hall", "near", "ba")),
					"this world's fixed zones stay, A's bubble comes in, A's fixed zones do not, was " + afterA.zones());

			// FR-11.6: a JSON typo, a radius of 900 and an empty file each fail with a clear message; nothing changes.
			String good = ProfileCodec.toJson(original, key);
			write(imports.resolve("typo.json"), good.replaceFirst("\\{", "{,"));
			write(imports.resolve("radius.json"), good.replaceFirst("\"radius\": 16(\\.0)?", "\"radius\": 900"));
			write(imports.resolve("empty.json"), "");
			for (String[] bad : new String[][] {{"typo.json", ""}, {"radius.json", "900"}, {"empty.json", "empty"}}) {
				importFromScreen(context, bad[0]);
				String title = context.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
				String text = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
				check(title.equals("Can't import " + bad[0]), "a problem dialog for " + bad[0] + ", was " + title);
				check(text.contains(bad[1]) && text.contains("Nothing was changed."), bad[0] + " should explain the problem, was " + text);
				context.takeScreenshot("import-problem-" + bad[0].replace(".json", ""));
				context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
				context.waitForScreen(SoundSculptorScreen.class);
				check(context.computeOnClient(mc -> ProfileManager.profile()).equals(afterA), bad[0] + " must not change the profile");
			}

			// FR-11.5: a file dropped on the Profiles tab is checked and imported the same way.
			context.runOnClient(mc -> main(mc).onFilesDrop(List.of(exported)));
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Import " + exported.getFileName() + "?"),
					"a dropped file opens the import dialog");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
			context.waitForScreen(SoundSculptorScreen.class);

			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.change(ws -> {
					SetLibrary lib = ws.library();
					for (String id : ws.library().ids()) {
						if (!libraryBefore.contains(id)) {
							lib = lib.remove(id);
						}
					}
					return new Workspace(Profile.empty(ws.profile().name()), lib);
				});
				ProfileManager.flushSaves();
			});
			for (String name : List.of("backup.json", "server-a.json", "typo.json", "radius.json", "empty.json")) {
				delete(imports.resolve(name));
			}
			delete(exported);
		}
	}

	private static void openProfiles(ClientGameTestContext context) {
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_PROFILES));
	}

	/** Profiles tab → Import… → the file's Import button; leaves the resulting dialog open. */
	private static void importFromScreen(ClientGameTestContext context, String fileName) {
		openProfiles(context);
		context.runOnClient(mc -> main(mc).profilesPanel().importButton.onPress(null));
		context.waitForScreen(ProfileImportScreen.class);
		context.runOnClient(mc -> {
			SculptList.Row row = ((ProfileImportScreen) mc.gui.screen()).list.findRow(fileName);
			check(row != null, fileName + " should be listed in the imports folder");
			row.buttons().get(0).onPress(null);
		});
		context.waitForScreen(SculptDialog.class);
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		Screen screen = mc.gui.screen();
		check(screen instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + screen);
		return (SoundSculptorScreen) screen;
	}

	private static Zone zoneFor(String id, String key) {
		return new Zone(id, "Zone " + id, true, key, "minecraft:overworld", new Point(5, 70, 5), new Shape.Sphere(6), PlaceSets.EMPTY);
	}

	private static ExportCodec.Read parse(String json) {
		try {
			return ExportCodec.read(json);
		} catch (soundsculptor.core.profile.ProfileFormatException e) {
			throw new AssertionError(e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void copy(Path from, Path to) {
		write(to, read(from));
	}

	private static void delete(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
