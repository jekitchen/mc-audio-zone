package audiozone.mc.ui.demo;

import audiozone.core.ui.SliderModel;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzColorPicker;
import audiozone.mc.ui.AzDialog;
import audiozone.mc.ui.AzDropdown;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzSlider;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.AzToggle;
import audiozone.mc.ui.Tooltips;
import audiozone.mc.ui.UiSettings;
import java.util.ArrayList;
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

	public static final int TAB_BUTTONS = 0;
	public static final int TAB_INPUTS = 1;
	public static final int TAB_LISTS = 2;
	public static final int TAB_PICKERS = 3;
	public static final int TAB_DIALOGS = 4;

	/** Sample sounds for the list tab: name and ID. */
	private static final String[][] SAMPLE_SOUNDS = {
			{"Villager mumbles", "minecraft:entity.villager.ambient"},
			{"Glass breaks", "minecraft:block.glass.break"},
			{"Cow moos", "minecraft:entity.cow.ambient"},
			{"Iron golem walks", "minecraft:entity.iron_golem.step"},
			{"Hopper clicks", "minecraft:block.hopper.lock"},
			{"Bell rings", "minecraft:block.bell.use"},
			{"Ghast cries", "minecraft:entity.ghast.scream"},
			{"Explosion", "minecraft:entity.generic.explode"},
	};
	private static final String[] BANDS = {"Close", "Near", "Far"};

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

	public int refreshCount;
	public AzList soundList;
	public AzDropdown<String> sortDropdown;
	public AzDropdown<String> applyToDropdown;
	public AzColorPicker colorPicker;
	public String sort = "Newest";
	public String applyTo = "Global";
	public int zoneColor = 0xE69F00;

	public UiKitDemoScreen(Screen parent) {
		super(Component.translatable("audiozone.demo.title"), parent);
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				new Tab(Component.translatable("audiozone.demo.tab.buttons"), true),
				new Tab(Component.translatable("audiozone.demo.tab.inputs"), true),
				new Tab(Component.translatable("audiozone.demo.tab.lists"), true),
				new Tab(Component.translatable("audiozone.demo.tab.pickers"), true),
				new Tab(Component.translatable("audiozone.demo.tab.dialogs"), false));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("audiozone.demo.profile");
	}

	@Override
	protected void addHeaderWidgets(List<AbstractWidget> header) {
		header.add(tip(new AzToggle(90, Component.translatable("audiozone.demo.bypass"), bypass, value -> bypass = value), "bypass"));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(AzButton.icon(icon("help"), Component.translatable("audiozone.ui.help"), this::openHelp));
		footer.add(tip(AzButton.text(Component.translatable("audiozone.ui.done"), this::onClose), "done"));
	}

	@Override
	protected void buildTab(int index, Body body) {
		switch (index) {
			case TAB_BUTTONS -> buildButtons(body);
			case TAB_INPUTS -> buildInputs(body);
			case TAB_LISTS -> buildLists(body);
			case TAB_PICKERS -> buildPickers(body);
			default -> buildDialogs(body);
		}
	}

	private void buildButtons(Body body) {
		body.label(() -> Component.translatable("audiozone.demo.last_action", lastAction));
		AzButton disabled = AzButton.text(Component.translatable("audiozone.demo.disabled_button"), () -> lastAction = "disabled?!");
		disabled.active = false;
		tip(disabled, "disabled_button");
		body.row(tip(AzButton.text(Component.translatable("audiozone.demo.plain_button"), () -> lastAction = "Plain button"), "plain_button"), disabled);
		body.row(
				AzButton.icon(icon("mute"), Component.translatable("audiozone.demo.mute"), () -> lastAction = "Mute"),
				AzButton.icon(icon("volume"), Component.translatable("audiozone.demo.volume"), () -> lastAction = "Volume"),
				AzButton.icon(icon("edit"), Component.translatable("audiozone.demo.edit"), () -> lastAction = "Edit"),
				AzButton.icon(icon("help"), Component.translatable("audiozone.demo.why"), () -> lastAction = "Why?"));
		showCueToggle = tip(new AzToggle(TOGGLE_WIDTH, Component.translatable("audiozone.demo.show_cue"), showCue, value -> showCue = value), "show_cue");
		body.row(showCueToggle,
				tip(new AzToggle(TOGGLE_WIDTH, Component.translatable("audiozone.demo.reduced_motion"), reducedMotion, value -> {
					reducedMotion = value;
					UiSettings.reducedMotion = value;
				}), "reduced_motion"));
	}

	private void buildLists(Body body) {
		body.label(() -> Component.translatable("audiozone.demo.last_action", lastAction));
		body.row(tip(AzButton.text(Component.translatable("audiozone.demo.refresh"), this::refreshList), "refresh"));
		soundList = new AzList(body.width(), 100, Component.translatable("audiozone.demo.list_label"));
		body.fill(soundList);
		soundList.setRows(sampleRows());
	}

	/** Simulates new data arriving: rows get a new play count; focus and scroll position must stay. */
	public void refreshList() {
		refreshCount++;
		soundList.setRows(sampleRows());
		lastAction = "Refreshed (" + refreshCount + ")";
	}

	private List<AzList.Row> sampleRows() {
		List<AzList.Row> rows = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			String[] sound = SAMPLE_SOUNDS[i % SAMPLE_SOUNDS.length];
			String name = sound[0] + " #" + (i + 1);
			String id = sound[1];
			String detail = BANDS[i % BANDS.length] + " · ×" + (1 + (i * 7 + refreshCount) % 9);
			String key = id + "#" + i;
			rows.add(new AzList.Row(key,
					List.of(new AzList.Row.Column(() -> name, 110), new AzList.Row.Column(() -> id, 0), new AzList.Row.Column(() -> detail, 60)),
					List.of(
							AzButton.icon(icon("mute"), Component.translatable("audiozone.demo.mute"), () -> lastAction = "Mute " + name),
							AzButton.icon(icon("volume"), Component.translatable("audiozone.demo.volume"), () -> lastAction = "Volume " + name),
							AzButton.icon(icon("help"), Component.translatable("audiozone.demo.why"), () -> lastAction = "Why? " + name))));
		}
		return rows;
	}

	private void buildPickers(Body body) {
		sortDropdown = tip(new AzDropdown<>(140, Component.translatable("audiozone.demo.sort"),
				List.of("Newest", "Oldest", "Name", "Distance"), Component::literal, sort, value -> {
					sort = value;
					lastAction = "Sort: " + value;
				}), "sort");
		applyToDropdown = tip(new AzDropdown<>(160, Component.translatable("audiozone.demo.apply_to"),
				List.of("Global", "Trading hall", "Build bubble"), Component::literal, applyTo, value -> {
					applyTo = value;
					lastAction = "Apply to: " + value;
				}), "apply_to");
		body.row(sortDropdown, applyToDropdown);
		body.label(() -> Component.translatable("audiozone.demo.zone_color", net.minecraft.network.chat.Component.literal(
				audiozone.core.ui.Palette.formatHex(zoneColor)).getString()));
		colorPicker = new AzColorPicker(font, Component.translatable("audiozone.demo.zone_color_short"), zoneColor, 0xE69F00, value -> {
			zoneColor = value;
			lastAction = "Color " + audiozone.core.ui.Palette.formatHex(value);
		});
		colorPicker.addTo(body);
		body.row(tip(AzButton.text(Component.translatable("audiozone.demo.show_toast"), this::showPlainToast), "show_toast"),
				tip(AzButton.text(Component.translatable("audiozone.demo.show_undo_toast"), this::showUndoToast), "show_undo_toast"));
	}

	public void showPlainToast() {
		showToast(Component.translatable("audiozone.demo.toast_plain"), null);
	}

	public void showUndoToast() {
		lastAction = "Muted villager";
		showToast(Component.translatable("audiozone.demo.toast_undo"), () -> lastAction = "Undone");
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
		tip(volumeSlider, "volume_slider");
		volumeField = tip(new AzTextField(font, 50, Component.translatable("audiozone.demo.volume_typed")), "volume_typed");
		volumeField.setMaxLength(3);
		volumeField.setValue(Integer.toString((int) volume));
		volumeField.validator(UiKitDemoScreen::validateVolume)
				.onChange(text -> {
					if (validateVolume(text) == null) {
						volumeSlider.setTypedValue(Integer.parseInt(text));
					}
				});
		body.row(volumeSlider, volumeField);

		patternField = tip(new AzTextField(font, 220, Component.translatable("audiozone.demo.pattern"))
				.placeholder(Component.literal("minecraft:entity.villager.*")), "pattern");
		patternField.setValue(pattern);
		patternField.validator(UiKitDemoScreen::validatePattern).onChange(text -> pattern = text);
		body.row(patternField);
		body.label(() -> Component.translatable("audiozone.demo.slider_keys"));
	}

	private void buildDialogs(Body body) {
		body.label(() -> Component.translatable("audiozone.demo.last_action", lastAction));
		body.row(tip(AzButton.text(Component.translatable("audiozone.demo.open_two"), this::openTwoButtonDialog), "open_two"));
		body.row(tip(AzButton.text(Component.translatable("audiozone.demo.open_three"), this::openThreeButtonDialog), "open_three"));
		body.row(tip(new AzToggle(200, Component.translatable("audiozone.demo.pretend_unsaved"), pretendUnsaved, value -> pretendUnsaved = value), "pretend_unsaved"));
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

	/** Every demo control gets a tooltip from "audiozone.demo.tip.<key>". */
	private static <T extends AbstractWidget> T tip(T widget, String key) {
		return Tooltips.describe(widget, Component.translatable("audiozone.demo.tip." + key));
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("audiozone", "icon/" + name);
	}
}
