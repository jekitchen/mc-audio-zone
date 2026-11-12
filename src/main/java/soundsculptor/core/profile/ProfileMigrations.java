package soundsculptor.core.profile;

import soundsculptor.core.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

/**
 * Upgrades older profile files to the current schema (PRD 7.6). Each schema change bumps
 * {@link Profile#SCHEMA_VERSION} and adds one {@link Step} from that version to the next. Before migrating, the caller
 * saves the original (for files on disk, as {@code <name>.v3.bak}).
 *
 * <p>Schema 3, the first with Sound Sets, is the oldest this build reads. Files in schema 1 or 2 were made by earlier
 * test builds and are not migrated (PRD 7.5, Decisions Log 2026-11-11): reading one fails with
 * {@value #OLD_BUILD}, and the store sets the file aside as {@code .broken}.
 */
public final class ProfileMigrations {
	/** The oldest schema version this build reads. */
	public static final int OLDEST = 3;
	/** "This profile was made by an earlier test build of Sound Sculptor and can't be read." */
	public static final String OLD_BUILD = "soundsculptor.profile.error.old_build";

	/** One upgrade, from {@code from} to {@code from + 1}; it edits the JSON in place. */
	public record Step(int from, Consumer<JsonObject> apply) {
	}

	/** Saves the original before an older file is migrated; called with the file's schema version. */
	@FunctionalInterface
	public interface Backup {
		void save(int fromVersion) throws IOException;
	}

	/** The steps for this version of Sound Sculptor: none yet, schema 3 is current. */
	public static final ProfileMigrations CURRENT = new ProfileMigrations(OLDEST, List.of());

	private final int oldest;
	private final List<Step> steps;

	/**
	 * @param oldest the oldest version read; older files are refused as {@link #OLD_BUILD}
	 * @param steps  one step for each version from {@code oldest} up, in order
	 */
	public ProfileMigrations(int oldest, List<Step> steps) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).from() != oldest + i) {
				throw new IllegalArgumentException("step " + i + " starts at " + steps.get(i).from() + ", expected " + (oldest + i));
			}
		}
		this.oldest = oldest;
		this.steps = List.copyOf(steps);
	}

	/** The schema version files are migrated to. */
	public int target() {
		return oldest + steps.size();
	}

	/**
	 * Returns the file brought up to {@link #target()}. A file already at the target is returned as is; an older one is
	 * backed up first, then migrated on a copy, so {@code root} itself is never changed.
	 *
	 * @throws ProfileFormatException if the version is missing, unknown, newer or older than {@code oldest}, or the
	 *                                backup fails
	 */
	public JsonObject migrate(JsonObject root, Backup backup) throws ProfileFormatException {
		int version = versionOf(root);
		if (version > target()) {
			throw ProfileText.problem(ProfileText.NEWER, version);
		}
		if (version < oldest) {
			throw ProfileText.problem(OLD_BUILD);
		}
		if (version == target()) {
			return root;
		}
		try {
			backup.save(version);
		} catch (IOException e) {
			throw new ProfileFormatException(Message.of(ProfileText.BACKUP_FAILED, String.valueOf(e.getMessage())), e);
		}
		JsonObject copy = root.deepCopy();
		for (int v = version; v < target(); v++) {
			steps.get(v - oldest).apply().accept(copy);
			copy.addProperty("schemaVersion", v + 1);
		}
		return copy;
	}

	/** The file's schema version. */
	static int versionOf(JsonObject root) throws ProfileFormatException {
		JsonElement value = root.get("schemaVersion");
		if (value == null || value.isJsonNull()) {
			throw ProfileText.problem(ProfileText.NO_SCHEMA);
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
				|| value.getAsDouble() != Math.rint(value.getAsDouble())) {
			throw ProfileText.problem(ProfileText.UNKNOWN_SCHEMA, value.toString());
		}
		double version = value.getAsDouble();
		if (version < 1 || version > Integer.MAX_VALUE) {
			throw ProfileText.problem(ProfileText.UNKNOWN_SCHEMA, ProfileCodec.format(version));
		}
		return (int) version;
	}

	/** Copies {@code file} to {@code <name>.v<version>.bak} beside it, e.g. {@code play.example.net.v3.bak}. */
	public static Path backup(Path file, int version) throws IOException {
		String name = file.getFileName().toString();
		String base = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
		Path copy = file.resolveSibling(base + ".v" + version + ".bak");
		Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
		return copy;
	}
}
