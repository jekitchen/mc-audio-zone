package audiozone.mc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

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
 *   <li>Overlays (e.g. an open dropdown) draw above everything and trap focus until closed with Esc, a choice, or a
 *   click outside.</li>
 *   <li>Toasts: a short message for 5 seconds above the footer, with an optional Undo button, narrated once and
 *   sliding in unless reduced motion is on (FR-12.5, A-6).</li>
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

	private final List<AbstractWidget> overlayWidgets = new ArrayList<>();
	private boolean overlayOpen;
	private int overlayX;
	private int overlayY;
	private int overlayWidth;
	private int overlayHeight;
	private Runnable overlayClosed;

	private Toast toast;

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

	/**
	 * Moves keyboard focus along a path that starts at one of this screen's widgets (e.g. a list, then a row, then a
	 * button), unfocusing the old path. Vanilla's focus paths start at the screen, so the screen is added here.
	 */
	public void focusPath(ComponentPath pathFromChild) {
		changeFocus(ComponentPath.path(this, pathFromChild));
	}

	// ---- Overlays ----

	/**
	 * Shows widgets in a panel above the screen. Until it closes, only these widgets get input and focus.
	 *
	 * @param onClosed runs after the overlay closes for any reason (e.g. to return focus to the widget that opened it)
	 */
	public void openOverlay(int x, int y, int panelWidth, int panelHeight, List<? extends AbstractWidget> widgets,
			AbstractWidget initialFocus, Runnable onClosed) {
		closeOverlay();
		overlayX = x;
		overlayY = y;
		overlayWidth = panelWidth;
		overlayHeight = panelHeight;
		overlayClosed = onClosed;
		overlayOpen = true;
		for (AbstractWidget widget : widgets) {
			overlayWidgets.add(addRenderableWidget(widget));
		}
		setFocused(initialFocus);
	}

	public void closeOverlay() {
		if (!overlayOpen) {
			return;
		}
		for (AbstractWidget widget : overlayWidgets) {
			removeWidget(widget);
		}
		overlayWidgets.clear();
		overlayOpen = false;
		Runnable closed = overlayClosed;
		overlayClosed = null;
		if (closed != null) {
			closed.run();
		}
	}

	public boolean hasOverlay() {
		return overlayOpen;
	}

	public List<AbstractWidget> overlayWidgets() {
		return overlayWidgets;
	}

	/** While an overlay is open, only its widgets take input (focus is trapped in it). */
	@Override
	public List<? extends GuiEventListener> children() {
		return hasOverlay() ? overlayWidgets : super.children();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (hasOverlay() && !(event.x() >= overlayX && event.x() < overlayX + overlayWidth
				&& event.y() >= overlayY && event.y() < overlayY + overlayHeight)) {
			closeOverlay();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---- Toasts ----

	public static final long TOAST_MILLIS = 5000;
	private static final long TOAST_SLIDE_MILLIS = 150;
	private static final int TOAST_WIDTH = 220;

	/** Shows a toast for 5 seconds, replacing any current one. {@code undo} may be null for no Undo button. */
	public void showToast(Component message, Runnable undo) {
		dismissToast();
		toast = new Toast(message, undo);
		addToastWidgets();
		if (minecraft.getNarrator().isActive()) {
			minecraft.getNarrator().saySystemQueued(message);
		}
		toast.announcements++;
	}

	public void dismissToast() {
		if (toast == null) {
			return;
		}
		if (toast.undoButton != null) {
			removeWidget(toast.undoButton);
		}
		toast = null;
	}

	public Toast toast() {
		return toast;
	}

	private void addToastWidgets() {
		if (toast.undo != null) {
			toast.undoButton = addRenderableWidget(AzButton.text(Component.translatable("audiozone.ui.undo"), () -> {
				Runnable undo = toast.undo;
				dismissToast();
				undo.run();
			}));
			toast.layout();
		}
	}

	@Override
	public void tick() {
		super.tick();
		// A toast stays while its Undo button has keyboard focus, so focus is never pulled away mid-use.
		if (toast != null && toast.ageMillis() > TOAST_MILLIS && (toast.undoButton == null || !toast.undoButton.isFocused())) {
			dismissToast();
		}
	}

	/** A toast message with optional Undo (PRD 6.2). */
	public final class Toast {
		private final Component message;
		private final Runnable undo;
		private final long shownAt = Util.getMillis();
		private AzButton undoButton;
		private int announcements;

		private Toast(Component message, Runnable undo) {
			this.message = message;
			this.undo = undo;
		}

		public Component message() {
			return message;
		}

		public AzButton undoButton() {
			return undoButton;
		}

		/** How many times the narrator was asked to read it (PRD: once). */
		public int announcements() {
			return announcements;
		}

		long ageMillis() {
			return Util.getMillis() - shownAt;
		}

		/** Pixels the toast is still shifted right while sliding in; 0 with reduced motion. */
		public int slideOffset() {
			if (UiSettings.reducedMotion) {
				return 0;
			}
			double progress = Math.min(1.0, ageMillis() / (double) TOAST_SLIDE_MILLIS);
			return (int) Math.round((1.0 - progress) * (TOAST_WIDTH + Theme.PADDING));
		}

		private int width() {
			return Math.min(TOAST_WIDTH, AzScreen.this.width - 2 * Theme.PADDING);
		}

		private int x() {
			return AzScreen.this.width - Theme.PADDING - width() + slideOffset();
		}

		private int y() {
			return AzScreen.this.height - FOOTER_HEIGHT - Theme.PADDING - Theme.WIDGET_HEIGHT - 4;
		}

		private void layout() {
			if (undoButton != null) {
				undoButton.setPosition(x() + width() - undoButton.getWidth() - 2, y() + 2);
			}
		}

		private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			layout();
			int h = Theme.WIDGET_HEIGHT + 4;
			Draw.fill(g, x() - 1, y() - 1, width() + 2, h + 2, Theme.SEPARATOR);
			Draw.fill(g, x(), y(), width(), h, Theme.TOAST);
			int textWidth = width() - 8 - (undoButton == null ? 0 : undoButton.getWidth() + Theme.GAP);
			Draw.truncatedText(g, font, message.getString(), x() + 6, y() + (h - font.lineHeight) / 2 + 1, textWidth, Theme.TEXT);
		}
	}

	private void drawLayers(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (toast != null) {
			toast.draw(g, mouseX, mouseY, partialTick);
		}
		if (overlayOpen) {
			Draw.fill(g, overlayX - 1, overlayY - 1, overlayWidth + 2, overlayHeight + 2, Theme.SEPARATOR);
			Draw.fill(g, overlayX, overlayY, overlayWidth, overlayHeight, Theme.TOAST);
		}
	}

	// ---- Layout ----

	@Override
	protected void init() {
		List<Tab> tabs = tabs();
		currentTab = Math.min(currentTab, tabs.size() - 1);
		tabButtons.clear();
		bodyWidgets.clear();
		labels.clear();
		// init() runs after the widget list was cleared (resize, tab switch): drop any overlay without callbacks.
		overlayWidgets.clear();
		overlayOpen = false;
		overlayClosed = null;

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

		// Toast and overlay panels: one layer added after all base widgets, so it draws above them and below the
		// toast's and overlay's own buttons, which are added later.
		addRenderableOnly(this::drawLayers);

		// A toast outlives a relayout; add its button back.
		if (toast != null) {
			addToastWidgets();
		}
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
		if (hasOverlay() && event.key() == InputConstants.KEY_ESCAPE) {
			closeOverlay();
			return true;
		}
		if (event.key() == InputConstants.KEY_TAB && event.hasControlDown() && !hasOverlay()) {
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
		for (var child : super.children()) {
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

		/** Adds one widget that takes the full width and all the remaining height (e.g. a list). */
		public Body fill(AbstractWidget widget) {
			widget.setPosition(x, cursorY);
			widget.setWidth(width);
			widget.setHeight(Math.max(AzList.ROW_HEIGHT, bottom() - cursorY));
			widgets.add(widget);
			cursorY += widget.getHeight() + Theme.GAP;
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
