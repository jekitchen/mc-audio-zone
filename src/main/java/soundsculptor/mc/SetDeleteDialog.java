package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptDialog;

/**
 * The warning before a library Sound Set is deleted (PRD FR-4.11). It lists the places in this profile that use the set
 * (Everywhere and each zone by name), then the other server and world profiles it will also be removed from, by display
 * name only and never their zones. Delete and Cancel; Cancel (also Esc, and the button focused when it opens) changes
 * nothing.
 */
public final class SetDeleteDialog {
	private SetDeleteDialog() {
	}

	/**
	 * The dialog for {@code plan}.
	 *
	 * @param othersCounted whether the usage index has read the other profiles yet; if not, the warning says so
	 * @param delete        runs when the player confirms
	 */
	public static SculptDialog create(Screen parent, Profile profile, LibraryOps.DeletePlan plan, boolean othersCounted, Runnable delete) {
		List<String> here = new ArrayList<>();
		for (PlaceRef where : plan.placesHere()) {
			here.add(placeName(profile, where));
		}
		return new SculptDialog(parent,
				Component.translatable("soundsculptor.sets.delete.title", plan.name()),
				message(here, plan.otherProfiles(), othersCounted),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.sets.delete.confirm"), delete),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1);
	}

	/**
	 * The warning text, one statement per line: "Used here in: Everywhere, Main base", then "Also removed from: Hypixel
	 * Skyblock, Creative test world"; just "This set is not attached anywhere." when nothing uses it.
	 *
	 * @param placesHere    the names of the places in this profile that use the set
	 * @param otherProfiles the display names of the other profiles that use it
	 * @param othersCounted false while the other profiles are still being read: the warning then says so instead of
	 *                      naming them
	 */
	public static Component message(List<String> placesHere, List<String> otherProfiles, boolean othersCounted) {
		List<Component> lines = new ArrayList<>();
		if (!placesHere.isEmpty()) {
			lines.add(Component.translatable("soundsculptor.sets.delete.here", String.join(", ", placesHere)));
		} else if (!othersCounted) {
			lines.add(Component.translatable("soundsculptor.sets.delete.not_here"));
		}
		if (!othersCounted) {
			lines.add(Component.translatable("soundsculptor.sets.delete.counting"));
		} else if (!otherProfiles.isEmpty()) {
			lines.add(Component.translatable("soundsculptor.sets.delete.others", String.join(", ", otherProfiles)));
		}
		if (lines.isEmpty()) {
			return Component.translatable("soundsculptor.sets.delete.unattached");
		}
		MutableComponent text = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				text.append("\n");
			}
			text.append(lines.get(i));
		}
		return text;
	}

	/** "Everywhere", or the zone's name (its ID if it has none), as the notes about removed sets name places. */
	static String placeName(Profile profile, PlaceRef where) {
		if (where.isEverywhere()) {
			return Component.translatable("soundsculptor.profile.place.everywhere").getString();
		}
		Zone zone = profile.zone(where.zoneId());
		return zone == null || zone.name() == null || zone.name().isBlank() ? where.zoneId() : zone.name();
	}
}
