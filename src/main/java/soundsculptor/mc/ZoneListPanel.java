package soundsculptor.mc;

import soundsculptor.core.Translator;
import soundsculptor.core.lists.SummaryPart;
import soundsculptor.core.lists.ZoneSummary;
import soundsculptor.core.profile.Profile;
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
 * The Zones tab (PRD 6.1): "New zone" and the list of zones. Each row shows the zone's color, name, shape and mode,
 * where it is ("Here", "120 blocks away", "Other world") and its rule count, with On/Off, Edit, Rules, Move up/down
 * and Delete. List order is the last tie-break between zones (FR-9 step 5). On/Off, Move and Delete save at once and
 * show a 5-second toast with Undo (FR-12.5).
 */
public final class ZoneListPanel {
	private static final int WHERE_WIDTH = 90;
	private static final int RULE_COUNT_WIDTH = 45;
	private static final int ON_OFF_WIDTH = 30;
	private static final int RULES_WIDTH = 44;
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
		list.setEmptyText(() -> Component.translatable("soundsculptor.zones.empty"));
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
		List<SculptList.Row> rows = new ArrayList<>(zones.size());
		for (int i = 0; i < zones.size(); i++) {
			Zone zone = zones.get(i);
			String name = zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
			List<SummaryPart> parts = ZoneSummary.parts(zone, key, dimension, player, profile.settings().defaultEdge());
			String shape = joinParts(parts.subList(0, parts.size() - 1));
			String where = parts.get(parts.size() - 1).text(TRANSLATOR);
			int ruleCount = zone.rules().size();
			String rules = Component.translatable(ruleCount == 1 ? "soundsculptor.zones.rule_count.one" : "soundsculptor.zones.rule_count", ruleCount).getString();
			String state = zone.enabled() ? "" : Component.translatable("soundsculptor.summary.rule.disabled").getString();
			// Name and where it is (with "Off" for a zone that is off) on the first line; shape and rule count below.
			String place = zone.enabled() ? where : state + ", " + where;
			List<SculptList.Row.Column> columns = List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> place, WHERE_WIDTH, 0),
					new SculptList.Row.Column(() -> shape, 0, 1),
					new SculptList.Row.Column(() -> rules, RULE_COUNT_WIDTH, 1));
			List<SculptButton> buttons = new ArrayList<>();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(zone.enabled()), ON_OFF_WIDTH, () -> setEnabled(zone.id(), !zone.enabled()))
					.narrationContext(() -> Component.translatable("soundsculptor.zones.enabled")),
					Component.translatable(zone.enabled() ? "soundsculptor.zones.turn_off" : "soundsculptor.zones.turn_on")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.zones.edit"), () -> setScreen(ZoneEditorScreen.edit(host, zone))));
			buttons.add(Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zones.rules"), RULES_WIDTH,
					() -> setScreen(new ZoneRulesScreen(host, zone.id()))), Component.translatable("soundsculptor.zones.rules.tip")));
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
