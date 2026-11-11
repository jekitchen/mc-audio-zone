package audiozone.mc;

import audiozone.core.profile.Profile;
import audiozone.core.profile.ProfileOps;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.Tooltips;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Rename a profile (PRD FR-11.3): changes its display name only; the world key, and so the file, stay the same. Save
 * and Cancel with the unsaved-changes dialog (FR-12.4); an empty or too long name shows an error and disables Save
 * (FR-12.7).
 */
public final class ProfileRenameScreen extends AzScreen {
	private static final int NAME_WIDTH = 240;

	private final String key;
	private final Profile profile;
	private String value;
	public AzTextField nameField;
	public AzButton saveButton;

	public ProfileRenameScreen(Screen parent, String key, Profile profile) {
		super(Component.translatable("audiozone.profiles.rename.title"), parent);
		this.key = key;
		this.profile = profile;
		this.value = profile.name();
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.literal(key);
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(AzButton.text(Component.translatable("audiozone.ui.cancel"), this::onClose));
		saveButton = AzButton.text(Component.translatable("audiozone.ui.save"), this::save);
		footer.add(saveButton);
		saveButton.active = problem(value) == null;
	}

	@Override
	protected void buildTab(int index, Body body) {
		nameField = Tooltips.describe(new AzTextField(font, NAME_WIDTH, Component.translatable("audiozone.profiles.rename.name")),
				Component.translatable("audiozone.profiles.rename.name.tip"));
		nameField.setMaxLength(ProfileOps.MAX_NAME_LENGTH + 16);
		nameField.setValue(value);
		nameField.validator(ProfileRenameScreen::problem);
		nameField.onChange(text -> {
			value = text;
			if (saveButton != null) {
				saveButton.active = problem(text) == null;
			}
		});
		body.row(nameField);
		body.label(() -> Component.translatable("audiozone.profiles.rename.hint"));
	}

	/** The error under the field, in the player's language, or null. */
	private static Component problem(String name) {
		audiozone.core.Message problem = ProfileOps.nameProblem(name);
		return problem == null ? null : Component.literal(ProfileManager.text(problem));
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
		return !value.strip().equals(profile.name());
	}

	@Override
	protected boolean saveChanges() {
		if (problem(value) != null) {
			nameField.revealError();
			return false;
		}
		if (key.equals(ProfileManager.key())) {
			ProfileManager.update(p -> ProfileOps.rename(p, value));
		} else {
			// Rename what is stored now, not the copy read when the list was built, so no other change is lost.
			Profile latest = ProfileManager.storedProfile(key);
			ProfileManager.saveOther(key, ProfileOps.rename(latest != null ? latest : profile, value));
		}
		return true;
	}
}
