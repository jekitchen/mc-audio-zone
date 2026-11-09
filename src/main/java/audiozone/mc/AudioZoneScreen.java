package audiozone.mc;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main Audio Zone screen (FR-12.1). Empty at M0; tabs arrive in later milestones.
 * Lives in {@code audiozone.mc} because Minecraft's GUI API is version-sensitive (PRD 7.6).
 */
public final class AudioZoneScreen extends Screen {
	private static final int TITLE_Y = 15;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	public AudioZoneScreen() {
		super(Component.translatable("screen.audiozone.title"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_Y, TEXT_COLOR);
	}
}
