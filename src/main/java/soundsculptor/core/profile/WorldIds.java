package soundsculptor.core.profile;

import soundsculptor.core.zones.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Singleplayer world IDs (PRD FR-11.1). On first join Sound Sculptor writes a small file, {@value #FILE_NAME}, into the
 * world's save folder and uses the ID in it, so the world keeps its profile through folder renames and backups. A
 * copied world shares the original's ID, and so its profile, until the player clicks "Give this copy its own profile".
 *
 * <p>Only the text is handled here; the caller reads and writes the file. If it cannot be written (a read-only save),
 * the caller falls back to {@link WorldKey#folder}.
 */
public final class WorldIds {
	/** The file in the world's save folder. */
	public static final String FILE_NAME = "soundsculptor_id.txt";

	private WorldIds() {
	}

	/** A new random ID: a lowercase UUID such as {@code 6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30}. */
	public static String newId(Supplier<UUID> random) {
		return random.get().toString().toLowerCase(Locale.ROOT);
	}

	/** Whether {@code id} is a world ID in its stored form: a lowercase UUID with dashes. */
	public static boolean isValidId(String id) {
		if (id == null || id.length() != 36) {
			return false;
		}
		for (int i = 0; i < 36; i++) {
			char c = id.charAt(i);
			boolean ok = i == 8 || i == 13 || i == 18 || i == 23 ? c == '-' : (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	/** What to write into {@value #FILE_NAME}: the ID on one line. */
	public static String fileText(String id) {
		if (!isValidId(id)) {
			throw new IllegalArgumentException("not a world id: " + id);
		}
		return id + "\n";
	}

	/**
	 * The ID in a {@value #FILE_NAME}'s text. Forgiving about case, surrounding whitespace, line endings and a byte-order
	 * mark (an editor may add them); anything else gives empty, and the caller treats the file as missing and writes a
	 * new ID.
	 */
	public static Optional<String> readId(String text) {
		if (text == null) {
			return Optional.empty();
		}
		String id = text.startsWith("﻿") ? text.substring(1) : text;
		id = id.strip().toLowerCase(Locale.ROOT);
		return isValidId(id) ? Optional.of(id) : Optional.empty();
	}

	/**
	 * "Give this copy its own profile" (FR-11.1): a new ID for this world, and the profile it starts with.
	 *
	 * @param id        the new ID
	 * @param worldKey  the new key, {@code world:<id>}
	 * @param fileText  what to write into {@value #FILE_NAME} (replacing the shared ID)
	 * @param profile   a copy of the shared profile, saved under the new key; its fixed zones for this world follow it
	 */
	public record OwnProfile(String id, WorldKey worldKey, String fileText, Profile profile) {
	}

	/**
	 * Gives a copied world its own ID and profile. The copy starts with everything it had: the same rules, settings,
	 * bubbles and zone colors, and its fixed zones, which are moved to the new key so they stay active here (bubbles carry no world key) (the
	 * coordinates mean the same place in a copy). Zones made for another world are left as they are. The shared
	 * profile itself is not changed, so the original world keeps it.
	 *
	 * @param current the profile the copy shares now
	 * @param oldKey  the key the copy uses now ({@code world:<shared id>}, or a folder fallback)
	 * @param random  the source of new IDs; an ID equal to the current one is never used
	 */
	public static OwnProfile giveOwnProfile(Profile current, String oldKey, Supplier<UUID> random) {
		String id;
		do {
			id = newId(random);
		} while (oldKey.equals(WorldKey.Kind.WORLD.prefix() + id));
		WorldKey newKey = WorldKey.world(id);
		List<Zone> zones = new ArrayList<>(current.zones().size());
		for (Zone zone : current.zones()) {
			zones.add(zone.worldKey().equals(oldKey)
					? zone.withWorldKey(newKey.key())
					: zone);
		}
		return new OwnProfile(id, newKey, fileText(id), current.withZones(zones));
	}
}
