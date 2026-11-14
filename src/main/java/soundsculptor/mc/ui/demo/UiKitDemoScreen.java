package soundsculptor.mc.ui.demo;

import soundsculptor.core.ui.SliderModel;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptColorPicker;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;
import soundsculptor.mc.ui.UiSettings;
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
public final class UiKitDemoScreen extends SculptScreen {
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

	public SculptSlider volumeSlider;
	public SculptTextField volumeField;
	public SculptTextField patternField;
	public SculptToggle showCueToggle;

	public int refreshCount;
	public SculptList soundList;
	public SculptDropdown<String> sortDropdown;
	public SculptDropdown<String> applyToDropdown;
	public SculptColorPicker colorPicker;
	public String sort = "Newest";
	public String applyTo = "Global";
	public int zoneColor = 0xE69F00;

	public UiKitDemoScreen(Screen parent) {
		super(Component.translatable("soundsculptor.demo.title"), parent);
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				new Tab(Component.translatable("soundsculptor.demo.tab.buttons"), true),
				new Tab(Component.translatable("soundsculptor.demo.tab.inputs"), true),
				new Tab(Component.translatable("soundsculptor.demo.tab.lists"), true),
				new Tab(Component.translatable("soundsculptor.demo.tab.pickers"), true),
				new Tab(Component.translatable("soundsculptor.demo.tab.dialogs"), false));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("soundsculptor.demo.profile");
	}

	@Override
	protected void addHeaderWidgets(List<AbstractWidget> header) {
		header.add(tip(new SculptToggle(90, Component.translatable("soundsculptor.demo.bypass"), bypass, value -> bypass = value), "bypass"));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.icon(icon("help"), Component.translatable("soundsculptor.ui.help"), this::openHelp));
		footer.add(tip(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose), "done"));
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
		body.label(() -> Component.translatable("soundsculptor.demo.last_action", lastAction));
		SculptButton disabled = SculptButton.text(Component.translatable("soundsculptor.demo.disabled_button"), () -> lastAction = "disabled?!");
		disabled.active = false;
		tip(disabled, "disabled_button");
		body.row(tip(SculptButton.text(Component.translatable("soundsculptor.demo.plain_button"), () -> lastAction = "Plain button"), "plain_button"), disabled);
		body.row(
				SculptButton.icon(icon("mute"), Component.translatable("soundsculptor.demo.mute"), () -> lastAction = "Mute"),
				SculptButton.icon(icon("volume"), Component.translatable("soundsculptor.demo.volume"), () -> lastAction = "Volume"),
				SculptButton.icon(icon("edit"), Component.translatable("soundsculptor.demo.edit"), () -> lastAction = "Edit"),
				SculptButton.icon(icon("help"), Component.translatable("soundsculptor.demo.why"), () -> lastAction = "Why?"));
		showCueToggle = tip(new SculptToggle(TOGGLE_WIDTH, Component.translatable("soundsculptor.demo.show_cue"), showCue, value -> showCue = value), "show_cue");
		body.row(showCueToggle,
				tip(new SculptToggle(TOGGLE_WIDTH, Component.translatable("soundsculptor.demo.reduced_motion"), reducedMotion, value -> {
					reducedMotion = value;
					UiSettings.reducedMotion = value;
				}), "reduced_motion"));
	}

	private void buildLists(Body body) {
		body.label(() -> Component.translatable("soundsculptor.demo.last_action", lastAction));
		body.row(tip(SculptButton.text(Component.translatable("soundsculptor.demo.refresh"), this::refreshList), "refresh"));
		soundList = new SculptList(body.width(), 100, Component.translatable("soundsculptor.demo.list_label"));
		body.fill(soundList);
		soundList.setRows(sampleRows());
	}

	/** Simulates new data arriving: rows get a new play count; focus and scroll position must stay. */
	public void refreshList() {
		refreshCount++;
		soundList.setRows(sampleRows());
		lastAction = "Refreshed (" + refreshCount + ")";
	}

	private List<SculptList.Row> sampleRows() {
		List<SculptList.Row> rows = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			String[] sound = SAMPLE_SOUNDS[i % SAMPLE_SOUNDS.length];
			String name = sound[0] + " #" + (i + 1);
			String id = sound[1];
			String detail = BANDS[i % BANDS.length] + " · ×" + (1 + (i * 7 + refreshCount) % 9);
			String key = id + "#" + i;
			rows.add(new SculptList.Row(key,
					List.of(new SculptList.Row.Column(() -> name, 110), new SculptList.Row.Column(() -> id, 0), new SculptList.Row.Column(() -> detail, 60)),
					List.of(
							SculptButton.icon(icon("mute"), Component.translatable("soundsculptor.demo.mute"), () -> lastAction = "Mute " + name),
							SculptButton.icon(icon("volume"), Component.translatable("soundsculptor.demo.volume"), () -> lastAction = "Volume " + name),
							SculptButton.icon(icon("help"), Component.translatable("soundsculptor.demo.why"), () -> lastAction = "Why? " + name))));
		}
		return rows;
	}

	private void buildPickers(Body body) {
		sortDropdown = tip(new SculptDropdown<>(140, Component.translatable("soundsculptor.demo.sort"),
				List.of("Newest", "Oldest", "Name", "Distance"), Component::literal, sort, value -> {
					sort = value;
					lastAction = "Sort: " + value;
				}), "sort");
		applyToDropdown = tip(new SculptDropdown<>(160, Component.translatable("soundsculptor.demo.apply_to"),
				List.of("Global", "Trading hall", "Build site"), Component::literal, applyTo, value -> {
					applyTo = value;
					lastAction = "Apply to: " + value;
				}), "apply_to");
		body.row(sortDropdown, applyToDropdown);
		body.label(() -> Component.translatable("soundsculptor.demo.zone_color", net.minecraft.network.chat.Component.literal(
				soundsculptor.core.ui.Palette.formatHex(zoneColor)).getString()));
		colorPicker = new SculptColorPicker(font, Component.translatable("soundsculptor.demo.zone_color_short"), zoneColor, 0xE69F00, value -> {
			zoneColor = value;
			lastAction = "Color " + soundsculptor.core.ui.Palette.formatHex(value);
		});
		colorPicker.addTo(body);
		body.row(tip(SculptButton.text(Component.translatable("soundsculptor.demo.show_toast"), this::showPlainToast), "show_toast"),
				tip(SculptButton.text(Component.translatable("soundsculptor.demo.show_undo_toast"), this::showUndoToast), "show_undo_toast"));
	}

	public void showPlainToast() {
		showToast(Component.translatable("soundsculptor.demo.toast_plain"), null);
	}

	public void showUndoToast() {
		lastAction = "Muted villager";
		showToast(Component.translatable("soundsculptor.demo.toast_undo"), () -> lastAction = "Undone");
	}

	private void buildInputs(Body body) {
		volumeSlider = new SculptSlider(160, Component.translatable("soundsculptor.demo.volume"),
				new SliderModel(0, 200, 5, 1, 100, volume),
				value -> Component.literal((int) value + "%"),
				Component.translatable("soundsculptor.demo.louder_than_vanilla"),
				value -> {
					volume = value;
					if (volumeField != null && !volumeField.isFocused()) {
						volumeField.setValue(Integer.toString((int) value));
					}
				});
		tip(volumeSlider, "volume_slider");
		volumeField = tip(new SculptTextField(font, 50, Component.translatable("soundsculptor.demo.volume_typed")), "volume_typed");
		volumeField.setMaxLength(3);
		volumeField.setValue(Integer.toString((int) volume));
		volumeField.validator(UiKitDemoScreen::validateVolume)
				.onChange(text -> {
					if (validateVolume(text) == null) {
						volumeSlider.setTypedValue(Integer.parseInt(text));
					}
				});
		body.row(volumeSlider, volumeField);

		patternField = tip(new SculptTextField(font, 220, Component.translatable("soundsculptor.demo.pattern"))
				.placeholder(Component.literal("minecraft:entity.villager.*")), "pattern");
		patternField.setValue(pattern);
		patternField.validator(UiKitDemoScreen::validatePattern).onChange(text -> pattern = text);
		body.row(patternField);
		body.label(() -> Component.translatable("soundsculptor.demo.slider_keys"));
	}

	private void buildDialogs(Body body) {
		body.label(() -> Component.translatable("soundsculptor.demo.last_action", lastAction));
		body.row(tip(SculptButton.text(Component.translatable("soundsculptor.demo.open_two"), this::openTwoButtonDialog), "open_two"));
		body.row(tip(SculptButton.text(Component.translatable("soundsculptor.demo.open_three"), this::openThreeButtonDialog), "open_three"));
		body.row(tip(new SculptToggle(200, Component.translatable("soundsculptor.demo.pretend_unsaved"), pretendUnsaved, value -> pretendUnsaved = value), "pretend_unsaved"));
	}

	public void openTwoButtonDialog() {
		minecraft.gui.setScreen(new SculptDialog(this,
				Component.translatable("soundsculptor.demo.delete_title"),
				Component.translatable("soundsculptor.demo.delete_message"),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.demo.delete"), () -> lastAction = "Delete"),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> lastAction = "Cancel")),
				1, 1));
	}

	public void openThreeButtonDialog() {
		minecraft.gui.setScreen(new SculptDialog(this,
				Component.translatable("soundsculptor.ui.unsaved.title"),
				Component.translatable("soundsculptor.ui.unsaved.message"),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.ui.save"), () -> lastAction = "Save"),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.discard"), () -> lastAction = "Discard"),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.keep_editing"), () -> lastAction = "Keep editing")),
				0, 2));
	}

	private void openHelp() {
		minecraft.gui.setScreen(new SculptDialog(this,
				Component.translatable("soundsculptor.demo.help_title"),
				Component.translatable("soundsculptor.demo.help_message"),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.ui.ok"), () -> { }),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.close"), () -> { })),
				0, 1));
	}

	@Override
	protected boolean hasUnsavedChanges() {
		return pretendUnsaved;
	}

	@Override
	protected boolean saveChanges() {
		lastAction = "Saved";
		pretendUnsaved = false;
		return true;
	}

	@Override
	protected void discardChanges() {
		lastAction = "Discarded";
		pretendUnsaved = false;
	}

	static Component validateVolume(String text) {
		if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
			return Component.translatable("soundsculptor.demo.error.volume");
		}
		int value = Integer.parseInt(text);
		return value > 200 ? Component.translatable("soundsculptor.demo.error.volume") : null;
	}

	static Component validatePattern(String text) {
		if (text.isEmpty()) {
			return Component.translatable("soundsculptor.demo.error.pattern_empty");
		}
		if (text.chars().anyMatch(Character::isWhitespace)) {
			return Component.translatable("soundsculptor.demo.error.pattern_space");
		}
		return null;
	}

	/** Every demo control gets a tooltip from "soundsculptor.demo.tip.<key>". */
	private static <T extends AbstractWidget> T tip(T widget, String key) {
		return Tooltips.describe(widget, Component.translatable("soundsculptor.demo.tip." + key));
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("soundsculptor", "icon/" + name);
	}
}
