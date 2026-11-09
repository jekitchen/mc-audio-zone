package audiozone.core.trace;

import java.util.Objects;

import audiozone.core.zones.Precedence;
import audiozone.core.zones.ZoneEdge;

/**
 * The sound a trace panel is open for, and what is needed to recompute its trace after an edit.
 *
 * @param play           the sound's last play (its position and the player's), or {@code null} if not heard yet
 * @param profileEdge    the profile's default zone edge
 * @param bypass         whether Bypass Audio Zone is on
 * @param vanillaCovered whether the sound has its own vanilla slider
 */
public record TraceEditContext(String soundId, LastPlay play, ZoneEdge profileEdge, boolean bypass, boolean vanillaCovered) {
	public TraceEditContext {
		Objects.requireNonNull(soundId, "soundId");
		Objects.requireNonNull(profileEdge, "profileEdge");
	}

	/** The trace for these rules and zones, exactly as {@link SoundTracer} builds it. */
	public SoundTrace trace(EditableRules rules) {
		return SoundTracer.trace(new Precedence(rules.globalRules(), rules.zones(), profileEdge), soundId, play, bypass,
				vanillaCovered);
	}
}
