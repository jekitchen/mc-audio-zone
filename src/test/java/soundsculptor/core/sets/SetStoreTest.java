package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.TestEnglish;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
	}
}
