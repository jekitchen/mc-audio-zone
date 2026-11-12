package soundsculptor.core.sets;

import java.util.Objects;

/**
 * A Sound Set attached to a place, switched on or off.
 *
 * @param setId   the library set's ID, or {@link SoundSet#QUICK_ID} for the place's Quick changes
 * @param enabled disabled attachments are kept but ignored
 */
public record Attachment(String setId, boolean enabled) {
	public Attachment {
		Objects.requireNonNull(setId, "setId");
	}
}
