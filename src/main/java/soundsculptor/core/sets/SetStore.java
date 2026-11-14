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
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Library Sound Sets on disk (PRD FR-11.8): one JSON file per set in {@code config/soundsculptor/sets/}, named
 * {@code <id>.json}. Writes are atomic (temp file, then rename) and the previous version is kept as {@code .bak}. A
 * file that is not a valid set is renamed {@code .broken} and the other sets still load. A file the disk would not
 * read is left as it is and the load is marked incomplete, so no attachment is dropped because of it (PRD 9.2).
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

	/** Errors from the folder listing after which the rest of it is not asked for. */
	private static final int MAX_LISTING_ERRORS = 16;
	private static final String DELETED_FILE = "deleted.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path directory;
	private final DiskRead<Stream<Path>> lister;
	private final DiskRead<String> reader;

	public SetStore(Path directory) {
		this(directory, Files::list, SetStore::readText);
	}

	/** For tests: {@code lister} lists the folder and {@code reader} reads one file, so disk failures can be staged. */
	SetStore(Path directory, DiskRead<Stream<Path>> lister, DiskRead<String> reader) {
		this.directory = directory;
		this.lister = lister;
		this.reader = reader;
	}

	/** One read from the disk. */
	@FunctionalInterface
	interface DiskRead<T> {
		T read(Path path) throws IOException;
	}

	/** A file's text as UTF-8. */
	static String readText(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	public Path directory() {
		return directory;
	}

	/**
	 * The sets that loaded, and one message for each file that did not (or for the sets beyond the limit).
	 *
	 * @param complete whether the library holds every set file in the folder, so an attachment to a set it lacks
	 *                 really is to a missing set. False when the folder or a file could not be read, or when sets
	 *                 beyond the limit were left on disk: those sets may load at the next start.
	 */
	public record LoadAll(SetLibrary library, List<Message> problems, boolean complete) {
		public LoadAll {
			Objects.requireNonNull(library, "library");
			problems = List.copyOf(problems);
		}
	}

	/** The file for a set ID. */
	public Path fileFor(String id) {
		if (!SetCodec.ID_PATTERN.matcher(id).matches() || SetCodec.isReservedId(id)) {
			throw new IllegalArgumentException("not a library Sound Set ID: " + id);
		}
		return directory.resolve(id + ".json");
	}

	/**
	 * Loads every set file; the others still load when one does not. Never throws.
	 *
	 * <ul>
	 *   <li>A file that is not a valid set, or whose name is not its set's ID, is renamed {@code <name>.broken} and
	 *   reported (FR-11.8).</li>
	 *   <li>A file the disk would not read (locked, no permission, device error) is not broken: it is left as it is,
	 *   reported, and the load is {@linkplain LoadAll#complete() incomplete}. So is a folder that cannot be
	 *   listed; when the listing fails part way, the sets it did list still load.</li>
	 *   <li>Backups, {@code .broken} copies, temp files and {@code deleted.json} are not sets.</li>
	 *   <li>At most {@link SetLibrary#MAX_SETS} load (the first by file name); the files beyond that are left alone,
	 *   one problem is reported and the load is incomplete.</li>
	 * </ul>
	 */
	public LoadAll loadAll() {
		Listing listing;
		try {
			listing = setFiles();
		} catch (IOException e) {
			return unlisted(e);
		} catch (UncheckedIOException e) {
			return unlisted(e.getCause());
		}
		List<Path> files = listing.files();
		if (listing.failure() != null && files.isEmpty()) {
			return unlisted(listing.failure());
		}
		List<SoundSet> sets = new ArrayList<>();
		List<Message> problems = new ArrayList<>();
		boolean complete = true;
		if (listing.failure() != null) {
			// The sets the listing did give still load; one it could not give may be missing, so nothing is pruned.
			problems.add(Message.of(SetText.FOLDER_PARTLY_UNREADABLE, String.valueOf(listing.failure().getMessage())));
			complete = false;
		}
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
			} catch (CharacterCodingException e) {
				problem = Message.of(SetText.UNREADABLE, String.valueOf(e.getMessage())); // the bytes are not text
			} catch (IOException e) {
				problems.add(Message.of(SetText.FILE_UNREADABLE, name, String.valueOf(e.getMessage())));
				complete = false;
				continue;
			}
			problems.add(Message.of(SetText.FILE_PROBLEM, name, problem));
			setAside(file);
		}
		if (beyondLimit > 0) {
			problems.add(Message.of(SetText.TOO_MANY_SETS, SetLibrary.MAX_SETS, beyondLimit));
			complete = false;
		}
		return new LoadAll(new SetLibrary(sets), problems, complete);
	}

	private static LoadAll unlisted(IOException e) {
		return new LoadAll(SetLibrary.EMPTY, List.of(Message.of(SetText.FOLDER_UNREADABLE, String.valueOf(e.getMessage()))),
				false);
	}

	/**
	 * The set files the folder listing gave, by name, and the first error it reported on the way, or {@code null}.
	 */
	private record Listing(List<Path> files, IOException failure) {
	}

	/**
	 * The set files, by name; none when the folder does not exist. An entry the listing cannot read is skipped and the
	 * listing goes on, up to {@link #MAX_LISTING_ERRORS} such errors, so one bad entry does not hide the other sets.
	 *
	 * @throws IOException if the folder cannot be listed at all
	 */
	private Listing setFiles() throws IOException {
		if (Files.notExists(directory)) {
			return new Listing(List.of(), null);
		}
		List<Path> files = new ArrayList<>();
		IOException failure = null;
		int errors = 0;
		try (Stream<Path> entries = lister.read(directory)) {
			Iterator<Path> it = entries.iterator();
			while (true) {
				Path file;
				try {
					if (!it.hasNext()) {
						break;
					}
					file = it.next();
				} catch (UncheckedIOException e) {
					if (failure == null) {
						failure = e.getCause();
					}
					if (++errors >= MAX_LISTING_ERRORS) {
						break; // the listing keeps failing: stop rather than ask forever
					}
					continue;
				}
				String name = file.getFileName().toString();
				if (name.endsWith(".json") && !name.equals(DELETED_FILE) && Files.isRegularFile(file)) {
					files.add(file);
				}
			}
		}
		files.sort(Comparator.comparing(f -> f.getFileName().toString()));
		return new Listing(files, failure);
	}

	private SoundSet readFile(Path file) throws IOException, SetFormatException {
		if (Files.size(file) > MAX_FILE_BYTES) {
			throw new SetFormatException(Message.of(SetText.TOO_LARGE));
		}
		return SetCodec.read(reader.read(file));
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
		save(set, true);
	}

	/** Like {@link #save(SoundSet)}; see {@link AtomicFiles#write(Path, String, boolean)} for {@code waitOutLock}. */
	public void save(SoundSet set, boolean waitOutLock) throws IOException {
		AtomicFiles.write(fileFor(set.id()), SetCodec.toJson(set), waitOutLock);
	}

	/**
	 * Remembers a set's name, then deletes its file (kept as {@code .bak}, so it can be recovered by hand). In that
	 * order, so a failure part way never loses both: if the name cannot be recorded the file stays, and if the file
	 * cannot be moved the name is already recorded and the delete can simply be tried again.
	 *
	 * @param name the set's name, for {@link #deletedNames()}
	 */
	public void delete(String id, String name) throws IOException {
		Path file = fileFor(id);
		rememberDeleted(id, name);
		if (Files.exists(file)) {
			Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void rememberDeleted(String id, String name) throws IOException {
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
