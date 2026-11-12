package soundsculptor.mc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Modal dialog with 2 or 3 buttons (PRD 6.2). It is its own screen, so focus cannot leave it. The default button
 * has focus when it opens; Esc picks the safest choice. The narrator reads the title and the message.
 *
 * <p>Choosing a button returns to the parent screen first, then runs the choice's action, so an action may open
 * another screen.
 */
public final class SculptDialog extends Screen {
	private static final int MAX_WIDTH = 300;

	public record Choice(Component label, Runnable action) {
	}

	private final Screen parent;
	private final Component message;
	private final List<Choice> choices;
	private final int defaultChoice;
	private final int safeChoice;
	private final List<SculptButton> buttons = new ArrayList<>();
	private List<FormattedCharSequence> lines = List.of();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	/**
	 * @param defaultChoice index of the button focused when the dialog opens
	 * @param safeChoice    index of the choice Esc picks (the one that changes nothing)
	 */
	public SculptDialog(Screen parent, Component title, Component message, List<Choice> choices, int defaultChoice, int safeChoice) {
		super(title);
		if (choices.size() < 2 || choices.size() > 3) {
			throw new IllegalArgumentException("a dialog has 2 or 3 choices");
		}
		this.parent = parent;
		this.message = message;
		this.choices = List.copyOf(choices);
		this.defaultChoice = defaultChoice;
		this.safeChoice = safeChoice;
	}

	public List<SculptButton> buttons() {
		return buttons;
	}

	public Screen parent() {
		return parent;
	}

	/** The text under the title. */
	public Component message() {
		return message;
	}

	@Override
	protected void init() {
		buttons.clear();
		panelWidth = Math.min(MAX_WIDTH, width - 2 * Theme.PADDING);
		int inner = panelWidth - 2 * Theme.PADDING;
		lines = font.split(message, inner);
		int buttonWidth = (inner - Theme.GAP * (choices.size() - 1)) / choices.size();
		panelHeight = Theme.PADDING + font.lineHeight + Theme.GAP + lines.size() * font.lineHeight
				+ Theme.PADDING + Theme.WIDGET_HEIGHT + Theme.PADDING;
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(Theme.PADDING, (height - panelHeight) / 2);

		int x = panelX + Theme.PADDING;
		int y = panelY + panelHeight - Theme.PADDING - Theme.WIDGET_HEIGHT;
		for (int i = 0; i < choices.size(); i++) {
			int index = i;
			SculptButton button = SculptButton.text(choices.get(i).label(), buttonWidth, () -> choose(index));
			button.setPosition(x, y);
			buttons.add(addRenderableWidget(button));
			x += buttonWidth + Theme.GAP;
		}
	}

	/** Vanilla calls this after {@link #init()}; the default button always starts focused (PRD 6.2). */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(buttons.get(defaultChoice));
	}

	/**
	 * Runs the choice, then returns to the parent screen unless the choice opened another one. The choice runs first so
	 * the parent is rebuilt from what it changed (e.g. a deleted rule is gone from the list), not from the state before.
	 */
	public void choose(int index) {
		choices.get(index).action().run();
		if (minecraft.gui.screen() == this) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			choose(safeChoice);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return parent == null || parent.isPauseScreen();
	}

	@Override
	public Component getNarrationMessage() {
		return Component.translatable("soundsculptor.ui.dialog.narration", title, message);
	}

	/** The panel is part of the background so the buttons draw on top of it. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		Draw.fill(g, panelX, panelY, panelWidth, panelHeight, Theme.PANEL);
		Draw.outline(g, panelX, panelY, panelWidth, panelHeight, Theme.SEPARATOR);
		int y = panelY + Theme.PADDING;
		Draw.text(g, font, title, panelX + Theme.PADDING, y, Theme.TEXT);
		y += font.lineHeight + Theme.GAP;
		for (FormattedCharSequence line : lines) {
			Draw.text(g, font, line, panelX + Theme.PADDING, y, Theme.TEXT_DIM);
			y += font.lineHeight;
		}
	}
}
