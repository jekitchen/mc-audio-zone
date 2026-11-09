package audiozone.mc;

import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.Draw;
import audiozone.mc.ui.Theme;
import audiozone.mc.ui.demo.UiKitDemoScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main Audio Zone screen (FR-12.1). Still empty; its tabs arrive from M3 on, built with the UI kit.
 * In the development environment it has a button that opens the UI kit demo (M2).
 * Lives in {@code audiozone.mc} because Minecraft's GUI API is version-sensitive (PRD 7.6).
 */
public final class AudioZoneScreen extends Screen {
	private static final int TITLE_Y = 15;

	public AudioZoneScreen() {
		super(Component.translatable("screen.audiozone.title"));
	}

	@Override
	protected void init() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			AzButton demo = AzButton.text(Component.translatable("screen.audiozone.ui_demo"),
					() -> minecraft.gui.setScreen(new UiKitDemoScreen(this)));
			demo.setPosition((width - demo.getWidth()) / 2, height / 2 - Theme.WIDGET_HEIGHT / 2);
			addRenderableWidget(demo);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Draw.centeredText(graphics, this.font, this.title, this.width / 2, TITLE_Y, Theme.TEXT);
	}
}
