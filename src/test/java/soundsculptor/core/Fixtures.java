package soundsculptor.core;

import java.util.List;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;

/** Short ways for tests to build places from a plain entry list: the entries become the place's Quick changes. */
public final class Fixtures {
	private Fixtures() {
	}

	/** A place whose only set is Quick changes with these entries; no sets at all for an empty list. */
	public static PlaceSets quick(List<SetEntry> entries) {
		return PlaceSets.EMPTY.withQuickEntries(entries);
	}

	public static PlaceSets quick(SetEntry... entries) {
		return quick(List.of(entries));
	}

	/** Everywhere's Quick changes holding {@code everywhere}, these zones, an empty library and the hard default edge. */
	public static Precedence precedence(List<SetEntry> everywhere, List<Zone> zones) {
		return new Precedence(quick(everywhere), zones, SetLibrary.EMPTY);
	}

	public static Precedence precedence(List<SetEntry> everywhere, List<Zone> zones, ZoneEdge profileEdge) {
		return new Precedence(quick(everywhere), zones, SetLibrary.EMPTY, profileEdge);
	}
}
