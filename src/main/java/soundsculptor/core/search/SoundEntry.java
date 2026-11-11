package soundsculptor.core.search;

import java.util.Locale;
import java.util.Objects;

/**
 * One searchable sound: its ID and its subtitle text in the current language, or {@code null} if it has none
 * (PRD FR-3.2). Lowercased copies are made once, so searching while the player types does no case conversion.
 */
public final class SoundEntry {
	private final String id;
	private final String subtitle;
	final String lowerId;
	final String lowerSubtitle;

	public SoundEntry(String id, String subtitle) {
		this.id = Objects.requireNonNull(id, "id");
		this.subtitle = subtitle;
		this.lowerId = id.toLowerCase(Locale.ROOT);
		this.lowerSubtitle = subtitle == null ? null : subtitle.toLowerCase(Locale.ROOT);
	}

	public String id() {
		return id;
	}

	/** Subtitle text, or {@code null}. */
	public String subtitle() {
		return subtitle;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SoundEntry other && id.equals(other.id) && Objects.equals(subtitle, other.subtitle);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, subtitle);
	}

	@Override
	public String toString() {
		return subtitle == null ? id : id + " (" + subtitle + ")";
	}
}
