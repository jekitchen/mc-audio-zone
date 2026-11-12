package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import soundsculptor.core.Message;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.SetUsage;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Tooltips;

/**
 * The Sound Sets tab (PRD FR-4.7): every set in the library, sorted by name, with its entry count and where it is used
 * ("3 places in this world, 2 other worlds", or "Not attached"). Actions: New set, and on each row Edit, Duplicate,
 * Rename and Delete.
 *
 * <p>New, Duplicate, Rename and Delete each change the library at once and show a 5-second toast with Undo (FR-12.5);
 * Delete asks first ({@link SetDeleteDialog}, FR-4.11). A limit (library full, a bad name) is shown, never thrown.
 */
public final class SetLibraryPanel {
	private static final int ENTRIES_WIDTH = 80;
	private static final int DUPLICATE_WIDTH = 60;
	private static final int RENAME_WIDTH = 50;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/delete");

	private final SculptScreen host;
	public SculptList list;
	public SculptButton newButton;
	/** The usage the rows show, to notice when the background index has more. */
	private SetUsage shownUsage;

	public SetLibraryPanel(SculptScreen host) {
		this.host = host;
	}

	public void build(SculptScreen.Body body) {
		newButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.sets.new"), this::openNew),
				Component.translatable("soundsculptor.sets.new.tip"));
		body.row(newButton);
		body.label(() -> Component.translatable("soundsculptor.sets.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.sets.list"));
		list.setEmptyText(() -> Component.translatable("soundsculptor.sets.empty"));
		body.fill(list);
		refresh();
	}

	/** Rebuilds the rows from the library and the usage index. The list keeps the focused row and button. */
	public void refresh() {
		if (list == null) {
			return;
		}
		SetUsage usage = SetUsageIndex.current();
		shownUsage = usage;
		Workspace ws = ProfileManager.workspace();
		String key = ProfileManager.key();
		List<SculptList.Row> rows = new ArrayList<>();
		for (SoundSet set : ws.library().sets()) {
			rows.add(row(set, ws.profile().placesUsing(set.id()).size(), usage == null ? 0 : usage.otherCount(set.id(), key), usage != null));
		}
		list.setRows(rows);
	}

	/** Refreshes the rows if the background usage index has changed since they were built (e.g. its first read finished). */
	public void refreshIfUsageChanged() {
		if (list != null && SetUsageIndex.current() != shownUsage) {
			refresh();
		}
	}

	private SculptList.Row row(SoundSet set, int here, int others, boolean othersCounted) {
		String name = set.name();
		int size = set.entries().size();
		String entries = Component.translatable(size == 1 ? "soundsculptor.sets.entries.one" : "soundsculptor.sets.entries", size).getString();
		String usage = usageText(here, others, othersCounted);
		Component rowName = Component.literal(name);
		String id = set.id();
		List<SculptButton> buttons = List.of(
				SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.sets.edit"), () -> openEditor(id))
						.narrationContext(() -> rowName),
				Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.sets.duplicate"), DUPLICATE_WIDTH, () -> duplicate(id))
						.narrationContext(() -> rowName), Component.translatable("soundsculptor.sets.duplicate.tip")),
				Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.sets.rename"), RENAME_WIDTH, () -> openRename(id))
						.narrationContext(() -> rowName), Component.translatable("soundsculptor.sets.rename.tip")),
				SculptButton.icon(DELETE_ICON, Component.translatable("soundsculptor.sets.delete"), () -> confirmDelete(id))
						.narrationContext(() -> rowName));
		return new SculptList.Row(id, List.of(
				new SculptList.Row.Column(() -> name, 0, 0),
				new SculptList.Row.Column(() -> entries, ENTRIES_WIDTH, 0),
				new SculptList.Row.Column(() -> usage, 0, 1)), buttons);
	}

	/**
	 * Where a set is used: "3 places in this world, 2 other worlds", "1 place in this world", "2 other worlds" or "Not
	 * attached". Until the other profiles have been read ({@code othersCounted} false), only this world's count shows.
	 */
	public static String usageText(int here, int others, boolean othersCounted) {
		String hereText = Component.translatable(here == 1 ? "soundsculptor.sets.usage.here.one" : "soundsculptor.sets.usage.here", here).getString();
		if (!othersCounted) {
			return here == 0 ? Component.translatable("soundsculptor.sets.usage.none_here").getString() : hereText;
		}
		String otherText = Component.translatable(others == 1 ? "soundsculptor.sets.usage.other.one" : "soundsculptor.sets.usage.other", others).getString();
		if (here > 0 && others > 0) {
			return Component.translatable("soundsculptor.sets.usage.both", hereText, otherText).getString();
		}
		if (here > 0) {
			return hereText;
		}
		return others > 0 ? otherText : Component.translatable("soundsculptor.sets.usage.none").getString();
	}

	// ---- Actions ----

	/** Edit: opens the set editor (FR-4.3). */
	public void openEditor(String setId) {
		if (ProfileManager.library().get(setId) != null) {
			setScreen(SetEditorScreen.edit(host, setId));
		}
	}

	/** New set: asks for a name, unless the library is full. */
	public void openNew() {
		Message full = ProfileManager.library().canCreate();
		if (full != null) {
			showProblem(full);
			return;
		}
		setScreen(SetNameScreen.create(host, this));
	}

	public void openRename(String setId) {
		if (ProfileManager.library().get(setId) != null) {
			setScreen(SetNameScreen.rename(host, this, setId));
		}
	}

	/** Adds an empty set named {@code name} (made unique), attached nowhere; returns whether it was added. */
	public boolean create(String name) {
		Workspace ws = ProfileManager.workspace();
		Message problem = LibraryOps.canCreate(ws, name);
		if (problem != null) {
			showProblem(problem);
			return false;
		}
		String made = addedName(ws, LibraryOps.create(ws, name));
		return apply(current -> LibraryOps.create(current, name), "soundsculptor.sets.toast.created", made);
	}

	/** An independent copy named "name (copy)", attached nowhere (FR-4.7). */
	public boolean duplicate(String setId) {
		Workspace ws = ProfileManager.workspace();
		if (ws.library().get(setId) == null) {
			return false;
		}
		Message problem = LibraryOps.canDuplicate(ws, setId);
		if (problem != null) {
			showProblem(problem);
			return false;
		}
		String made = addedName(ws, LibraryOps.duplicate(ws, setId));
		return apply(current -> LibraryOps.duplicate(current, setId), "soundsculptor.sets.toast.duplicated", made);
	}

	/** Renames the set; a name another set has gets " (2)" and so on. The ID stays. */
	public boolean rename(String setId, String name) {
		Workspace ws = ProfileManager.workspace();
		if (ws.library().get(setId) == null) {
			return false;
		}
		Message problem = LibraryOps.canRename(ws, setId, name);
		if (problem != null) {
			showProblem(problem);
			return false;
		}
		SoundSet renamed = LibraryOps.rename(ws, setId, name).library().get(setId);
		return apply(current -> LibraryOps.rename(current, setId, name), "soundsculptor.sets.toast.renamed", renamed.name());
	}

	/** Opens the delete warning (FR-4.11). */
	public void confirmDelete(String setId) {
		Workspace ws = ProfileManager.workspace();
		if (ws.library().get(setId) == null) {
			return;
		}
		SetUsage usage = SetUsageIndex.current();
		LibraryOps.DeletePlan plan = LibraryOps.planDelete(ws, usage == null ? SetUsage.EMPTY : usage, ProfileManager.key(), setId);
		setScreen(SetDeleteDialog.create(host, ws.profile(), plan, usage != null, () -> delete(setId)));
	}

	/** Removes the set from the library and from every place in this profile; other profiles drop it when next joined. */
	public boolean delete(String setId) {
		SoundSet set = ProfileManager.library().get(setId);
		if (set == null) {
			return false;
		}
		return apply(current -> LibraryOps.delete(current, setId), "soundsculptor.sets.toast.deleted", set.name());
	}

	/** Applies the change with the Undo toast, and rebuilds the rows. */
	private boolean apply(UnaryOperator<Workspace> change, String toastKey, String name) {
		boolean changed = QuickUndo.applyWorkspace(host, this::refreshAfterUndo, change, toastKey, name);
		refresh();
		return changed;
	}

	/**
	 * After Undo. The tab may have been rebuilt since the action (the delete dialog and the name screen return to a new
	 * panel), so the main screen refreshes the panel it shows now.
	 */
	private void refreshAfterUndo() {
		if (host instanceof SoundSculptorScreen main) {
			main.refreshLists();
		} else {
			refresh();
		}
	}

	/** The name of the set {@code after} has and {@code before} lacks: what a create or duplicate is about to add. */
	private static String addedName(Workspace before, Workspace after) {
		for (SoundSet set : after.library().sets()) {
			if (!before.library().contains(set.id())) {
				return set.name();
			}
		}
		return "";
	}

	private void showProblem(Message problem) {
		host.showToast(ProfileManager.component(problem), null);
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
