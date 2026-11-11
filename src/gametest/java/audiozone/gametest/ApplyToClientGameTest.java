package audiozone.gametest;

import audiozone.core.profile.Profile;
import audiozone.core.profile.RuleTarget;
import audiozone.core.quick.ApplyTarget;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.mc.AudioZoneScreen;
import audiozone.mc.ProfileManager;
import audiozone.mc.RuleEditorScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * "Apply to" on Search and Recently Played (PRD FR-3.7, FR-1.5, M16): the selector offers Everywhere and the zones the
 * player stands in; with a zone chosen, Mute and Edit work on that zone's exact-ID rule, not the global list; the
 * choice is shared by both tabs, and falls back to Everywhere when the player is no longer in the zone.
 */
public final class ApplyToClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
			Zone pen = new Zone("pen", "Pen", true, key, "minecraft:overworld", new Point(eye.x, eye.y, eye.z), new Shape.Box(9, 9, 9), List.of());
			Zone far = new Zone("far", "Far barn", true, key, "minecraft:overworld", new Point(eye.x + 500, eye.y, eye.z),
					new Shape.Sphere(8), List.of());
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withZones(List.of(pen, far))));
			context.setScreen(AudioZoneScreen::new);
			context.waitForScreen(AudioZoneScreen.class);

			// Search offers Everywhere and the zone the player stands in, not a zone elsewhere.
			context.runOnClient(mc -> {
				main(mc).selectTab(AudioZoneScreen.TAB_SEARCH);
				main(mc).searchField.setValue("villager");
				main(mc).runSearch();
			});
			List<String> choices = context.computeOnClient(mc -> main(mc).applyChoices().stream()
					.map(c -> c.isEverywhere() ? "Everywhere" : c.zoneName()).toList());
			check(choices.equals(List.of("Everywhere", "Pen")), "Apply to should offer Everywhere and Pen, was " + choices);
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Everywhere"),
					"Apply to starts at Everywhere");

			// FR-3.7 AC: set Apply to to the zone, find villager, Mute: the rule is in the zone's rules, not the global list.
			context.runOnClient(mc -> main(mc).setApplyTarget(main(mc).applyChoices().get(1)));
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Zone: Pen"),
					"the selector shows the zone");
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().isEmpty()), "the global list stays empty");
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("pen").rules().stream()
					.anyMatch(r -> r.pattern().text().equals(VILLAGER) && Boolean.TRUE.equals(r.mute()))), "the mute rule is in Pen's rules");
			String toast = context.computeOnClient(mc -> main(mc).toast().message().getString());
			check(toast.startsWith("Muted ") && toast.endsWith(" in Pen"), "the toast names the zone, was " + toast);
			check(rowButton(context).equals("Unmute"), "with Pen chosen, the row offers Unmute");
			context.takeScreenshot("apply-to-search-zone");

			// With Everywhere chosen the same row offers Mute: the global list has no rule for it.
			context.runOnClient(mc -> main(mc).setApplyTarget(ApplyTarget.EVERYWHERE));
			check(rowButton(context).equals("Mute"), "with Everywhere chosen, the row offers Mute");

			// Edit follows Apply to: it opens Pen's rule for the sound.
			context.runOnClient(mc -> main(mc).setApplyTarget(main(mc).applyChoices().get(1)));
			context.runOnClient(mc -> main(mc).searchList.findRow(VILLAGER).buttons().get(1).onPress(null));
			context.waitForScreen(RuleEditorScreen.class);
			check(context.computeOnClient(mc -> ((RuleEditorScreen) mc.gui.screen()).target()).equals(RuleTarget.zone("pen")),
					"Edit opens the rule in Pen's list");
			context.runOnClient(mc -> ((RuleEditorScreen) mc.gui.screen()).onClose());
			context.waitForScreen(AudioZoneScreen.class);

			// Undo removes the zone rule again.
			context.runOnClient(mc -> main(mc).toggleMute(VILLAGER));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("pen").rules().stream()
					.anyMatch(r -> Boolean.TRUE.equals(r.mute()))), "undoing Unmute in Pen mutes there again");

			// Recently Played shares the choice.
			context.runOnClient(mc -> main(mc).selectTab(AudioZoneScreen.TAB_RECENT));
			check(context.computeOnClient(mc -> main(mc).applyToDropdown.getMessage().getString()).contains("Zone: Pen"),
					"Recently Played uses the same Apply to choice");

			// The player is no longer in Pen (it moved away): the selector drops it and falls back to Everywhere.
			context.runOnClient(mc -> ProfileManager.update(p -> p.putZone(new Zone("pen", "Pen", true, key, "minecraft:overworld",
					new Point(eye.x + 900, eye.y, eye.z), new Shape.Box(9, 9, 9), p.zone("pen").rules()))));
			context.waitTicks(AudioZoneScreen.REFRESH_TICKS * 2);
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

	private static AudioZoneScreen main(Minecraft mc) {
		return (AudioZoneScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
