package audiozone.mc.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Button and icon button (PRD 6.2). Built on vanilla's button, which provides the hover, pressed, disabled and
 * focused looks (the focused look has a white border, PRD A-1) and Enter/Space activation.
 *
 * <p>An icon button shows only its icon; its label is used for the tooltip and the narration, so every icon has a
 * text label.
 */
public class AzButton extends AbstractButton {
	private final Runnable onPress;
	private final Identifier icon;

	protected AzButton(int width, int height, Component label, Identifier icon, Runnable onPress) {
		super(0, 0, width, height, label);
		this.icon = icon;
		this.onPress = onPress;
	}

	/** A text button sized to its label (at least 60 pixels wide). */
	public static AzButton text(Component label, Runnable onPress) {
		int width = Math.max(60, net.minecraft.client.Minecraft.getInstance().font.width(label) + 16);
		return new AzButton(width, Theme.WIDGET_HEIGHT, label, null, onPress);
	}

	public static AzButton text(Component label, int width, Runnable onPress) {
		return new AzButton(width, Theme.WIDGET_HEIGHT, label, null, onPress);
	}

	/**
	 * A square icon button. {@code label} is required: it becomes the tooltip and what the narrator reads.
	 *
	 * @param icon a GUI sprite such as {@code audiozone:icon/mute}
	 */
	public static AzButton icon(Identifier icon, Component label, Runnable onPress) {
		AzButton button = new AzButton(Theme.ICON_BUTTON_SIZE, Theme.ICON_BUTTON_SIZE, label, icon, onPress);
		button.setTooltip(Tooltip.create(label));
		return button;
	}

	public boolean isIconButton() {
		return icon != null;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(g);
		if (icon != null) {
			int x = getX() + (getWidth() - Theme.ICON_SIZE) / 2;
			int y = getY() + (getHeight() - Theme.ICON_SIZE) / 2;
			Draw.sprite(g, icon, x, y, Theme.ICON_SIZE, Theme.ICON_SIZE);
		} else {
			extractDefaultLabel(Draw.widgetLabel(g, this));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
