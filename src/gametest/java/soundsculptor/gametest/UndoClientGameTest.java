package soundsculptor.gametest;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptScreen;
import com.mojang.blaze3d.platform.InputConstants;
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
			check(context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)), "Mute should apply at once");
			check(context.computeOnClient(mc -> Boolean.TRUE.equals(ProfileManager.profile().exactRule(VILLAGER).cue())),
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
			check(!context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)), "Undo should unmute");
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().isEmpty()), "Undo should leave no rule behind");
			check(toastText(context).equals("Undone"), "a toast should confirm the undo");
			check(context.computeOnClient(mc -> main(mc).searchList.findRow(VILLAGER).buttons().get(0).getMessage().getString())
					.equals("Mute"), "the row should offer Mute again");

			// Ctrl+Z also undoes, but not while a text field has focus (it keeps Ctrl+Z for itself).
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> main(mc).setFocused(main(mc).searchField));
			context.runOnClient(mc -> main(mc).keyPressed(ctrlZ()));
			check(context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)), "Ctrl+Z in the search field does not undo");
			context.runOnClient(mc -> {
				main(mc).setFocused(main(mc).searchList);
				main(mc).keyPressed(ctrlZ());
			});
			check(!context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)), "Ctrl+Z should undo the mute");

			// Unmute has Undo too.
			context.runOnClient(mc -> {
				main(mc).toggleMute(VILLAGER);
				main(mc).toggleMute(VILLAGER);
			});
			check(toastText(context).startsWith("Unmuted "), "Unmute should show its own toast, was " + toastText(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)), "undoing Unmute mutes again");

			// The toast stays while Undo has focus, past 5 seconds, and Undo still works then.
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> main(mc).setFocused(main(mc).toast().undoButton()));
			context.waitTicks(130);
			check(context.computeOnClient(mc -> main(mc).toast()) != null, "the toast should stay while Undo has focus");
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)), "a held Undo still works after 5 seconds");

			// Without focus the toast goes after 5 seconds.
			context.runOnClient(mc -> {
				main(mc).toggleMute(VILLAGER);
				main(mc).setFocused(main(mc).searchList);
			});
			context.waitTicks(110);
			check(context.computeOnClient(mc -> main(mc).toast()) == null, "the toast should close after 5 seconds");

			// Undo is refused once the profile changed some other way, and nothing changes.
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> ProfileManager.update(p -> p.putRule(SetEntry.of("other", "minecraft:entity.cow.*").withVolume(40))));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(toastText(context).startsWith("Can't undo"), "a stale Undo should say it can't undo, was " + toastText(context));
			check(context.computeOnClient(mc -> ProfileManager.profile().isExactMuted(VILLAGER)
					&& ProfileManager.profile().rule("other") != null), "a refused Undo changes nothing");

			// Rules tab: On/Off, Move and Delete each offer Undo.
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())
					.putRule(SetEntry.of("a", "minecraft:entity.cow.*").withVolume(40))
					.putRule(SetEntry.of("b", "minecraft:entity.pig.*").withMute(true))));
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_RULES));
			context.runOnClient(mc -> main(mc).setRuleEnabled("a", false));
			check(toastText(context).equals("Turned off minecraft:entity.cow.*"), "On/Off toast, was " + toastText(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().rule("a").enabled()), "Undo turns the rule back on");
			context.runOnClient(mc -> main(mc).moveRule("b", -1));
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().get(0).id()).equals("b"), "Move up applies at once");
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().get(0).id()).equals("a"), "Undo puts the order back");
			context.runOnClient(mc -> main(mc).deleteRule("a"));
			check(context.computeOnClient(mc -> ProfileManager.profile().rule("a")) == null, "Delete applies at once");
			check(toastText(context).equals("Deleted the rule for minecraft:entity.cow.*"), "Delete toast, was " + toastText(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().stream().map(SetEntry::id).toList())
					.equals(List.of("a", "b")), "Undo brings the deleted rule back in its place");
			check(context.computeOnClient(mc -> main(mc).rulesList.findRow("a")) != null, "the restored rule is listed again");

			// Zones tab: Delete a zone (with its rules), then Undo.
			context.runOnClient(mc -> ProfileManager.update(p -> p.withZones(List.of(
					new Zone("z1", "Pen", true, key, "minecraft:overworld", new Point(0, -60, 0), new Shape.Box(9, 3, 9),
							List.of(SetEntry.of("r1", "minecraft:entity.sheep.*").withMute(true)))))));
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
					&& ProfileManager.profile().zone("z1").rules().size() == 1), "Undo brings the zone and its rules back");
			check(context.computeOnClient(mc -> main(mc).zonesPanel().list.findRow("z1")) != null, "the restored zone is listed again");
			context.takeScreenshot("undo-toast-zone");

			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
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

	private static SoundSculptorScreen main(Minecraft mc) {
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
