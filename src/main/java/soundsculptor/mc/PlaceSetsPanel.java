package soundsculptor.mc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.NewSet;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.QuickChanges;
import soundsculptor.core.sets.SetUsage;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;

/**
 * The Sound Sets attached to one place, Everywhere or a zone (PRD FR-8.6, FR-8.10), in order. Each line shows the
 * set's name, its entry count and "Also used in N places" when the set is shared, with On/Off, Edit, Move up, Move down
 * and Detach. The place's Quick changes, if any, is pinned first (FR-4.10): it has no move buttons, and Save to library
 * instead, which names it and turns it into a library set in the same slot. Attach set… lists the library sets not
 * attached here, plus New set ({@link AttachSetScreen}).
 *
 * <p>Every change applies at once with a 5-second toast and Undo (FR-12.5). A limit (32 sets in a place, a full
 * library, a bad name) is shown, never thrown.
 */
public final class PlaceSetsPanel {
	private static final Component DETACH = Component.translatable("soundsculptor.place_sets.detach");
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier UP_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/up");
	private static final Identifier DOWN_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/down");

	private final SculptScreen host;
	private final PlaceRef where;
	public SculptButton attachButton;
	public SculptList list;
	/** The usage the rows show, to notice when the background index has more. */
	private SetUsage shownUsage;

	/**
	 * @param host the screen the panel is on; it shows the toasts
	 */
	public PlaceSetsPanel(SculptScreen host, PlaceRef where) {
		this.host = host;
		this.where = where;
	}

	public PlaceRef where() {
		return where;
	}

