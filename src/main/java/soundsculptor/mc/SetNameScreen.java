package soundsculptor.mc;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * (FR-12.7). A name another set already has gets " (2)" and so on. Saving hands the name to the screen's owner (the
 * {@link SetLibraryPanel} or the {@link PlaceSetsPanel}), which applies it with the toast and Undo.
 */
public final class SetNameScreen extends SculptScreen {
	private static final int NAME_WIDTH = 240;

	/** The problem with a name, or {@code null} when Save can go ahead. */
	private final Function<String, Message> problemOf;
	/** Applies the name. */
	private final Consumer<String> onSave;
	/** Where Save returns to; Cancel returns to the parent. */
	private final Screen afterSave;
	private final boolean renaming;
	private final String original;
	private String value;
	private boolean saved;
	public SculptTextField nameField;
	public SculptButton saveButton;

	private SetNameScreen(Screen parent, Screen afterSave, Component title, String original, boolean renaming,
			Function<String, Message> problemOf, Consumer<String> onSave) {
		super(title, parent);
		this.afterSave = afterSave;
		this.original = original;
		this.value = original;
		this.renaming = renaming;
		this.problemOf = problemOf;
		this.onSave = onSave;
	}

	/** Names a new, empty set. */
	public static SetNameScreen create(Screen parent, SetLibraryPanel panel) {
		return new SetNameScreen(parent, parent, Component.translatable("soundsculptor.sets.new.title"), "", false,
				name -> LibraryOps.canCreate(ProfileManager.workspace(), name), panel::create);
	}

	/** Renames the set {@code setId}, starting from its current name. */
	public static SetNameScreen rename(Screen parent, SetLibraryPanel panel, String setId) {
		SoundSet set = ProfileManager.library().get(setId);
		return new SetNameScreen(parent, parent, Component.translatable("soundsculptor.sets.rename.title"), set == null ? "" : set.name(),
				true, name -> LibraryOps.canRename(ProfileManager.workspace(), setId, name), name -> panel.rename(setId, name));
	}

	/**
	 * Asks for a name and hands it to {@code onSave}.
	 *
	 * @param parent    where Cancel returns to
	 * @param afterSave where Save returns to
	 * @param initial   the name the field starts with
	 * @param problemOf the problem with a name, shown under the field, or {@code null} when it can be saved
	 */
	public static SetNameScreen ask(Screen parent, Screen afterSave, Component title, String initial,
			Function<String, Message> problemOf, Consumer<String> onSave) {
		return new SetNameScreen(parent, afterSave, title, initial, false, problemOf, onSave);
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return renaming ? Component.literal(original) : null;
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
		Message problem = problemOf.apply(name);
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
		onSave.accept(value);
		saved = true;
		return true;
	}

	@Override
	protected void closeNow() {
		minecraft.gui.setScreen(saved ? afterSave : parent);
	}
}
