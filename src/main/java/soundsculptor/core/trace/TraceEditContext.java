package soundsculptor.core.trace;

import java.util.Objects;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.ZoneEdge;

/**
 * The sound a trace panel is open for, and what is needed to recompute its trace after an edit.
 *
 * @param play           the sound's last play (its position and the player's), or {@code null} if not heard yet
 * @param profileEdge    the profile's default zone edge
 * @param bypass         whether Bypass Sound Sculptor is on
 * @param vanillaCovered whether the sound has its own vanilla slider
 */
public record TraceEditContext(String soundId, LastPlay play, ZoneEdge profileEdge, boolean bypass, boolean vanillaCovered) {
	public TraceEditContext {
		Objects.requireNonNull(soundId, "soundId");
		Objects.requireNonNull(profileEdge, "profileEdge");
	}

	/**
	 * The trace for these rules and zones, exactly as {@link SoundTracer} builds it. Only Quick changes count: library
	 * sets are left out until the trace is in set terms.
	 */
	public SoundTrace trace(EditableRules rules) {
		// M8c shim: removed in Task 10
		PlaceSets everywhere = PlaceSets.EMPTY.withQuickEntries(rules.globalRules());
		return SoundTracer.trace(new Precedence(everywhere, rules.zones(), SetLibrary.EMPTY, profileEdge), soundId, play,
				bypass, vanillaCovered);
	}
}
