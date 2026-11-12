package soundsculptor.mc;

import soundsculptor.core.Message;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Tooltips;
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
public final class RuleCopyScreen extends SculptScreen {
	private static final int STATUS_WIDTH = 110;
	private static final int COPY_WIDTH = 44;

	private final RuleTarget target;
	public SculptList list;

	public RuleCopyScreen(Screen parent, RuleTarget target) {
		super(Component.translatable("soundsculptor.rule_copy.title"), parent);
		this.target = target;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("soundsculptor.zone_rules.subtitle", zoneName(ProfileManager.profile(), target));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		body.label(() -> Component.translatable("soundsculptor.rule_copy.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.rule_copy.list"));
		list.setEmptyText(() -> Component.translatable("soundsculptor.rule_copy.empty"));
		body.fill(list);
		Profile profile = ProfileManager.profile();
		List<String> known = KnownSounds.ids();
		List<SculptList.Row> rows = new ArrayList<>();
		List<RuleTarget.Sourced> others = target.otherRules(profile);
		for (int i = 0; i < others.size(); i++) {
			RuleTarget.Sourced sourced = others.get(i);
			SetEntry rule = sourced.rule();
			String pattern = rule.pattern().text();
			String status = SetEditorScreen.status(rule);
			String from = sourced.source().isGlobal()
					? Component.translatable("soundsculptor.rule_copy.from_global").getString()
					: Component.translatable("soundsculptor.rule_copy.from_zone", zoneName(profile, sourced.source())).getString();
			String details = SetEditorScreen.details(rule, known);
			String second = details.isEmpty() ? from : from + " · " + details;
			boolean duplicate = target.hasPattern(profile, rule.pattern());
			SculptButton copy = SculptButton.text(Component.translatable("soundsculptor.rule_copy.copy"), COPY_WIDTH, () -> copy(rule));
			copy.active = !duplicate;
			Tooltips.describe(copy, Component.translatable(duplicate ? "soundsculptor.rule_copy.duplicate" : "soundsculptor.rule_copy.copy.tip"));
			rows.add(new SculptList.Row(i, List.of(
					new SculptList.Row.Column(() -> pattern, 0, 0),
					new SculptList.Row.Column(() -> status, STATUS_WIDTH, 0),
					new SculptList.Row.Column(() -> second, 0, 1)), List.of(copy)));
		}
		list.setRows(rows);
	}

	/** Copies the rule into this zone and returns to its rules, with a toast saying what happened. */
	public void copy(SetEntry rule) {
		Profile profile = ProfileManager.profile();
		Message full = target.canPut(profile, rule.withId(target.newRuleId(profile)));
		if (full != null) {
			showToast(ProfileManager.component(full), null);
			return;
		}
		ProfileManager.update(p -> target.copyIn(p, rule));
		closeNow();
		if (parent instanceof SculptScreen screen) {
			screen.showToast(Component.translatable("soundsculptor.rule_copy.copied", rule.pattern().text()), null);
		}
	}

	private static String zoneName(Profile profile, RuleTarget target) {
		Zone zone = target.zoneId() == null ? null : profile.zone(target.zoneId());
		return zone == null || zone.name() == null || zone.name().isBlank() ? String.valueOf(target.zoneId()) : zone.name();
	}
}
