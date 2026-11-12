package soundsculptor.core.sets;

import soundsculptor.core.AtomicFiles;
import soundsculptor.core.Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Library Sound Sets on disk (PRD FR-11.8): one JSON file per set in {@code config/soundsculptor/sets/}, named
 * {@code <id>.json}. Writes are atomic (temp file, then rename) and the previous version is kept as {@code .bak}. A
 * file that fails to load is renamed {@code .broken} and the other sets still load.
 *
 * <p>{@code deleted.json} remembers the names of deleted sets, so a note about a missing set can name it.
 *
 * <p>Not thread-safe; callers save from one background thread.
 */
public final class SetStore {
	/** Files larger than this are not loaded (the same limit as profile files). */
	public static final long MAX_FILE_BYTES = 1024 * 1024;
	/** {@code deleted.json} keeps this many names; the oldest are dropped first. */
	public static final int MAX_DELETED_NAMES = 1024;

	private static final String DELETED_FILE = "deleted.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path directory;

	public SetStore(Path directory) {
		this.directory = directory;
	}

	public Path directory() {
		return directory;
	}

	/** The sets that loaded, and one message for each file that did not (or for the sets beyond the limit). */
	public record LoadAll(SetLibrary library, List<Message> problems) {
		public LoadAll {
			problems = List.copyOf(problems);
		}
	}

	/** The file for a set ID. */
	public Path fileFor(String id) {
		if (!SetCodec.ID_PATTERN.matcher(id).matches() || SoundSet.QUICK_ID.equals(id)) {
			throw new IllegalArgumentException("not a library Sound Set ID: " + id);
		}
		return directory.resolve(id + ".json");
	}

	/**
	 * Loads every set file. A file that cannot be read, is not valid, or whose name is not its set's ID is renamed
	 * {@code <name>.broken} and reported; the rest still load. Backups, {@code .broken} copies, temp files and
	 * {@code deleted.json} are not sets. At most {@link SetLibrary#MAX_SETS} load (the first by file name); the
	 * files beyond that are left alone and one problem is reported. Never throws.
	 */
	public LoadAll loadAll() {
		List<Path> files = setFiles();
		List<SoundSet> sets = new ArrayList<>();
		List<Message> problems = new ArrayList<>();
		int beyondLimit = 0;
		for (Path file : files) {
			if (sets.size() >= SetLibrary.MAX_SETS) {
				beyondLimit++;
				continue;
			}
			String name = file.getFileName().toString();
			Message problem;
			try {
				SoundSet set = readFile(file);
				if (!(set.id() + ".json").equals(name)) {
					problem = Message.of(SetText.ID_MISMATCH, set.id());
				} else {
					sets.add(set);
					continue;
				}
			} catch (SetFormatException e) {
				problem = e.problem();
			} catch (IOException e) {
				problem = Message.of(SetText.UNREADABLE, String.valueOf(e.getMessage()));
			}
			problems.add(Message.of(SetText.FILE_PROBLEM, name, problem));
			setAside(file);
		}
		if (beyondLimit > 0) {
			problems.add(Message.of(SetText.TOO_MANY_SETS, SetLibrary.MAX_SETS, beyondLimit));
		}
		return new LoadAll(new SetLibrary(sets), problems);
	}

	private List<Path> setFiles() {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(directory)) {
			return entries.filter(f -> {
				String name = f.getFileName().toString();
				return name.endsWith(".json") && !name.equals(DELETED_FILE) && Files.isRegularFile(f);
			}).sorted(Comparator.comparing(f -> f.getFileName().toString())).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static SoundSet readFile(Path file) throws IOException, SetFormatException {
		if (Files.size(file) > MAX_FILE_BYTES) {
			throw new SetFormatException(Message.of(SetText.TOO_LARGE));
		}
		return SetCodec.read(Files.readString(file, StandardCharsets.UTF_8));
	}

	private static void setAside(Path file) {
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// The file stays where it is; the problem is still reported.
		}
	}

	/** Saves atomically, keeping the previous file as {@code .bak}. */
	public void save(SoundSet set) throws IOException {
		AtomicFiles.write(fileFor(set.id()), SetCodec.toJson(set));
	}

	/**
	 * Deletes a set's file (kept as {@code .bak}, so it can be recovered by hand) and remembers its name.
	 *
	 * @param name the set's name, for {@link #deletedNames()}
	 */
	public void delete(String id, String name) throws IOException {
		Path file = fileFor(id);
		if (Files.exists(file)) {
			Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		Map<String, String> names = readDeleted();
		names.remove(id); // so a repeated delete counts as the newest
		names.put(id, name);
		while (names.size() > MAX_DELETED_NAMES) {
			names.remove(names.keySet().iterator().next());
		}
		JsonObject map = new JsonObject();
		names.forEach(map::addProperty);
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SetCodec.SCHEMA_VERSION);
		root.add("names", map);
		AtomicFiles.write(directory.resolve(DELETED_FILE), GSON.toJson(root) + "\n");
	}

	/** The last name of each deleted set by ID, oldest deletion first. Empty if the record is missing or unreadable. */
	public Map<String, String> deletedNames() {
		return Collections.unmodifiableMap(readDeleted());
	}

	private Map<String, String> readDeleted() {
		Map<String, String> names = new LinkedHashMap<>();
		Path file = directory.resolve(DELETED_FILE);
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
				return names;
			}
			JsonElement stored = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get("names");
			if (stored == null || !stored.isJsonObject()) {
				return names;
			}
			for (Map.Entry<String, JsonElement> e : stored.getAsJsonObject().entrySet()) {
				if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
					names.put(e.getKey(), e.getValue().getAsString());
				}
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return new LinkedHashMap<>();
		}
		return names;
	}
}
