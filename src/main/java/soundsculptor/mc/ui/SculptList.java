package soundsculptor.mc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Scrolling list (PRD 6.2, 6.3) on top of vanilla's container list, whose rows hold their own buttons.
 *
 * <ul>
 *   <li>Only visible rows are drawn (vanilla skips rows outside the list).</li>
 *   <li>Mouse wheel scrolls one row per notch; the scrollbar can be dragged.</li>
 *   <li>The whole list is one Tab stop: Tab enters it on the last focused row, Tab again leaves it. Inside, Up/Down
 *   move between rows, Left/Right between a row's buttons, Page Up/Down by a screenful.</li>
 *   <li>The narrator reads the row's text, the focused button and "Selected list row 3 out of 40".</li>
 *   <li>{@link #setRows} keeps the scroll position and the focused row (matched by key) when the list refreshes.</li>
 * </ul>
 */
public class SculptList extends ContainerObjectSelectionList<SculptList.Row> {
	public static final int ROW_HEIGHT = 24;

	private final Component label;
	private Supplier<Component> emptyText;
	private Object rememberedKey;
	private int rememberedButton;
	private int rowsDrawnThisFrame;
	private int rowsDrawnLastFrame;

	public SculptList(int width, int height, Component label) {
		super(Minecraft.getInstance(), width, height, 0, ROW_HEIGHT);
		this.label = label;
		setMessage(label);
	}

	// ---- Rows ----

	/** Replaces all rows, keeping the scroll position and the focused row (matched by {@link Row#key()}). */
	public void setRows(List<Row> rows) {
		double scroll = scrollAmount();
		Row focusedRow = getFocused();
		int focusedButton = focusedRow == null ? rememberedButton : focusedRow.focusedButtonIndex();
		Object focusedKey = focusedRow == null ? rememberedKey : focusedRow.key();
		boolean hadFocus = focusedRow != null;

		replaceEntries(rows);
		setScrollAmount(scroll);

		Row match = findRow(focusedKey);
		if (hadFocus && match != null && Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
			screen.focusPath(pathTo(match, focusedButton));
			setScrollAmount(scroll);
		}
	}

	/** Text shown in the middle of the list when it has no rows. */
	public void setEmptyText(Supplier<Component> text) {
		this.emptyText = text;
	}

	public Row findRow(Object key) {
		for (Row row : children()) {
			if (Objects.equals(row.key(), key)) {
				return row;
			}
		}
		return null;
	}

	public int indexOf(Row row) {
		return children().indexOf(row);
	}

	/** How many rows were drawn in the last frame (only visible ones are). */
	public int rowsDrawnLastFrame() {
		return rowsDrawnLastFrame;
	}

	public int visibleRowCount() {
		return Math.max(1, getHeight() / ROW_HEIGHT);
	}

	// ---- Layout and drawing ----

	@Override
	public int getRowWidth() {
		return getWidth() - scrollbarWidth() - 4;
	}

	/**
	 * Where the next added row goes. Vanilla adds up the height of every row already in the list, so filling a list of
	 * n rows took n² steps (about 6 ms for the 2,000 rows of the Sounds tab's full list, PRD 9.1). Every row here is
	 * {@link #ROW_HEIGHT} tall, so it is one multiplication.
	 */
	@Override
	public int getNextY() {
		return getY() + 2 - (int) scrollAmount() + children().size() * defaultEntryHeight;
	}

	@Override
	protected double scrollRate() {
		return ROW_HEIGHT;
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor g) {
		Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.LIST_BACKGROUND);
	}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor g) {
		Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.SEPARATOR);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		rowsDrawnThisFrame = 0;
		super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
		rowsDrawnLastFrame = rowsDrawnThisFrame;
		if (children().isEmpty() && emptyText != null) {
			Font font = Minecraft.getInstance().font;
			Draw.centeredText(g, font, emptyText.get(), getX() + getWidth() / 2, getY() + (getHeight() - font.lineHeight) / 2, Theme.TEXT_DIM);
		}
	}

	// ---- Focus and keys ----

	/**
	 * Scrolls {@code row} into view and gives the path to its button {@code buttonIndex}, for
	 * {@link SculptScreen#focusPath}: e.g. an editor that opens at one of its rows.
	 */
	public ComponentPath revealRow(Row row, int buttonIndex) {
		scrollToEntry(row);
		return pathTo(row, buttonIndex);
	}

	/** Moves keyboard focus to the row's first button (or its focus target), e.g. when the list opens in an overlay. */
	public void focusRow(Row row) {
		if (Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
			screen.focusPath(pathTo(row, 0));
		}
	}

	private ComponentPath pathTo(Row row, int buttonIndex) {
		List<? extends GuiEventListener> buttons = row.children();
		GuiEventListener target = buttons.get(Math.max(0, Math.min(buttonIndex, buttons.size() - 1)));
		return ComponentPath.path(this, ComponentPath.path(row, ComponentPath.leaf(target)));
	}

	/** One Tab stop: entering goes to the remembered row, Tab inside leaves the list. Arrows stay vanilla. */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		if (event instanceof FocusNavigationEvent.TabNavigation) {
			if (getFocused() != null || children().isEmpty()) {
				return null;
			}
			Row target = findRow(rememberedKey);
			if (target == null) {
				target = children().get(0);
			}
			return pathTo(target, rememberedButton);
		}
		return super.nextFocusPath(event);
	}

	@Override
	public void setFocused(GuiEventListener listener) {
		super.setFocused(listener);
		if (listener instanceof Row row) {
			rememberedKey = row.key();
			rememberedButton = Math.max(0, row.focusedButtonIndex());
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if ((key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) && getFocused() != null) {
			Row current = getFocused();
			int page = visibleRowCount();
			int index = indexOf(current) + (key == InputConstants.KEY_PAGEDOWN ? page : -page);
			Row target = children().get(Math.max(0, Math.min(children().size() - 1, index)));
			if (target != current && Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
				screen.focusPath(pathTo(target, current.focusedButtonIndex()));
				scrollToEntry(target);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	/**
	 * Narrates the focused row: its focused button (which reads the row's text first) and "Selected list row 3 out
	 * of 40". Vanilla 26.2 would narrate the row under the mouse instead, which is wrong for keyboard users whose
	 * mouse rests over the list, so this is built here and behaves the same in every version.
	 */
	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		Row focused = getFocused();
		if (focused == null) {
			output.add(NarratedElementType.TITLE, Component.translatable("soundsculptor.ui.list", label, children().size()));
			output.add(NarratedElementType.USAGE, Component.translatable("soundsculptor.ui.list.usage"));
			return;
		}
		if (focused.getFocused() instanceof AbstractWidget button) {
			button.updateNarration(output.nest());
		}
		narrateListElementPosition(output, focused);
	}

	/**
	 * A row: text columns on one or two lines (cut with "…" when too long, full text in a tooltip on hover) and inline
	 * icon buttons on the right. Its buttons narrate the row's text before their own label. A row without buttons
	 * (read-only) gets an invisible focus target instead, so it can still be reached with the keyboard and narrated.
	 */
	public static class Row extends ContainerObjectSelectionList.Entry<Row> {
		static final int SWATCH_SIZE = 12;

		/**
		 * A text column. {@code width} 0 means "take the remaining space" (at most one such column per line).
		 * {@code line} 0 is the main line; line 1 is a second, dimmer line underneath.
		 */
		public record Column(Supplier<String> text, int width, int line) {
			public Column(Supplier<String> text, int width) {
				this(text, width, 0);
			}
		}

		private final Object key;
		private final List<Column> columns;
		private final List<SculptButton> buttons;
		private final RowFocus focusTarget;
		/** Color square at the start of the row (e.g. a zone's color), as 0xRRGGBB; -1 for none. */
		private int swatch = -1;
		/** Shown when the mouse is over the row's text, or {@code null}. */
		private Component tooltip;

		public Row(Object key, List<Column> columns, List<SculptButton> buttons) {
			this.key = key;
			this.columns = List.copyOf(columns);
			this.buttons = List.copyOf(buttons);
			for (SculptButton button : this.buttons) {
				button.narrationContext(this::narrationText);
			}
			this.focusTarget = this.buttons.isEmpty() ? new RowFocus(this) : null;
		}

		public Object key() {
			return key;
		}

		/**
		 * Shows a color square before the text. The color is decoration only: the row's text must say everything
		 * (A-5).
		 */
		public Row swatch(int rgb) {
			this.swatch = rgb & 0xFFFFFF;
			return this;
		}

		public int swatch() {
			return swatch;
		}

		/**
		 * A tooltip for the row's text, shown on hover (a cut-off column's full text takes precedence). Keyboard and
		 * narrator users need the same text on one of the row's buttons, e.g. as that button's tooltip.
		 */
		public Row tooltip(Component text) {
			this.tooltip = text;
			return this;
		}

		public Component tooltip() {
			return tooltip;
		}

		public List<SculptButton> buttons() {
			return buttons;
		}

		public Component narrationText() {
			List<String> parts = new ArrayList<>();
			for (Column column : columns) {
				String text = column.text().get();
				if (!text.isEmpty()) {
					parts.add(text);
				}
			}
			return Component.literal(String.join(", ", parts));
		}

		/** The visible text of every column, for tests and diagnostics. */
		public List<String> columnTexts() {
			List<String> texts = new ArrayList<>();
			for (Column column : columns) {
				texts.add(column.text().get());
			}
			return texts;
		}

		int focusedButtonIndex() {
			GuiEventListener focused = getFocused();
			return focused == null || focusTarget != null ? 0 : buttons.indexOf(focused);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return focusTarget != null ? List.of(focusTarget) : buttons;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return focusTarget != null ? List.of(focusTarget) : buttons;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (owner != null) {
				owner.rowsDrawnThisFrame++;
			}
			int x = getContentX();
			int y = getContentY();
			int width = getContentWidth();
			int height = getContentHeight();
			if (getFocused() != null) {
				Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_FOCUSED);
			} else if (hovered) {
				Draw.fill(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
			}

			// Buttons, right-aligned.
			int buttonsWidth = 0;
			for (SculptButton button : buttons) {
				buttonsWidth += button.getWidth() + Theme.GAP;
			}
			int bx = x + width - buttonsWidth + Theme.GAP;
			for (SculptButton button : buttons) {
				button.setPosition(bx, y + (height - button.getHeight()) / 2);
				button.extractRenderState(g, mouseX, mouseY, partialTick);
				bx += button.getWidth() + Theme.GAP;
			}

			if (focusTarget != null) {
				focusTarget.setRectangle(getWidth(), getHeight(), getX(), getY());
				focusTarget.extractRenderState(g, mouseX, mouseY, partialTick);
			}

			// Color square, then text columns, line by line.
			Font font = Minecraft.getInstance().font;
			int textLeft = x;
			if (swatch >= 0) {
				int size = SWATCH_SIZE;
				int sy = y + (height - size) / 2;
				Draw.fill(g, x, sy, size, size, Theme.SEPARATOR);
				Draw.fill(g, x + 1, sy + 1, size - 2, size - 2, 0xFF000000 | swatch);
				textLeft = x + size + Theme.GAP;
			}
			int textRight = x + width - buttonsWidth - Theme.GAP;
			boolean twoLines = columns.stream().anyMatch(column -> column.line() == 1);
			boolean cutShown = false;
			for (int line = 0; line <= 1; line++) {
				int fixed = 0;
				for (Column column : columns) {
					if (column.line() == line) {
						fixed += column.width() + Theme.GAP;
					}
				}
				int textY = twoLines
						? y + 1 + line * (font.lineHeight + 2)
						: y + (height - font.lineHeight) / 2 + 1;
				int color = line == 0 ? Theme.TEXT : Theme.TEXT_DIM;
				int cx = textLeft;
				for (Column column : columns) {
					if (column.line() != line) {
						continue;
					}
					int columnWidth = column.width() > 0 ? column.width() : Math.max(0, textRight - textLeft - fixed);
					columnWidth = Math.min(columnWidth, Math.max(0, textRight - cx));
					String text = column.text().get();
					boolean cut = Draw.truncatedText(g, font, text, cx, textY, columnWidth, color);
					if (cut && hovered && mouseX >= cx && mouseX < cx + columnWidth
							&& mouseY >= textY && mouseY < textY + font.lineHeight) {
						Draw.tooltip(g, font, Component.literal(text), mouseX, mouseY);
						cutShown = true;
					}
					cx += columnWidth + Theme.GAP;
				}
			}
			if (tooltip != null && !cutShown && hovered && mouseX >= textLeft && mouseX < textRight) {
				g.setTooltipForNextFrame(font, Tooltip.splitTooltip(Minecraft.getInstance(), tooltip), mouseX, mouseY);
			}
		}

		/** The list this row is in (set when the row is added), for the drawn-rows counter. */
		private SculptList owner;
	}

	/** Invisible, focusable stand-in for a read-only row: shows the focus ring and narrates the row. */
	private static final class RowFocus extends AbstractWidget {
		private final Row row;

		RowFocus(Row row) {
			super(0, 0, 0, 0, Component.empty());
			this.row = row;
		}

		@Override
		public Component getMessage() {
			return row.narrationText();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			if (isFocused()) {
				Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.FOCUS);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, row.narrationText());
		}
	}

	@Override
	protected int addEntry(Row row) {
		row.owner = this;
		return super.addEntry(row);
	}

	@Override
	public void replaceEntries(java.util.Collection<Row> rows) {
		for (Row row : rows) {
			row.owner = this;
		}
		super.replaceEntries(rows);
	}
}
