package soundsculptor.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SetDeleteDialog;
import soundsculptor.mc.SetLibraryPanel;
import soundsculptor.mc.SetNameScreen;
import soundsculptor.mc.SetUsageIndex;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;

/**
 * The Sound Sets tab (FR-4.7, FR-4.11): New, Duplicate, Rename and Delete on the library; each row says where its set is
 * used; the delete warning lists the places here and the other profiles by name only, and Cancel changes nothing.
 */
public final class SoundSetsTabClientGameTest implements FabricClientGameTest {
	private static final String OTHER = "sets-tab.example.net";
	private static final String OTHER_2 = "sets-tab-2.example.net";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			newDuplicateRenameDelete(context);
			deleteWarningListsPlacesAndOtherProfiles(context, key);
			rowShowsUsage(context, key);
		}
	}

	/** New makes "Farm animals"; Duplicate gives an unattached "Farm animals (copy)" with Undo; Rename and Delete work. */
	private static void newDuplicateRenameDelete(ClientGameTestContext context) {
		SetLibrary libraryBefore = context.computeOnClient(mc -> ProfileManager.library());
		try {
			openTab(context);
			check(context.computeOnClient(mc -> main(mc).tabButtons().get(SoundSculptorScreen.TAB_SETS).getMessage().getString())
					.equals("Sound Sets"), "the Sound Sets tab follows Rules");
			check(context.computeOnClient(mc -> main(mc).tabButtons().get(SoundSculptorScreen.TAB_RULES).getMessage().getString())
					.equals("Rules"), "the Rules tab stays until it is replaced");

			// New: a name screen; an empty name disables Save.
			context.runOnClient(mc -> panel(mc).newButton.onPress(null));
			context.waitForScreen(SetNameScreen.class);
			context.runOnClient(mc -> nameScreen(mc).setName("   "));
			check(!context.computeOnClient(mc -> nameScreen(mc).saveButton.active), "an empty name disables Save");
			context.runOnClient(mc -> {
				nameScreen(mc).setName("Farm animals");
				nameScreen(mc).save();
			});
			context.waitForScreen(SoundSculptorScreen.class);
			String farmId = context.computeOnClient(mc -> idOf("Farm animals"));
			check(farmId != null, "New adds the set to the library");
			check(context.computeOnClient(mc -> toastText(mc)).equals("Created 'Farm animals'"), "a toast says it was created");
			List<String> farmRow = context.computeOnClient(mc -> row(mc, farmId).columnTexts());
			check(farmRow.equals(List.of("Farm animals", "0 entries", "Not attached")), "the new set's row, was " + farmRow);

			// Duplicate: "Farm animals (copy)", attached nowhere; Undo takes it away again.
			context.runOnClient(mc -> pressButton(row(mc, farmId), "Duplicate"));
			String copyId = context.computeOnClient(mc -> idOf("Farm animals (copy)"));
			check(copyId != null && !copyId.equals(farmId), "Duplicate adds a copy with its own ID");
			check(context.computeOnClient(mc -> ProfileManager.profile().placesUsing(copyId)).isEmpty(), "the copy is attached nowhere");
			check(context.computeOnClient(mc -> toastText(mc)).equals("Duplicated as 'Farm animals (copy)'"), "a toast names the copy");
			check(context.computeOnClient(mc -> row(mc, copyId).columnTexts().get(2)).equals("Not attached"), "the copy's row");
			context.runOnClient(mc -> main(mc).toast().undoButton().onPress(null));
			check(context.computeOnClient(mc -> ProfileManager.library().get(copyId)) == null, "Undo removes the copy");
			check(context.computeOnClient(mc -> panel(mc).list.findRow(copyId)) == null, "Undo refreshes the list");
			context.runOnClient(mc -> panel(mc).duplicate(farmId));
			String copy2 = context.computeOnClient(mc -> idOf("Farm animals (copy)"));
			check(copy2 != null, "Duplicate again");

			// Rename: through the name screen; the ID stays.
			context.runOnClient(mc -> pressButton(row(mc, copy2), "Rename"));
			context.waitForScreen(SetNameScreen.class);
			check(context.computeOnClient(mc -> nameScreen(mc).nameField.getValue()).equals("Farm animals (copy)"),
					"Rename starts from the current name");
			context.runOnClient(mc -> {
				nameScreen(mc).setName("Barn");
				nameScreen(mc).save();
			});
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.library().get(copy2).name()).equals("Barn"), "Rename keeps the ID");
			check(context.computeOnClient(mc -> toastText(mc)).equals("Renamed to 'Barn'"), "a toast confirms the rename");

			// Delete: an unattached set's warning says only that.
			context.runOnClient(mc -> pressButton(row(mc, copy2), "Delete"));
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Delete 'Barn'?"), "the dialog title");
			String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
			check(message.equals("This set is not attached anywhere."), "an unattached set's warning, was " + message);
			context.takeScreenshot("sets-delete-unattached");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.library().get(copy2)) == null, "Delete removes the set");
			check(context.computeOnClient(mc -> toastText(mc)).equals("Deleted 'Barn'"), "a toast confirms the delete");
			check(context.computeOnClient(mc -> panel(mc).list.findRow(copy2)) == null, "the row is gone");
			context.takeScreenshot("sets-tab");
			// Undo after the dialog: the tab was rebuilt when the dialog closed, and the row it shows now comes back.
			context.runOnClient(mc -> main(mc).toast().undoButton().onPress(null));
			check(context.computeOnClient(mc -> ProfileManager.library().get(copy2)) != null, "Undo brings the set back");
			check(context.computeOnClient(mc -> panel(mc).list.findRow(copy2)) != null, "and its row");
		} finally {
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.change(ws -> new Workspace(ws.profile(), libraryBefore));
				ProfileManager.flushSaves();
			});
		}
	}

	/** The warning lists Everywhere and both zones here, and the other profile by name but none of its zones. */
	private static void deleteWarningListsPlacesAndOtherProfiles(ClientGameTestContext context, String key) {
		SoundSet farm = new SoundSet("set-tab-farm", "Tab farm", List.of(SetEntry.of("e1", "minecraft:entity.cow.ambient")));
		Profile profileBefore = context.computeOnClient(mc -> ProfileManager.profile());
		try {
			Profile other = Profile.empty("Hypixel Skyblock")
					.withZones(List.of(zone("vault", "Secret vault", OTHER, PlaceSets.EMPTY.attach(farm.id()))));
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(ws.profile()
						.withEverywhere(PlaceSets.EMPTY.attach(farm.id()))
						.withZones(List.of(zone("z-main", "Main base", key, PlaceSets.EMPTY.attach(farm.id())),
								zone("z-mine", "Mine", key, PlaceSets.EMPTY.attach(farm.id())),
								zone("z-pond", "Pond", key, PlaceSets.EMPTY))),
						ws.library().put(farm)));
				ProfileManager.saveOther(OTHER, other);
				ProfileManager.flushSaves();
			});
			waitForOthers(context, farm.id(), key, 1);
			Profile attached = context.computeOnClient(mc -> ProfileManager.profile());

			openTab(context);
			context.runOnClient(mc -> panel(mc).confirmDelete(farm.id()));
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Delete 'Tab farm'?"), "the dialog title");
			String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
			check(message.contains("Used here in: Everywhere, Main base, Mine"), "the places here, was " + message);
			check(message.contains("Also removed from: Hypixel Skyblock"), "the other profile by name, was " + message);
			check(!message.contains("Secret vault"), "never the other profile's zones, was " + message);
			check(!message.contains("Pond"), "only places that use the set, was " + message);
			check(context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString()).contains("Hypixel Skyblock"),
					"the narrator reads the warning");
			context.takeScreenshot("sets-delete-warning");

			// Cancel changes nothing.
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.library().get(farm.id())) != null, "Cancel keeps the set");
			check(context.computeOnClient(mc -> ProfileManager.profile()).equals(attached), "Cancel keeps every place");

			// Delete removes it from the library and every place here.
			context.runOnClient(mc -> panel(mc).confirmDelete(farm.id()));
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			Profile after = context.computeOnClient(mc -> ProfileManager.profile());
			check(context.computeOnClient(mc -> ProfileManager.library().get(farm.id())) == null, "Delete removes the set");
			check(!after.zone("z-main").sets().uses(farm.id()) && !after.zone("z-mine").sets().uses(farm.id()),
					"Delete removes the set from both zones");
			check(!after.everywhere().uses(farm.id()), "and from Everywhere");

			// Before the usage index has read the other profiles, the warning says they are still being counted.
			String counting = context.computeOnClient(mc -> SetDeleteDialog.message(List.of("Main base"), List.of(), false).getString());
			check(counting.contains("Main base") && counting.contains("still being counted"), "the counting warning, was " + counting);
		} finally {
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.change(ws -> new Workspace(profileBefore, ws.library().remove(farm.id())));
				ProfileManager.delete(OTHER);
				ProfileManager.flushSaves();
			});
		}
	}

	/** "3 places in this world, 2 other worlds", "Not attached", and the entry count. */
	private static void rowShowsUsage(ClientGameTestContext context, String key) {
		SoundSet farm = new SoundSet("set-tab-usage", "Tab usage", List.of(SetEntry.of("e1", "minecraft:entity.cow.ambient"),
				SetEntry.of("e2", "minecraft:entity.pig.ambient")));
		SoundSet lone = new SoundSet("set-tab-lone", "Tab lone", List.of(SetEntry.of("e1", "minecraft:entity.cow.ambient")));
		Profile profileBefore = context.computeOnClient(mc -> ProfileManager.profile());
		try {
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(ws.profile()
						.withEverywhere(PlaceSets.EMPTY.attach(farm.id()))
						.withZones(List.of(zone("z-a", "A", key, PlaceSets.EMPTY.attach(farm.id())),
								zone("z-b", "B", key, PlaceSets.EMPTY.attach(farm.id())))),
						ws.library().put(farm).put(lone)));
				ProfileManager.saveOther(OTHER, Profile.empty("Other one").withEverywhere(PlaceSets.EMPTY.attach(farm.id())));
				ProfileManager.saveOther(OTHER_2, Profile.empty("Other two").withEverywhere(PlaceSets.EMPTY.attach(farm.id())));
				ProfileManager.flushSaves();
			});
			waitForOthers(context, farm.id(), key, 2);
			openTab(context);
			List<String> row = context.computeOnClient(mc -> row(mc, farm.id()).columnTexts());
			check(row.equals(List.of("Tab usage", "2 entries", "3 places in this world, 2 other worlds")), "the used set's row, was " + row);
			List<String> loneRow = context.computeOnClient(mc -> row(mc, lone.id()).columnTexts());
			check(loneRow.equals(List.of("Tab lone", "1 entry", "Not attached")), "the unattached set's row, was " + loneRow);
			check(context.computeOnClient(mc -> SetLibraryPanel.usageText(3, 0, false)).equals("3 places in this world"),
					"before the index is built, only this world's count shows");
			check(context.computeOnClient(mc -> SetLibraryPanel.usageText(1, 1, true)).equals("1 place in this world, 1 other world"),
					"one place and one world");
			context.takeScreenshot("sets-tab-usage");
		} finally {
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.change(ws -> new Workspace(profileBefore, ws.library().remove(farm.id()).remove(lone.id())));
				ProfileManager.delete(OTHER);
				ProfileManager.delete(OTHER_2);
				ProfileManager.flushSaves();
			});
		}
	}

	private static void openTab(ClientGameTestContext context) {
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_SETS));
	}

	/** Waits for the background usage index to see {@code count} other profiles using the set. */
	private static void waitForOthers(ClientGameTestContext context, String setId, String key, int count) {
		boolean seen = false;
		for (int i = 0; i < 100 && !seen; i++) {
			context.waitTicks(2);
			seen = context.computeOnClient(mc -> SetUsageIndex.current() != null
					&& SetUsageIndex.current().otherCount(setId, key) == count);
		}
		check(seen, "the usage index should see " + count + " other profiles using " + setId);
	}

	private static Zone zone(String id, String name, String worldKey, PlaceSets sets) {
		return new Zone(id, name, true, worldKey, "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(8), sets);
	}

	private static String idOf(String name) {
		for (SoundSet set : ProfileManager.library().sets()) {
			if (set.name().equals(name)) {
				return set.id();
			}
		}
		return null;
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static SetLibraryPanel panel(Minecraft mc) {
		return main(mc).setsPanel();
	}

	private static SetNameScreen nameScreen(Minecraft mc) {
		return (SetNameScreen) mc.gui.screen();
	}

	private static SculptList.Row row(Minecraft mc, String setId) {
		SculptList.Row row = panel(mc).list.findRow(setId);
		if (row == null) {
			throw new AssertionError("no row for " + setId);
		}
		return row;
	}

	private static void pressButton(SculptList.Row row, String label) {
		List<String> labels = new ArrayList<>();
		for (SculptButton button : row.buttons()) {
			String text = button.getMessage().getString();
			labels.add(text);
			if (text.equals(label)) {
				button.onPress(null);
				return;
			}
		}
		throw new AssertionError("no " + label + " button in " + labels);
	}

	private static String toastText(Minecraft mc) {
		return main(mc).toast() == null ? "" : main(mc).toast().message().getString();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
