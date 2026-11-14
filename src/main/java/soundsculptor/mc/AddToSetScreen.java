package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import soundsculptor.core.Message;
import soundsculptor.core.browser.LikeThis;
import soundsculptor.core.browser.PickerModel;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.NewSet;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Tooltips;

/**
 * Add to set… on a Sounds row (PRD FR-1.5, FR-4.8): first the library Sound Set, or New set (a name, made unique,
 * FR-4.7); then what to add: <b>This sound</b> or one of its <b>Like this</b> groups, closest first, each with how many
 * known sounds it matches. The new entry has no settings yet (the player sets them in the set editor, FR-4.3). What
 * the set already has is marked "In this set" and cannot be added again (FR-1.7). A set that holds 512 entries says so
 * and nothing changes.
 *
 * <p>Adding changes the library at once through {@link ProfileManager#change}, and returns to the Sounds tab with a
 * 5-second Undo toast there (FR-12.5); a new set is made in the same change, so Undo removes it too. Cancel changes
 * nothing.
 */
public final class AddToSetScreen extends SculptScreen {
	/** The screen the toast shows on, and where Add and Cancel return to. */
	private final SculptScreen host;
	private final String soundId;
	private final String soundName;
	/** Rebuilds the host's rows after Undo. */
	private final Runnable refresh;
	/** The library set chosen in the first step, or {@code null}. */
	private String setId;
	/** The name for New set, chosen in the first step, or {@code null}. */
	private String newSetName;
	public SculptButton newButton;
	public SculptButton backButton;
	/** The library sets in the first step; This sound and the Like this groups in the second. */
	public SculptList list;

	public AddToSetScreen(SculptScreen host, String soundId, String soundName, Runnable refresh) {
		super(Component.translatable("soundsculptor.add_to_set.title"), host);
		this.host = host;
		this.soundId = soundId;
		this.soundName = soundName;
		this.refresh = refresh;
	}

	public String soundId() {
		return soundId;
	}

	/** Whether the set is chosen and the screen asks what to add. */
	public boolean isChoosingTarget() {
		return setId != null || newSetName != null;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.literal(soundName);
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		if (isChoosingTarget()) {
			backButton = SculptButton.text(Component.translatable("soundsculptor.add_to_set.back"), this::back);
			footer.add(backButton);
		} else {
			backButton = null;
		}
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		if (isChoosingTarget()) {
			buildTargets(body);
		} else {
			buildSets(body);
		}
	}

	// ---- First step: which set ----

