package soundsculptor.gametest;

import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.BoxEdit;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundSculptorSettings;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.mc.ZoneMarking;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * The zone drawing tools (M8 review): new zones are boxes; "Mark corners" sets the box from two blocks marked in the
 * world with left-click (which then breaks nothing) or the Sound Sculptor key, with the size in the on-screen hint; the cancel key returns unchanged; Grow and Shrink
 * move one side and keep the opposite side.
 */
public final class ZoneToolsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				SoundSculptorSettings.setOutlineMode(OutlineMode.EDITING);
			});
			// Look straight ahead over the flat world, so each corner is the block the player stands in.
			world.getServer().runCommand("gamemode creative @a");
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> mc.gui.setScreen(ZoneEditorScreen.create(mc.gui.screen())));
			context.waitForScreen(ZoneEditorScreen.class);
			check(editor(context, e -> e.markCornersButton != null && e.sideDropdown != null && e.shapeDropdown == null),
					"a new zone is a box with the drawing tools and no shape choice");

			// Mark corners: corner 1 where the player stands, corner 2 after moving; the editor returns with that box.
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).markCorners());
			context.waitTicks(2);
			check(context.computeOnClient(mc -> ZoneMarking.isActive() && mc.gui.screen() == null), "marking closes the editor");
			// Corner 1: look straight down and left-click the ground. In Creative a left-click would break the block; while
			// marking it only marks it.
			moveTo(context, world, 0, 0, 90);
			BlockPos first = context.computeOnClient(mc -> ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos());
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(3);
			check(context.computeOnClient(mc -> !mc.level.getBlockState(first).isAir()), "left-click while marking must not break the block");
			// Corner 2: walk away, look level (no block under the crosshair: the block the player stands in), press ;.
			BlockPos second = moveTo(context, world, 4, 6, 0);
			String hint = context.computeOnClient(mc -> ZoneMarking.hintText());
			check(hint.startsWith("Second corner · 5 × 2 × 7 blocks\n") && hint.contains("Left-click a block"),
					"the hint should show the box's size, then the controls, was " + hint);
			context.takeScreenshot("zone-tools-marking");
			context.getInput().pressKey(InputConstants.KEY_SEMICOLON);
			context.waitForScreen(ZoneEditorScreen.class);
			check(!context.computeOnClient(mc -> ZoneMarking.isActive()), "the second corner ends marking");
			BoxEdit.Placed expected = BoxEdit.fromCorners(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
			Zone marked = context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build());
			check(marked.shape().equals(expected.box()) && marked.anchor().equals(expected.center()),
					"the editor should hold the marked box " + expected + ", was " + marked.anchor() + " " + marked.shape());
			check(marked.shape().equals(new Shape.Box(5, 2, 7)), "the ground block and a block 4 and 6 away give a 5 × 2 × 7 box");

			// The cancel key returns to the editor without changing the box.
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).markCorners());
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_SEMICOLON);
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.waitForScreen(ZoneEditorScreen.class);
			check(context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build().shape()).equals(new Shape.Box(5, 2, 7)),
					"cancel keeps the box");

			// Grow and Shrink: Grow east adds width and keeps the west face; Shrink up stops at the 1-block minimum.
			context.runOnClient(mc -> {
				ZoneEditorScreen e = (ZoneEditorScreen) mc.gui.screen();
				e.selectSide(BoxEdit.Side.EAST);
				e.moveSide(1);
			});
			Zone grown = context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build());
			check(grown.shape().equals(new Shape.Box(6, 2, 7)), "Grow east adds a block of width, was " + grown.shape());
			check(Math.abs((grown.anchor().x() - 3) - (marked.anchor().x() - 2.5)) < 1e-9, "the west face stays where it was");
			context.runOnClient(mc -> {
				ZoneEditorScreen e = (ZoneEditorScreen) mc.gui.screen();
				e.selectSide(BoxEdit.Side.UP);
				e.moveSide(-1);
				e.moveSide(-1);
			});
			check(context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).draft().build().shape()).equals(new Shape.Box(6, 1, 7)),
					"Shrink up stops at 1 block high");
			context.takeScreenshot("zone-tools-editor");

			// Save: the zone is stored as the marked and adjusted box.
			String id = context.computeOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).zoneId());
			context.runOnClient(mc -> ((ZoneEditorScreen) mc.gui.screen()).save());
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone(id).shape()).equals(new Shape.Box(6, 1, 7)), "Save stores the box");

			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	/**
	 * Teleports the player {@code dx}, {@code dz} blocks from where the world started, looking {@code pitch} degrees down;
	 * returns the block they stand in.
	 */
	private static BlockPos moveTo(ClientGameTestContext context, TestSingleplayerContext world, int dx, int dz, int pitch) {
		world.getServer().runCommand("tp @a " + (0.5 + dx) + " -60 " + (0.5 + dz) + " 0 " + pitch);
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
