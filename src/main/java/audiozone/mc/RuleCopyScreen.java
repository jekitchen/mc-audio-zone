package audiozone.mc;

import audiozone.core.profile.Profile;
import audiozone.core.profile.RuleTarget;
import audiozone.core.rules.Rule;
import audiozone.core.zones.Zone;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Add existing rule" for a zone's rules: every rule in the global list and in the other zones, each with Copy. Copy
 * adds an independent copy to this zone under a new ID; changing either later never changes the other. A rule whose
 * pattern this zone already has cannot be copied (edit that one instead), so a zone never holds two competing rules for
 * the same sounds.
 */
public final class RuleCopyScreen extends AzScreen {
	private static final int STATUS_WIDTH = 110;
	private static final int COPY_WIDTH = 44;

	private final RuleTarget target;
	public AzList list;

	public RuleCopyScreen(Screen parent, RuleTarget target) {
		super(Component.translatable("audiozone.rule_copy.title"), parent);
		this.target = target;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("audiozone.zone_rules.subtitle", zoneName(ProfileManager.profile(), target));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(AzButton.text(Component.translatable("audiozone.ui.cancel"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		body.label(() -> Component.translatable("audiozone.rule_copy.hint"));
		list = new AzList(body.width(), 100, Component.translatable("audiozone.rule_copy.list"));
		list.setEmptyText(() -> Component.translatable("audiozone.rule_copy.empty"));
		body.fill(list);
		Profile profile = ProfileManager.profile();
		List<String> known = KnownSounds.ids();
		List<AzList.Row> rows = new ArrayList<>();
		List<RuleTarget.Sourced> others = target.otherRules(profile);
		for (int i = 0; i < others.size(); i++) {
			RuleTarget.Sourced sourced = others.get(i);
			Rule rule = sourced.rule();
			String pattern = rule.pattern().text();
			String status = RuleListPanel.status(rule);
			String from = sourced.source().isGlobal()
					? Component.translatable("audiozone.rule_copy.from_global").getString()
					: Component.translatable("audiozone.rule_copy.from_zone", zoneName(profile, sourced.source())).getString();
			String details = RuleListPanel.details(rule, known);
			String second = details.isEmpty() ? from : from + " · " + details;
			boolean duplicate = target.hasPattern(profile, rule.pattern());
			AzButton copy = AzButton.text(Component.translatable("audiozone.rule_copy.copy"), COPY_WIDTH, () -> copy(rule));
			copy.active = !duplicate;
			Tooltips.describe(copy, Component.translatable(duplicate ? "audiozone.rule_copy.duplicate" : "audiozone.rule_copy.copy.tip"));
			rows.add(new AzList.Row(i, List.of(
					new AzList.Row.Column(() -> pattern, 0, 0),
					new AzList.Row.Column(() -> status, STATUS_WIDTH, 0),
					new AzList.Row.Column(() -> second, 0, 1)), List.of(copy)));
		}
		list.setRows(rows);
	}

	/** Copies the rule into this zone and returns to its rules, with a toast saying what happened. */
	public void copy(Rule rule) {
		ProfileManager.update(profile -> target.copyIn(profile, rule));
		closeNow();
		if (parent instanceof AzScreen screen) {
			screen.showToast(Component.translatable("audiozone.rule_copy.copied", rule.pattern().text()), null);
		}
	}

	private static String zoneName(Profile profile, RuleTarget target) {
		Zone zone = target.zoneId() == null ? null : profile.zone(target.zoneId());
		return zone == null || zone.name() == null || zone.name().isBlank() ? String.valueOf(target.zoneId()) : zone.name();
	}
}
