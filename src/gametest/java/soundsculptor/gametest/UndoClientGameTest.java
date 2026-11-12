package soundsculptor.gametest;

import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetCodec;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.mc.QuickUndo;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;

/**
 * Undo toasts (PRD FR-12.5, M16): quick actions in lists save at once and show a 5-second toast with Undo. Covers
 * Mute/Unmute on Search rows (with the Cue default, FR-7.6), On/Off, Move and Delete on the Rules and Zones tabs,
 * Undo by keyboard (Enter on the focused button, and Ctrl+Z), the toast staying while Undo has focus, and Undo being
 * refused once something else changed the profile.
 */
public final class UndoClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final SoundSet COWS = new SoundSet("undo-cows", "Quiet cows", List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withVolume(40)));
	private static final SoundSet PIGS = new SoundSet("undo-pigs", "Pigs muted", List.of(SetEntry.of("e1", "minecraft:entity.pig.*").withMute(true)));

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);

			// Mute from Search: saved at once, the toast names the sound and has Undo; the rule takes the Cue default.
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SEARCH);
				main(mc).searchField.setValue("villager ambient");
				main(mc).runSearch();
				main(mc).toggleMute(VILLAGER);
			});
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)), "Mute should apply at once");
			check(context.computeOnClient(mc -> Boolean.TRUE.equals(QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER).cue())),
					"a new mute rule should start with the profile's Cue default (on)");
			String toast = toastText(context);
			check(toast.startsWith("Muted "), "the toast should say what was muted, was " + toast);
			check(context.computeOnClient(mc -> main(mc).toast().undoButton()) != null, "the toast should offer Undo");
			check(context.computeOnClient(mc -> main(mc).searchList.findRow(VILLAGER).buttons().get(0).getMessage().getString())
					.equals("Unmute"), "the row should now offer Unmute");
			context.takeScreenshot("undo-toast-mute");

			// Undo with the keyboard: focus the button (narrated with what it undoes), Enter.
			context.runOnClient(mc -> main(mc).setFocused(main(mc).toast().undoButton()));
			String narration = context.computeOnClient(mc -> NarrationCompat.collect(main(mc).toast().undoButton()::updateNarration));
			check(narration.contains("Muted ") && narration.contains("Undo"), "Undo should be narrated with its action, was " + narration);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(2);
			check(!context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)), "Undo should unmute");
			check(context.computeOnClient(mc -> QuickEntries.of(ProfileManager.profile()).isEmpty()), "Undo should leave no entry behind");
			check(toastText(context).equals("Undone"), "a toast should confirm the undo");
			check(context.computeOnClient(mc -> main(mc).searchList.findRow(VILLAGER).buttons().get(0).getMessage().getString())
					.equals("Mute"), "the row should offer Mute again");

			// Ctrl+Z also undoes, but not while a text field has focus (it keeps Ctrl+Z for itself).
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> main(mc).setFocused(main(mc).searchField));
			context.runOnClient(mc -> main(mc).keyPressed(ctrlZ()));
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)), "Ctrl+Z in the search field does not undo");
			context.runOnClient(mc -> {
				main(mc).setFocused(main(mc).searchList);
				main(mc).keyPressed(ctrlZ());
			});
			check(!context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)), "Ctrl+Z should undo the mute");

			// Unmute has Undo too.
			context.runOnClient(mc -> {
				main(mc).toggleMute(VILLAGER);
				main(mc).toggleMute(VILLAGER);
			});
			check(toastText(context).startsWith("Unmuted "), "Unmute should show its own toast, was " + toastText(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)), "undoing Unmute mutes again");

			// The toast stays while Undo has focus, past 5 seconds, and Undo still works then.
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> main(mc).setFocused(main(mc).toast().undoButton()));
			context.waitTicks(130);
			check(context.computeOnClient(mc -> main(mc).toast()) != null, "the toast should stay while Undo has focus");
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)), "a held Undo still works after 5 seconds");

			// Without focus the toast goes after 5 seconds.
			context.runOnClient(mc -> {
				main(mc).toggleMute(VILLAGER);
				main(mc).setFocused(main(mc).searchList);
			});
			context.waitTicks(110);
			check(context.computeOnClient(mc -> main(mc).toast()) == null, "the toast should close after 5 seconds");

			// Undo is refused once the profile changed some other way, and nothing changes.
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> ProfileManager.update(p -> QuickEntries.put(p, SetEntry.of("other", "minecraft:entity.cow.*").withVolume(40))));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(toastText(context).startsWith("Can't undo"), "a stale Undo should say it can't undo, was " + toastText(context));
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)
					&& QuickEntries.get(ProfileManager.profile(), "other") != null), "a refused Undo changes nothing");

			// Everywhere's Sound Sets (FR-8.6): On/Off, Move and Detach each offer Undo.
			context.runOnClient(mc -> ProfileManager.change(ws -> new soundsculptor.core.sets.Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(COWS.id()).attach(PIGS.id())), ws.library().put(COWS).put(PIGS))));
			context.runOnClient(mc -> mc.gui.setScreen(new PlaceSetsScreen(main(mc), PlaceRef.EVERYWHERE)));
			context.waitForScreen(PlaceSetsScreen.class);
			context.runOnClient(mc -> places(mc).panel.setEnabled(COWS.id(), false));
			check(placeToast(context).equals("Turned off 'Quiet cows' here"), "On/Off toast, was " + placeToast(context));
			context.runOnClient(mc -> places(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).enabled()), "Undo turns the set back on");
			context.runOnClient(mc -> places(mc).panel.move(PIGS.id(), -1));
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).setId()).equals(PIGS.id()), "Move up applies at once");
			context.runOnClient(mc -> places(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).setId()).equals(COWS.id()), "Undo puts the order back");
			context.runOnClient(mc -> places(mc).panel.detach(COWS.id()));
			check(!context.computeOnClient(mc -> ProfileManager.profile().everywhere().uses(COWS.id())), "Detach applies at once");
			check(context.computeOnClient(mc -> ProfileManager.library().contains(COWS.id())), "a detached set stays in the library");
			check(placeToast(context).equals("Detached 'Quiet cows'"), "Detach toast, was " + placeToast(context));
			context.runOnClient(mc -> places(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().stream().map(a -> a.setId()).toList())
					.equals(List.of(COWS.id(), PIGS.id())), "Undo brings the detached set back in its place");
			check(context.computeOnClient(mc -> places(mc).panel.list.findRow(COWS.id())) != null, "the restored set is listed again");
			context.runOnClient(mc -> places(mc).onClose());
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> ProfileManager.change(ws -> new soundsculptor.core.sets.Workspace(Profile.empty(ws.profile().name()),
					ws.library().remove(COWS.id()).remove(PIGS.id()))));

			// Zones tab: Delete a zone (with its Quick changes), then Undo.
			context.runOnClient(mc -> ProfileManager.update(p -> p.withZones(List.of(
					new Zone("z1", "Pen", true, key, "minecraft:overworld", new Point(0, -60, 0), new Shape.Box(9, 3, 9),
							PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.sheep.*").withMute(true))))))));
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_ZONES));
			context.runOnClient(mc -> main(mc).zonesPanel().setEnabled("z1", false));
			check(toastText(context).equals("Turned off Pen"), "zone On/Off toast, was " + toastText(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z1").enabled()), "Undo turns the zone back on");
			context.runOnClient(mc -> main(mc).zonesPanel().delete("z1"));
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z1")) == null, "the zone is deleted at once");
			check(toastText(context).equals("Deleted the zone Pen"), "zone Delete toast, was " + toastText(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z1") != null
					&& ProfileManager.profile().zone("z1").sets().quickEntries().size() == 1), "Undo brings the zone and its Quick changes back");
			check(context.computeOnClient(mc -> main(mc).zonesPanel().list.findRow("z1")) != null, "the restored zone is listed again");
			context.takeScreenshot("undo-toast-zone");

			// Library Sound Sets: Undo puts a set edit back in memory and in its file.
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SETS);
				ProfileManager.change(ws -> new soundsculptor.core.sets.Workspace(ws.profile(), ws.library().put(
						new SoundSet("farm-animals", "Farm animals", List.of(SetEntry.of("cow", "minecraft:entity.cow.ambient").withVolume(30))))));
				ProfileManager.flushSaves();
			});
			context.runOnClient(mc -> QuickUndo.applyWorkspace(main(mc), () -> { }, ws -> new soundsculptor.core.sets.Workspace(ws.profile(),
					ws.library().put(ws.library().get("farm-animals").putEntry(ws.library().get("farm-animals").entry("cow").withVolume(60)))),
					"soundsculptor.zones.toast.deleted", "Farm animals"));
			check(setVolume(context) == 60, "the set edit applies at once");
			context.runOnClient(mc -> main(mc).pressUndo());
			check(setVolume(context) == 30, "Undo puts the set's level back to 30 in the library");
			check(fileVolume(context) == 30, "after a flush the set file says 30 again");

			// A set added by the action is deleted by Undo; a set removed by it is saved again.
			context.runOnClient(mc -> QuickUndo.applyWorkspace(main(mc), () -> { }, ws -> new soundsculptor.core.sets.Workspace(ws.profile(),
					ws.library().remove("farm-animals")), "soundsculptor.zones.toast.deleted", "Farm animals"));
			context.runOnClient(mc -> ProfileManager.flushSaves());
			check(!context.computeOnClient(mc -> Files.exists(ProfileManager.setStore().fileFor("farm-animals"))), "the removed set's file is gone");
			context.runOnClient(mc -> main(mc).pressUndo());
			context.runOnClient(mc -> ProfileManager.flushSaves());
			check(context.computeOnClient(mc -> Files.exists(ProfileManager.setStore().fileFor("farm-animals"))), "Undo saves the removed set again");
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new soundsculptor.core.sets.Workspace(ws.profile(), ws.library().remove("farm-animals")));
				ProfileManager.flushSaves();
			});

			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static int setVolume(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ProfileManager.library().get("farm-animals").entry("cow").volume());
	}

	/** The level in the set file on disk, after queued saves. */
	private static int fileVolume(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			ProfileManager.flushSaves();
			try {
				return SetCodec.read(Files.readString(ProfileManager.setStore().fileFor("farm-animals"))).entry("cow").volume();
			} catch (Exception e) {
				throw new AssertionError("could not read the set file", e);
			}
		});
	}

	/** Ctrl+Z, or Cmd+Z on Mac. */
	private static KeyEvent ctrlZ() {
		return new KeyEvent(InputConstants.KEY_Z, 0, InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER);
	}

	private static String toastText(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			SculptScreen.Toast toast = main(mc).toast();
			return toast == null ? "(no toast)" : toast.message().getString();
		});
	}

	private static PlaceSetsScreen places(Minecraft mc) {
		return (PlaceSetsScreen) mc.gui.screen();
	}

	private static String placeToast(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			SculptScreen.Toast toast = places(mc).toast();
			return toast == null ? "(no toast)" : toast.message().getString();
		});
	}

	private static SoundSculptorScreen main(Minecraft mc) {

		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
