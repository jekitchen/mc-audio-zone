package audiozone.core.profile;

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
 * saves the original (for files on disk, as {@code <name>.v1.bak}).
 */
public final class ProfileMigrations {
	/** One upgrade, from {@code from} to {@code from + 1}; it edits the JSON in place. */
	public record Step(int from, Consumer<JsonObject> apply) {
	}

	/** Saves the original before an older file is migrated; called with the file's schema version. */
	@FunctionalInterface
	public interface Backup {
		void save(int fromVersion) throws IOException;
	}

	/** The steps for this version of Audio Zone. Schema 1 is the first, so there are none yet. */
	public static final ProfileMigrations CURRENT = new ProfileMigrations(List.of());

	private final List<Step> steps;

	/** @param steps one step for each version from 1 up, in order: 1→2, 2→3, ... */
	public ProfileMigrations(List<Step> steps) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).from() != i + 1) {
				throw new IllegalArgumentException("step " + i + " starts at " + steps.get(i).from() + ", expected " + (i + 1));
			}
		}
		this.steps = List.copyOf(steps);
	}

	/** The schema version files are migrated to. */
	public int target() {
		return steps.size() + 1;
	}

	/**
	 * Returns the file brought up to {@link #target()}. A file already at the target is returned as is; an older one is
	 * backed up first, then migrated on a copy, so {@code root} itself is never changed.
	 *
	 * @throws ProfileFormatException if the version is missing, unknown or newer, or the backup fails
	 */
	public JsonObject migrate(JsonObject root, Backup backup) throws ProfileFormatException {
		int version = versionOf(root);
		if (version > target()) {
			throw new ProfileFormatException("This file was made with a newer Audio Zone (schema " + version
					+ "). Update the mod to use it.");
		}
		if (version == target()) {
			return root;
		}
		try {
			backup.save(version);
		} catch (IOException e) {
			throw new ProfileFormatException("The file could not be backed up before upgrading it: " + e.getMessage(), e);
		}
		JsonObject copy = root.deepCopy();
		for (int v = version; v < target(); v++) {
			steps.get(v - 1).apply().accept(copy);
			copy.addProperty("schemaVersion", v + 1);
		}
		return copy;
	}

	/** The file's schema version. */
	static int versionOf(JsonObject root) throws ProfileFormatException {
		JsonElement value = root.get("schemaVersion");
		if (value == null || value.isJsonNull()) {
			throw new ProfileFormatException("The file has no schemaVersion.");
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
				|| value.getAsDouble() != Math.rint(value.getAsDouble())) {
			throw new ProfileFormatException("Unknown schemaVersion " + value + ".");
		}
		double version = value.getAsDouble();
		if (version < 1 || version > Integer.MAX_VALUE) {
			throw new ProfileFormatException("Unknown schemaVersion " + ProfileCodec.format(version) + ".");
		}
		return (int) version;
	}

	/** Copies {@code file} to {@code <name>.v<version>.bak} beside it, e.g. {@code play.example.net.v1.bak}. */
	public static Path backup(Path file, int version) throws IOException {
		String name = file.getFileName().toString();
		String base = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
		Path copy = file.resolveSibling(base + ".v" + version + ".bak");
		Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
		return copy;
	}
}
