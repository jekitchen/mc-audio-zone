package soundsculptor.mc;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import soundsculptor.core.Message;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.Tooltips;

/**
 * The name of a new library Sound Set, or a new name for one (PRD FR-4.7), like {@link ProfileRenameScreen}: Save and
 * Cancel with the unsaved-changes dialog (FR-12.4); an empty or too long name shows an error and disables Save
 * (FR-12.7). A name another set already has gets " (2)" and so on. Saving goes through the {@link SetLibraryPanel},
 * which shows the toast with Undo.
 */
public final class SetNameScreen extends SculptScreen {
	private static final int NAME_WIDTH = 240;

	private final SetLibraryPanel panel;
	/** The set being renamed, or {@code null} for a new set. */
	private final String setId;
	private final String original;
	private String value;
	public SculptTextField nameField;
	public SculptButton saveButton;

	private SetNameScreen(Screen parent, SetLibraryPanel panel, String setId, String original, Component title) {
		super(title, parent);
		this.panel = panel;
		this.setId = setId;
		this.original = original;
		this.value = original;
	}

	/** Names a new, empty set. */
	public static SetNameScreen create(Screen parent, SetLibraryPanel panel) {
		return new SetNameScreen(parent, panel, null, "", Component.translatable("soundsculptor.sets.new.title"));
	}

	/** Renames the set {@code setId}, starting from its current name. */
	public static SetNameScreen rename(Screen parent, SetLibraryPanel panel, String setId) {
		SoundSet set = ProfileManager.library().get(setId);
		return new SetNameScreen(parent, panel, setId, set == null ? "" : set.name(),
				Component.translatable("soundsculptor.sets.rename.title"));
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return setId == null ? null : Component.literal(original);
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
		saveButton = SculptButton.text(Component.translatable("soundsculptor.ui.save"), this::save);
		footer.add(saveButton);
		saveButton.active = problem(value) == null;
	}

	@Override
	protected void buildTab(int index, Body body) {
		nameField = Tooltips.describe(new SculptTextField(font, NAME_WIDTH, Component.translatable("soundsculptor.sets.name")),
				Component.translatable("soundsculptor.sets.name.tip"));
		nameField.setMaxLength(SoundSet.MAX_NAME_LENGTH + 16);
		nameField.setValue(value);
		nameField.validator(this::problem);
		nameField.onChange(text -> {
			value = text;
			if (saveButton != null) {
				saveButton.active = problem(text) == null;
			}
		});
		body.row(nameField);
		body.label(() -> Component.translatable("soundsculptor.sets.name.hint"));
	}

	/** The error under the field, in the player's language, or null. */
	private Component problem(String name) {
		Message problem = setId == null
				? LibraryOps.canCreate(ProfileManager.workspace(), name)
				: LibraryOps.canRename(ProfileManager.workspace(), setId, name);
		return problem == null ? null : ProfileManager.component(problem);
	}

	public void setName(String name) {
		nameField.setValue(name);
	}

	public void save() {
		if (saveChanges()) {
			closeNow();
		}
	}

	@Override
	protected boolean hasUnsavedChanges() {
		return !value.strip().equals(original);
	}

	@Override
	protected boolean saveChanges() {
		if (problem(value) != null) {
			nameField.revealError();
			return false;
		}
		if (setId == null) {
			panel.create(value);
		} else {
			panel.rename(setId, value);
		}
		return true;
	}
}
