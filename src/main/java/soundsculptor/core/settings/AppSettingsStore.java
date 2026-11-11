package soundsculptor.core.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The app-wide settings file on disk, for example {@code config/soundsculptor/settings.json}. Like profile files
 * (PRD FR-11.8), writes are atomic (temp file, then rename) and the previous version is kept as {@code .bak}. A file
 * that cannot be read at all is renamed {@code .broken} and defaults are used; a file with some bad values keeps its
 * good values and stays in place (see {@link AppSettingsCodec}).
 *
 * <p>Not thread-safe; callers save from one background thread.
 */
public final class AppSettingsStore {
	/** Files larger than this are not read; a real settings file is well under 1 KB. */
	public static final long MAX_FILE_BYTES = 64 * 1024;

	private final Path file;

	public AppSettingsStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/**
	 * The outcome of loading.
	 *
	 * @param problems   what was wrong with the file, in plain words for the log; empty if nothing
	 * @param brokenCopy where an unreadable file was moved, or {@code null}
	 */
	public record LoadResult(AppSettings settings, List<String> problems, Path brokenCopy) {
		public LoadResult {
			problems = List.copyOf(problems);
		}

		public boolean hasProblems() {
			return !problems.isEmpty();
		}
	}

	/** Loads the settings; a missing file gives the defaults without a problem. */
	public LoadResult load() {
		if (!Files.exists(file)) {
			return new LoadResult(AppSettings.DEFAULTS, List.of(), null);
		}
		String problem;
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				problem = "The file is larger than 64 KB.";
			} else {
				AppSettingsCodec.Read read = AppSettingsCodec.read(Files.readString(file, StandardCharsets.UTF_8));
				return new LoadResult(read.settings(), read.problems(), null);
			}
		} catch (AppSettingsFormatException e) {
			problem = e.getMessage();
		} catch (IOException e) {
			problem = "The file could not be read: " + e.getMessage();
		}
		Path broken = sibling(".broken");
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			broken = null;
		}
		return new LoadResult(AppSettings.DEFAULTS, List.of(problem), broken);
	}

	/** Saves atomically, keeping the previous file as {@code .bak}. Creates the folder if needed. */
	public void save(AppSettings settings) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path temp = sibling(".tmp");
		Files.writeString(temp, AppSettingsCodec.toJson(settings), StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Files.copy(file, sibling(".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Path sibling(String suffix) {
		return file.resolveSibling(file.getFileName() + suffix);
	}
}
