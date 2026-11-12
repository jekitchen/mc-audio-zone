package soundsculptor.mc;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptScreen;

/**
 * The Sound Sets attached to one place (PRD FR-8.6): Everywhere, from the first row of the Zones tab (FR-8.10), or a
 * zone, from its row or from the zone editor. Changes apply at once with an Undo toast, so the screen has Done only.
 */
public final class PlaceSetsScreen extends SculptScreen {
	/** How often the lines check whether the other worlds have been counted. */
	private static final int USAGE_REFRESH_TICKS = 5;

	private final PlaceRef where;
	public PlaceSetsPanel panel;
	private int ticks;

	public PlaceSetsScreen(Screen parent, PlaceRef where) {
		super(Component.translatable("soundsculptor.place_sets.title"), parent);
		this.where = where;
	}

	public PlaceRef where() {
		return where;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("soundsculptor.place_sets.subtitle", SetDeleteDialog.placeName(ProfileManager.profile(), where));
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		panel = new PlaceSetsPanel(this, where);
		panel.build(body);
	}

	@Override
	public void tick() {
		super.tick();
		if (panel != null && ++ticks % USAGE_REFRESH_TICKS == 0) {
			panel.refreshIfUsageChanged();
		}
	}

	/** Rebuilds the lines from the profile, e.g. after Undo. */
	public void refreshLists() {
		if (panel != null) {
			panel.refresh();
		}
	}
}
