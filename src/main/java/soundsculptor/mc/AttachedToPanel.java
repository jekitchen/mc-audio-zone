package soundsculptor.mc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetUsage;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * The set editor's Attached to panel (PRD FR-4.9): each place in this world or server that uses the set (Everywhere,
 * then zones in list order), with Detach on each line; Attach to… choosing among this profile's places that don't have
 * it; and "Also used in N other worlds or servers", with their names in a tooltip. Until the other profiles have been
 * read (the usage index is built in the background), it says they are still being counted.
 *
 * <p>Attach and Detach are staged: nothing changes until the set editor's Save, which applies them with the set's own
 * changes ({@link #apply}). They count as unsaved changes, and Cancel drops them. Attach respects the place's limit
 * (FR-8.6: 32 sets) and is never offered for a place that already has the set.
 */
public final class AttachedToPanel {
	private static final int DETACH_WIDTH = 60;
	private static final int ATTACH_WIDTH = 60;
	private static final int MENU_WIDTH = 240;

	private final SculptScreen host;
	private final String setId;
	/** Places whose attachment changes on Save: {@code true} attaches the set there, {@code false} detaches it. */
	private final Map<PlaceRef, Boolean> staged = new LinkedHashMap<>();
	/** The usage the panel shows, to notice when the background index has more. */
	private SetUsage shownUsage;

	public SculptButton attachButton;
	/** "Also used in N other worlds or servers"; its tooltip names them. */
	public SculptButton othersButton;
	public SculptList list;
	/** The places offered by Attach to…, while that menu is open. */
	public SculptList attachMenu;

	public AttachedToPanel(SculptScreen host, String setId) {
		this.host = host;
		this.setId = setId;
	}

	/** The height the panel needs besides its list: the heading and the button row. */
	static int fixedHeight(Font font) {
		return font.lineHeight + Theme.GAP + Theme.WIDGET_HEIGHT + Theme.GAP;
	}

	/** Adds the heading, Attach to… with the other-worlds line, and the list of places, which takes the rest of the body. */
	public void build(SculptScreen.Body body) {
		body.label(() -> Component.translatable("soundsculptor.attached.heading"));
		attachButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.attached.attach"), this::openAttachMenu),
				Component.translatable("soundsculptor.attached.attach.tip"));
		othersButton = SculptButton.text(Component.empty(), Math.max(0, body.width() - attachButton.getWidth() - Theme.GAP), this::showOthers);
		body.row(attachButton, othersButton);
		list = new SculptList(body.width(), SculptList.ROW_HEIGHT, Component.translatable("soundsculptor.attached.list"));
		list.setEmptyText(() -> Component.translatable("soundsculptor.attached.empty"));
		body.fill(list);
		refresh();
	}

	/** Rebuilds the rows and the other-worlds line. The list keeps the focused row and button. */
	public void refresh() {
		if (list == null) {
			return;
		}
		Profile profile = ProfileManager.profile();
		List<SculptList.Row> rows = new ArrayList<>();
		for (PlaceRef where : places()) {
			String name = SetDeleteDialog.placeName(profile, where);
			String note = Boolean.TRUE.equals(staged.get(where)) ? Component.translatable("soundsculptor.attached.on_save").getString() : "";
			SculptButton detach = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.attached.detach"), DETACH_WIDTH,
					() -> detach(where)), Component.translatable("soundsculptor.attached.detach.tip"));
			rows.add(new SculptList.Row(key(where), List.of(new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> note, 0, 1)), List.of(detach)));
		}
		list.setRows(rows);
		attachButton.active = !attachChoices().isEmpty();

		SetUsage usage = SetUsageIndex.current();
		shownUsage = usage;
		Component text;
		Component tip;
		if (usage == null) {
			text = Component.translatable("soundsculptor.attached.others.counting");
			tip = text;
		} else {
			List<String> names = otherWorldNames(usage);
			text = Component.translatable(names.isEmpty() ? "soundsculptor.attached.others.none"
					: names.size() == 1 ? "soundsculptor.attached.others.one" : "soundsculptor.attached.others", names.size());
			tip = names.isEmpty() ? text : Component.translatable("soundsculptor.attached.others.names", String.join(", ", names));
		}
		othersButton.setMessage(text);
		othersButton.setTooltip(Tooltip.create(tip));
	}

	/** Refreshes if the background usage index has changed since the panel was built (e.g. its first read finished). */
	public void refreshIfUsageChanged() {
		if (list != null && SetUsageIndex.current() != shownUsage) {
			refresh();
		}
	}

	private List<String> otherWorldNames(SetUsage usage) {
		return usage.otherProfileNames(setId, ProfileManager.key());
	}

	/** The other worlds' names, in a toast the narrator reads (the tooltip has them too). */
	public void showOthers() {
		SetUsage usage = SetUsageIndex.current();
		List<String> names = usage == null ? List.of() : otherWorldNames(usage);
		host.showToast(names.isEmpty() ? othersButton.getMessage()
				: Component.translatable("soundsculptor.attached.others.names", String.join(", ", names)), null);
	}

	// ---- Staged Attach and Detach ----

	/** Whether the set will be attached at the place once the editor saves. */
	public boolean uses(PlaceRef where) {
		Boolean change = staged.get(where);
		if (change != null) {
			return change;
		}
		PlaceSets sets = ProfileManager.profile().place(where);
		return sets != null && sets.uses(setId);
	}

	/** The places that will use the set once the editor saves: Everywhere first, then zones in list order. */
	public List<PlaceRef> places() {
		return allPlaces().stream().filter(this::uses).toList();
	}

	/** The places Attach to… offers: this profile's places that will not have the set. */
	public List<PlaceRef> attachChoices() {
		return allPlaces().stream().filter(where -> !uses(where)).toList();
	}

	private static List<PlaceRef> allPlaces() {
		List<PlaceRef> places = new ArrayList<>();
		places.add(PlaceRef.EVERYWHERE);
		for (Zone zone : ProfileManager.profile().zones()) {
			places.add(PlaceRef.zone(zone.id()));
		}
		return places;
	}

	/** Attaches the set at the place on Save, unless the place is full (a toast says so). */
	public void attach(PlaceRef where) {
		PlaceSets sets = ProfileManager.profile().place(where);
		if (sets == null) {
			return;
		}
		if (sets.uses(setId)) {
			staged.remove(where);
		} else {
			Message problem = sets.canAttach(setId);
			if (problem != null) {
				host.showToast(ProfileManager.component(problem), null);
				return;
			}
			staged.put(where, true);
		}
		refresh();
	}

	/** Detaches the set from the place on Save. */
	public void detach(PlaceRef where) {
		PlaceSets sets = ProfileManager.profile().place(where);
		if (sets == null) {
			return;
		}
		if (sets.uses(setId)) {
			staged.put(where, false);
		} else {
			staged.remove(where);
		}
		refresh();
	}

	/** Whether Attach or Detach changed anything since the editor opened. */
	public boolean hasChanges() {
		return !staged.isEmpty();
	}

	/** The profile with the staged Attach and Detach applied; places that no longer exist are skipped. */
	public Profile apply(Profile profile) {
		Profile out = profile;
		for (Map.Entry<PlaceRef, Boolean> change : staged.entrySet()) {
			PlaceSets sets = out.place(change.getKey());
			if (sets != null) {
				out = out.withPlace(change.getKey(), change.getValue() ? sets.attach(setId) : sets.detach(setId));
			}
		}
		return out;
	}

	// ---- Attach to… ----

	/** Attach to…: a list of the places without the set, each with Attach, above the screen; Esc closes it. */
	public void openAttachMenu() {
		List<PlaceRef> choices = attachChoices();
		if (choices.isEmpty()) {
			host.showToast(Component.translatable("soundsculptor.attached.everywhere_already"), null);
			return;
		}
		Profile profile = ProfileManager.profile();
		int width = Math.min(MENU_WIDTH, host.width - 2 * Theme.PADDING);
		int room = host.height - SculptScreen.HEADER_HEIGHT - SculptScreen.FOOTER_HEIGHT - 2 * Theme.PADDING;
		int height = Math.max(SculptList.ROW_HEIGHT, Math.min(choices.size() * SculptList.ROW_HEIGHT + 4, room));
		int x = Math.max(Theme.PADDING, Math.min(attachButton.getX(), host.width - width - Theme.PADDING));
		int y = attachButton.getBottom() + 2;
		if (y + height > host.height - SculptScreen.FOOTER_HEIGHT) {
			y = Math.max(SculptScreen.HEADER_HEIGHT + Theme.PADDING, attachButton.getY() - height - 2);
		}
		attachMenu = new SculptList(width, height, Component.translatable("soundsculptor.attached.menu"));
		attachMenu.setPosition(x, y);
		attachMenu.setWidth(width);
		attachMenu.setHeight(height);
		List<SculptList.Row> rows = new ArrayList<>();
		for (PlaceRef where : choices) {
			String name = SetDeleteDialog.placeName(profile, where);
			SculptButton attach = SculptButton.text(Component.translatable("soundsculptor.attached.menu.attach"), ATTACH_WIDTH, () -> {
				host.closeOverlay();
				attach(where);
			});
			rows.add(new SculptList.Row(key(where), List.of(new SculptList.Row.Column(() -> name, 0)), List.of(attach)));
		}
		attachMenu.setRows(rows);
		host.openOverlay(x, y, width, height, List.of(attachMenu), attachMenu, () -> {
			attachMenu = null;
			host.setFocused(attachButton);
		});
		attachMenu.focusRow(attachMenu.children().get(0));
	}

	private static String key(PlaceRef where) {
		return where.isEverywhere() ? "" : where.zoneId();
	}
}
