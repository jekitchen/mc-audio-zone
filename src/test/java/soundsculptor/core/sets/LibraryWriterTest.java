package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PRD 9.2: a library set whose save failed is written again, so the next start does not lose it. */
class LibraryWriterTest {
	@TempDir
	Path dir;

	private final List<String> failures = new ArrayList<>();

	private SetStore store() {
		return new SetStore(dir.resolve("sets"));
	}

	private void failed(Path file, boolean deleting, IOException e) {
		failures.add((deleting ? "delete " : "save ") + file.getFileName());
	}

	private static SoundSet set(String id, String name) {
		return new SoundSet(id, name, List.of(SetEntry.of("e1", "*" + id + "*")));
	}

	private static SetLibrary library(SoundSet... sets) {
		return new SetLibrary(List.of(sets));
	}

	/** Saves fail while a plain file stands where the sets folder should be. */
	private void blockSaves() throws IOException {
		Files.writeString(dir.resolve("sets"), "in the way");
	}

	private void unblockSaves() throws IOException {
		Files.delete(dir.resolve("sets"));
	}

	/** Deletes fail while a non-empty folder stands where {@code deleted.json} should be. */
	private void blockDeletes() throws IOException {
		Files.createDirectories(dir.resolve("sets/deleted.json"));
		Files.writeString(dir.resolve("sets/deleted.json/x"), "in the way");
	}

	private void unblockDeletes() throws IOException {
		Files.delete(dir.resolve("sets/deleted.json/x"));
		Files.delete(dir.resolve("sets/deleted.json"));
	}

	private String nameOnDisk(String id) throws IOException {
		return store().loadAll().library().get(id).name();
	}

	@Test
	void failedSaveIsRetriedWithTheNextChange() throws IOException {
		LibraryWriter writer = new LibraryWriter(store());
		SoundSet a = set("a", "Alpha");
		blockSaves();
		writer.save(library(a), List.of(a), this::failed);
		assertEquals(List.of("save a.json"), failures);
		assertEquals(Set.of("a"), writer.pending());

		unblockSaves();
		SoundSet b = set("b", "Beta");
		writer.save(library(a, b), List.of(b), this::failed);
		assertEquals(Set.of(), writer.pending());
		assertEquals("Alpha", nameOnDisk("a"), "the unchanged set that failed before is written too");
		assertEquals("Beta", nameOnDisk("b"));
		assertEquals(1, failures.size());
	}

	@Test
	void retryWritesTheLatestVersion() throws IOException {
		LibraryWriter writer = new LibraryWriter(store());
		blockSaves();
		SoundSet a1 = set("a", "Alpha");
		writer.save(library(a1), List.of(a1), this::failed);
		SoundSet a2 = set("a", "Alpha 2");
		writer.save(library(a2), List.of(a2), this::failed);
		assertEquals(2, failures.size());

		unblockSaves();
		writer.retry(this::failed);
		assertEquals(Set.of(), writer.pending());
		assertEquals("Alpha 2", nameOnDisk("a"));
	}

	@Test
	void failedSaveOfASetThatLeftIsDropped() throws IOException {
		LibraryWriter writer = new LibraryWriter(store());
		SoundSet a = set("a", "Alpha");
		blockSaves();
		writer.save(library(a), List.of(a), this::failed);
		unblockSaves();
		writer.save(SetLibrary.EMPTY, List.of(), this::failed);
		writer.delete(SetLibrary.EMPTY, List.of(a), this::failed);
		assertEquals(Set.of(), writer.pending());
		assertFalse(Files.exists(dir.resolve("sets/a.json")));
		assertEquals(Map.of("a", "Alpha"), store().deletedNames());
	}

	@Test
	void failedDeleteIsRetried() throws IOException {
		LibraryWriter writer = new LibraryWriter(store());
		SoundSet a = set("a", "Alpha");
		writer.save(library(a), List.of(a), this::failed);
		blockDeletes();
		writer.delete(SetLibrary.EMPTY, List.of(a), this::failed);
		assertEquals(List.of("delete a.json"), failures);
		assertEquals(Set.of("a"), writer.pending());

		unblockDeletes();
		writer.retry(this::failed);
		assertEquals(Set.of(), writer.pending());
		assertEquals(Map.of("a", "Alpha"), store().deletedNames());
		assertEquals(0, store().loadAll().library().size());
	}

	@Test
	void restoringASetCancelsItsFailedDelete() throws IOException {
		LibraryWriter writer = new LibraryWriter(store());
		SoundSet a = set("a", "Alpha");
		writer.save(library(a), List.of(a), this::failed);
		blockDeletes();
		writer.delete(SetLibrary.EMPTY, List.of(a), this::failed);

		writer.save(library(a), List.of(a), this::failed); // Undo brings it back
		assertEquals(Set.of(), writer.pending());
		unblockDeletes();
		writer.retry(this::failed);
		assertEquals("Alpha", nameOnDisk("a"));
		assertEquals(Map.of(), store().deletedNames());
	}

	@Test
	void retryWithNothingPendingWritesNothing() {
		LibraryWriter writer = new LibraryWriter(store());
		writer.retry(this::failed);
		assertFalse(Files.exists(dir.resolve("sets")));
		assertTrue(failures.isEmpty());
	}
}
