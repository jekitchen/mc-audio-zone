package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.mc.ui.Draw;
import audiozone.mc.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** HUD label "Audio Zone: bypassed" while Bypass is on (FR-13.1), top left. */
public final class BypassLabel {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(AudioZoneClient.MOD_ID, "bypass_label");
	private static final int MARGIN = 4;

	private BypassLabel() {
	}

	public static void register() {
		HudElementRegistry.addLast(ID, (g, delta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			// Vanilla skips HUD elements entirely while the HUD is hidden (F1).
			if (!ProfileManager.isBypassed()) {
				return;
			}
			Font font = minecraft.font;
			Component text = Component.translatable("audiozone.hud.bypassed");
			int width = font.width(text);
			Draw.fill(g, MARGIN - 2, MARGIN - 2, width + 4, font.lineHeight + 3, Theme.PANEL);
			Draw.text(g, font, text, MARGIN, MARGIN, Theme.WARNING);
		});
	}
}
