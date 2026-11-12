package soundsculptor.core.trace;

import java.util.List;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Zone;

/**
 * The parts of a profile the trace panel can edit (PRD FR-14.4): the global rule list and the zones. Immutable: every
 * edit returns a new value, which is also the before-state kept for Undo ({@link soundsculptor.core.undo.UndoSlot}).
 *
 * <p>Plain lists, so the editor works the same once the profile model holds zones.
 */
public record EditableRules(List<SetEntry> globalRules, List<Zone> zones) {
	public EditableRules {
		globalRules = List.copyOf(globalRules);
		zones = List.copyOf(zones);
	}
}
