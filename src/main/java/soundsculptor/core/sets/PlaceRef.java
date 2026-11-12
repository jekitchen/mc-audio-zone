package soundsculptor.core.sets;

import java.util.Objects;

/**
 * A place that Sound Sets attach to: Everywhere or one zone.
 *
 * @param zoneId the zone's ID, or {@code null} for Everywhere
 */
public record PlaceRef(String zoneId) {
	public static final PlaceRef EVERYWHERE = new PlaceRef(null);

	public static PlaceRef zone(String id) {
		return new PlaceRef(Objects.requireNonNull(id, "id"));
	}

	public boolean isEverywhere() {
		return zoneId == null;
	}
}
