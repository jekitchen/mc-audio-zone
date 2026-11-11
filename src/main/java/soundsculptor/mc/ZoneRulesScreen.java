package soundsculptor.mc;

import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptScreen;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A zone's own rule list (PRD FR-8.6): the same format and buttons as the global Rules tab. Pattern {@code *} means
 * every positional sound in the zone.
 */
public final class ZoneRulesScreen extends SculptScreen {
	private final String zoneId;
	public RuleListPanel panel;

	public ZoneRulesScreen(Screen parent, String zoneId) {
		super(Component.translatable("soundsculptor.zone_rules.title"), parent);
		this.zoneId = zoneId;
	}

	public String zoneId() {
		return zoneId;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		Zone zone = ProfileManager.profile().zone(zoneId);
		return Component.translatable("soundsculptor.zone_rules.subtitle", zone == null ? zoneId : zone.name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		panel = new RuleListPanel(this, RuleTarget.zone(zoneId));
		panel.build(body);
	}
}
