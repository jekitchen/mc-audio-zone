package audiozone.mc.ui;

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
public class AzList extends ContainerObjectSelectionList<AzList.Row> {
	public static final int ROW_HEIGHT = 24;

	private final Component label;
	private Object rememberedKey;
	private int rememberedButton;
	private int rowsDrawnThisFrame;
	private int rowsDrawnLastFrame;

	public AzList(int width, int height, Component label) {
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
		if (hadFocus && match != null && Minecraft.getInstance().gui.screen() instanceof AzScreen screen) {
			screen.focusPath(pathTo(match, focusedButton));
			setScrollAmount(scroll);
		}
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
	}

	// ---- Focus and keys ----

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
			if (target != current && Minecraft.getInstance().gui.screen() instanceof AzScreen screen) {
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
			output.add(NarratedElementType.TITLE, Component.translatable("audiozone.ui.list", label, children().size()));
			output.add(NarratedElementType.USAGE, Component.translatable("audiozone.ui.list.usage"));
			return;
		}
		if (focused.getFocused() instanceof AbstractWidget button) {
			button.updateNarration(output.nest());
		}
		narrateListElementPosition(output, focused);
	}

	/**
	 * A row: text columns (cut with "…" when too long, full text in a tooltip on hover) and inline icon buttons on
	 * the right. Its buttons narrate the row's text before their own label.
	 */
	public static class Row extends ContainerObjectSelectionList.Entry<Row> {
		/** A text column; {@code width} 0 means "take the remaining space" (at most one such column). */
		public record Column(Supplier<String> text, int width) {
		}

		private final Object key;
		private final List<Column> columns;
		private final List<AzButton> buttons;

		public Row(Object key, List<Column> columns, List<AzButton> buttons) {
			this.key = key;
			this.columns = List.copyOf(columns);
			this.buttons = List.copyOf(buttons);
			for (AzButton button : this.buttons) {
				button.narrationContext(this::narrationText);
			}
		}

		public Object key() {
			return key;
		}

		public List<AzButton> buttons() {
			return buttons;
		}

		public Component narrationText() {
			List<String> parts = new ArrayList<>();
			for (Column column : columns) {
				parts.add(column.text().get());
			}
			return Component.literal(String.join(", ", parts));
		}

		int focusedButtonIndex() {
			GuiEventListener focused = getFocused();
			return focused == null ? 0 : buttons.indexOf(focused);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return buttons;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return buttons;
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
			for (AzButton button : buttons) {
				buttonsWidth += button.getWidth() + Theme.GAP;
			}
			int bx = x + width - buttonsWidth + Theme.GAP;
			for (AzButton button : buttons) {
				button.setPosition(bx, y + (height - button.getHeight()) / 2);
				button.extractRenderState(g, mouseX, mouseY, partialTick);
				bx += button.getWidth() + Theme.GAP;
			}

			// Text columns.
			Font font = Minecraft.getInstance().font;
			int textRight = x + width - buttonsWidth - Theme.GAP;
			int fixed = 0;
			for (Column column : columns) {
				fixed += column.width() + Theme.GAP;
			}
			int textY = y + (height - font.lineHeight) / 2 + 1;
			int cx = x;
			for (Column column : columns) {
				int columnWidth = column.width() > 0 ? column.width() : Math.max(0, textRight - x - fixed);
				columnWidth = Math.min(columnWidth, Math.max(0, textRight - cx));
				String text = column.text().get();
				boolean cut = Draw.truncatedText(g, font, text, cx, textY, columnWidth, Theme.TEXT);
				if (cut && hovered && mouseX >= cx && mouseX < cx + columnWidth) {
					Draw.tooltip(g, font, Component.literal(text), mouseX, mouseY);
				}
				cx += columnWidth + Theme.GAP;
			}
		}

		/** The list this row is in (set when the row is added), for the drawn-rows counter. */
		private AzList owner;
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
