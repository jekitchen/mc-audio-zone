package audiozone.mc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Screen base (PRD 6.1, 6.2): header (title, subtitle such as the active profile, header widgets, close button),
 * tab bar, body and footer.
 *
 * <ul>
 *   <li>Focus order is reading order: widgets are added header, tabs, body, footer, each left to right and top to
 *   bottom, and Tab / Shift+Tab follow that order (PRD A-1).</li>
 *   <li>Initial focus is the first body widget, or the selected tab if the body has none.</li>
 *   <li>Ctrl+Tab and Ctrl+Shift+Tab switch tabs. The last used tab is remembered per screen type (FR-12.1).</li>
 *   <li>Esc closes the screen, through the unsaved-changes guard (FR-12.4): Save, Discard, Keep editing; Esc in
 *   that dialog keeps editing.</li>
 *   <li>Each tab says whether the game pauses behind it.</li>
 * </ul>
 */
public abstract class AzScreen extends Screen {
	public static final int HEADER_HEIGHT = 24;
	public static final int TAB_BAR_HEIGHT = Theme.WIDGET_HEIGHT;
	public static final int FOOTER_HEIGHT = 28;
	private static final int MAX_TAB_WIDTH = 100;
	private static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/close");
	private static final Map<Class<?>, Integer> LAST_TAB = new HashMap<>();

	/** A tab: its title, and whether the game pauses while it is open (single player). */
	public record Tab(Component title, boolean pausesGame) {
	}

	protected final Screen parent;
	private int currentTab;
	private final List<AzTabButton> tabButtons = new ArrayList<>();
	private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
	private final List<Body.Label> labels = new ArrayList<>();
	private Body body;

