package soundsculptor.core.trace;

import soundsculptor.core.zones.Zone;

/** The zone that decided one setting, and the FR-9 step that picked it. */
public record ZoneWinner(Zone zone, ZoneWinReason reason) {
}
