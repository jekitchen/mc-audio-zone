package soundsculptor.mc;

import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.sets.SetEntry;
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

/**
 * A rule list with Add rule (PRD 6.1 Rules): each row shows the pattern, its summary ("Muted", "Volume 50%") and match
 * count, with On/Off, Edit, Move up/down and Delete. Used for the global list on the Rules tab and for a zone's own
 * list (FR-8.6). On/Off, Move and Delete save at once and show a 5-second toast with Undo (FR-12.5).
 */
public final class RuleListPanel {
	private static final int STATUS_WIDTH = 110;
	private static final int ON_OFF_WIDTH = 30;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");
	private static final Identifier UP_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/up");
	private static final Identifier DOWN_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/down");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/delete");

	private final SculptScreen host;
	private final RuleTarget target;
	public SculptButton addButton;
	/** "Add existing rule", on a zone's list only. */
	public SculptButton addExistingButton;
	public SculptList list;

	public RuleListPanel(SculptScreen host, RuleTarget target) {
		this.host = host;
		this.target = target;
	}

	public RuleTarget target() {
		return target;
	}

	/** Adds Add rule (and, for a zone, Add existing rule), a hint line and the list to the body. */
	public void build(SculptScreen.Body body) {
		addButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.rules.add"),
				() -> setScreen(EntryEditorScreen.create(host, target))), Component.translatable("soundsculptor.rules.add.tip"));
		if (target.isGlobal()) {
			body.row(addButton);
		} else {
			// A zone's list can also take a copy of a rule from the global list or another zone.
			addExistingButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.rules.add_existing"),
					() -> setScreen(new RuleCopyScreen(host, target))), Component.translatable("soundsculptor.rules.add_existing.tip"));
			body.row(addButton, addExistingButton);
		}
		body.label(() -> Component.translatable("soundsculptor.rules.hint"));
		list = new SculptList(body.width(), 100, Component.translatable(target.isGlobal() ? "soundsculptor.rules.list" : "soundsculptor.zone_rules.list"));
		list.setEmptyText(() -> Component.translatable(target.isGlobal() ? "soundsculptor.rules.empty" : "soundsculptor.zone_rules.empty"));
		body.fill(list);
		refresh();
	}

	/** Rebuilds the rows from the profile. The list keeps the focused row and button. */
	public void refresh() {
		if (list == null) {
			return;
		}
		List<String> known = KnownSounds.ids();
		List<SetEntry> rules = target.rules(ProfileManager.profile());
		List<SculptList.Row> rows = new ArrayList<>(rules.size());
		for (int i = 0; i < rules.size(); i++) {
			SetEntry rule = rules.get(i);
			String pattern = rule.pattern().text();
			String status = SetEditorScreen.status(rule);
			String details = SetEditorScreen.details(rule, known);
			List<SculptList.Row.Column> columns = List.of(
					new SculptList.Row.Column(() -> pattern, 0, 0),
					new SculptList.Row.Column(() -> status, STATUS_WIDTH, 0),
					new SculptList.Row.Column(() -> details, 0, 1));
			List<SculptButton> buttons = new ArrayList<>();
			buttons.add(Tooltips.describe(SculptButton.text(SculptToggle.stateText(rule.enabled()), ON_OFF_WIDTH, () -> setEnabled(rule.id(), !rule.enabled()))
					.narrationContext(() -> Component.translatable("soundsculptor.rules.enabled")),
					Component.translatable(rule.enabled() ? "soundsculptor.rules.turn_off" : "soundsculptor.rules.turn_on")));
			buttons.add(SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.rules.edit"),
					() -> setScreen(EntryEditorScreen.edit(host, target, rule))));
			SculptButton up = SculptButton.icon(UP_ICON, Component.translatable("soundsculptor.rules.move_up"), () -> move(rule.id(), -1));
			up.active = i > 0;
			buttons.add(up);
			SculptButton down = SculptButton.icon(DOWN_ICON, Component.translatable("soundsculptor.rules.move_down"), () -> move(rule.id(), 1));
			down.active = i < rules.size() - 1;
			buttons.add(down);
			buttons.add(SculptButton.icon(DELETE_ICON, Component.translatable("soundsculptor.rules.delete"), () -> confirmDelete(rule)));
			rows.add(new SculptList.Row(rule.id(), columns, buttons));
		}
		list.setRows(rows);
	}

	/** FR-4.7: a rule can be turned off without deleting it; the effect stops at once. */
	public void setEnabled(String ruleId, boolean enabled) {
		QuickUndo.apply(host, this::refresh, profile -> {
			SetEntry rule = target.rule(profile, ruleId);
			return rule == null ? profile : target.put(profile, rule.withEnabled(enabled));
		}, enabled ? "soundsculptor.rules.toast.on" : "soundsculptor.rules.toast.off", patternOf(ruleId));
		refresh();
	}

	/** Moves a rule up or down; list order is the last tie-break between rules (FR-4.5 step 4). */
	public void move(String ruleId, int delta) {
		QuickUndo.apply(host, this::refresh, profile -> target.move(profile, ruleId, delta),
				delta < 0 ? "soundsculptor.rules.toast.moved_up" : "soundsculptor.rules.toast.moved_down", patternOf(ruleId));
		refresh();
	}

	public void confirmDelete(SetEntry rule) {
		setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.rules.delete.title"),
				Component.translatable("soundsculptor.rules.delete.message", rule.pattern().text()),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.rules.delete.confirm"), () -> delete(rule.id())),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	public void delete(String ruleId) {
		String pattern = patternOf(ruleId);
		QuickUndo.apply(host, this::refresh, profile -> target.remove(profile, ruleId), "soundsculptor.rules.toast.deleted", pattern);
		refresh();
	}

	/** The rule's pattern for its toast, or its ID if it is gone. */
	private String patternOf(String ruleId) {
		SetEntry rule = target.rule(ProfileManager.profile(), ruleId);
		return rule == null ? ruleId : rule.pattern().text();
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
