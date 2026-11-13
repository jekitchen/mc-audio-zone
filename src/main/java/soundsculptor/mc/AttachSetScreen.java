package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import soundsculptor.core.Message;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetUsage;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Tooltips;

/**
 * Attach set… (PRD FR-8.6): the library Sound Sets not yet attached to the place, sorted by name, each with its entry
 * count, where it is used and Attach; plus New set, which makes an empty set and attaches it. A set already attached
 * here is never offered, so a place never has the same set twice. Attaching applies at once, with the toast and Undo on
 * the place's screen, and returns there.
 */
public final class AttachSetScreen extends SculptScreen {
	private static final int ENTRIES_WIDTH = 80;
	private static final int ATTACH_WIDTH = 60;

	private final PlaceRef where;
	/** Does the attaching, with its toast on the place's screen. */
	private final PlaceSetsPanel actions;
	public SculptButton newButton;
	public SculptList list;

	public AttachSetScreen(Screen parent, PlaceRef where) {
		super(Component.translatable("soundsculptor.attach_set.title"), parent);
		this.where = where;
		this.actions = new PlaceSetsPanel(parent instanceof SculptScreen place ? place : this, where);
	}

	public PlaceRef where() {
		return where;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("soundsculptor.place_sets.subtitle", SetDeleteDialog.placeName(ProfileManager.profile(), where));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		newButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.attach_set.new"), this::openNew),
				Component.translatable("soundsculptor.attach_set.new.tip"));
		body.row(newButton);
		body.label(() -> Component.translatable("soundsculptor.attach_set.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.attach_set.list"));
		list.setEmptyText(() -> Component.translatable(ProfileManager.library().size() == 0
				? "soundsculptor.attach_set.empty_library" : "soundsculptor.attach_set.empty"));
		body.fill(list);
		refresh();
	}

	/** The library sets this place does not have yet. */
	public void refresh() {
		if (list == null) {
			return;
		}
		Workspace ws = ProfileManager.workspace();
		PlaceSets place = ws.profile().place(where);
		SetUsage usage = SetUsageIndex.current();
		String key = ProfileManager.key();
		List<SculptList.Row> rows = new ArrayList<>();
		for (SoundSet set : ws.library().sets()) {
			if (place == null || place.uses(set.id())) {
				continue;
			}
			String id = set.id();
			String name = set.name();
			String entries = PlaceSetsPanel.entriesText(set.entries().size());
			String used = SetLibraryPanel.usageText(ws.profile().placesUsing(id).size(), usage == null ? 0 : usage.otherCount(id, key), usage != null);
			Component rowName = Component.literal(name);
			SculptButton attach = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.attach_set.attach"), ATTACH_WIDTH,
					() -> attach(id)).narrationContext(() -> rowName), Component.translatable("soundsculptor.attach_set.attach.tip"));
			rows.add(new SculptList.Row(id, List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> entries, ENTRIES_WIDTH, 0),
					new SculptList.Row.Column(() -> used, 0, 1)), List.of(attach)));
		}
		list.setRows(rows);
	}

	/** Attaches the set and returns to the place; a problem (the place holds 32 sets) shows here and changes nothing. */
	public void attach(String setId) {
		Message problem = PlaceSetsPanel.attachProblem(where, setId);
		if (problem != null) {
			showToast(ProfileManager.component(problem), null);
			return;
		}
		actions.attach(setId);
		closeNow();
	}

	/** New set: asks for a name, then makes the set and attaches it here in one change. */
	public void openNew() {
		Workspace ws = ProfileManager.workspace();
		Message problem = ws.library().canCreate();
		if (problem == null) {
			problem = PlaceSetsPanel.attachProblem(where, null);
		}
		if (problem != null) {
			showToast(ProfileManager.component(problem), null);
			return;
		}
		minecraft.gui.setScreen(SetNameScreen.ask(this, parent, Component.translatable("soundsculptor.sets.new.title"), "",
				name -> LibraryOps.canCreateAttached(ProfileManager.workspace(), where, name), actions::createAndAttach));
	}
}
