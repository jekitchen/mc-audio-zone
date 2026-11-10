package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.zones.BoxEdit;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * "Mark corners" (M8 review): the zone editor closes so the player can walk or fly around and press the Audio Zone
 * key (;) twice, once for each corner of the box. Each corner is the block under the crosshair, or the block the player
 * stands in if the crosshair is not on a block, so only blocks the player can see or reach count (fair play). The box
 * from the first corner to the current target is outlined live with its size; after the second corner the editor comes
 * back with the box filled in. "Cancel zone marking" (Backspace by default, rebindable) returns to the editor unchanged.
 */
public final class ZoneMarking {
	private static ZoneEditorScreen editor;
	private static BlockPos firstCorner;

	private ZoneMarking() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(AudioZoneClient.MOD_ID, "zone_marking"), (graphics, delta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (!isActive() || minecraft.gui.screen() != null) {
				return;
			}
			Component hint = hint();
			int x = (minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(hint)) / 2;
			audiozone.mc.ui.Draw.fill(graphics, x - 4, 20, minecraft.font.width(hint) + 8, minecraft.font.lineHeight + 6, audiozone.mc.ui.Theme.PANEL);
			audiozone.mc.ui.Draw.text(graphics, minecraft.font, hint, x, 23, audiozone.mc.ui.Theme.TEXT);
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
		while (AudioZoneClient.cancelMarkingKey.consumeClick()) {
			// drop it
		}
	}

	/** The Audio Zone key was pressed while marking: set the next corner. */
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
		while (AudioZoneClient.cancelMarkingKey.consumeClick()) {
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
				placed.box(), List.of());
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

	private static Component hint() {
		Component key = AudioZoneClient.openKey.getTranslatedKeyMessage();
		Component cancel = AudioZoneClient.cancelMarkingKey.getTranslatedKeyMessage();
		if (firstCorner == null) {
			return Component.translatable("audiozone.marking.first", key, cancel);
		}
		Zone preview = preview(Minecraft.getInstance());
		Shape.Box box = preview == null ? null : (Shape.Box) preview.shape();
		return Component.translatable("audiozone.marking.second", key, box == null ? "" : ZoneOutlines.size(box), cancel);
	}
}
