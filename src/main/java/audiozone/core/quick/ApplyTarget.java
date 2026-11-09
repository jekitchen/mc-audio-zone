package audiozone.core.quick;

import java.util.List;
import java.util.Objects;

/**
 * One choice in the Recently Played "Apply to" selector (PRD FR-1.5): Everywhere (the global rule list) or one zone's
 * rule list. Quick actions on a row create or edit the sound's exact-ID rule in that list.
 *
 * @param zoneId   the zone's ID, or {@code null} for Everywhere
 * @param zoneName the zone's name for the label, or {@code null} for Everywhere
 * @param bubble   whether the zone is a personal bubble, which gets its own label
 */
public record ApplyTarget(String zoneId, String zoneName, boolean bubble) {
	/** The global rule list. */
	public static final ApplyTarget EVERYWHERE = new ApplyTarget(null, null, false);

	public ApplyTarget {
		if ((zoneId == null) != (zoneName == null)) {
			throw new IllegalArgumentException("a zone target needs both an ID and a name");
		}
		if (zoneId == null && bubble) {
			throw new IllegalArgumentException("Everywhere is not a bubble");
		}
	}

	public boolean isEverywhere() {
		return zoneId == null;
	}

	/** Key in {@code assets/audiozone/lang/en_us.json} for the selector entry; takes {@link #args()}. */
	public String translationKey() {
		if (isEverywhere()) {
			return "audiozone.recent.apply_to.everywhere";
		}
		return bubble ? "audiozone.recent.apply_to.bubble" : "audiozone.recent.apply_to.zone";
	}

	/** The label arguments: nothing for Everywhere, else the zone's name. */
	public List<String> args() {
		return isEverywhere() ? List.of() : List.of(Objects.requireNonNull(zoneName));
	}
}
