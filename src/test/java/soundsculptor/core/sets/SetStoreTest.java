package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.TestEnglish;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PRD FR-11.8: one file per library Sound Set. */
class SetStoreTest {
	private static final TestEnglish EN = new TestEnglish();

	@TempDir
	Path dir;

	private SetStore store() {
		return new SetStore(dir.resolve("sets"));
	}

	private static SoundSet set(String id, String name) {
		return new SoundSet(id, name, List.of(SetEntry.of("e1", "*" + id + "*")));
	}

	@Test
	void saveAndLoadAllRoundTrip() throws IOException {
		store().save(set("a", "Alpha"));
		store().save(set("b", "Beta"));
		SetStore.LoadAll all = store().loadAll();
		assertEquals(2, all.library().size());
		assertEquals(set("a", "Alpha"), all.library().get("a"));
		assertTrue(all.problems().isEmpty());
	}

	@Test
	void missingFolderLoadsAnEmptyLibrary() {
		SetStore.LoadAll all = store().loadAll();
		assertEquals(0, all.library().size());
		assertTrue(all.problems().isEmpty());
		assertTrue(all.complete(), "no folder means no sets, so attachments to missing sets really are missing");
	}

	@Test
	void unreadableDirectoryReportsProblem() throws IOException {
		Files.writeString(dir.resolve("sets"), "not a folder"); // listing it fails with an IOException
		SetStore.LoadAll all = store().loadAll();
		assertEquals(0, all.library().size());
		assertFalse(all.complete(), "the sets on disk are unknown, so nothing may be pruned");
		assertEquals(1, all.problems().size());
		assertTrue(all.problems().get(0).text(EN).startsWith("The Sound Sets folder could not be read"),
				all.problems().get(0).text(EN));
		assertEquals("not a folder", Files.readString(dir.resolve("sets")), "nothing is moved or rewritten");
	}

