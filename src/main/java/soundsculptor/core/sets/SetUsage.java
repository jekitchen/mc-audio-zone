package soundsculptor.core.sets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import soundsculptor.core.profile.Profile;

/**
 * Where each library Sound Set is used, across every stored profile (FR-4.7, FR-4.11). Immutable.
 *
 * <p>Other profiles are only ever described by their display name: their zones are never listed.
 *
 * @param bySet for each set ID, the profiles that attach it, in world key order
 */
public record SetUsage(Map<String, List<Use>> bySet) {
	public static final SetUsage EMPTY = new SetUsage(Map.of());

	/**
	 * One profile that attaches a set.
	 *
	 * @param worldKey    the profile's world key
	 * @param profileName its display name
	 * @param places      the places that attach the set: Everywhere first, then zones in list order
	 */
	public record Use(String worldKey, String profileName, List<PlaceRef> places) {
		public Use {
			places = List.copyOf(places);
		}
	}

	public SetUsage {
		Map<String, List<Use>> copy = new LinkedHashMap<>();
		bySet.forEach((id, uses) -> copy.put(id, List.copyOf(uses)));
		bySet = Map.copyOf(copy);
	}

	/** Builds the usage of every set from the profiles, by world key. Quick changes is not a library set and is skipped. */
	public static SetUsage of(Map<String, Profile> profilesByKey) {
		Map<String, List<Use>> out = new LinkedHashMap<>();
		new TreeMap<>(profilesByKey).forEach((key, profile) -> {
			Map<String, List<PlaceRef>> perSet = new LinkedHashMap<>();
			collect(perSet, PlaceRef.EVERYWHERE, profile.everywhere());
			for (var zone : profile.zones()) {
				collect(perSet, PlaceRef.zone(zone.id()), zone.sets());
			}
			perSet.forEach((setId, places) -> out.computeIfAbsent(setId, id -> new ArrayList<>())
					.add(new Use(key, profile.name(), places)));
		});
		return new SetUsage(out);
	}

	private static void collect(Map<String, List<PlaceRef>> perSet, PlaceRef place, PlaceSets sets) {
		for (Attachment a : sets.attached()) {
			if (!a.setId().equals(SoundSet.QUICK_ID)) {
				perSet.computeIfAbsent(a.setId(), id -> new ArrayList<>()).add(place);
			}
		}
	}

	/** The places of the current profile that attach the set, or an empty list. */
	public List<PlaceRef> placesHere(String setId, String currentKey) {
		for (Use use : uses(setId)) {
			if (sameWorld(use.worldKey(), currentKey)) {
				return use.places();
			}
		}
		return List.of();
	}

	/** The display names of the other profiles that attach the set, sorted ignoring case. */
	public List<String> otherProfileNames(String setId, String currentKey) {
		return uses(setId).stream().filter(use -> !sameWorld(use.worldKey(), currentKey)).map(Use::profileName)
				.sorted(Comparator.comparing((String n) -> n.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()))
				.toList();
	}

	public int otherCount(String setId, String currentKey) {
		return (int) uses(setId).stream().filter(use -> !sameWorld(use.worldKey(), currentKey)).count();
	}

	private List<Use> uses(String setId) {
		return bySet.getOrDefault(setId, List.of());
	}

	private static boolean sameWorld(String a, String b) {
		return b != null && a.equalsIgnoreCase(b);
	}
}
