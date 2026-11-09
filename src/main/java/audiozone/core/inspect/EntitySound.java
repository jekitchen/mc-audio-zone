package audiozone.core.inspect;

/**
 * One sound in the inspector panel (PRD FR-2).
 *
 * @param soundId the sound event ID, e.g. {@code minecraft:entity.villager.ambient}
 * @param kind    how sure the link is
 * @param timeMs  when it started, on the caller's clock
 */
public record EntitySound(String soundId, LinkKind kind, long timeMs) {
}
