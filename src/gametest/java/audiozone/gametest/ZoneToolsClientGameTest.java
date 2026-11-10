package audiozone.gametest;

import audiozone.core.outline.OutlineMode;
import audiozone.core.profile.Profile;
import audiozone.core.zones.BoxEdit;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.mc.AudioZoneScreen;
import audiozone.mc.AudioZoneSettings;
import audiozone.mc.ProfileManager;
import audiozone.mc.ZoneEditorScreen;
import audiozone.mc.ZoneMarking;
import audiozone.mc.ZoneOutlines;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * The zone drawing tools (M8 review): new zones are boxes; "Mark corners" sets the box from two blocks marked in the
 * world with the Audio Zone key, with a live outline and size label; the cancel key returns unchanged; Grow and Shrink
 * move one side and keep the opposite side.
 */
public final class ZoneToolsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				AudioZoneSettings.setOutlineMode(OutlineMode.EDITING);
			});
			// Look straight ahead over the flat world, so each corner is the block the player stands in.
			world.getServer().runCommand("gamemode creative @a");
			context.setScreen(AudioZoneScreen::new);
			context.waitForScreen(AudioZoneScreen.class);
			context.runOnClient(mc -> mc.gui.setScreen(ZoneEditorScreen.create(mc.gui.screen())));
			context.waitForScreen(ZoneEditorScreen.class);
			check(editor(context, e -> e.markCornersButton != null && e.sideDropdown != null && e.shapeDropdown == null),
					"a new zone is a box with the drawing tools and no shape choice");

			// Mark corners: corner 1 where the player stands, corner 2 after moving; the editor returns with that box.
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).markCorners());
			context.waitTicks(2);
			check(context.computeOnClient(mc -> ZoneMarking.isActive() && mc.gui.screen() == null), "marking closes the editor");
			BlockPos first = moveTo(context, world, 0, 0);
			context.getInput().pressKey(InputConstants.KEY_SEMICOLON);
			context.waitTicks(2);
			BlockPos second = moveTo(context, world, 4, 6);
			context.waitTicks(3);
			String label = context.computeOnClient(mc -> ZoneOutlines.lastSizeLabel());
			check(label.equals("5 × 1 × 7 blocks · You are inside"), "the box being marked should show its size, was " + label);
			context.takeScreenshot("zone-tools-marking");
			context.getInput().pressKey(InputConstants.KEY_SEMICOLON);
			context.waitForScreen(ZoneEditorScreen.class);
			check(!context.computeOnClient(mc -> ZoneMarking.isActive()), "the second corner ends marking");
			BoxEdit.Placed expected = BoxEdit.fromCorners(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
			Zone marked = context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build());
			check(marked.shape().equals(expected.box()) && marked.anchor().equals(expected.center()),
					"the editor should hold the marked box " + expected + ", was " + marked.anchor() + " " + marked.shape());
			check(marked.shape().equals(new Shape.Box(5, 1, 7)), "corners 4 and 6 blocks apart give a 5 × 1 × 7 box");

			// The cancel key returns to the editor without changing the box.
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).markCorners());
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_SEMICOLON);
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.waitForScreen(ZoneEditorScreen.class);
			check(context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build().shape()).equals(new Shape.Box(5, 1, 7)),
					"cancel keeps the box");

			// Grow and Shrink: Grow east adds width and keeps the west face; Shrink up stops at the 1-block minimum.
			context.runOnClient(mc -> {
				ZoneEditorScreen e = (ZoneEditorScreen) mc.gui.screen();
				e.selectSide(BoxEdit.Side.EAST);
				e.moveSide(1);
			});
			Zone grown = context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build());
			check(grown.shape().equals(new Shape.Box(6, 1, 7)), "Grow east adds a block of width, was " + grown.shape());
			check(Math.abs((grown.anchor().x() - 3) - (marked.anchor().x() - 2.5)) < 1e-9, "the west face stays where it was");
			context.runOnClient(mc -> {
				ZoneEditorScreen e = (ZoneEditorScreen) mc.gui.screen();
				e.selectSide(BoxEdit.Side.UP);
				e.moveSide(-1);
			});
			check(context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build().shape()).equals(new Shape.Box(6, 1, 7)),
					"a box cannot shrink below 1 block");
			context.takeScreenshot("zone-tools-editor");

			// Save: the zone is stored as the marked and adjusted box.
			String id = context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).zoneId());
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).save());
			context.waitForScreen(AudioZoneScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone(id).shape()).equals(new Shape.Box(6, 1, 7)), "Save stores the box");

			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	/** Teleports the player {@code dx}, {@code dz} blocks from where the world started, facing level; returns their block. */
	private static BlockPos moveTo(ClientGameTestContext context, TestSingleplayerContext world, int dx, int dz) {
		world.getServer().runCommand("tp @a " + (0.5 + dx) + " -60 " + (0.5 + dz) + " 0 0");
		context.waitTicks(3);
		return context.computeOnClient(mc -> mc.player.blockPosition());
	}

	private static <T> T editor(ClientGameTestContext context, Function<ZoneEditorScreen, T> read) {
		return context.computeOnClient(mc -> read.apply((ZoneEditorScreen) mc.gui.screen()));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