	/** Adds Attach set…, a hint line and the list of attached sets, which takes the rest of the body. */
	public void build(SculptScreen.Body body) {
		attachButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.place_sets.attach"), this::openAttach),
				Component.translatable("soundsculptor.place_sets.attach.tip"));
		body.row(attachButton);
		body.label(() -> Component.translatable("soundsculptor.place_sets.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.place_sets.list"));
		list.setEmptyText(() -> Component.translatable(ProfileManager.profile().place(where) == null
				? "soundsculptor.place_sets.zone_gone" : "soundsculptor.place_sets.empty"));
		body.fill(list);
		refresh();
	}

	/** Rebuilds the rows from the profile and the usage index. The list keeps the focused row and button. */
	public void refresh() {
		if (list == null) {
			return;
		}
		SetUsage usage = SetUsageIndex.current();
		shownUsage = usage;
		Workspace ws = ProfileManager.workspace();
		PlaceSets place = ws.profile().place(where);
		List<SculptList.Row> rows = new ArrayList<>();
		if (place != null) {
			List<Attachment> attached = place.attached();
			int first = place.quickChanges() != null ? 1 : 0;
			for (int i = 0; i < attached.size(); i++) {
				Attachment a = attached.get(i);
				rows.add(a.setId().equals(SoundSet.QUICK_ID)
						? quickRow(place.quickChanges(), a.enabled())
						: setRow(ws, usage, a, i > first, i < attached.size() - 1));
			}
		}
		list.setRows(rows);
	}

	/** Refreshes the rows if the background usage index has changed since they were built (e.g. its first read finished). */
	public void refreshIfUsageChanged() {
		if (list != null && SetUsageIndex.current() != shownUsage) {
			refresh();
		}
	}

	/** The pinned Quick changes line: On/Off, Edit, Save to library and Detach, never a move button (FR-4.10). */
	private SculptList.Row quickRow(SoundSet quick, boolean enabled) {
		String name = quickName();
		String status = statusText(enabled);
		String details = join(entriesText(quick.entries().size()), Component.translatable("soundsculptor.place_sets.quick.pinned").getString());
		List<SculptButton> buttons = List.of(
				onOffButton(SoundSet.QUICK_ID, enabled),
				SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.place_sets.edit"), () -> edit(SoundSet.QUICK_ID)),
				Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.place_sets.save_to_library"), this::openSaveToLibrary),
						Component.translatable("soundsculptor.place_sets.save_to_library.tip")),
				detachButton(SoundSet.QUICK_ID));
		return new SculptList.Row(SoundSet.QUICK_ID, List.of(
				new SculptList.Row.Column(() -> name, 0, 0),
				new SculptList.Row.Column(() -> status, statusWidth(), 0),
				new SculptList.Row.Column(() -> details, 0, 1)), buttons);
	}

	/** A library set's line; {@code canMoveUp} is false for the top set and for the set just under Quick changes. */
	private SculptList.Row setRow(Workspace ws, SetUsage usage, Attachment a, boolean canMoveUp, boolean canMoveDown) {
		String id = a.setId();
		SoundSet set = ws.library().get(id);
		String name = set == null ? id : set.name();
		String status = statusText(a.enabled());
		int alsoUsed = ws.profile().placesUsing(id).size() - 1 + (usage == null ? 0 : usage.otherPlaceCount(id, ProfileManager.key()));
		String details = join(set == null ? "" : entriesText(set.entries().size()), alsoUsedText(alsoUsed));
		List<SculptButton> buttons = new ArrayList<>();
		buttons.add(onOffButton(id, a.enabled()));
		SculptButton edit = SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.place_sets.edit"), () -> edit(id));
		edit.active = set != null;
		buttons.add(edit);
		SculptButton up = SculptButton.icon(UP_ICON, Component.translatable("soundsculptor.place_sets.move_up"), () -> move(id, -1));
		up.active = canMoveUp;
		buttons.add(up);
		SculptButton down = SculptButton.icon(DOWN_ICON, Component.translatable("soundsculptor.place_sets.move_down"), () -> move(id, 1));
		down.active = canMoveDown;
		buttons.add(down);
		buttons.add(detachButton(id));
		return new SculptList.Row(id, List.of(
				new SculptList.Row.Column(() -> name, 0, 0),
				new SculptList.Row.Column(() -> status, statusWidth(), 0),
				new SculptList.Row.Column(() -> details, 0, 1)), buttons);
	}

	private SculptButton onOffButton(String setId, boolean enabled) {
		return Tooltips.describe(SculptButton.text(SculptToggle.stateText(enabled), onOffWidth(), () -> setEnabled(setId, !enabled)),
				Component.translatable(enabled ? "soundsculptor.place_sets.turn_off" : "soundsculptor.place_sets.turn_on"));
	}

	private SculptButton detachButton(String setId) {
		return Tooltips.describe(SculptButton.text(DETACH, SculptButton.widthFor(DETACH), () -> detach(setId)),
				Component.translatable(SoundSet.QUICK_ID.equals(setId) ? "soundsculptor.place_sets.detach.quick_tip" : "soundsculptor.place_sets.detach.tip"));
	}

	/** "○ Off" for a set switched off here, so the state is text and not color alone; empty when on. */
	private static String statusText(boolean enabled) {
		return enabled ? "" : Component.translatable("soundsculptor.place_sets.status.off").getString();
	}

	private static int statusWidth() {
		return SculptList.Row.Column.widthFor(Component.translatable("soundsculptor.place_sets.status.off"));
	}

	/** An On/Off button's width: the wider of On and Off, so it keeps its size when pressed. */
	static int onOffWidth() {
		return SculptButton.widthFor(SculptToggle.stateText(true), SculptToggle.stateText(false));
	}

	/** "1 entry", "N entries". */
	static String entriesText(int size) {
		return Component.translatable(size == 1 ? "soundsculptor.sets.entries.one" : "soundsculptor.sets.entries", size).getString();
	}

	/** A column that fits {@link #entriesText} for each of {@code sets}: the widest row of the list, measured once per build. */
	static int entriesWidth(Collection<SoundSet> sets) {
		int widest = 0;
		for (SoundSet set : sets) {
			widest = Math.max(widest, SculptList.Row.Column.widthFor(Component.literal(entriesText(set.entries().size()))));
		}
		return widest;
	}

	/** "Also used in N places": other places in this world plus places in other worlds; empty when the set is not shared. */
	static String alsoUsedText(int places) {
		if (places <= 0) {
			return "";
		}
		return Component.translatable(places == 1 ? "soundsculptor.place_sets.also_used.one" : "soundsculptor.place_sets.also_used", places)
				.getString();
	}

	private static String join(String a, String b) {
		return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
	}

	/** The name Quick changes shows under, and the name Save to library suggests. */
	static String quickName() {
		return Component.translatable("soundsculptor.place_sets.quick.name").getString();
	}

	// ---- Actions ----

	/** Attach set…: the library sets not attached here, plus New set; refused at once when the place is full. */
	public void openAttach() {
		Message problem = attachProblem(where, null);
		if (problem != null) {
			showProblem(problem);
			return;
		}
		setScreen(new AttachSetScreen(host, where));
	}

	/** Edit: the set editor (FR-4.3), or the Quick changes editor for the pinned line. */
	public void edit(String setId) {
		if (SoundSet.QUICK_ID.equals(setId)) {
			setScreen(SetEditorScreen.forQuickChanges(host, where));
		} else if (ProfileManager.library().get(setId) != null) {
			setScreen(SetEditorScreen.edit(host, setId));
		}
	}

	/** On/Off (FR-8.6): stops the set applying in this place without detaching it. */
	public boolean setEnabled(String setId, boolean on) {
		return apply(ws -> withPlace(ws, place -> place.setEnabled(setId, on)),
				on ? "soundsculptor.place_sets.toast.on" : "soundsculptor.place_sets.toast.off", nameOf(setId));
	}

	/** Moves a set up or down; Quick changes never moves and nothing passes above it (FR-4.10). */
	public boolean move(String setId, int delta) {
		return apply(ws -> withPlace(ws, place -> place.move(setId, delta)),
				delta < 0 ? "soundsculptor.place_sets.toast.moved_up" : "soundsculptor.place_sets.toast.moved_down", nameOf(setId));
	}

	/** Detach: the set stays in the library. Detaching Quick changes removes its entries (Undo puts them back). */
	public boolean detach(String setId) {
		return apply(ws -> withPlace(ws, place -> place.detach(setId)), "soundsculptor.place_sets.toast.detached", nameOf(setId));
	}

	/** Attaches a library set at the bottom; shows the problem instead if it is already here or the place is full. */
	public boolean attach(String setId) {
		Message problem = attachProblem(where, setId);
		if (problem != null) {
			showProblem(problem);
			return false;
		}
		return apply(ws -> withPlace(ws, place -> place.attach(setId)), "soundsculptor.place_sets.toast.attached", nameOf(setId));
	}

	/**
	 * New set from Attach set…: an empty library set named {@code name} (made unique), attached at the bottom of this
	 * place, in one change.
	 */
	public boolean createAndAttach(String name) {
		Workspace ws = ProfileManager.workspace();
		return applyNewSet(ws, LibraryOps.createAttached(ws, where, name), "soundsculptor.place_sets.toast.created");
	}

	/** Save to library: asks for a name (default "Quick changes"), unless there is a problem to show first. */
	public void openSaveToLibrary() {
		Message problem = QuickChanges.canSave(ProfileManager.workspace(), where);
		if (problem != null) {
			showProblem(problem);
			return;
		}
		setScreen(SetNameScreen.ask(host, host, Component.translatable("soundsculptor.place_sets.save_to_library.title"), quickName(),
				name -> saveProblem(ProfileManager.workspace(), where, name), this::saveToLibrary));
	}

	/**
	 * Turns Quick changes into a library set named {@code name} (made unique), still attached in the same slot; the next
	 * quick action here starts a new Quick changes, pinned above it (FR-4.10).
	 */
	public boolean saveToLibrary(String name) {
		Workspace ws = ProfileManager.workspace();
		Message problem = saveProblem(ws, where, name);
		if (problem != null) {
			showProblem(problem);
			return false;
		}
		return applyNewSet(ws, QuickChanges.saveToLibrary(ws, where, name.strip()), "soundsculptor.place_sets.toast.saved");
	}

	/** Applies an operation that added a library set, worked out once from {@code before}; or shows its problem. */
	private boolean applyNewSet(Workspace before, NewSet result, String toastKey) {
		if (!result.ok()) {
			showProblem(result.problem());
			return false;
		}
		boolean changed = QuickUndo.applyResult(host, this::refreshAfterUndo, before, result.workspace(), toastKey, result.name());
		refresh();
		return changed;
	}

	/**
	 * The problem with attaching {@code setId} here (already attached, or the place holds 32 sets), or with attaching
	 * any set when {@code setId} is null; {@code null} when it can go ahead. A zone that no longer exists has no problem
	 * to show: the attach just changes nothing.
	 */
	static Message attachProblem(PlaceRef where, String setId) {
		PlaceSets place = ProfileManager.profile().place(where);
		if (place == null) {
			return null;
		}
		return setId == null ? place.canAttachAnother() : place.canAttach(setId);
	}

	private static Message saveProblem(Workspace ws, PlaceRef where, String name) {
		Message problem = LibraryOps.canCreate(ws, name);
		return problem != null ? problem : QuickChanges.canSave(ws, where);
	}

	/** The workspace with this place's sets changed; unchanged for a zone that no longer exists. */
	private Workspace withPlace(Workspace ws, UnaryOperator<PlaceSets> change) {
		Profile profile = ws.profile();
		PlaceSets place = profile.place(where);
		return place == null ? ws : new Workspace(profile.withPlace(where, change.apply(place)), ws.library());
	}

	/** Applies the change with the Undo toast, and rebuilds the rows. */
	private boolean apply(UnaryOperator<Workspace> change, String toastKey, String name) {
		boolean changed = QuickUndo.applyWorkspace(host, this::refreshAfterUndo, change, toastKey, name);
		refresh();
		return changed;
	}

	/**
	 * After Undo. The screen may have been rebuilt since the action (the name screen and the set editor return to a new
	 * panel), so the place screen refreshes the panel it shows now.
	 */
	private void refreshAfterUndo() {
		if (host instanceof PlaceSetsScreen screen) {
			screen.refreshLists();
		} else {
			refresh();
		}
	}

	/** The set's name for its toast: Quick changes, the library name, or its ID if it is gone. */
	private static String nameOf(String setId) {
		if (SoundSet.QUICK_ID.equals(setId)) {
			return quickName();
		}
		SoundSet set = ProfileManager.library().get(setId);
		return set == null ? setId : set.name();
	}

	private void showProblem(Message problem) {
		host.showToast(ProfileManager.component(problem), null);
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
