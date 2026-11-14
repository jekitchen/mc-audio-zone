package soundsculptor.core.quick;

import java.util.List;
import java.util.Objects;

import soundsculptor.core.sets.PlaceRef;

/**
 * One choice in the Sounds tab's "Apply to" selector (PRD FR-1.5): Everywhere or one zone. Quick actions on a row
 * edit the sound's single-sound entry in that place's Quick changes set (FR-4.10).
 *
 * @param zoneId   the zone's ID, or {@code null} for Everywhere
 * @param zoneName the zone's name for the label, or {@code null} for Everywhere
 */
public record ApplyTarget(String zoneId, String zoneName) {
	/** Everywhere. */
	public static final ApplyTarget EVERYWHERE = new ApplyTarget(null, null);

	public ApplyTarget {
		if ((zoneId == null) != (zoneName == null)) {
			throw new IllegalArgumentException("a zone target needs both an ID and a name");
		}
	}

	public boolean isEverywhere() {
		return zoneId == null;
	}

	/** The place whose Quick changes the quick actions edit (FR-4.10). */
	public PlaceRef place() {
		return isEverywhere() ? PlaceRef.EVERYWHERE : PlaceRef.zone(zoneId);
	}

	/** Key in {@code assets/soundsculptor/lang/en_us.json} for the selector entry; takes {@link #args()}. */
	public String translationKey() {
		if (isEverywhere()) {
			return "soundsculptor.sounds.apply_to.everywhere";
		}
		return "soundsculptor.sounds.apply_to.zone";
	}

	/** The label arguments: nothing for Everywhere, else the zone's name. */
	public List<String> args() {
		return isEverywhere() ? List.of() : List.of(Objects.requireNonNull(zoneName));
	}
}
