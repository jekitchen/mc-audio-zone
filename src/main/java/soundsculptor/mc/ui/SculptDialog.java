package soundsculptor.mc.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import soundsculptor.mc.compat.Keys;

/**
 * Modal dialog with 2 or 3 buttons (PRD 6.2), or a single OK for a message that offers no choice (an import that
 * cannot go ahead, FR-11.5a). It is its own screen, so focus cannot leave it. The default button has focus when it
 * opens; Esc picks the safest choice. The narrator reads the title and the message.
 *
 * <p>A message too long for the window (a set used in 40 zones, FR-4.11) scrolls inside the panel, so the buttons stay
 * on screen and no line is dropped: the mouse wheel, Up/Down and Page Up/Page Down scroll it, and a scrollbar shows
 * where the view is. The narrator still reads the whole message.
 *
 * <p>Choosing a button returns to the parent screen first, then runs the choice's action, so an action may open
 * another screen.
 */
public final class SculptDialog extends Screen {
	private static final int MAX_WIDTH = 300;
	/** Room the scrollbar takes beside a message that scrolls. */
	private static final int SCROLLBAR_SPACE = 6;
	/** Lines scrolled per mouse wheel notch. */
	private static final int WHEEL_LINES = 3;

	public record Choice(Component label, Runnable action) {
	}

	private final Screen parent;
	private final Component message;
	private final List<Choice> choices;
	private final int defaultChoice;
	private final int safeChoice;
	private final List<SculptButton> buttons = new ArrayList<>();
	private List<FormattedCharSequence> lines = List.of();
	/** How many message lines fit in the panel, and the first one shown. */
	private int visibleLines;
	private int scroll;
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
		if (choices.isEmpty() || choices.size() > 3) {
			throw new IllegalArgumentException("a dialog has 1 to 3 choices");
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
		int buttonWidth = (inner - Theme.GAP * (choices.size() - 1)) / choices.size();
		int fixedHeight = Theme.PADDING + font.lineHeight + Theme.GAP + Theme.PADDING + Theme.WIDGET_HEIGHT + Theme.PADDING;
		int fitting = Math.max(1, (height - 2 * Theme.PADDING - fixedHeight) / font.lineHeight);
		lines = font.split(message, inner);
		if (lines.size() > fitting) {
			// It scrolls: wrap again beside the scrollbar.
			lines = font.split(message, inner - SCROLLBAR_SPACE);
		}
		visibleLines = Math.min(lines.size(), fitting);
		scrollTo(scroll);
		panelHeight = fixedHeight + visibleLines * font.lineHeight;
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
		if (event.key() == Keys.ESCAPE) {
			choose(safeChoice);
			return true;
		}
		// The buttons are one row, so Up and Down are free to scroll a long message.
		if (isScrollable()) {
			int key = event.key();
			int by = key == Keys.UP ? -1
					: key == Keys.DOWN ? 1
					: key == Keys.PAGE_UP ? -visibleLines
					: key == Keys.PAGE_DOWN ? visibleLines
					: 0;
			if (by != 0) {
				scrollBy(by);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isScrollable() && scrollY != 0) {
			scrollBy(scrollY > 0 ? -WHEEL_LINES : WHEEL_LINES);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Whether the message is longer than the panel can show, so it scrolls. */
	public boolean isScrollable() {
		return lines.size() > visibleLines;
	}

	/** The message's lines as wrapped for the panel, every one of them, shown or scrolled away. */
	public int lineCount() {
		return lines.size();
	}

	/** How many lines the panel shows at once. */
	public int visibleLineCount() {
		return visibleLines;
	}

	/** The first line shown. */
	public int firstVisibleLine() {
		return scroll;
	}

	/** Scrolls the message by {@code lines} (negative is up), stopping at the first and the last line. */
	public void scrollBy(int lines) {
		scrollTo(scroll + lines);
	}

	private void scrollTo(int line) {
		scroll = Math.max(0, Math.min(line, lines.size() - visibleLines));
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
		int messageTop = y;
		for (int i = scroll; i < scroll + visibleLines; i++) {
			Draw.text(g, font, lines.get(i), panelX + Theme.PADDING, y, Theme.TEXT_DIM);
			y += font.lineHeight;
		}
		if (isScrollable()) {
			// The track along the message's right edge, and a thumb as long as the share of the message shown.
			int trackX = panelX + panelWidth - Theme.PADDING - 2;
			int trackHeight = visibleLines * font.lineHeight;
			int thumbHeight = Math.max(4, trackHeight * visibleLines / lines.size());
			int thumbY = messageTop + (trackHeight - thumbHeight) * scroll / (lines.size() - visibleLines);
			Draw.fill(g, trackX, messageTop, 2, trackHeight, Theme.SEPARATOR);
			Draw.fill(g, trackX, thumbY, 2, thumbHeight, Theme.TEXT_DIM);
		}
	}
}
