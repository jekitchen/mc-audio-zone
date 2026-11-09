package audiozone.mc.ui.demo;

import audiozone.core.ui.SliderModel;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzDialog;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzSlider;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.AzToggle;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * M2 demo screen (PRD 10.1): every UI kit component on three tabs, for checking keyboard navigation, narration and
 * layout at GUI scales 1–4. Opened from the main screen in the development environment only.
 */
public final class UiKitDemoScreen extends AzScreen {
	private static final int TOGGLE_WIDTH = 130;

	// Observable state (shown on screen and read by the M2 game test).
	public String lastAction = "none";
	public boolean bypass;
	public boolean showCue = true;
	public boolean reducedMotion;
	public double volume = 100;
	public String pattern = "";
	public boolean pretendUnsaved;

	public AzSlider volumeSlider;
	public AzTextField volumeField;
	public AzTextField patternField;
	public AzToggle showCueToggle;

	public UiKitDemoScreen(Screen parent) {
		super(Component.translatable("audiozone.demo.title"), parent);
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				new Tab(Component.translatable("audiozone.demo.tab.buttons"), true),
				new Tab(Component.translatable("audiozone.demo.tab.inputs"), true),
				new Tab(Component.translatable("audiozone.demo.tab.dialogs"), false));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("audiozone.demo.profile");
	}

	@Override
	protected void addHeaderWidgets(List<AbstractWidget> header) {
		header.add(new AzToggle(90, Component.translatable("audiozone.demo.bypass"), bypass, value -> bypass = value));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(AzButton.icon(icon("help"), Component.translatable("audiozone.ui.help"), this::openHelp));
		footer.add(AzButton.text(Component.translatable("audiozone.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		switch (index) {
			case 0 -> buildButtons(body);
			case 1 -> buildInputs(body);
			default -> buildDialogs(body);
		}
	}

	private void buildButtons(Body body) {
		body.label(() -> Component.translatable("audiozone.demo.last_action", lastAction));
		AzButton disabled = AzButton.text(Component.translatable("audiozone.demo.disabled_button"), () -> lastAction = "disabled?!");
		disabled.active = false;
		body.row(AzButton.text(Component.translatable("audiozone.demo.plain_button"), () -> lastAction = "Plain button"), disabled);
		body.row(
				AzButton.icon(icon("mute"), Component.translatable("audiozone.demo.mute"), () -> lastAction = "Mute"),
				AzButton.icon(icon("volume"), Component.translatable("audiozone.demo.volume"), () -> lastAction = "Volume"),
				AzButton.icon(icon("edit"), Component.translatable("audiozone.demo.edit"), () -> lastAction = "Edit"),
				AzButton.icon(icon("help"), Component.translatable("audiozone.demo.why"), () -> lastAction = "Why?"));
		showCueToggle = new AzToggle(TOGGLE_WIDTH, Component.translatable("audiozone.demo.show_cue"), showCue, value -> showCue = value);
		body.row(showCueToggle,
				new AzToggle(TOGGLE_WIDTH, Component.translatable("audiozone.demo.reduced_motion"), reducedMotion, value -> reducedMotion = value));
	}

	private void buildInputs(Body body) {
		volumeSlider = new AzSlider(160, Component.translatable("audiozone.demo.volume"),
				new SliderModel(0, 200, 5, 1, 100, volume),
				value -> Component.literal((int) value + "%"),
				Component.translatable("audiozone.demo.louder_than_vanilla"),
				value -> {
					volume = value;
					if (volumeField != null && !volumeField.isFocused()) {
						volumeField.setValue(Integer.toString((int) value));
					}
				});
		volumeField = new AzTextField(font, 50, Component.translatable("audiozone.demo.volume_typed"));
		volumeField.setMaxLength(3);
		volumeField.setValue(Integer.toString((int) volume));
		volumeField.validator(UiKitDemoScreen::validateVolume)
				.onChange(text -> {
					if (validateVolume(text) == null) {
						volumeSlider.setTypedValue(Integer.parseInt(text));
					}
				});
		body.row(volumeSlider, volumeField);

		patternField = new AzTextField(font, 220, Component.translatable("audiozone.demo.pattern"))
				.placeholder(Component.literal("minecraft:entity.villager.*"));
		patternField.setValue(pattern);
		patternField.validator(UiKitDemoScreen::validatePattern).onChange(text -> pattern = text);
		body.row(patternField);
		body.label(() -> Component.translatable("audiozone.demo.slider_keys"));
	}

	private void buildDialogs(Body body) {
		body.label(() -> Component.translatable("audiozone.demo.last_action", lastAction));
		body.row(AzButton.text(Component.translatable("audiozone.demo.open_two"), this::openTwoButtonDialog));
		body.row(AzButton.text(Component.translatable("audiozone.demo.open_three"), this::openThreeButtonDialog));
		body.row(new AzToggle(200, Component.translatable("audiozone.demo.pretend_unsaved"), pretendUnsaved, value -> pretendUnsaved = value));
	}

	public void openTwoButtonDialog() {
		minecraft.gui.setScreen(new AzDialog(this,
				Component.translatable("audiozone.demo.delete_title"),
				Component.translatable("audiozone.demo.delete_message"),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.demo.delete"), () -> lastAction = "Delete"),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> lastAction = "Cancel")),
				1, 1));
	}

	public void openThreeButtonDialog() {
		minecraft.gui.setScreen(new AzDialog(this,
				Component.translatable("audiozone.ui.unsaved.title"),
				Component.translatable("audiozone.ui.unsaved.message"),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.ui.save"), () -> lastAction = "Save"),
						new AzDialog.Choice(Component.translatable("audiozone.ui.discard"), () -> lastAction = "Discard"),
						new AzDialog.Choice(Component.translatable("audiozone.ui.keep_editing"), () -> lastAction = "Keep editing")),
				0, 2));
	}

	private void openHelp() {
		minecraft.gui.setScreen(new AzDialog(this,
				Component.translatable("audiozone.demo.help_title"),
				Component.translatable("audiozone.demo.help_message"),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.ui.ok"), () -> { }),
						new AzDialog.Choice(Component.translatable("audiozone.ui.close"), () -> { })),
				0, 1));
	}

	@Override
	protected boolean hasUnsavedChanges() {
		return pretendUnsaved;
	}

	@Override
	protected void saveChanges() {
		lastAction = "Saved";
		pretendUnsaved = false;
	}

	@Override
	protected void discardChanges() {
		lastAction = "Discarded";
		pretendUnsaved = false;
	}

	static Component validateVolume(String text) {
		if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
			return Component.translatable("audiozone.demo.error.volume");
		}
		int value = Integer.parseInt(text);
		return value > 200 ? Component.translatable("audiozone.demo.error.volume") : null;
	}

	static Component validatePattern(String text) {
		if (text.isEmpty()) {
			return Component.translatable("audiozone.demo.error.pattern_empty");
		}
		if (text.chars().anyMatch(Character::isWhitespace)) {
			return Component.translatable("audiozone.demo.error.pattern_space");
		}
		return null;
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("audiozone", "icon/" + name);
	}
}
