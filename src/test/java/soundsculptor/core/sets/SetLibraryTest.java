package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import soundsculptor.core.Message;

class SetLibraryTest {
	private static SoundSet set(String id, String name) {
		return new SoundSet(id, name, List.of());
	}

	@Test
	void uniqueNameAddsNumber() {
		SetLibrary lib = SetLibrary.EMPTY.put(set("a", "Farm animals"));
		assertEquals("Farm animals (2)", lib.uniqueName("Farm animals", null));
		lib = lib.put(set("b", "Farm animals (2)"));
		assertEquals("Farm animals (3)", lib.uniqueName("Farm animals", null));
		assertEquals("Farm animals", lib.uniqueName("Farm animals", "a"));
		assertEquals("Other", lib.uniqueName("Other", null));
	}

	@Test
	void createBeyond256ReturnsProblem() {
		List<SoundSet> sets = new ArrayList<>();
		for (int i = 0; i < 256; i++) {
			sets.add(set("s" + i, "N" + i));
		}
		SetLibrary lib = new SetLibrary(sets);
		Message m = lib.canCreate();
		assertEquals("soundsculptor.sets.error.library_full", m.key());
		assertEquals(List.of(256), m.args());
		assertNull(SetLibrary.EMPTY.canCreate());
		sets.add(set("x", "X"));
		assertThrows(IllegalArgumentException.class, () -> new SetLibrary(sets));
	}

	@Test
	void setsSortedByName() {
		SetLibrary lib = SetLibrary.EMPTY.put(set("1", "banana")).put(set("2", "Apple")).put(set("3", "cherry"));
		assertEquals(List.of("Apple", "banana", "cherry"), lib.sets().stream().map(SoundSet::name).toList());
	}

	@Test
	void newIdIsUniqueAndWellFormed() {
		SetLibrary lib = SetLibrary.EMPTY;
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			String id = lib.newId();
			assertTrue(id.matches("set-[0-9a-f]{12}"), id);
			assertTrue(seen.add(id));
			lib = lib.put(set(id, "n" + i));
		}
	}

	@Test
	void rejectsDuplicatesAndQuick() {
		assertThrows(IllegalArgumentException.class, () -> new SetLibrary(List.of(set("a", "x"), set("a", "y"))));
		assertThrows(IllegalArgumentException.class, () -> new SetLibrary(List.of(SoundSet.quick(List.of()))));
	}

	@Test
	void putReplacesAndRemoveDrops() {
		SetLibrary lib = SetLibrary.EMPTY.put(set("a", "x")).put(set("a", "y"));
		assertEquals(1, lib.size());
		assertEquals("y", lib.get("a").name());
		assertTrue(lib.contains("a"));
		assertEquals(Set.of("a"), lib.ids());
		assertEquals(0, lib.remove("a").size());
	}
}
