package soundsculptor.mc.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * One tab in the screen's tab bar. The selected tab has a lighter background and an underline, and the narrator
 * says "selected" and the tab's position ("Tab 2 of 4"), so selection is not shown by color alone.
 */
public class SculptTabButton extends AbstractButton {
	private final int index;
	private final int count;
	private final boolean selected;
	private final Runnable onSelect;

	public SculptTabButton(int width, Component title, int index, int count, boolean selected, Runnable onSelect) {
		super(0, 0, width, Theme.WIDGET_HEIGHT, title);
		this.index = index;
		this.count = count;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	public boolean isSelected() {
		return selected;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (!selected) {
			onSelect.run();
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		if (selected) {
			Draw.fill(g, x, y, w, h, Theme.TAB_SELECTED);
			Draw.fill(g, x, y + h - 2, w, 2, Theme.TAB_UNDERLINE);
		} else if (isHoveredOrFocused()) {
			Draw.fill(g, x, y, w, h, Theme.TAB_HOVER);
		}
		Font font = Minecraft.getInstance().font;
		String title = Draw.truncate(font, getMessage().getString(), w - 6);
		int textX = x + (w - font.width(title)) / 2;
		Draw.text(g, font, title, textX, y + (h - font.lineHeight) / 2 + 1, selected ? Theme.TEXT : Theme.TEXT_DIM);
		if (isFocused()) {
			Draw.focusRing(g, x, y, w, h);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, selected
				? Component.translatable("soundsculptor.ui.tab.selected", getMessage())
				: Component.translatable("soundsculptor.ui.tab", getMessage()));
		output.add(NarratedElementType.POSITION, Component.translatable("soundsculptor.ui.tab.position", index + 1, count));
		if (!selected) {
			output.add(NarratedElementType.USAGE, Component.translatable("soundsculptor.ui.tab.usage"));
		}
	}
}
