package audiozone.mc;

import audiozone.core.Translator;
import audiozone.core.lists.SummaryPart;
import audiozone.core.lists.ZoneSummary;
import audiozone.core.profile.Profile;
import audiozone.core.zones.Point;
import audiozone.core.zones.Zone;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzDialog;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzToggle;
import audiozone.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The Zones tab (PRD 6.1): "New zone" and the list of zones. Each row shows the zone's color, name, shape and mode,
 * where it is ("Here", "120 blocks away", "Other world") and its rule count, with On/Off, Edit, Rules, Move up/down
 * and Delete. List order is the last tie-break between zones (FR-9 step 5).
 */
public final class ZoneListPanel {
	private static final int WHERE_WIDTH = 90;
	private static final int RULE_COUNT_WIDTH = 45;
	private static final int ON_OFF_WIDTH = 30;
	private static final int RULES_WIDTH = 44;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/edit");
	private static final Identifier UP_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/up");
	private static final Identifier DOWN_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/down");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/delete");
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();

	private final AzScreen host;
	public AzButton newZoneButton;
	public AzList list;

	public ZoneListPanel(AzScreen host) {
		this.host = host;
	}

	public void build(AzScreen.Body body) {
		newZoneButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.zones.new"),
				() -> setScreen(ZoneEditorScreen.create(host))), Component.translatable("audiozone.zones.new.tip"));
		body.row(newZoneButton);
		body.label(() -> Component.translatable("audiozone.zones.hint"));
		list = new AzList(body.width(), 100, Component.translatable("audiozone.zones.list"));
		list.setEmptyText(() -> Component.translatable("audiozone.zones.empty"));
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
		List<AzList.Row> rows = new ArrayList<>(zones.size());
		for (int i = 0; i < zones.size(); i++) {
			Zone zone = zones.get(i);
			String name = zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
			List<SummaryPart> parts = ZoneSummary.parts(zone, key, dimension, player, profile.settings().defaultEdge());
			String shape = joinParts(parts.subList(0, parts.size() - 1));
			String where = parts.get(parts.size() - 1).text(TRANSLATOR);
			int ruleCount = zone.rules().size();
			String rules = Component.translatable(ruleCount == 1 ? "audiozone.zones.rule_count.one" : "audiozone.zones.rule_count", ruleCount).getString();
			String state = zone.enabled() ? "" : Component.translatable("audiozone.summary.rule.disabled").getString();
			// Name and where it is (with "Off" for a zone that is off) on the first line; shape and rule count below.
			String place = zone.enabled() ? where : state + ", " + where;
			List<AzList.Row.Column> columns = List.of(
					new AzList.Row.Column(() -> name, 0, 0),
					new AzList.Row.Column(() -> place, WHERE_WIDTH, 0),
					new AzList.Row.Column(() -> shape, 0, 1),
					new AzList.Row.Column(() -> rules, RULE_COUNT_WIDTH, 1));
			List<AzButton> buttons = new ArrayList<>();
			buttons.add(Tooltips.describe(AzButton.text(AzToggle.stateText(zone.enabled()), ON_OFF_WIDTH, () -> setEnabled(zone.id(), !zone.enabled()))
					.narrationContext(() -> Component.translatable("audiozone.zones.enabled")),
					Component.translatable(zone.enabled() ? "audiozone.zones.turn_off" : "audiozone.zones.turn_on")));
			buttons.add(AzButton.icon(EDIT_ICON, Component.translatable("audiozone.zones.edit"), () -> setScreen(ZoneEditorScreen.edit(host, zone))));
			buttons.add(Tooltips.describe(AzButton.text(Component.translatable("audiozone.zones.rules"), RULES_WIDTH,
					() -> setScreen(new ZoneRulesScreen(host, zone.id()))), Component.translatable("audiozone.zones.rules.tip")));
			AzButton up = AzButton.icon(UP_ICON, Component.translatable("audiozone.zones.move_up"), () -> move(zone.id(), -1));
			up.active = i > 0;
			buttons.add(up);
			AzButton down = AzButton.icon(DOWN_ICON, Component.translatable("audiozone.zones.move_down"), () -> move(zone.id(), 1));
			down.active = i < zones.size() - 1;
			buttons.add(down);
			buttons.add(AzButton.icon(DELETE_ICON, Component.translatable("audiozone.zones.delete"), () -> confirmDelete(zone)));
			AzList.Row row = new AzList.Row(zone.id(), columns, buttons);
			Integer rgb = profile.colorOf(zone.id());
			if (rgb != null) {
				row.swatch(rgb);
			}
			rows.add(row);
		}
		list.setRows(rows);
	}

	private static String joinParts(List<SummaryPart> parts) {
		return audiozone.core.lists.RuleSummary.join(parts, TRANSLATOR);
	}

	public void setEnabled(String zoneId, boolean enabled) {
		ProfileManager.update(profile -> {
			Zone zone = profile.zone(zoneId);
			return zone == null ? profile : profile.putZone(zone.withEnabled(enabled));
		});
		refresh();
	}

	public void move(String zoneId, int delta) {
		ProfileManager.update(profile -> profile.moveZone(zoneId, delta));
		refresh();
	}

	public void confirmDelete(Zone zone) {
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.zones.delete.title"),
				Component.translatable("audiozone.zones.delete.message", zone.name()),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.zones.delete.confirm"), () -> delete(zone.id())),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
				1, 1));
	}

	public void delete(String zoneId) {
		ProfileManager.update(profile -> profile.removeZone(zoneId));
		refresh();
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
