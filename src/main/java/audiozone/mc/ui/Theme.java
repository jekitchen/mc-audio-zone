package audiozone.mc.ui;

/**
 * Colors and sizes shared by all components. Text colors keep a contrast ratio of at least 4.5:1 against
 * {@link #PANEL} (PRD A-3); the ratio is noted next to each.
 */
public final class Theme {
	private Theme() {
	}

	/** Header, tab bar and footer backgrounds. */
	public static final int PANEL = 0xE0101010;
	/** Separator lines. */
	public static final int SEPARATOR = 0xFF505050;

	/** Primary text, about 19:1 on PANEL. */
	public static final int TEXT = 0xFFFFFFFF;
	/** Secondary text (labels, hints), about 10:1 on PANEL. */
	public static final int TEXT_DIM = 0xFFC0C0C0;
	/** Error messages and invalid outlines, about 7:1 on PANEL. */
	public static final int ERROR = 0xFFFF7070;
	/** Warning text and outlines (e.g. volume above 100%), about 13:1 on PANEL. */
	public static final int WARNING = 0xFFFFD24D;

	/** Keyboard focus ring (PRD A-1). */
	public static final int FOCUS = 0xFFFFFFFF;
	/** Selected tab background. */
	public static final int TAB_SELECTED = 0xFF3A3A3A;
	/** Hovered or focused, unselected tab background. */
	public static final int TAB_HOVER = 0xFF2A2A2A;
	/** Underline on the selected tab. Paired with the "selected" narration, so color is not the only signal. */
	public static final int TAB_UNDERLINE = 0xFFFFFFFF;

	public static final int WIDGET_HEIGHT = 20;
	public static final int ICON_BUTTON_SIZE = 20;
	public static final int ICON_SIZE = 16;
	public static final int GAP = 4;
	public static final int PADDING = 6;
}