	@Test
	void ioErrorWhileListingDoesNotThrow() throws IOException {
		store().save(set("a", "Alpha"));
		Iterator<Path> failing = new Iterator<>() {
			@Override
			public boolean hasNext() {
				throw new UncheckedIOException(new IOException("device not ready"));
			}

			@Override
			public Path next() {
				throw new AssertionError();
			}
		};
		SetStore store = new SetStore(dir.resolve("sets"),
				folder -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(failing, 0), false), SetStore::readText);
		SetStore.LoadAll all = store.loadAll();
		assertFalse(all.complete());
		assertEquals(0, all.library().size());
		assertEquals(1, all.problems().size());
		assertTrue(all.problems().get(0).text(EN).contains("device not ready"), all.problems().get(0).text(EN));
		assertTrue(Files.exists(dir.resolve("sets/a.json")));
	}

	@Test
	void fileWithIoErrorIsNotSetAside() throws IOException {
		store().save(set("a", "Alpha"));
		store().save(set("b", "Beta"));
		SetStore store = new SetStore(dir.resolve("sets"), Files::list, file -> {
			if (file.getFileName().toString().equals("b.json")) {
				throw new AccessDeniedException(file.toString(), null, "locked by another program");
			}
			return SetStore.readText(file);
		});
		SetStore.LoadAll all = store.loadAll();
		assertEquals(1, all.library().size());
		assertTrue(all.library().contains("a"));
		assertFalse(all.complete(), "b may load at the next start, so its attachments must stay");
		assertEquals(1, all.problems().size());
		String text = all.problems().get(0).text(EN);
		assertTrue(text.contains("b.json") && text.contains("left as it is"), text);
		assertTrue(Files.exists(dir.resolve("sets/b.json")), "an unreadable file is not broken");
		assertFalse(Files.exists(dir.resolve("sets/b.json.broken")));
		SetStore.LoadAll later = store().loadAll();
		assertTrue(later.complete());
		assertEquals(2, later.library().size(), "it loads once the disk lets it");
	}

	@Test
	void tooLargeFileIsSetAside() throws IOException {
		Files.createDirectories(dir.resolve("sets"));
		Files.writeString(dir.resolve("sets/big.json"), " ".repeat((int) SetStore.MAX_FILE_BYTES + 1));
		SetStore.LoadAll all = store().loadAll();
		assertEquals(1, all.problems().size());
		assertTrue(all.complete(), "a file too large to be a set is a content problem");
		assertTrue(Files.exists(dir.resolve("sets/big.json.broken")));
	}

	@Test
	void fileThatIsNotUtf8IsSetAside() throws IOException {
		Files.createDirectories(dir.resolve("sets"));
		Files.write(dir.resolve("sets/bad.json"), new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
		SetStore.LoadAll all = store().loadAll();
		assertEquals(1, all.problems().size());
		assertTrue(all.complete(), "bytes that are not text are a content problem, not a disk one");
		assertTrue(Files.exists(dir.resolve("sets/bad.json.broken")));
	}

	@Test
	void brokenSetFileIsSetAsideAndOthersLoad() throws IOException {
		store().save(set("a", "Alpha"));
		store().save(set("c", "Gamma"));
		Files.writeString(dir.resolve("sets/b.json"), "{ not json");
		SetStore.LoadAll all = store().loadAll();
		assertEquals(2, all.library().size());
		assertEquals(1, all.problems().size());
		assertTrue(all.problems().get(0).text(EN).contains("b.json"), all.problems().get(0).text(EN));
		assertTrue(Files.exists(dir.resolve("sets/b.json.broken")));
		assertFalse(Files.exists(dir.resolve("sets/b.json")));
		assertTrue(all.complete(), "a broken file is set aside, so its attachments may go (FR-11.8)");
		assertTrue(store().loadAll().problems().isEmpty());
	}

	@Test
	void fileNameMustMatchTheSetId() throws IOException {
		store().save(set("a", "Alpha"));
		Files.move(dir.resolve("sets/a.json"), dir.resolve("sets/other.json"));
		SetStore.LoadAll all = store().loadAll();
		assertEquals(0, all.library().size());
		assertEquals(1, all.problems().size());
		assertTrue(Files.exists(dir.resolve("sets/other.json.broken")));
	}

	@Test
	void nonSetFilesAreSkipped() throws IOException {
		store().save(set("a", "Alpha"));
		store().save(set("a", "Alpha 2")); // leaves a.json.bak
		store().delete("zz", "Zed");        // leaves deleted.json
		Files.writeString(dir.resolve("sets/x.json.tmp"), "junk");
		Files.writeString(dir.resolve("sets/y.json.broken"), "junk");
		SetStore.LoadAll all = store().loadAll();
		assertEquals(1, all.library().size());
		assertTrue(all.problems().isEmpty());
	}

	@Test
	void moreThan256SetsLoadTheFirstOnesAndReportOnce() throws IOException {
		for (int i = 0; i < SetLibrary.MAX_SETS + 3; i++) {
			store().save(set(String.format("s%03d", i), "Set " + i));
		}
		SetStore.LoadAll all = store().loadAll();
		assertEquals(SetLibrary.MAX_SETS, all.library().size());
		assertTrue(all.library().contains("s000") && all.library().contains("s255"));
		assertFalse(all.library().contains("s256"));
		assertEquals(1, all.problems().size());
		assertTrue(all.problems().get(0).text(EN).contains("3 more"), all.problems().get(0).text(EN));
		assertTrue(Files.exists(dir.resolve("sets/s256.json")));
		assertFalse(all.complete(), "the sets left on disk still exist, so their attachments must stay");
	}

	@Test
	void saveKeepsBak() throws IOException {
		store().save(set("a", "Alpha"));
		store().save(set("a", "Alpha 2"));
		assertEquals("Alpha", readBak());
		assertEquals("Alpha 2", store().loadAll().library().get("a").name());
		assertFalse(Files.exists(dir.resolve("sets/a.json.tmp")));
	}

	private String readBak() throws IOException {
		try {
			return SetCodec.read(Files.readString(dir.resolve("sets/a.json.bak"))).name();
		} catch (SetFormatException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void deleteRecordsName() throws IOException {
		store().save(set("a", "Alpha"));
		store().delete("a", "Alpha");
		assertFalse(Files.exists(dir.resolve("sets/a.json")));
		assertTrue(Files.exists(dir.resolve("sets/a.json.bak")));
		assertEquals(Map.of("a", "Alpha"), store().deletedNames());
		assertEquals(0, store().loadAll().library().size());
	}

	@Test
	void deletedNamesKeepTheNewest1024() throws IOException {
		SetStore store = store();
		for (int i = 0; i < SetStore.MAX_DELETED_NAMES + 2; i++) {
			store.delete("s" + i, "Set " + i);
		}
		Map<String, String> names = store.deletedNames();
		assertEquals(SetStore.MAX_DELETED_NAMES, names.size());
		assertNull(names.get("s0"));
		assertNull(names.get("s1"));
		assertEquals("Set 2", names.get("s2"));
		assertEquals("Set 1025", names.get("s1025"));
	}

	@Test
	void unreadableDeletedRecordGivesNoNames() throws IOException {
		Files.createDirectories(dir.resolve("sets"));
		Files.writeString(dir.resolve("sets/deleted.json"), "{ nope");
		assertTrue(store().deletedNames().isEmpty());
		Files.writeString(dir.resolve("sets/deleted.json"), "[1]");
		assertTrue(store().deletedNames().isEmpty());
		store().delete("a", "Alpha"); // recovers by rewriting
		assertEquals(Map.of("a", "Alpha"), store().deletedNames());
	}

	@Test
	void refusesIdsThatAreNotLibraryIds() {
		assertThrows(IllegalArgumentException.class, () -> store().save(SoundSet.quick(List.of())));
		assertThrows(IllegalArgumentException.class, () -> store().fileFor("../x"));
		assertThrows(IllegalArgumentException.class, () -> store().fileFor("deleted"));
		assertThrows(IllegalArgumentException.class, () -> store().fileFor("con"));
	}
}
