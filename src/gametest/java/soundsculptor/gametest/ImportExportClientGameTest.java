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
import soundsculptor.mc.SetRenameOnImportScreen;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * M15 export and import (PRD FR-11.4 to FR-11.7), with Sound Sets (M8c, FR-11.5a as changed on 2026-11-13):
 *
 * <ul>
 *   <li>Export writes the whole profile with exact fixed-zone coordinates and a copy of every library set it attaches;
 *       importing it back into an empty library restores the zones and the set.</li>
 *   <li>A library set changed since the export clashes by name: a screen asks for a new name, filled in with
 *       "(imported)". A name in use is refused until it is unique; Cancel cancels the whole import.</li>
 *   <li>A set in the file with exactly the entries of a library set under another ID cancels the import, naming that
 *       set, with only OK; nothing changes.</li>
 *   <li>Your own export imported into another world attaches the same sets; nothing is duplicated.</li>
 *   <li>Another server's export brings its sets and settings but skips its fixed zones and a set it does not
 *       have, and says so; a file with a JSON typo, a radius of 900 or nothing in it fails with a clear message and
 *       changes nothing; a file dropped on the Profiles tab is imported the same way.</li>
 *   <li>An export larger than the 1 MB import limit is still written, with a warning.</li>
 * </ul>
 */
