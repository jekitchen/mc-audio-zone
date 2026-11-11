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

	/**
	 * Schema 1 → 2: profile settings gained {@code cueDefault}, {@code outlines} and {@code edge}, and the profile gained
	 * {@code lastJoined} and {@code boostConfirmed}. All are optional, with defaults. Schema 1 readers ignored these
	 * names, so a version 1 file that holds them (written by hand from the PRD sketch) meant nothing by them; the step
	 * removes them, and the file loads exactly as version 1 loaded it.
	 */
	static final Step V1_TO_V2 = new Step(1, root -> {
		root.remove("lastJoined");
		root.remove("boostConfirmed");
		JsonElement settings = root.get("settings");
		if (settings != null && settings.isJsonObject()) {
			JsonObject s = settings.getAsJsonObject();
			s.remove("cueDefault");
			s.remove("outlines");
			s.remove("edge");
		}
	});

	/** The steps for this version of Sound Sculptor. */
	public static final ProfileMigrations CURRENT = new ProfileMigrations(List.of(V1_TO_V2));

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
			throw ProfileText.problem(ProfileText.NEWER, version);
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
			steps.get(v - 1).apply().accept(copy);
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

	/** Copies {@code file} to {@code <name>.v<version>.bak} beside it, e.g. {@code play.example.net.v1.bak}. */
	public static Path backup(Path file, int version) throws IOException {
		String name = file.getFileName().toString();
		String base = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
		Path copy = file.resolveSibling(base + ".v" + version + ".bak");
		Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
		return copy;
	}
}
