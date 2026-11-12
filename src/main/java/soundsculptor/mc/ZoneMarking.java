package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.BoxEdit;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * "Mark corners" (M8 review): the zone editor closes so the player can walk or fly around and left-click (or press the
 * Sound Sculptor key, ;) twice, once for each corner of the box; while marking, left-click never attacks or breaks anything. Each corner is the block under the crosshair, or the block the player
 * stands in if the crosshair is not on a block, so only blocks the player can see or reach count (fair play). The box
 * from the first corner to the current target is outlined live, its size in the on-screen hint; after the second corner the editor comes
 * back with the box filled in. "Cancel zone marking" (Backspace by default, rebindable) returns to the editor unchanged.
 */
public final class ZoneMarking {
	/** Top of the hint, below the Bypass label's row. */
	private static final int HINT_TOP = 18;
	private static ZoneEditorScreen editor;
	private static BlockPos firstCorner;

	private ZoneMarking() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SoundSculptorClient.MOD_ID, "zone_marking"), (graphics, delta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (!isActive() || minecraft.gui.screen() != null) {
				return;
			}
			// Two short centered lines (status, then controls), so the hint fits a small window.
			List<Component> lines = hint();
			int lineHeight = minecraft.font.lineHeight + 2;
			int width = 0;
			for (Component line : lines) {
				width = Math.max(width, minecraft.font.width(line));
			}
			int center = minecraft.getWindow().getGuiScaledWidth() / 2;
			soundsculptor.mc.ui.Draw.fill(graphics, center - width / 2 - 4, HINT_TOP - 3, width + 8, lines.size() * lineHeight + 4,
					soundsculptor.mc.ui.Theme.PANEL);
			for (int i = 0; i < lines.size(); i++) {
				soundsculptor.mc.ui.Draw.centeredText(graphics, minecraft.font, lines.get(i), center, HINT_TOP + i * lineHeight,
						i == 0 ? soundsculptor.mc.ui.Theme.TEXT : soundsculptor.mc.ui.Theme.TEXT_DIM);
			}
		});
	}

	public static boolean isActive() {
		return editor != null;
	}

	/** Leaves the editor open in the background and starts marking. */
	static void start(ZoneEditorScreen screen) {
		editor = screen;
		firstCorner = null;
		Minecraft.getInstance().gui.setScreen(null);
		// A press made while the editor was open (e.g. Backspace in a text field) must not cancel right away.
		while (SoundSculptorClient.cancelMarkingKey.consumeClick()) {
			// drop it
		}
	}

	/** The Sound Sculptor key was pressed while marking: set the next corner. */
	public static void mark(Minecraft minecraft) {
		BlockPos target = target(minecraft);
		if (target == null) {
			return;
		}
		if (firstCorner == null) {
			firstCorner = target;
			return;
		}
		BoxEdit.Placed placed = BoxEdit.fromCorners(firstCorner.getX(), firstCorner.getY(), firstCorner.getZ(),
				target.getX(), target.getY(), target.getZ());
		ZoneEditorScreen screen = editor;
		stop();
		screen.applyBox(placed);
		minecraft.gui.setScreen(screen);
	}

	/** Every client tick: the cancel key (Backspace by default) returns to the editor unchanged. */
	public static void tick(Minecraft minecraft) {
		while (SoundSculptorClient.cancelMarkingKey.consumeClick()) {
			if (isActive()) {
				cancel(minecraft);
			}
		}
		if (isActive() && minecraft.level == null) {
			stop();
		}
	}

	/** Back to the editor, unchanged. */
	public static void cancel(Minecraft minecraft) {
		ZoneEditorScreen screen = editor;
		stop();
		minecraft.gui.setScreen(screen);
	}

	static void stop() {
		editor = null;
		firstCorner = null;
	}

	/** The box being marked, for its live outline: from the first corner to the current target, or the target alone. */
	public static Zone preview(Minecraft minecraft) {
		if (!isActive()) {
			return null;
		}
		BlockPos target = target(minecraft);
		if (target == null) {
			return null;
		}
		BlockPos from = firstCorner != null ? firstCorner : target;
		BoxEdit.Placed placed = BoxEdit.fromCorners(from.getX(), from.getY(), from.getZ(), target.getX(), target.getY(), target.getZ());
		Zone zone = editor.draftZone();
		String name = zone == null || zone.name() == null ? "" : zone.name();
		return new Zone(editor.zoneId(), name, true, ProfileManager.key(), WorldSession.dimension(minecraft), placed.center(),
				placed.box(), PlaceSets.EMPTY);
	}

	/** The color of the zone being marked. */
	public static int color(Minecraft minecraft) {
		return isActive() ? editor.currentColor() : 0xFFFFFF;
	}

	/** The block under the crosshair, or else the block the player stands in. */
	private static BlockPos target(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return block.getBlockPos();
		}
		return minecraft.player == null ? null : minecraft.player.blockPosition();
	}

	/** The on-screen hint: which corner is next with the box's size so far, then the keys. */
	private static List<Component> hint() {
		Component key = SoundSculptorClient.openKey.getTranslatedKeyMessage();
		Component cancel = SoundSculptorClient.cancelMarkingKey.getTranslatedKeyMessage();
		Component status;
		if (firstCorner == null) {
			status = Component.translatable("soundsculptor.marking.first");
		} else {
			Zone preview = preview(Minecraft.getInstance());
			Shape.Box box = preview == null ? null : (Shape.Box) preview.shape();
			status = Component.translatable("soundsculptor.marking.second", box == null ? "" : ZoneOutlines.size(box));
		}
		return List.of(status, Component.translatable("soundsculptor.marking.controls", key, cancel));
	}

	/** The hint's text, for tests. */
	public static String hintText() {
		if (!isActive()) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		for (Component line : hint()) {
			text.append(text.isEmpty() ? "" : "\n").append(line.getString());
		}
		return text.toString();
	}
}
