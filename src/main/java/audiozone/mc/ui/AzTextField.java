package audiozone.mc.ui;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text field (PRD 6.2) on top of vanilla's edit box, with a placeholder, a validation callback and an error slot
 * under the field. Invalid input outlines the field and shows the message; the narrator reads the error as soon as
 * it appears and again as part of the field's narration (PRD FR-12.7, A-4).
 *
 * <p>The error slot is drawn below the field's bounds, so layouts must leave {@link #ERROR_SLOT_HEIGHT} pixels
 * under every text field.
 */
public class AzTextField extends EditBox {
	public static final int ERROR_SLOT_HEIGHT = 11;

	private Function<String, Component> validator = value -> null;
	private Consumer<String> onChange = value -> { };
	private Component error;

	public AzTextField(Font font, int width, Component label) {
		super(font, 0, 0, width, Theme.WIDGET_HEIGHT, label);
		super.setResponder(this::changed);
	}

	public AzTextField placeholder(Component placeholder) {
		setHint(placeholder);
		return this;
	}

	/** {@code validator} returns an error message, or null when the value is valid. Runs on every change. */
	public AzTextField validator(Function<String, Component> validator) {
		this.validator = validator;
		validate(false);
		return this;
	}

	public AzTextField onChange(Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	/** Use {@link #onChange} instead: the field's own responder runs validation. */
	@Override
	public void setResponder(Consumer<String> responder) {
		onChange(responder);
	}

	public boolean isValid() {
		return error == null;
	}

	public Component error() {
		return error;
	}

	private void changed(String value) {
		validate(true);
		onChange.accept(value);
	}

	private void validate(boolean announce) {
		Component newError = validator.apply(getValue());
		boolean appeared = newError != null && (error == null || !newError.getString().equals(error.getString()));
		error = newError;
		if (announce && appeared) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.getNarrator().isActive()) {
				minecraft.getNarrator().saySystemQueued(newError);
			}
		}
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		MutableComponent message = super.createNarrationMessage();
		return error == null ? message : Component.translatable("audiozone.ui.field.error_narration", message, error);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
		if (error != null) {
			Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.ERROR);
			if (isFocused()) {
				// The error outline covers vanilla's focus border, so keep focus visible outside it (PRD A-1).
				Draw.focusRing(g, getX(), getY(), getWidth(), getHeight());
			}
			Font font = Minecraft.getInstance().font;
			Draw.truncatedText(g, font, error.getString(), getX(), getBottom() + 2, getWidth(), Theme.ERROR);
		}
	}
}
