package soundsculptor.mc.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A checkbox the size of an icon button, for list rows (PRD 6.2): an empty box, or a box with a "✔" when checked, so
 * the state is a shape and never color alone. Enter or Space presses it; the caller changes its state, usually by
 * rebuilding the row. The narrator reads the row, the label and the state: "Cow moos: Pick, checkbox, not checked".
 */
public final class SculptCheckbox extends SculptButton {
	private static final Component CHECK = Component.literal("✔");
	private final Component label;
	private final boolean checked;

	/**
	 * @param label   what checking it does, e.g. "Pick"; read with the state
	 * @param tooltip shown on hover and keyboard focus
	 */
	public SculptCheckbox(Component label, Component tooltip, boolean checked, Runnable onPress) {
		super(Theme.ICON_BUTTON_SIZE, Theme.ICON_BUTTON_SIZE, Component.translatable("soundsculptor.ui.checkbox", label, stateText(checked)), null,
				onPress);
		this.label = label;
		this.checked = checked;
		setTooltip(Tooltip.create(tooltip));
	}

	public boolean checked() {
		return checked;
	}

	public static Component stateText(boolean checked) {
		return Component.translatable(checked ? "soundsculptor.ui.checked" : "soundsculptor.ui.unchecked");
	}

	/** It shows a box, not its label. */
	@Override
	public boolean isIconButton() {
		return true;
	}

	@Override
	protected MutableComponent ownNarration() {
		return Component.translatable("soundsculptor.ui.checkbox.narration", label, stateText(checked));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(g);
		int size = Theme.ICON_SIZE - 4;
		int x = getX() + (getWidth() - size) / 2;
		int y = getY() + (getHeight() - size) / 2;
		Draw.fill(g, x, y, size, size, Theme.LIST_BACKGROUND);
		Draw.outline(g, x, y, size, size, Theme.TEXT);
		if (checked) {
			var font = Minecraft.getInstance().font;
			Draw.centeredText(g, font, CHECK, x + size / 2, y + (size - font.lineHeight) / 2 + 1, Theme.TEXT);
		}
	}
}
