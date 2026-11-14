package soundsculptor.mc.ui;

import soundsculptor.core.ui.SliderModel;
import soundsculptor.mc.compat.Keys;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Slider (PRD 6.2) on top of vanilla's slider. Keyboard: Left/Right move by one step, Shift+Left/Right by the fine
 * step. Values above the warning threshold draw a warning outline, and the narrator adds the warning text after the
 * value; the label itself shows only "Label: value" (the explanation belongs in the slider's tooltip). Typed values
 * come in through {@link #setTypedValue}.
 */
public class SculptSlider extends AbstractSliderButton {
	private final Component label;
	private final SliderModel model;
	private final DoubleFunction<Component> format;
	private final Component warningText;
	private final DoubleConsumer onChange;

	/**
	 * @param format      turns a value into display text, e.g. {@code v -> Component.literal((int) v + "%")}
	 * @param warningText narrated after the value in the warning zone, e.g. "louder than vanilla"; may be null if
	 *                    the model has no warning zone
	 */
	public SculptSlider(int width, Component label, SliderModel model, DoubleFunction<Component> format,
			Component warningText, DoubleConsumer onChange) {
		super(0, 0, width, Theme.WIDGET_HEIGHT, Component.empty(), model.fraction());
		this.label = label;
		this.model = model;
		this.format = format;
		this.warningText = warningText;
		this.onChange = onChange;
		updateMessage();
	}

	public double modelValue() {
		return model.value();
	}

	public boolean isWarning() {
		return model.isWarning();
	}

	/** A value typed into a paired number field. */
	public void setTypedValue(double typed) {
		double before = model.value();
		model.set(typed);
		syncFromModel(before);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		double before = model.value();
		int key = event.key();
		if (key == Keys.LEFT) {
			model.stepBy(-1, event.hasShiftDown());
		} else if (key == Keys.RIGHT) {
			model.stepBy(1, event.hasShiftDown());
		} else {
			return super.keyPressed(event);
		}
		syncFromModel(before);
		return true;
	}

	private void syncFromModel(double before) {
		this.value = model.fraction();
		updateMessage();
		if (model.value() != before) {
			onChange.accept(model.value());
		}
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.translatable("soundsculptor.ui.slider", label, format.apply(model.value())));
	}

	/** The narrator still hears the warning; it is not on the visible label. */
	@Override
	protected MutableComponent createNarrationMessage() {
		MutableComponent message = super.createNarrationMessage();
		return model.isWarning() && warningText != null
				? Component.translatable("soundsculptor.ui.slider.warning", message, warningText)
				: message;
	}

	/** Mouse input: vanilla sets {@code value} to the pointer position; snap it to the step grid. */
	@Override
	protected void applyValue() {
		double before = model.value();
		model.setFraction(this.value);
		this.value = model.fraction();
		if (model.value() != before) {
			onChange.accept(model.value());
		}
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
		if (model.isWarning()) {
			Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.WARNING);
			if (isFocused()) {
				Draw.focusRing(g, getX(), getY(), getWidth(), getHeight());
			}
		}
	}
}
