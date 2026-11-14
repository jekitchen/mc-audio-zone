package soundsculptor.mc.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Dropdown (PRD 6.2). Shows "Label: Option" with an arrow; Enter, Space or a click opens the option list in an
 * overlay above the screen. In the list, Up/Down move, Enter picks, Esc or a click outside cancels; focus then
 * returns to the dropdown. The narrator reads the selected option, and each option with its position ("2 of 4").
 * Must be used on an {@link SculptScreen}.
 */
public class SculptDropdown<T> extends AbstractButton {
	private static final String ARROW = "▼";
	/** Room around a dropdown's text: its borders and the arrow. */
	public static final int TEXT_PADDING = 28;

	private final Component label;
	private final List<T> options;
	private final Function<T, Component> names;
	private final Consumer<T> onChange;
	private T value;

	public SculptDropdown(int width, Component label, List<T> options, Function<T, Component> names, T initial, Consumer<T> onChange) {
		super(0, 0, width, Theme.WIDGET_HEIGHT, Component.empty());
		if (options.isEmpty()) {
			throw new IllegalArgumentException("a dropdown needs options");
		}
		this.label = label;
		this.options = List.copyOf(options);
		this.names = names;
		this.onChange = onChange;
		this.value = options.contains(initial) ? initial : options.get(0);
		refreshMessage();
	}

	/**
	 * The width of a dropdown that shows "Label: Option" in full for the widest of {@code optionNames}, so it keeps its
	 * size when the choice changes and a longer translation is not cut (PRD 9.3).
	 */
	public static int widthFor(Component label, List<Component> optionNames) {
		Font font = Minecraft.getInstance().font;
		int widest = 0;
		for (Component name : optionNames) {
			widest = Math.max(widest, font.width(Component.translatable("soundsculptor.ui.toggle", label, name)));
		}
		return widest + TEXT_PADDING;
	}

	public T value() {
		return value;
	}

	/** Sets the value without calling the change listener. */
	public void setValue(T newValue) {
		if (options.contains(newValue)) {
			value = newValue;
			refreshMessage();
		}
	}

	private void refreshMessage() {
		setMessage(Component.translatable("soundsculptor.ui.toggle", label, names.apply(value)));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (!(Minecraft.getInstance().gui.screen() instanceof SculptScreen screen)) {
			return;
		}
		List<Option> buttons = new ArrayList<>();
		Option current = null;
		for (int i = 0; i < options.size(); i++) {
			Option option = new Option(options.get(i), i);
			buttons.add(option);
			if (options.get(i).equals(value)) {
				current = option;
			}
		}
		int listHeight = options.size() * Theme.WIDGET_HEIGHT;
		int below = getBottom() + 1;
		int limit = screen.height - SculptScreen.FOOTER_HEIGHT;
		int y = below + listHeight <= limit ? below : Math.max(0, getY() - 1 - listHeight);
		for (Option option : buttons) {
			option.setPosition(getX(), y + option.index * Theme.WIDGET_HEIGHT);
		}
		screen.openOverlay(getX(), y, getWidth(), listHeight, buttons, current, () -> screen.setFocused(this));
	}

	private void choose(T option) {
		boolean changed = !option.equals(value);
		value = option;
		refreshMessage();
		if (Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
			screen.closeOverlay();
		}
		if (changed) {
			onChange.accept(option);
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(g);
		Font font = Minecraft.getInstance().font;
		int arrowWidth = font.width(ARROW);
		int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
		Draw.truncatedText(g, font, getMessage().getString(), getX() + 6, textY, getWidth() - 16 - arrowWidth, Theme.TEXT);
		Draw.text(g, font, ARROW, getRight() - 6 - arrowWidth, textY, Theme.TEXT);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("soundsculptor.ui.dropdown", getMessage()));
		output.add(NarratedElementType.USAGE, Component.translatable("soundsculptor.ui.dropdown.usage"));
	}

	/** One option in the open list. */
	public final class Option extends AbstractButton {
		private final T option;
		private final int index;

		private Option(T option, int index) {
			super(0, 0, SculptDropdown.this.getWidth(), Theme.WIDGET_HEIGHT, names.apply(option));
			this.option = option;
			this.index = index;
		}

		public T option() {
			return option;
		}

		public boolean isSelected() {
			return option.equals(value);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			choose(option);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			if (isHoveredOrFocused()) {
				Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_FOCUSED);
			}
			Font font = Minecraft.getInstance().font;
			// The current option is marked by a check mark (text), not only by color.
			String text = (isSelected() ? "✔ " : "   ") + getMessage().getString();
			Draw.truncatedText(g, font, text, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2 + 1, getWidth() - 12, Theme.TEXT);
			if (isFocused()) {
				Draw.focusRing(g, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, isSelected()
					? Component.translatable("soundsculptor.ui.dropdown.option.selected", getMessage())
					: getMessage());
			output.add(NarratedElementType.POSITION, Component.translatable("soundsculptor.ui.tab.position", index + 1, options.size()));
		}
	}
}
