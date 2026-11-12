package soundsculptor.core.trace;

import java.util.List;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Zone;

// M8c shim: removed in Task 10
/**
 * The parts of a profile the trace panel can edit (PRD FR-14.4): Everywhere's Quick changes entries and the zones,
 * whose Quick changes it edits too. Immutable: every edit returns a new value, which is also the before-state kept for
 * Undo ({@link soundsculptor.core.undo.UndoSlot}).
 *
 * @param globalRules Everywhere's Quick changes entries, in order
 */
public record EditableRules(List<SetEntry> globalRules, List<Zone> zones) {
	public EditableRules {
		globalRules = List.copyOf(globalRules);
		zones = List.copyOf(zones);
	}
}