public final class ImportExportClientGameTest implements FabricClientGameTest {
	private static final String A = "a.example.net";
	private static final String OTHER_WORLD = "other-world.example.net";
	private static final SoundSet VILLAGERS = new SoundSet("set-gametest-villagers", "Gametest villagers",
			List.of(SetEntry.of("e1", "minecraft:entity.villager.*").withVolume(30)));
	private static final String TAKEN = "That name is already in use. Choose a unique name to import this set.";
	private static final List<String> IMPORT_FILES = List.of("backup.json", "other.json", "server-a.json", "typo.json",
			"radius.json", "empty.json");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			Set<String> libraryBefore = context.computeOnClient(mc -> Set.copyOf(ProfileManager.library().ids()));
			Path imports = context.computeOnClient(mc -> ProfileTransfer.importsDir());
			Path exports = context.computeOnClient(mc -> ProfileTransfer.exportsDir());
			List<Path> written = new ArrayList<>();
			try {
				Profile original = Profile.empty("Export test")
						.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true)))
								.attach(VILLAGERS.id()))
						.withZones(List.of(
								new Zone("hall", "Trading hall", true, key, "minecraft:overworld", new Point(120.5, 64, -33.25),
										new Shape.Box(20, 8, 12), PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("z1", "*cow*").withVolume(40)))),
								new Zone("near", "Near", true, key, "minecraft:the_nether", new Point(-7, 30, 9), new Shape.Cylinder(16, 32), PlaceSets.EMPTY)));
				Path exported = exportImportOnEmptyLibraryRestoresSets(context, key, original, imports, exports, written);
				SoundSet changed = VILLAGERS.withEntries(List.of(SetEntry.of("e1", "minecraft:entity.villager.*").withVolume(80)));
				context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(changed))));
				takenNameIsRefusedUntilUnique(context);
				changedSetAsksForNameAndAddsImported(context, changed);
				exactMatchUnderOtherIdCancelsImport(context);
				ownExportIntoOtherWorldLinksSets(context, imports, exports, written);
				otherServersExportSkipsItsFixedZones(context, imports, written);
				badFilesChangeNothing(context, original, key, imports, written);
				droppedFileOpensTheImportDialog(context, exported);
				largeExportWarns(context, exports, written);
			} finally {
				context.runOnClient(mc -> {
					mc.gui.setScreen(null);
					ProfileManager.delete(OTHER_WORLD);
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
				Path otherFile = context.computeOnClient(mc -> ProfileManager.store().fileFor(OTHER_WORLD));
				delete(otherFile.resolveSibling(otherFile.getFileName() + ".bak"));
				for (String name : IMPORT_FILES) {
					delete(imports.resolve(name));
				}
				for (Path file : written) {
					delete(file);
				}
			}
		}
	}

	/**
	 * Export (FR-11.4): a dialog names the file and offers Open folder; the file holds the whole profile and a copy of the
	 * attached set. Imported back after a reset into a library without the set, both fixed zones return exactly and are
	 * active here (FR-11.7), and the set is added to the library and attached again (FR-11.5a "Otherwise").
	 *
	 * @return the exported file
	 */
	private static Path exportImportOnEmptyLibraryRestoresSets(ClientGameTestContext context, String key, Profile original,
			Path imports, Path exports, List<Path> written) {
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(original, ws.library().put(VILLAGERS))));
		openProfiles(context);
		context.runOnClient(mc -> pressRowButton(mc, key, "Export"));
		context.waitForScreen(SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Profile exported"), "the export dialog");
		check(context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).buttons().get(0).getMessage().getString()).equals("Open folder"),
				"the export dialog offers Open folder");
		check(!context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString()).contains("1 MB"),
				"a small export has no size warning");
		context.takeScreenshot("export-dialog");
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
		context.waitForScreen(SoundSculptorScreen.class);
		Path exported = exports.resolve("Export test-" + java.time.LocalDate.now() + ".json");
		written.add(exported);
		check(Files.exists(exported), "the export file " + exported);
		ExportCodec.Read fromFile = parse(read(exported));
		check(fromFile.profile().zones().equals(original.zones()), "the export keeps fixed zones with exact coordinates and dimension");
		check(read(exported).contains("\"worldKey\": \"" + key + "\""), "the export records its world key");
		check(fromFile.sets().equals(List.of(VILLAGERS)), "the export carries a copy of the attached library set, was " + fromFile.sets());

		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()),
				ws.library().remove(VILLAGERS.id()))));
		copy(exported, imports.resolve("backup.json"));
		importFromScreen(context, "backup.json", SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Import backup.json?"), "the import dialog");
		String backupMessage = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		check(backupMessage.contains("Its 2 Sound Sets on Everywhere and settings will replace this world's."),
				"the dialog lists what is replaced, was " + backupMessage);
		check(backupMessage.contains("2 fixed zones made for this server will be added."), "the dialog counts the fixed zones, was " + backupMessage);
		check(backupMessage.contains("1 Sound Set will be added to the library."), "the dialog says the set is added, was " + backupMessage);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
		Profile restored = context.computeOnClient(mc -> ProfileManager.profile());
		check(restored.zones().equals(original.zones()) && QuickEntries.of(restored).equals(QuickEntries.of(original))
				&& restored.everywhere().equals(original.everywhere()),
				"importing the export restores rules, the attached set and fixed zones exactly, was " + restored);
		check(VILLAGERS.equals(context.computeOnClient(mc -> ProfileManager.library().get(VILLAGERS.id()))),
				"the set is back in the library");
		return exported;
	}

	/**
	 * The library's "Gametest villagers" has changed since the export: the name clashes, so a screen asks for a new name
	 * (FR-11.5a). Typing a name in use (the library's, ignoring case) is refused with the PRD's message and the import
	 * does not go ahead; Cancel then cancels the whole import and nothing changes.
	 */
	private static void takenNameIsRefusedUntilUnique(ClientGameTestContext context) {
		Profile before = context.computeOnClient(mc -> ProfileManager.profile());
		SetLibrary libraryBefore = context.computeOnClient(mc -> ProfileManager.library());
		importFromScreen(context, "backup.json", SetRenameOnImportScreen.class);
		context.runOnClient(mc -> rename(mc).setName("gametest VILLAGERS"));
		check(!context.computeOnClient(mc -> rename(mc).useButton.active), "a name in use disables Use this name");
		String error = context.computeOnClient(mc -> rename(mc).nameField.error().getString());
		check(error.equals(TAKEN), "the field says the name is in use, was " + error);
		context.takeScreenshot("import-rename-taken");
		context.runOnClient(mc -> rename(mc).use());
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof SetRenameOnImportScreen), "the import does not go ahead");
		context.runOnClient(mc -> rename(mc).setName("A unique name"));
		check(context.computeOnClient(mc -> rename(mc).useButton.active), "a unique name can be used");

		// Cancel cancels the whole import.
		context.runOnClient(mc -> rename(mc).onClose());
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.profile()).equals(before), "Cancel changes no profile");
		check(context.computeOnClient(mc -> ProfileManager.library()).sets().equals(libraryBefore.sets()), "Cancel changes no set");
	}

	/**
	 * The same clash, accepted: the screen is filled in with "Gametest villagers (imported)"; the file's version is added
	 * under that name with a new ID and attached, and the library's own version is unchanged.
	 */
	private static void changedSetAsksForNameAndAddsImported(ClientGameTestContext context, SoundSet changed) {
		importFromScreen(context, "backup.json", SetRenameOnImportScreen.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Name the imported Sound Set"),
				"the rename screen's title");
		check(context.computeOnClient(mc -> rename(mc).fileSet().id()).equals(VILLAGERS.id()), "it names the file's villagers set");
		String filled = context.computeOnClient(mc -> rename(mc).nameField.getValue());
		check(filled.equals("Gametest villagers (imported)"), "the name is filled in, was " + filled);
		context.takeScreenshot("import-rename");
		context.runOnClient(mc -> rename(mc).use());
		context.waitForScreen(SculptDialog.class);
		String message = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		check(message.contains("1 Sound Set will be added to the library."), "the dialog says the set is added, was " + message);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
		SetLibrary lib = context.computeOnClient(mc -> ProfileManager.library());
		List<String> attached = context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().stream()
				.map(Attachment::setId).toList());
		check(changed.equals(lib.get(VILLAGERS.id())), "the library's version is unchanged");
		check(attached.size() == 2 && !attached.get(1).equals(VILLAGERS.id()), "Everywhere attaches the imported set, was " + attached);
		SoundSet imported = lib.get(attached.get(1));
		check(imported != null && imported.name().equals("Gametest villagers (imported)")
				&& imported.entries().equals(VILLAGERS.entries()), "the file's version is added as (imported), was " + imported);
	}

	/**
	 * Importing the backup again: its villagers set now has exactly the entries of "Gametest villagers (imported)", a
	 * library set with another ID. The import fails with a dialog naming that set, offering only OK; nothing changes.
	 */
	private static void exactMatchUnderOtherIdCancelsImport(ClientGameTestContext context) {
		Profile before = context.computeOnClient(mc -> ProfileManager.profile());
		SetLibrary libraryBefore = context.computeOnClient(mc -> ProfileManager.library());
		importFromScreen(context, "backup.json", SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Can't import backup.json"),
				"the exact-match dialog's title");
		String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
		check(message.equals("The import failed because \"Gametest villagers (imported)\" already has exactly these entries. Nothing was changed."),
				"the dialog names the library set, was " + message);
		List<String> buttons = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).buttons().stream()
				.map(b -> b.getMessage().getString()).toList());
		check(buttons.equals(List.of("OK")), "only OK is offered, was " + buttons);
		context.takeScreenshot("import-exact-match");
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.profile()).equals(before), "the profile is unchanged");
		check(context.computeOnClient(mc -> ProfileManager.library()).sets().equals(libraryBefore.sets()), "the library is unchanged");
	}

	/**
	 * Another world on this computer attaches the library's villagers set; its export, imported here, attaches the same
	 * set: no name is asked for and nothing is added to the library.
	 */
	private static void ownExportIntoOtherWorldLinksSets(ClientGameTestContext context, Path imports, Path exports, List<Path> written) {
		Profile other = Profile.empty("Other world").withEverywhere(PlaceSets.EMPTY.attach(VILLAGERS.id()));
		context.runOnClient(mc -> ProfileManager.saveOther(OTHER_WORLD, other));
		openProfiles(context);
		context.runOnClient(mc -> pressRowButton(mc, OTHER_WORLD, "Export"));
		context.waitForScreen(SculptDialog.class);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
		context.waitForScreen(SoundSculptorScreen.class);
		Path exported = exports.resolve("Other world-" + java.time.LocalDate.now() + ".json");
		written.add(exported);
		copy(exported, imports.resolve("other.json"));
		Set<String> idsBefore = context.computeOnClient(mc -> Set.copyOf(ProfileManager.library().ids()));
		importFromScreen(context, "other.json", SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Import other.json?"),
				"the import dialog opens at once: no name clash");
		String message = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		check(message.startsWith("Import other.json?") && message.contains("Its 1 Sound Set on Everywhere and settings")
				&& !message.contains("added to the library"), "nothing is added to the library, was " + message);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.library().ids()).equals(idsBefore), "nothing is duplicated");
		List<String> attached = context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().stream()
				.map(Attachment::setId).toList());
		check(attached.equals(List.of(VILLAGERS.id())), "the same set is attached, was " + attached);
	}

	/**
	 * Another server's export (FR-11.5): rules and settings come in; its 2 fixed zones are skipped, and both the
	 * dialog and the toast say so; this world's own fixed zones stay. A attaches a set that is neither in its file nor in
	 * this library: it is skipped and counted (FR-11.6).
	 */
	private static void otherServersExportSkipsItsFixedZones(ClientGameTestContext context, Path imports, List<Path> written) {
		Profile serverA = Profile.empty("Server A")
				.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("a1", "*bee*").withVolume(20),
						SetEntry.of("a2", "*zombie*").withMute(true))).attach("set-gametest-gone"))
				.withZones(List.of(zoneFor("fa1", A), zoneFor("fa2", A)));
		write(imports.resolve("server-a.json"), ProfileCodec.toJson(serverA, A));
		importFromScreen(context, "server-a.json", SculptDialog.class);
		String message = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		check(message.contains("Its 1 Sound Set on Everywhere and settings"), "the counts use the singular, was " + message);
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
		check(afterA.zones().stream().map(Zone::id).toList().equals(List.of("hall", "near")),
				"this world's fixed zones stay and A's do not come in, was " + afterA.zones());
	}

	/** FR-11.6: a JSON typo, a radius of 900 and an empty file each fail with a clear message; nothing changes. */
	private static void badFilesChangeNothing(ClientGameTestContext context, Profile original, String key, Path imports, List<Path> written) {
		Profile before = context.computeOnClient(mc -> ProfileManager.profile());
		String good = ProfileCodec.toJson(original, key);
		write(imports.resolve("typo.json"), good.replaceFirst("\\{", "{,"));
		write(imports.resolve("radius.json"), good.replaceFirst("\"radius\": 16(\\.0)?", "\"radius\": 900"));
		write(imports.resolve("empty.json"), "");
		for (String[] bad : new String[][] {{"typo.json", ""}, {"radius.json", "900"}, {"empty.json", "empty"}}) {
			importFromScreen(context, bad[0], SculptDialog.class);
			String title = context.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
			String text = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
			check(title.equals("Can't import " + bad[0]), "a problem dialog for " + bad[0] + ", was " + title);
			check(text.contains(bad[1]) && text.contains("Nothing was changed."), bad[0] + " should explain the problem, was " + text);
			context.takeScreenshot("import-problem-" + bad[0].replace(".json", ""));
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile()).equals(before), bad[0] + " must not change the profile");
		}
	}

	/**
	 * FR-11.5: a file dropped on the Profiles tab is checked the same way. It is the first export again, so its set now
	 * exactly matches "Gametest villagers (imported)" and the import is cancelled.
	 */
	private static void droppedFileOpensTheImportDialog(ClientGameTestContext context, Path exported) {
		openProfiles(context);
		context.runOnClient(mc -> main(mc).onFilesDrop(List.of(exported)));
		context.waitForScreen(SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Can't import " + exported.getFileName()),
				"a dropped file is checked like any other: its set exactly matches the imported one, was "
						+ context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()));
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
	}

	/**
	 * An export larger than the 1 MB import limit (FR-11.6) is still written, and the dialog warns that it can't be
	 * imported again as it is. 32 full sets make about 2 MB; they are in the library but attached only to the exported
	 * (not the live) profile.
	 */
	private static void largeExportWarns(ClientGameTestContext context, Path exports, List<Path> written) {
		List<SoundSet> big = new ArrayList<>();
		PlaceSets everywhere = PlaceSets.EMPTY;
		for (int s = 0; s < 32; s++) {
			List<SetEntry> entries = new ArrayList<>();
			for (int e = 0; e < SoundSet.MAX_ENTRIES; e++) {
				entries.add(SetEntry.of("e" + e, "minecraft:entity.gametest_big_" + s + "_" + e).withVolume(50));
			}
			SoundSet set = new SoundSet("set-gametest-big-" + s, "Gametest big " + s, entries);
			big.add(set);
			everywhere = everywhere.attach(set.id());
		}
		Profile huge = Profile.empty("Huge export").withEverywhere(everywhere);
		context.runOnClient(mc -> ProfileManager.change(ws -> {
			SetLibrary lib = ws.library();
			for (SoundSet set : big) {
				lib = lib.put(set);
			}
			return new Workspace(ws.profile(), lib);
		}));
		openProfiles(context);
		context.runOnClient(mc -> ProfileTransfer.exportWithDialog(main(mc), "huge.example.net", huge));
		context.waitForScreen(SculptDialog.class);
		Path exported = exports.resolve("Huge export-" + java.time.LocalDate.now() + ".json");
		written.add(exported);
		String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
		check(message.equals("Saved as " + exported.getFileName() + " in config/soundsculptor/exports. Warning: it is larger than 1 MB, "
				+ "so it can't be imported again as it is."), "the export warns about its size, was " + message);
		check(size(exported) > 1024 * 1024, "the file is still written, and is over 1 MB: " + size(exported));
		context.takeScreenshot("export-too-large");
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
		context.waitForScreen(SoundSculptorScreen.class);
	}

	private static void openProfiles(ClientGameTestContext context) {
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_PROFILES));
	}

	private static void pressRowButton(Minecraft mc, String key, String label) {
		SculptList.Row row = main(mc).profilesPanel().list.findRow(key);
		check(row != null, "no profile row for " + key);
		row.buttons().stream().filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress(null);
	}

	/** Profiles tab → Import… → the file's Import button; leaves the screen that opens ({@code expected}) open. */
	private static void importFromScreen(ClientGameTestContext context, String fileName, Class<? extends Screen> expected) {
		openProfiles(context);
		context.runOnClient(mc -> main(mc).profilesPanel().importButton.onPress(null));
		context.waitForScreen(ProfileImportScreen.class);
		context.runOnClient(mc -> {
			SculptList.Row row = ((ProfileImportScreen) mc.gui.screen()).list.findRow(fileName);
			check(row != null, fileName + " should be listed in the imports folder");
			row.buttons().get(0).onPress(null);
		});
		context.waitForScreen(expected);
	}

	private static SetRenameOnImportScreen rename(Minecraft mc) {
		Screen screen = mc.gui.screen();
		check(screen instanceof SetRenameOnImportScreen, "expected the rename screen, found " + screen);
		return (SetRenameOnImportScreen) screen;
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		Screen screen = mc.gui.screen();
		check(screen instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + screen);
		return (SoundSculptorScreen) screen;
	}

	private static Zone zoneFor(String id, String key) {
		return new Zone(id, "Zone " + id, true, key, "minecraft:overworld", new Point(5, 70, 5), new Shape.Cylinder(6, 12), PlaceSets.EMPTY);
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

	private static long size(Path file) {
		try {
			return Files.size(file);
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
