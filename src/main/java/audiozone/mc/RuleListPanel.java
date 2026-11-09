package audiozone.mc;

import audiozone.core.Translator;
import audiozone.core.lists.RuleSummary;
import audiozone.core.profile.RuleTarget;
import audiozone.core.rules.Rule;
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

/**
 * A rule list with Add rule (PRD 6.1 Rules): each row shows the pattern, its summary ("Muted", "Volume 50%") and match
 * count, with On/Off, Edit, Move up/down and Delete. Used for the global list on the Rules tab and for a zone's own
 * list (FR-8.6).
 */
public final class RuleListPanel {
	private static final int SUMMARY_WIDTH = 150;
	private static final int ON_OFF_WIDTH = 30;
	private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/edit");
	private static final Identifier UP_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/up");
	private static final Identifier DOWN_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/down");
	private static final Identifier DELETE_ICON = Identifier.fromNamespaceAndPath("audiozone", "icon/delete");
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();

	private final AzScreen host;
	private final RuleTarget target;
	public AzButton addButton;
	public AzList list;

	public RuleListPanel(AzScreen host, RuleTarget target) {
		this.host = host;
		this.target = target;
	}

	public RuleTarget target() {
		return target;
	}

	/** Adds the Add rule button, a hint line and the list to the body. */
	public void build(AzScreen.Body body) {
		addButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.rules.add"),
				() -> setScreen(RuleEditorScreen.create(host, target))), Component.translatable("audiozone.rules.add.tip"));
		body.row(addButton);
		body.label(() -> Component.translatable("audiozone.rules.hint"));
		list = new AzList(body.width(), 100, Component.translatable(target.isGlobal() ? "audiozone.rules.list" : "audiozone.zone_rules.list"));
		list.setEmptyText(() -> Component.translatable(target.isGlobal() ? "audiozone.rules.empty" : "audiozone.zone_rules.empty"));
		body.fill(list);
		refresh();
	}

	/** Rebuilds the rows from the profile. The list keeps the focused row and button. */
	public void refresh() {
		if (list == null) {
			return;
		}
		List<String> known = KnownSounds.ids();
		List<Rule> rules = target.rules(ProfileManager.profile());
		List<AzList.Row> rows = new ArrayList<>(rules.size());
		for (int i = 0; i < rules.size(); i++) {
			Rule rule = rules.get(i);
			String pattern = rule.pattern().text();
			String summary = RuleSummary.text(rule, TRANSLATOR);
			String matches = RuleSummary.matchesPart(RuleSummary.matchCount(rule, known)).text(TRANSLATOR);
			List<AzList.Row.Column> columns = List.of(
					new AzList.Row.Column(() -> pattern, 0, 0),
					new AzList.Row.Column(() -> summary, SUMMARY_WIDTH, 0),
					new AzList.Row.Column(() -> matches, 0, 1));
			List<AzButton> buttons = new ArrayList<>();
			buttons.add(Tooltips.describe(AzButton.text(AzToggle.stateText(rule.enabled()), ON_OFF_WIDTH, () -> setEnabled(rule.id(), !rule.enabled()))
					.narrationContext(() -> Component.translatable("audiozone.rules.enabled")),
					Component.translatable(rule.enabled() ? "audiozone.rules.turn_off" : "audiozone.rules.turn_on")));
			buttons.add(AzButton.icon(EDIT_ICON, Component.translatable("audiozone.rules.edit"),
					() -> setScreen(RuleEditorScreen.edit(host, target, rule))));
			AzButton up = AzButton.icon(UP_ICON, Component.translatable("audiozone.rules.move_up"), () -> move(rule.id(), -1));
			up.active = i > 0;
			buttons.add(up);
			AzButton down = AzButton.icon(DOWN_ICON, Component.translatable("audiozone.rules.move_down"), () -> move(rule.id(), 1));
			down.active = i < rules.size() - 1;
			buttons.add(down);
			buttons.add(AzButton.icon(DELETE_ICON, Component.translatable("audiozone.rules.delete"), () -> confirmDelete(rule)));
			rows.add(new AzList.Row(rule.id(), columns, buttons));
		}
		list.setRows(rows);
	}

	/** FR-4.7: a rule can be turned off without deleting it; the effect stops at once. */
	public void setEnabled(String ruleId, boolean enabled) {
		ProfileManager.update(profile -> {
			Rule rule = target.rule(profile, ruleId);
			return rule == null ? profile : target.put(profile, rule.withEnabled(enabled));
		});
		refresh();
	}

	/** Moves a rule up or down; list order is the last tie-break between rules (FR-4.5 step 4). */
	public void move(String ruleId, int delta) {
		ProfileManager.update(profile -> target.move(profile, ruleId, delta));
		refresh();
	}

	public void confirmDelete(Rule rule) {
		setScreen(new AzDialog(host,
				Component.translatable("audiozone.rules.delete.title"),
				Component.translatable("audiozone.rules.delete.message", rule.pattern().text()),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.rules.delete.confirm"), () -> delete(rule.id())),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
				1, 1));
	}

	public void delete(String ruleId) {
		ProfileManager.update(profile -> target.remove(profile, ruleId));
		refresh();
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
