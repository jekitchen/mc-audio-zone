package soundsculptor.mc;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import soundsculptor.core.Message;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.Tooltips;

/**
 * A new name for a Sound Set in an import file whose name a library set with other entries already has (PRD
 * FR-11.5a), like {@link SetNameScreen}. It asks for one set at a time, filled in with "name (imported)"; the header
 * shows the set's name in the file. A name in use (in the library, or chosen for another set in this import) shows
 * "That name is already in use. Choose a unique name to import this set." under the field and disables "Use this name"
 * (FR-12.7). Cancel, or Esc, cancels the whole import and returns to the Profiles tab: nothing has changed yet.
 */
public final class SetRenameOnImportScreen extends SculptScreen {
	private final SoundSet fileSet;
	private final int position;
	private final int count;
	/** The problem with a name, or {@code null} when it can be used. */
	private final Function<String, Message> problemOf;
	/** Takes the chosen name and opens what comes next: the next set's name, or the import dialog. */
	private final Consumer<String> onChosen;
	private String value;
	public SculptTextField nameField;
	public SculptButton useButton;

	/**
	 * @param parent    where Cancel returns to (the import is cancelled)
	 * @param position  which name clash this is, from 1
	 * @param count     how many sets need a new name
	 * @param initial   the name the field starts with
	 * @param problemOf the problem with a name, shown under the field, or {@code null} when it can be used
	 * @param onChosen  takes the name (without spaces at either end) and opens the next screen
	 */
	public SetRenameOnImportScreen(Screen parent, SoundSet fileSet, int position, int count, String initial,
			Function<String, Message> problemOf, Consumer<String> onChosen) {
		super(Component.translatable("soundsculptor.import.rename.title"), parent);
		this.fileSet = fileSet;
		this.position = position;
		this.count = count;
		this.value = initial;
		this.problemOf = problemOf;
		this.onChosen = onChosen;
	}

	/** The file's set this screen names. */
	public SoundSet fileSet() {
		return fileSet;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.literal(fileSet.name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
		useButton = SculptButton.text(Component.translatable("soundsculptor.import.rename.confirm"), this::use);
		footer.add(useButton);
		useButton.active = problem(value) == null;
	}

	@Override
	protected void buildTab(int index, Body body) {
		body.label(() -> Component.translatable("soundsculptor.import.rename.clash"));
		// As wide as the body, so the "already in use" message under the field fits.
		nameField = Tooltips.describe(new SculptTextField(font, body.width(), Component.translatable("soundsculptor.import.rename.name")),
				Component.translatable("soundsculptor.import.rename.name.tip"));
		nameField.setMaxLength(SoundSet.MAX_NAME_LENGTH + 16);
		nameField.setValue(value);
		nameField.validator(this::problem);
		nameField.onChange(text -> {
			value = text;
			if (useButton != null) {
				useButton.active = problem(text) == null;
			}
		});
		body.row(nameField);
		body.label(() -> Component.translatable("soundsculptor.import.rename.progress", position, count));
	}

	/** The error under the field, in the player's language, or null. */
	private Component problem(String name) {
		Message problem = problemOf.apply(name);
		return problem == null ? null : ProfileManager.component(problem);
	}

	public void setName(String name) {
		nameField.setValue(name);
	}

	/** Uses the name and goes on; a name that cannot be used shows its error and stays. */
	public void use() {
		if (problem(value) != null) {
			nameField.revealError();
			return;
		}
		onChosen.accept(value.strip());
	}

	/** Nothing is changed until the import dialog's Import, so leaving needs no discard dialog. */
	@Override
	protected boolean hasUnsavedChanges() {
		return false;
	}
}
