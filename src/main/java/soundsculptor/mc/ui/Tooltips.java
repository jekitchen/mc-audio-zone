package soundsculptor.mc.ui;

import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/**
 * Tooltip timing (PRD 6.3): tooltips appear after 500 ms of mouse hover, and immediately when a widget gets
 * keyboard focus. Vanilla uses one delay for both, so the screen calls {@link #refresh} every frame to pick the
 * right delay per widget. Vanilla's tooltip positioner keeps tooltips on screen.
 */
public final class Tooltips {
	public static final Duration HOVER_DELAY = Duration.ofMillis(500);

	private Tooltips() {
	}

	/** Gives any widget a tooltip (shown on hover after 500 ms, immediately on keyboard focus). */
	public static <T extends AbstractWidget> T describe(T widget, Component text) {
		widget.setTooltip(Tooltip.create(text));
		return widget;
	}

	public static void refresh(Iterable<? extends GuiEventListener> children) {
		refresh(children, Minecraft.getInstance().getLastInputType().isKeyboard());
	}

	/** Also walks into containers (list rows hold their own buttons). */
	private static void refresh(Iterable<? extends GuiEventListener> children, boolean keyboard) {
		for (GuiEventListener child : children) {
			if (child instanceof AbstractWidget widget) {
				widget.setTooltipDelay(keyboard && widget.isFocused() ? Duration.ZERO : HOVER_DELAY);
			}
			if (child instanceof ContainerEventHandler container) {
				refresh(container.children(), keyboard);
			}
		}
	}
}
