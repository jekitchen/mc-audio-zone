package soundsculptor.mc.ui;

import soundsculptor.core.ui.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Color picker (PRD 6.2): the Okabe–Ito swatches (each named, e.g. "Orange", for the tooltip and the narrator), a
 * hex field for any other color, and a reset button. The chosen swatch has a thick two-tone frame and is narrated
 * as "selected", so the choice is not shown by color alone.
 */
public final class SculptColorPicker {
	private static final int HEX_FIELD_WIDTH = 70;

	private final int defaultColor;
	private final IntConsumer onChange;
	private final List<Swatch> swatches = new ArrayList<>();
	private final SculptTextField hexField;
	private final SculptButton resetButton;
	private int color;

	public SculptColorPicker(Font font, Component label, int initial, int defaultColor, IntConsumer onChange) {
		this.defaultColor = defaultColor & 0xFFFFFF;
		this.onChange = onChange;
		this.color = initial & 0xFFFFFF;
		for (Palette.Swatch swatch : Palette.OKABE_ITO) {
			swatches.add(new Swatch(swatch));
		}
		hexField = new SculptTextField(font, HEX_FIELD_WIDTH, Component.translatable("soundsculptor.ui.color.hex", label));
		hexField.setMaxLength(7);
		hexField.setValue(Palette.formatHex(color));
		hexField.validator(text -> Palette.parseHex(text).isPresent() ? null : Component.translatable("soundsculptor.ui.color.hex_error"))
				.onChange(text -> {
					OptionalInt parsed = Palette.parseHex(text);
					if (parsed.isPresent()) {
						apply(parsed.getAsInt(), false);
					}
				});
		resetButton = SculptButton.text(Component.translatable("soundsculptor.ui.reset"), () -> apply(this.defaultColor, true));
		Tooltips.describe(resetButton, Component.translatable("soundsculptor.ui.color.reset_tip", Palette.formatHex(this.defaultColor)));
	}

	/** Adds the picker to a tab body: swatches on one row, hex field and reset on the next. */
	public void addTo(SculptScreen.Body body) {
		body.row(swatches.toArray(new Swatch[0]));
		body.row(hexField, resetButton);
	}

	public int color() {
		return color;
	}

	public List<Swatch> swatches() {
		return swatches;
	}

	public SculptTextField hexField() {
		return hexField;
	}

	public SculptButton resetButton() {
		return resetButton;
	}

	private void apply(int newColor, boolean updateField) {
		int rgb = newColor & 0xFFFFFF;
		boolean changed = rgb != color;
		color = rgb;
		if (updateField) {
			hexField.setValue(Palette.formatHex(rgb));
		}
		if (changed) {
			onChange.accept(rgb);
		}
	}

	/** A palette swatch button. */
	public final class Swatch extends AbstractButton {
		private final Palette.Swatch swatch;

		private Swatch(Palette.Swatch swatch) {
			super(0, 0, Theme.ICON_BUTTON_SIZE, Theme.ICON_BUTTON_SIZE, Component.translatable("soundsculptor.color." + swatch.id()));
			this.swatch = swatch;
			Tooltips.describe(this, getMessage());
		}

		public int rgb() {
			return swatch.rgb();
		}

		public boolean isSelected() {
			return swatch.rgb() == color;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			apply(swatch.rgb(), true);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			int size = getWidth();
			Draw.fill(g, x, y, size, size, Theme.SEPARATOR);
			Draw.fill(g, x + 2, y + 2, size - 4, size - 4, 0xFF000000 | swatch.rgb());
			if (isSelected()) {
				// Thick white frame with a black inner line: visible on every swatch color, including black.
				Draw.outline(g, x, y, size, size, 0xFFFFFFFF);
				Draw.outline(g, x + 1, y + 1, size - 2, size - 2, 0xFFFFFFFF);
				Draw.outline(g, x + 2, y + 2, size - 4, size - 4, 0xFF000000);
			}
			if (isFocused()) {
				Draw.focusRing(g, x, y, size, size);
			} else if (isHovered()) {
				Draw.outline(g, x, y, size, size, Theme.TEXT_DIM);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, isSelected()
					? Component.translatable("soundsculptor.ui.color.swatch.selected", getMessage())
					: Component.translatable("soundsculptor.ui.color.swatch", getMessage()));
			output.add(NarratedElementType.USAGE, Component.translatable("soundsculptor.ui.color.swatch.usage"));
		}
	}
}
