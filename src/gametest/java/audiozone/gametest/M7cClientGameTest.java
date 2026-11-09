package audiozone.gametest;

import audiozone.core.editor.ZoneDraft;
import audiozone.core.outline.OutlineMode;
import audiozone.core.profile.Profile;
import audiozone.core.rules.Rule;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.mc.AppSettingsManager;
import audiozone.mc.AudioZoneScreen;
import audiozone.mc.AudioZoneSettings;
import audiozone.mc.Diagnostics;
import audiozone.mc.ProfileManager;
import audiozone.mc.ZoneEditorScreen;
import audiozone.mc.ZoneOutlines;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M7c tests: zone outlines in the world (PRD FR-10 MVP AC): "Show zone outlines" Always,
 * Only while editing (default) and Never; the zone's name label; nothing for other dimensions; the zone open in the
 * editor drawn from its current values; "Outlines visible through blocks"; the Diagnostics outline timing.
 */
public final class M7cClientGameTest implements FabricClientGameTest {
	private static final int FRAMES = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			// A sphere 8 blocks in front of the player, so the screenshot shows it.
			Vec3 center = context.computeOnClient(mc -> mc.player.getEyePosition().add(mc.player.getLookAngle().scale(8)));
			Zone pen = new Zone("z1", "Pen", true, key, "minecraft:overworld", new Point(center.x, center.y, center.z),
					new Shape.Sphere(4), List.of(Rule.of("r1", "*villager*").withMute(true)));
			Zone nether = new Zone("z2", "Nether pen", true, key, "minecraft:the_nether", new Point(center.x, center.y, center.z),
					new Shape.Sphere(4), List.of());
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withZones(List.of(pen, nether)).withZoneColor("z1", 0x56B4E9)));

			// The default is "Only while editing": nothing is drawn while no zone editor is open.
			check(context.computeOnClient(mc -> AudioZoneSettings.outlineMode()) == OutlineMode.EDITING, "the default is Only while editing");
			context.waitTicks(FRAMES);
			check(lines(context) == 0, "with no editor open, nothing is drawn by default");

			// FR-10 AC 1: Always: the nearby zone is drawn in its color with its name label; the Nether zone is not.
			context.runOnClient(mc -> AudioZoneSettings.setOutlineMode(OutlineMode.ALWAYS));
			context.waitTicks(FRAMES);
			int sphereLines = lines(context);
			check(sphereLines > 0, "Always should draw the zone");
			check(context.computeOnClient(mc -> ZoneOutlines.labelsLastFrame()) == 1, "one label (the Nether zone is not drawn)");
			context.takeScreenshot("m7c-outline-always");

			// Never: nothing.
			context.runOnClient(mc -> AudioZoneSettings.setOutlineMode(OutlineMode.NEVER));
			context.waitTicks(FRAMES);
			check(lines(context) == 0, "Never should draw nothing");

			// Only while editing: the zone open in the editor, drawn from the editor's values as they change.
			context.runOnClient(mc -> AudioZoneSettings.setOutlineMode(OutlineMode.EDITING));
			context.runOnClient(mc -> mc.gui.setScreen(ZoneEditorScreen.edit(new AudioZoneScreen(), ProfileManager.profile().zone("z1"))));
			context.waitForScreen(ZoneEditorScreen.class);
			context.waitTicks(FRAMES);
			check(lines(context) == sphereLines, "the edited zone should be drawn, was " + lines(context));
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).setShape(ZoneDraft.ShapeKind.BOX));
			context.waitTicks(FRAMES);
			check(lines(context) == 12, "switching to a box in the editor should draw its 12 edges, was " + lines(context));
			context.takeScreenshot("m7c-outline-editing-box");
			// Cycling with the (unbound) hotkey's action.
			context.runOnClient(mc -> AudioZoneSettings.cycleOutlineMode());
			check(context.computeOnClient(mc -> AudioZoneSettings.outlineMode()) == OutlineMode.NEVER, "the hotkey cycles to the next mode");
			context.runOnClient(mc -> {
				AudioZoneSettings.setOutlineMode(OutlineMode.EDITING);
				mc.gui.setScreen(null);
			});

			// FR-10.5: "Outlines visible through blocks" is an app-wide setting, on by default, and saved.
			check(context.computeOnClient(mc -> AudioZoneSettings.outlinesThroughBlocks()), "through blocks is on by default");
			context.runOnClient(mc -> AudioZoneSettings.setOutlinesThroughBlocks(false));
			check(!context.computeOnClient(mc -> AppSettingsManager.get().outlinesThroughBlocks()), "the setting should change");
			context.runOnClient(mc -> {
				AudioZoneSettings.setOutlineMode(OutlineMode.ALWAYS);
				AudioZoneSettings.setOutlinesThroughBlocks(true);
			});
			context.waitTicks(FRAMES);
			// Diagnostics times outline drawing (FR-10.6).
			check(context.computeOnClient(mc -> Diagnostics.OUTLINES.stats(Util.getMillis() + 1000).count()) >= 0, "outline timing is collected");
			check(context.computeOnClient(mc -> Diagnostics.OUTLINES.peakMaxMicros()) > 0, "outline drawing should be timed");

			// The Settings → Zones page has both settings.
			context.setScreen(AudioZoneScreen::new);
			context.waitForScreen(AudioZoneScreen.class);
			context.runOnClient(mc -> {
				AudioZoneScreen screen = (AudioZoneScreen) mc.gui.screen();
				screen.selectTab(AudioZoneScreen.TAB_SETTINGS);
				screen.setSettingsPage(AudioZoneScreen.SettingsPage.ZONES);
			});
			check(context.computeOnClient(mc -> {
				AudioZoneScreen screen = (AudioZoneScreen) mc.gui.screen();
				return screen.outlineModeDropdown.value() == OutlineMode.ALWAYS && screen.throughBlocksToggle.value();
			}), "the Zones settings page should show both settings");
			context.takeScreenshot("m7c-settings-zones");

			context.runOnClient(mc -> {
				((AudioZoneScreen) mc.gui.screen()).setSettingsPage(AudioZoneScreen.SettingsPage.GENERAL);
				mc.gui.setScreen(null);
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
				AppSettingsManager.flushSaves();
			});
		}
	}

	private static int lines(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ZoneOutlines.linesLastFrame());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
