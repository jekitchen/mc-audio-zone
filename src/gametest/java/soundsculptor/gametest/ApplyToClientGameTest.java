package soundsculptor.gametest;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.EntryEditorScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * "Apply to" on Search and Recently Played (PRD FR-3.7, FR-1.5, M16): the selector offers Everywhere and the zones the
 * player stands in; with a zone chosen, Mute and Edit work on the sound's entry in that zone's Quick changes, not
 * Everywhere's (FR-4.10); the choice is shared by both tabs, and falls back to Everywhere when the player is no longer in the zone.
 */
public final class ApplyToClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
			Zone pen = new Zone("pen", "Pen", true, key, "minecraft:overworld", new Point(eye.x, eye.y, eye.z), new Shape.Box(9, 9, 9), PlaceSets.EMPTY);
			Zone far = new Zone("far", "Far barn", true, key, "minecraft:overworld", new Point(eye.x + 500, eye.y, eye.z),
					new Shape.Sphere(8), PlaceSets.EMPTY);
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withZones(List.of(pen, far))));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);

			// Search offers Everywhere and the zone the player stands in, not a zone elsewhere.
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SEARCH);
				main(mc).searchField.setValue("villager");
				main(mc).runSearch();
			});
			List<String> choices = context.computeOnClient(mc -> main(mc).applyChoices().stream()
					.map(c -> c.isEverywhere() ? "Everywhere" : c.zoneName()).toList());
			check(choices.equals(List.of("Everywhere", "Pen")), "Apply to should offer Everywhere and Pen, was " + choices);
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Everywhere"),
					"Apply to starts at Everywhere");

			// FR-3.7 AC: set Apply to to the zone, find villager, Mute: the entry is in the zone's Quick changes, not Everywhere's.
			context.runOnClient(mc -> main(mc).setApplyTarget(main(mc).applyChoices().get(1)));
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Zone: Pen"),
					"the selector shows the zone");
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickChanges() == null),
					"Everywhere has no Quick changes");
			check(context.computeOnClient(mc -> {
				SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.zone("pen"), VILLAGER);
				return entry != null && Boolean.TRUE.equals(entry.mute())
						&& ProfileManager.profile().zone("pen").sets().attached().get(0).setId().equals(SoundSet.QUICK_ID);
			}), "the mute is a single-sound entry in Pen's Quick changes, pinned first (FR-4.10)");
			String toast = context.computeOnClient(mc -> main(mc).toast().message().getString());
			check(toast.startsWith("Muted ") && toast.endsWith(" in Pen"), "the toast names the zone, was " + toast);
			check(rowButton(context).equals("Unmute"), "with Pen chosen, the row offers Unmute");
			context.takeScreenshot("apply-to-search-zone");

			// With Everywhere chosen the same row offers Mute: nothing mutes it there.
			context.runOnClient(mc -> main(mc).setApplyTarget(ApplyTarget.EVERYWHERE));
			check(rowButton(context).equals("Mute"), "with Everywhere chosen, the row offers Mute");

			// Edit follows Apply to: it opens the sound's entry in Pen's Quick changes.
			context.runOnClient(mc -> main(mc).setApplyTarget(main(mc).applyChoices().get(1)));
			context.runOnClient(mc -> main(mc).searchList.findRow(VILLAGER).buttons().get(1).onPress(null));
			context.waitForScreen(EntryEditorScreen.class);
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).quickPlace()).equals(PlaceRef.zone("pen")),
					"Edit opens the entry in Pen's Quick changes");
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).draft().build().id()
					.equals(QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.zone("pen"), VILLAGER).id())),
					"Edit reuses the sound's existing entry, never a duplicate");
			context.runOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).onClose());
			context.waitForScreen(SoundSculptorScreen.class);

			// Unmute clears the zone's entry; Undo puts it back.
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			check(context.computeOnClient(mc -> QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.zone("pen"), VILLAGER) == null),
					"Unmute in Pen clears the entry and its Quick changes");
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> Boolean.TRUE.equals(
					QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.zone("pen"), VILLAGER).mute())),
					"undoing Unmute in Pen mutes there again");

			// Recently Played shares the choice.
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_RECENT));
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Zone: Pen"),
					"Recently Played uses the same Apply to choice");

			// The player is no longer in Pen (it moved away): the selector drops it and falls back to Everywhere.
			context.runOnClient(mc -> ProfileManager.update(p -> p.putZone(new Zone("pen", "Pen", true, key, "minecraft:overworld",
					new Point(eye.x + 900, eye.y, eye.z), new Shape.Box(9, 9, 9), p.zone("pen").sets()))));
			context.waitTicks(SoundSculptorScreen.REFRESH_TICKS * 2);
			check(context.computeOnClient(mc -> main(mc).applyChoices().size()) == 1, "only Everywhere is left");
			check(context.computeOnClient(mc -> main(mc).applyTarget().isEverywhere()), "Apply to falls back to Everywhere");
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Everywhere"),
					"the selector shows Everywhere again");

			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static String rowButton(ClientGameTestContext context) {
		return context.computeOnClient(mc -> main(mc).searchList.findRow(VILLAGER).buttons().get(0).getMessage().getString());
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