	protected AzScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
		this.currentTab = LAST_TAB.getOrDefault(getClass(), 0);
	}

	// ---- What subclasses provide ----

	protected abstract List<Tab> tabs();

	/** Adds the widgets of tab {@code index} to the body, in reading order. */
	protected abstract void buildTab(int index, Body body);

	/** Widgets shown in the header, left of the close button (e.g. the Bypass toggle). */
	protected void addHeaderWidgets(List<AbstractWidget> header) {
	}

	/** Buttons shown in the footer, right-aligned (e.g. Save, Cancel, Help). */
	protected void addFooterWidgets(List<AbstractWidget> footer) {
	}

	/** Text after the title in the header (e.g. "Profile: play.example.net"), or null. */
	protected Component headerSubtitle() {
		return null;
	}

	protected boolean hasUnsavedChanges() {
		return false;
	}

	protected void saveChanges() {
	}

	protected void discardChanges() {
	}

	// ---- Public state (used by tests) ----

	public int currentTab() {
		return currentTab;
	}

	public List<AzTabButton> tabButtons() {
		return tabButtons;
	}

	public List<AbstractWidget> bodyWidgets() {
		return bodyWidgets;
	}

	public Body body() {
		return body;
	}

	public void selectTab(int index) {
		int count = tabs().size();
		currentTab = Math.floorMod(index, count);
		LAST_TAB.put(getClass(), currentTab);
		rebuildWidgets();
		// After switching tabs, keep focus on the tab bar so Ctrl+Tab and Tab continue from there.
		setInitialFocus(tabButtons.get(currentTab));
	}

	// ---- Layout ----

	@Override
	protected void init() {
		List<Tab> tabs = tabs();
		currentTab = Math.min(currentTab, tabs.size() - 1);
		tabButtons.clear();
		bodyWidgets.clear();
		labels.clear();

		// Header, right-aligned: subclass widgets, then the close button.
		List<AbstractWidget> header = new ArrayList<>();
		addHeaderWidgets(header);
		header.add(AzButton.icon(CLOSE_ICON, Component.translatable("audiozone.ui.close"), this::onClose));
		placeRow(header, width - Theme.PADDING, (HEADER_HEIGHT - Theme.WIDGET_HEIGHT) / 2);

		// Tab bar.
		int tabWidth = Math.min(MAX_TAB_WIDTH, (width - 2 * Theme.PADDING) / tabs.size());
		for (int i = 0; i < tabs.size(); i++) {
			int index = i;
			AzTabButton button = new AzTabButton(tabWidth, tabs.get(i).title(), i, tabs.size(), i == currentTab, () -> selectTab(index));
			button.setPosition(Theme.PADDING + i * tabWidth, HEADER_HEIGHT);
			tabButtons.add(addRenderableWidget(button));
		}

		// Body.
		int bodyTop = HEADER_HEIGHT + TAB_BAR_HEIGHT + Theme.PADDING;
		int bodyBottom = height - FOOTER_HEIGHT - Theme.PADDING;
		body = new Body(Theme.PADDING, bodyTop, width - 2 * Theme.PADDING, bodyBottom - bodyTop);
		buildTab(currentTab, body);
		for (AbstractWidget widget : body.widgets()) {
			bodyWidgets.add(addRenderableWidget(widget));
		}
		labels.addAll(body.labels());

		// Footer, right-aligned.
		List<AbstractWidget> footer = new ArrayList<>();
		addFooterWidgets(footer);
		placeRow(footer, width - Theme.PADDING, height - FOOTER_HEIGHT + (FOOTER_HEIGHT - Theme.WIDGET_HEIGHT) / 2);

	}

	/** Vanilla calls this after {@link #init()}: focus the first body widget, or the selected tab. */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(bodyWidgets.isEmpty() ? tabButtons.get(currentTab) : bodyWidgets.get(0));
	}

	/** Places widgets in a row ending at {@code right}, and adds them left to right. */
	private void placeRow(List<AbstractWidget> widgets, int right, int y) {
		int total = 0;
		for (AbstractWidget widget : widgets) {
			total += widget.getWidth();
		}
		total += Theme.GAP * Math.max(0, widgets.size() - 1);
		int x = right - total;
		for (AbstractWidget widget : widgets) {
			widget.setPosition(x, y);
			addRenderableWidget(widget);
			x += widget.getWidth() + Theme.GAP;
		}
	}

	// ---- Keys, closing, pausing ----

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_TAB && event.hasControlDown()) {
			selectTab(currentTab + (event.hasShiftDown() ? -1 : 1));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (!hasUnsavedChanges()) {
			closeNow();
			return;
		}
		minecraft.gui.setScreen(new AzDialog(this,
				Component.translatable("audiozone.ui.unsaved.title"),
				Component.translatable("audiozone.ui.unsaved.message"),
				List.of(
						new AzDialog.Choice(Component.translatable("audiozone.ui.save"), () -> {
							saveChanges();
							closeNow();
						}),
						new AzDialog.Choice(Component.translatable("audiozone.ui.discard"), () -> {
							discardChanges();
							closeNow();
						}),
						new AzDialog.Choice(Component.translatable("audiozone.ui.keep_editing"), () -> { })),
				0, 2));
	}

	protected void closeNow() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return tabs().get(currentTab).pausesGame();
	}

	// ---- Drawing ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Tooltips.refresh(children());
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	/** Panels and text are part of the background so widgets draw on top of them. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		Draw.fill(g, 0, 0, width, HEADER_HEIGHT + TAB_BAR_HEIGHT, Theme.PANEL);
		Draw.fill(g, 0, HEADER_HEIGHT + TAB_BAR_HEIGHT, width, 1, Theme.SEPARATOR);
		Draw.fill(g, 0, height - FOOTER_HEIGHT, width, FOOTER_HEIGHT, Theme.PANEL);
		Draw.fill(g, 0, height - FOOTER_HEIGHT, width, 1, Theme.SEPARATOR);

		int textY = (HEADER_HEIGHT - font.lineHeight) / 2 + 1;
		Draw.text(g, font, title, Theme.PADDING, textY, Theme.TEXT);
		Component subtitle = headerSubtitle();
		if (subtitle != null) {
			int x = Theme.PADDING + font.width(title) + 2 * Theme.GAP;
			int headerWidgetsLeft = leftmostHeaderWidget();
			Draw.truncatedText(g, font, subtitle.getString(), x, textY, Math.max(0, headerWidgetsLeft - x - Theme.GAP), Theme.TEXT_DIM);
		}
		for (Body.Label label : labels) {
			Draw.truncatedText(g, font, label.text().get().getString(), label.x(), label.y(), label.width(), Theme.TEXT_DIM);
		}
	}

	private int leftmostHeaderWidget() {
		int left = width;
		for (var child : children()) {
			if (child instanceof AbstractWidget widget && widget.getBottom() <= HEADER_HEIGHT) {
				left = Math.min(left, widget.getX());
			}
		}
		return left;
	}

	/**
	 * Simple top-to-bottom layout for a tab's body. Rows are placed left to right; a text field reserves room for
	 * its error message.
	 */
	public static final class Body {
		private final int x;
		private final int top;
		private final int width;
		private final int height;
		private int cursorY;
		private final List<AbstractWidget> widgets = new ArrayList<>();
		private final List<Label> labels = new ArrayList<>();

		public record Label(Supplier<Component> text, int x, int y, int width) {
		}

		Body(int x, int top, int width, int height) {
			this.x = x;
			this.top = top;
			this.width = width;
			this.height = height;
			this.cursorY = top;
		}

		public int width() {
			return width;
		}

		public int x() {
			return x;
		}

		public int top() {
			return top;
		}

		public int bottom() {
			return top + height;
		}

		/** Whether the content is taller than the body (a scrolling list should be used instead). */
		public boolean overflows() {
			return cursorY - Theme.GAP > bottom();
		}

		/** Adds widgets side by side on one row, left to right. Widths wider than the body are reduced. */
		public Body row(AbstractWidget... row) {
			int rowX = x;
			int rowHeight = 0;
			for (AbstractWidget widget : row) {
				if (widget.getWidth() > x + width - rowX) {
					widget.setWidth(Math.max(0, x + width - rowX));
				}
				widget.setPosition(rowX, cursorY);
				rowX += widget.getWidth() + Theme.GAP;
				int extra = widget instanceof AzTextField ? AzTextField.ERROR_SLOT_HEIGHT : 0;
				rowHeight = Math.max(rowHeight, widget.getHeight() + extra);
				widgets.add(widget);
			}
			cursorY += rowHeight + Theme.GAP;
			return this;
		}

		/** A line of text; the supplier is read every frame, so it can show live values. */
		public Body label(Supplier<Component> text) {
			labels.add(new Label(text, x, cursorY + 2, width));
			cursorY += net.minecraft.client.Minecraft.getInstance().font.lineHeight + Theme.GAP;
			return this;
		}

		public Body space(int pixels) {
			cursorY += pixels;
			return this;
		}

		public List<AbstractWidget> widgets() {
			return widgets;
		}

		public List<Label> labels() {
			return labels;
		}
	}
}
