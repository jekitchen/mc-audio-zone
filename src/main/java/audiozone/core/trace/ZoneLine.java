package audiozone.core.trace;

import audiozone.core.zones.Zone;

/**
 * One zone that contains the sound (PRD FR-14.2 item 2).
 *
 * @param weight   fade weight w, 0 to 1 (always 1 for MVP hard-edged zones)
 * @param distance straight-line distance from the sound to the zone's anchor, in blocks
 * @param rules    how the zone's own rule list treated the sound
 */
public record ZoneLine(Zone zone, double weight, double distance, ListTrace rules) {
}
