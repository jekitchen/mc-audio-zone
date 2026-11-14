package soundsculptor.core.profile;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Zone;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A profile (PRD FR-11, section 7.5): the Sound Sets attached to Everywhere, its zones and profile settings. Library
 * sets are referred to by ID; each place's Quick changes are held inline. Immutable; every change returns a new
 * profile, which the sound path then reads as a snapshot (PRD 7.4).
 *
 * @param name     display name
 * @param settings profile settings
 * @param everywhere the Sound Sets attached to Everywhere (FR-8.10), and its Quick changes
 * @param zones    the zones, all fixed (FR-8.1), in the order the player sees them
 * @param zoneColors outline color of each zone that has one, as {@code 0xRRGGBB}, keyed by zone ID
 * @param lastJoined when the player last joined this server or world (FR-11.3), or null if never
 * @param boostConfirmed whether the player has confirmed the boost dialog for this profile (FR-5.4: asked once per
 *                   profile, the first time a level goes above 100%)
 */
public record Profile(String name, ProfileSettings settings, PlaceSets everywhere, List<Zone> zones,
		Map<String, Integer> zoneColors, Instant lastJoined, boolean boostConfirmed) {
	/** Bumped on every change to the file format (PRD 7.6). Schema 3 is the first with Sound Sets. */
	public static final int SCHEMA_VERSION = 3;

	public Profile {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(settings, "settings");
		Objects.requireNonNull(everywhere, "everywhere");
		zones = List.copyOf(zones);
		Set<String> ids = new HashSet<>();
		for (Zone zone : zones) {
			if (!ids.add(zone.id())) {
				throw new IllegalArgumentException("zone id " + zone.id() + " is used twice");
			}
		}
		for (String id : zoneColors.keySet()) {
			if (!ids.contains(id)) {
				throw new IllegalArgumentException("color for unknown zone " + id);
			}
		}
		zoneColors = Map.copyOf(zoneColors);
	}

	/** A profile never joined, with boost not yet confirmed. */
	public Profile(String name, ProfileSettings settings, PlaceSets everywhere, List<Zone> zones,
			Map<String, Integer> zoneColors) {
		this(name, settings, everywhere, zones, zoneColors, null, false);
	}

	/** A profile with no zones. */
	public Profile(String name, ProfileSettings settings, PlaceSets everywhere) {
		this(name, settings, everywhere, List.of(), Map.of());
	}

	public static Profile empty(String name) {
		return new Profile(name, ProfileSettings.DEFAULTS, PlaceSets.EMPTY);
	}

	public Profile withSettings(ProfileSettings value) {
		return new Profile(name, value, everywhere, zones, zoneColors, lastJoined, boostConfirmed);
	}

	public Profile withName(String value) {
		return new Profile(value, settings, everywhere, zones, zoneColors, lastJoined, boostConfirmed);
	}

	public Profile withEverywhere(PlaceSets value) {
		return new Profile(name, settings, value, zones, zoneColors, lastJoined, boostConfirmed);
	}

	/** Records a join (FR-11.3); called with the time the player joined this server or world. */
	public Profile withLastJoined(Instant value) {
		return new Profile(name, settings, everywhere, zones, zoneColors, value, boostConfirmed);
	}

	/** Set after the player confirms the boost dialog, so it is not shown again for this profile (FR-5.4). */
	public Profile withBoostConfirmed(boolean value) {
		return new Profile(name, settings, everywhere, zones, zoneColors, lastJoined, value);
	}

	// ---- Places (Everywhere and zones, FR-8.6, FR-8.10) ----

	/** The sets of Everywhere or of the zone; {@code null} for a zone this profile does not have. */
	public PlaceSets place(PlaceRef where) {
		if (where.isEverywhere()) {
			return everywhere;
		}
		Zone zone = zone(where.zoneId());
		return zone == null ? null : zone.sets();
	}

	/** Replaces the sets of Everywhere or of the zone; unchanged for a zone this profile does not have. */
	public Profile withPlace(PlaceRef where, PlaceSets value) {
		if (where.isEverywhere()) {
			return withEverywhere(value);
		}
		Zone zone = zone(where.zoneId());
		return zone == null ? this : putZone(zone.withSets(value));
	}

	/** Every place that attaches the set: Everywhere first, then zones in list order. */
	public List<PlaceRef> placesUsing(String setId) {
		List<PlaceRef> out = new ArrayList<>();
		if (everywhere.uses(setId)) {
			out.add(PlaceRef.EVERYWHERE);
		}
		for (Zone zone : zones) {
			if (zone.sets().uses(setId)) {
				out.add(PlaceRef.zone(zone.id()));
			}
		}
		return out;
	}

	// ---- Boost (FR-5.4) ----

	/**
	 * Whether setting an entry to this level must first show the boost dialog (FR-5.4): only above 100%, and only until
	 * the player has confirmed it once for this profile.
	 *
	 * @param volume the new level in percent, or null if the entry does not set one
	 */
	public boolean needsBoostConfirm(Integer volume) {
		return !boostConfirmed && volume != null && volume > 100;
	}

	/**
	 * Whether any entry attached anywhere in this profile, in a library set or in a place's Quick changes, sets a level
	 * above 100%: a profile that came in by import or copy with one must first show the boost dialog (FR-5.4) unless it
	 * was already confirmed. Sets the library does not have are skipped.
	 */
	public boolean hasBoost(SetLibrary library) {
		if (hasBoost(everywhere, library)) {
			return true;
		}
		for (Zone zone : zones) {
			if (hasBoost(zone.sets(), library)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBoost(PlaceSets place, SetLibrary library) {
		for (Attachment attachment : place.attached()) {
			SoundSet set = attachment.setId().equals(SoundSet.QUICK_ID) ? place.quickChanges() : library.get(attachment.setId());
			if (set == null) {
				continue;
			}
			for (SetEntry entry : set.entries()) {
				if (entry.volume() != null && entry.volume() > 100) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- Zones (Zones tab, PRD 6.1) ----

	/** Replaces the zones; colors of zones no longer present are dropped. */
	public Profile withZones(List<Zone> value) {
		Map<String, Integer> colors = new LinkedHashMap<>();
		for (Zone zone : value) {
			Integer rgb = zoneColors.get(zone.id());
			if (rgb != null) {
				colors.put(zone.id(), rgb);
			}
		}
		return new Profile(name, settings, everywhere, value, colors, lastJoined, boostConfirmed);
	}

	/** Sets the outline color of a zone in this profile. */
	public Profile withZoneColor(String zoneId, int rgb) {
		Map<String, Integer> colors = new LinkedHashMap<>(zoneColors);
		colors.put(zoneId, rgb);
		return new Profile(name, settings, everywhere, zones, colors, lastJoined, boostConfirmed);
	}

	/** The zone's color as {@code 0xRRGGBB}, or null if it has none. */
	public Integer colorOf(String zoneId) {
		return zoneColors.get(zoneId);
	}

	/** The zone with this ID, or null. */
	public Zone zone(String id) {
		for (Zone zone : zones) {
			if (zone.id().equals(id)) {
				return zone;
			}
		}
		return null;
	}

	/** Saves a zone from the editor: replaces the zone with the same ID in place, or adds it at the end. */
	public Profile putZone(Zone zone) {
		List<Zone> list = new ArrayList<>(zones);
		Zone existing = zone(zone.id());
		if (existing == null) {
			list.add(zone);
		} else {
			list.set(list.indexOf(existing), zone);
		}
		return withZones(list);
	}

	/** Deletes the zone with this ID, and its color; returns this profile if there is none. */
	public Profile removeZone(String id) {
		Zone existing = zone(id);
		if (existing == null) {
			return this;
		}
		List<Zone> list = new ArrayList<>(zones);
		list.remove(existing);
		return withZones(list);
	}

	/**
	 * Moves a zone up ({@code delta < 0}) or down the list. List order is the last tie-break between zones (FR-9 step
	 * 5). Returns this profile if the zone is missing or already at that end.
	 */
	public Profile moveZone(String id, int delta) {
		Zone existing = zone(id);
		if (existing == null) {
			return this;
		}
		int from = zones.indexOf(existing);
		int to = Math.max(0, Math.min(zones.size() - 1, from + delta));
		if (to == from) {
			return this;
		}
		List<Zone> list = new ArrayList<>(zones);
		list.remove(from);
		list.add(to, existing);
		return withZones(list);
	}

	/** An ID for a new zone: {@code z1}, {@code z2}, … the first one not in use. */
	public String newZoneId() {
		for (int n = 1; ; n++) {
			String id = "z" + n;
			if (zone(id) == null) {
				return id;
			}
		}
	}
}
