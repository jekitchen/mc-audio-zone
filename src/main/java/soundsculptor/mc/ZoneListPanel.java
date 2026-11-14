package soundsculptor.mc;

import soundsculptor.core.Translator;
import soundsculptor.core.lists.SummaryPart;
import soundsculptor.core.lists.ZoneSummary;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The Zones tab (PRD 6.1): "New zone" and the list of places. The first row is always Everywhere (FR-8.10): it has no
 * outline, color, shape or position and cannot be edited, moved or deleted, so its only control is Sound Sets. Then
 * each zone's row shows its color, name, shape and mode, where it is ("Here", "120 blocks away", "Other world") and
 * its Sound Set count, with On/Off, Edit, Sound Sets, Move up/down and Delete. Sound Sets opens the place's attached
 * sets (FR-8.6). List order is the last tie-break between zones (FR-9 step 5). On/Off, Move and Delete save at once and
 * show a 5-second toast with Undo (FR-12.5).
 */
public final class ZoneListPanel {
	private static final int SETS_PADDING = 12;
	/** The key of the "No zones yet" row; not a zone ID, so it can never clash with one. */
	public static final Object NO_ZONES_ROW = new Object();
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier UP_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/up");
	private static final Identifier DOWN_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/down");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/delete");
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();

	private final SculptScreen host;
	public SculptButton newZoneButton;
	public SculptList list;

	public ZoneListPanel(SculptScreen host) {
		this.host = host;
	}

	public void build(SculptScreen.Body body) {
		newZoneButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zones.new"),
				() -> setScreen(ZoneEditorScreen.create(host))), Component.translatable("soundsculptor.zones.new.tip"));
		body.row(newZoneButton);
		body.label(() -> Component.translatable("soundsculptor.zones.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.zones.list"));
		body.fill(list);
		refresh();
	}

	public void refresh() {
		if (list == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Profile profile = ProfileManager.profile();
		String key = ProfileManager.key() == null ? "" : ProfileManager.key();
		String dimension = WorldSession.dimension(minecraft);
		Vec3 feet = minecraft.player == null ? Vec3.ZERO : minecraft.player.position();
		Point player = new Point(feet.x, feet.y, feet.z);
		List<Zone> zones = profile.zones();
		List<SculptList.Row> rows = new ArrayList<>(zones.size() + 1);
		// The set count column is as wide as the widest count in this list, so the shape beside it keeps its room.
		int setsWidth = SculptList.Row.Column.widthFor(Component.literal(setCount(profile.everywhere())));
		for (Zone zone : zones) {
			setsWidth = Math.max(setsWidth, SculptList.Row.Column.widthFor(Component.literal(setCount(zone.sets()))));
		}
		rows.add(everywhereRow(profile, setsWidth));
		if (zones.isEmpty()) {
			rows.add(noZonesRow());
		}
		// Name and where it is (with "Off" for a zone that is off) on the first line; shape and Sound Set count below.
		// The where column is as wide as the widest, so the rows line up.
		List<String> places = new ArrayList<>(zones.size());
		List<List<SummaryPart>> summaries = new ArrayList<>(zones.size());
		int placeWidth = 0;
		for (Zone zone : zones) {
			List<SummaryPart> parts = ZoneSummary.parts(zone, key, dimension, player, profile.settings().defaultEdge());
			String where = parts.get(parts.size() - 1).text(TRANSLATOR);
			String place = zone.enabled() ? where
					: Component.translatable("soundsculptor.summary.rule.disabled").getString() + ", " + where;
			summaries.add(parts);
			places.add(place);
			placeWidth = Math.max(placeWidth, SculptList.Row.Column.widthFor(Component.literal(place)));
		}
		int onOffWidth = PlaceSetsPanel.onOffWidth();
		for (int i = 0; i < zones.size(); i++) {
			Zone zone = zones.get(i);
			String name = zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
			List<SummaryPart> parts = summaries.get(i);
			String shape = joinParts(parts.subList(0, parts.size() - 1));
			String place = places.get(i);
			String sets = setCount(zone.sets());
			List<SculptList.Row.Column> columns = List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> place, placeWidth, 0),
					new SculptList.Row.Column(() -> shape, 0, 1),
					new SculptList.Row.Column(() -> sets, setsWidth, 1));
			List<SculptButton> buttons = new ArrayList<>();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(zone.enabled()), onOffWidth, () -> setEnabled(zone.id(), !zone.enabled())),
					Component.translatable(zone.enabled() ? "soundsculptor.zones.turn_off" : "soundsculptor.zones.turn_on")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.zones.edit"), () -> setScreen(ZoneEditorScreen.edit(host, zone))));
			buttons.add(setsButton(PlaceRef.zone(zone.id())));
			SculptButton up = SculptButton.icon(UP_ICON, Component.translatable("soundsculptor.zones.move_up"), () -> move(zone.id(), -1));
			up.active = i > 0;
			buttons.add(up);
			SculptButton down = SculptButton.icon(DOWN_ICON, Component.translatable("soundsculptor.zones.move_down"), () -> move(zone.id(), 1));
			down.active = i < zones.size() - 1;
			buttons.add(down);
			buttons.add(SculptButton.icon(DELETE_ICON, Component.translatable("soundsculptor.zones.delete"), () -> confirmDelete(zone)));
			SculptList.Row row = new SculptList.Row(zone.id(), columns, buttons);
			Integer rgb = profile.colorOf(zone.id());
			if (rgb != null) {
				row.swatch(rgb);
			}
			rows.add(row);
		}
		list.setRows(rows);
	}

	/** Everywhere (FR-8.10): its name, what it applies to, and its Sound Set count; its only button opens its sets. */
	private SculptList.Row everywhereRow(Profile profile, int setsWidth) {
		String nameText = Component.translatable("soundsculptor.profile.place.everywhere").getString();
		String scope = Component.translatable("soundsculptor.zones.everywhere.scope").getString();
		String sets = setCount(profile.everywhere());
		return new SculptList.Row(PlaceRef.EVERYWHERE, List.of(
				new SculptList.Row.Column(() -> nameText, 0, 0),
				new SculptList.Row.Column(() -> scope, 0, 1),
				new SculptList.Row.Column(() -> sets, setsWidth, 1)), List.of(setsButton(PlaceRef.EVERYWHERE)));
	}

	/**
	 * Under Everywhere while the profile has no zones: "No zones yet. Use New zone…". The list is never empty (Everywhere
	 * is always there), so this hint is a row of its own; a row without buttons can still take focus, so it is read out.
	 */
	private static SculptList.Row noZonesRow() {
		String text = Component.translatable("soundsculptor.zones.empty").getString();
		return new SculptList.Row(NO_ZONES_ROW, List.of(new SculptList.Row.Column(() -> text, 0, 0)), List.of());
	}

	/** "Sound Sets": the place's attached sets (FR-8.6). */
	private SculptButton setsButton(PlaceRef where) {
		Component label = Component.translatable("soundsculptor.zones.sets");
		int width = Minecraft.getInstance().font.width(label) + SETS_PADDING;
		return Tooltips.describe(SculptButton.text(label, width, () -> openSets(where)),
				Component.translatable(where.isEverywhere() ? "soundsculptor.zones.sets.everywhere_tip" : "soundsculptor.zones.sets.tip"));
	}

	/** Opens the Sound Sets attached to Everywhere or a zone. */
	public void openSets(PlaceRef where) {
		setScreen(new PlaceSetsScreen(host, where));
	}

	/** "1 Sound Set", "3 Sound Sets": the place's library sets plus its Quick changes. */
	private static String setCount(PlaceSets sets) {
		int count = sets.librarySetCount() + (sets.quickChanges() != null ? 1 : 0);
		return Component.translatable(count == 1 ? "soundsculptor.zones.set_count.one" : "soundsculptor.zones.set_count", count).getString();
	}

	private static String joinParts(List<SummaryPart> parts) {
		return soundsculptor.core.lists.EntrySummary.join(parts, TRANSLATOR);
	}

	public void setEnabled(String zoneId, boolean enabled) {
		QuickUndo.apply(host, this::refresh, profile -> {
			Zone zone = profile.zone(zoneId);
			return zone == null ? profile : profile.putZone(zone.withEnabled(enabled));
		}, enabled ? "soundsculptor.zones.toast.on" : "soundsculptor.zones.toast.off", nameOf(zoneId));
		refresh();
	}

	public void move(String zoneId, int delta) {
		QuickUndo.apply(host, this::refresh, profile -> profile.moveZone(zoneId, delta),
				delta < 0 ? "soundsculptor.zones.toast.moved_up" : "soundsculptor.zones.toast.moved_down", nameOf(zoneId));
		refresh();
	}

	public void confirmDelete(Zone zone) {
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.zones.delete.title"),
				Component.translatable("soundsculptor.zones.delete.message", zone.name()),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.zones.delete.confirm"), () -> delete(zone.id())),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	public void delete(String zoneId) {
		String name = nameOf(zoneId);
		QuickUndo.apply(host, this::refresh, profile -> profile.removeZone(zoneId), "soundsculptor.zones.toast.deleted", name);
		refresh();
	}

	/** The zone's name for its toast, or its ID if it has none or is gone. */
	private static String nameOf(String zoneId) {
		Zone zone = ProfileManager.profile().zone(zoneId);
		return zone == null || zone.name() == null || zone.name().isBlank() ? zoneId : zone.name();
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