	private void buildSets(Body body) {
		newButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.add_to_set.new"), this::openNew),
				Component.translatable("soundsculptor.add_to_set.new.tip"));
		body.row(newButton);
		body.label(() -> Component.translatable("soundsculptor.add_to_set.choose_set", soundName));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.add_to_set.sets"));
		list.setEmptyText(() -> Component.translatable("soundsculptor.add_to_set.sets.empty"));
		body.fill(list);
		Component chooseLabel = Component.translatable("soundsculptor.add_to_set.choose");
		int chooseWidth = SculptButton.widthFor(chooseLabel);
		int entriesWidth = PlaceSetsPanel.entriesWidth(ProfileManager.library().sets());
		List<SculptList.Row> rows = new ArrayList<>();
		for (SoundSet set : ProfileManager.library().sets()) {
			String id = set.id();
			String name = set.name();
			String entries = PlaceSetsPanel.entriesText(set.entries().size());
			String has = new PickerModel(set).inSet(soundId) ? Component.translatable("soundsculptor.add_to_set.has_sound").getString() : "";
			SculptButton choose = Tooltips.describe(SculptButton.text(chooseLabel, chooseWidth, () -> chooseSet(id)),
					Component.translatable("soundsculptor.add_to_set.choose.tip"));
			rows.add(new SculptList.Row(id, List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> entries, entriesWidth, 0),
					new SculptList.Row.Column(() -> has, 0, 1)), List.of(choose)));
		}
		list.setRows(rows);
	}

	/** Chooses a library set, unless it is gone or full (then the problem shows and the step stays). */
	public void chooseSet(String id) {
		SoundSet set = ProfileManager.library().get(id);
		Message problem = set == null ? Message.of("soundsculptor.sets.error.not_found") : set.canAdd(1);
		if (problem != null) {
			showToast(ProfileManager.component(problem), null);
			return;
		}
		setId = id;
		newSetName = null;
		rebuildWidgets();
	}

	/** New set: asks for a name; the set is made when something is added to it. */
	public void openNew() {
		Message problem = ProfileManager.library().canCreate();
		if (problem != null) {
			showToast(ProfileManager.component(problem), null);
			return;
		}
		minecraft.gui.setScreen(SetNameScreen.ask(this, this, Component.translatable("soundsculptor.sets.new.title"), "",
				name -> LibraryOps.canCreate(ProfileManager.workspace(), name), this::chooseNewSet));
	}

	/** Chooses a new set named {@code name} (made unique when it is added). */
	public void chooseNewSet(String name) {
		newSetName = name.strip();
		setId = null;
	}

	/** Back to the first step. */
	public void back() {
		setId = null;
		newSetName = null;
		rebuildWidgets();
	}

	// ---- Second step: this sound or a group ----

	private void buildTargets(Body body) {
		SoundSet set = targetSet();
		String setName = set == null ? newSetName : set.name();
		body.label(() -> Component.translatable("soundsculptor.add_to_set.adding_to", setName));
		body.label(() -> Component.translatable("soundsculptor.add_to_set.choose_target"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.add_to_set.targets"));
		body.fill(list);
		PickerModel picker = set == null ? null : new PickerModel(set);
		List<SculptList.Row> rows = new ArrayList<>();
		String thisSound = Component.translatable("soundsculptor.add_to_set.this_sound").getString();
		rows.add(targetRow(soundId, thisSound, soundId, picker != null && picker.inSet(soundId), () -> addSound()));
		for (LikeThis.Group group : LikeThis.groups(soundId, KnownSounds.ids())) {
			String pattern = group.pattern();
			String matches = ProfileManager.component(group.matches()).getString();
			rows.add(targetRow(pattern, pattern, matches, picker != null && picker.groupInSet(pattern), () -> addGroup(pattern)));
		}
		list.setRows(rows);
	}

	/** A row with Add, or marked "In this set" with no button (the narrator still reads it). */
	private SculptList.Row targetRow(String key, String title, String detail, boolean inSet, Runnable add) {
		String mark = inSet ? SoundsPanel.IN_SET.getString() : "";
		List<SculptList.Row.Column> columns = List.of(
				new SculptList.Row.Column(() -> title, 0, 0),
				new SculptList.Row.Column(() -> mark, SculptList.Row.Column.widthFor(SoundsPanel.IN_SET), 0),
				new SculptList.Row.Column(() -> detail, 0, 1));
		if (inSet) {
			return new SculptList.Row(key, columns, List.of());
		}
		Component addLabel = Component.translatable("soundsculptor.add_to_set.add");
		SculptButton button = Tooltips.describe(SculptButton.text(addLabel, SculptButton.widthFor(addLabel), add),
				Component.translatable("soundsculptor.add_to_set.add.tip"));
		return new SculptList.Row(key, columns, List.of(button));
	}

	/** The chosen library set, or {@code null} for a new set (or one deleted since). */
	private SoundSet targetSet() {
		return setId == null ? null : ProfileManager.library().get(setId);
	}

	/** This sound: a single-sound entry with no settings. */
	public boolean addSound() {
		return add(picker -> picker.toggleSound(soundId));
	}

	/** A Like this group: a group entry with no settings. */
	public boolean addGroup(String pattern) {
		return add(picker -> picker.toggleGroup(pattern));
	}

	/**
	 * Adds the pick to the chosen set (making the new set first, in the same change), then returns to the Sounds tab,
	 * which shows the toast with Undo. Something already in the set, a full set or a full library changes nothing.
	 */
	private boolean add(java.util.function.Consumer<PickerModel> pick) {
		Workspace before = ProfileManager.workspace();
		Workspace start = before;
		String toastKey = "soundsculptor.sounds.toast.added";
		SoundSet set;
		if (newSetName != null) {
			NewSet created = LibraryOps.create(before, newSetName);
			if (!created.ok()) {
				showToast(ProfileManager.component(created.problem()), null);
				return false;
			}
			start = created.workspace();
			set = start.library().get(created.setId());
			toastKey = "soundsculptor.sounds.toast.added_new";
		} else {
			set = targetSet();
			if (set == null) {
				showToast(ProfileManager.component(Message.of("soundsculptor.sets.error.not_found")), null);
				back();
				return false;
			}
		}
		PickerModel picker = new PickerModel(set);
		pick.accept(picker);
		if (picker.pickedCount() == 0) {
			// Already in the set: show it marked.
			rebuildWidgets();
			return false;
		}
		Message full = picker.canAdd();
		if (full != null) {
			showToast(ProfileManager.component(full), null);
			return false;
		}
		SoundSet added = picker.addTo(set);
		Workspace after = new Workspace(start.profile(), start.library().put(added));
		boolean changed = QuickUndo.applyResult(host, refresh, before, after, toastKey, soundName, added.name());
		minecraft.gui.setScreen(host);
		return changed;
	}
}
