package audiozone.core.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Profile files on disk (PRD FR-11.8): one human-readable JSON file per profile in {@code config/audiozone/profiles/},
 * named from its key. Writes are atomic (temp file, then rename) and the previous version is kept as {@code .bak}.
 * A file that fails to load is renamed {@code .broken} and an empty profile is used instead.
 *
 * <p>Not thread-safe; callers save from one background thread.
 */
public final class ProfileStore {
	/** Files larger than this are not loaded (PRD FR-11.6 uses the same limit for imports). */
	public static final long MAX_FILE_BYTES = 1024 * 1024;

	private final Path directory;

	public ProfileStore(Path directory) {
		this.directory = directory;
	}

	public Path directory() {
		return directory;
	}

	/** The outcome of loading: the profile to use, and, if the file was unusable, why and where it was moved. */
	public record LoadResult(Profile profile, String problem, Path brokenCopy) {
		public boolean failed() {
			return problem != null;
		}
	}

	/** The file for a profile key, e.g. "play.example.net" or "world:1a2b" (made safe for file names). */
	public Path fileFor(String key) {
		return directory.resolve(safeName(key) + ".json");
	}

	static String safeName(String key) {
		String lower = key.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lower.length());
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_' ? c : '_');
		}
		String name = out.toString();
		return name.isEmpty() || name.chars().allMatch(c -> c == '.') ? "_" + name : name;
	}

	/** Loads the profile for {@code key}; a missing file gives an empty profile named {@code defaultName}. */
	public LoadResult load(String key, String defaultName) {
		Path file = fileFor(key);
		if (!Files.exists(file)) {
			return new LoadResult(Profile.empty(defaultName), null, null);
		}
		String problem;
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				problem = "The file is larger than 1 MB.";
			} else {
				// An older file is saved as <name>.v<n>.bak before it is migrated (PRD 7.6).
				String text = Files.readString(file, StandardCharsets.UTF_8);
				return new LoadResult(ProfileCodec.read(text, version -> ProfileMigrations.backup(file, version)).profile(),
						null, null);
			}
		} catch (ProfileFormatException e) {
			problem = e.getMessage();
		} catch (IOException e) {
			problem = "The file could not be read: " + e.getMessage();
		}
		Path broken = file.resolveSibling(file.getFileName() + ".broken");
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			broken = null;
		}
		return new LoadResult(Profile.empty(defaultName), problem, broken);
	}

	/** Saves atomically, keeping the previous file as {@code .bak}. */
	public void save(String key, Profile profile) throws IOException {
		Files.createDirectories(directory);
		Path file = fileFor(key);
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, ProfileCodec.toJson(profile), StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
