package soundsculptor.mc.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * On/off toggle (PRD 6.2). Shows "Label: On" or "Label: Off" as text, never color alone; Enter or Space toggles;
 * the narrator reads the label and state.
 */
public class SculptToggle extends AbstractButton {
	private final Component label;
	private final Consumer<Boolean> onChange;
	private boolean value;

	public SculptToggle(int width, Component label, boolean initial, Consumer<Boolean> onChange) {
		super(0, 0, width, Theme.WIDGET_HEIGHT, Component.empty());
		this.label = label;
		this.onChange = onChange;
		this.value = initial;
		refreshMessage();
	}

	public boolean value() {
		return value;
	}

	/** Sets the value without calling the change listener. */
	public void setValue(boolean newValue) {
		value = newValue;
		refreshMessage();
	}

	@Override
	public void onPress(InputWithModifiers input) {
		value = !value;
		refreshMessage();
		onChange.accept(value);
	}

	private void refreshMessage() {
		setMessage(Component.translatable("soundsculptor.ui.toggle", label, stateText(value)));
	}

	public static Component stateText(boolean on) {
		return Component.translatable(on ? "soundsculptor.ui.on" : "soundsculptor.ui.off");
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(g);
		extractDefaultLabel(Draw.widgetLabel(g, this));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
