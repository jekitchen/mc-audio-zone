package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import soundsculptor.core.Message;

class SoundSetTest {
	private static List<SetEntry> entries(int n) {
		List<SetEntry> list = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			list.add(SetEntry.of("e" + i, "minecraft:x" + i));
		}
		return list;
	}

	@Test
	void entryIdsMustBeUnique() {
		assertThrows(IllegalArgumentException.class, () -> new SoundSet("s", "S",
				List.of(SetEntry.of("e1", "a"), SetEntry.of("e1", "b"))));
	}

	@Test
	void moreThan512EntriesIsRejected() {
		assertDoesNotThrow(() -> new SoundSet("s", "S", entries(512)));
		assertThrows(IllegalArgumentException.class, () -> new SoundSet("s", "S", entries(513)));
	}

	@Test
	void canAddReportsFullAt512() {
		Message m = new SoundSet("s", "S", entries(512)).canAdd(1);
		assertEquals("soundsculptor.sets.error.entries_full", m.key());
		assertEquals(List.of(512), m.args());
		assertNull(new SoundSet("s", "S", entries(511)).canAdd(1));
	}

	@Test
	void sameContentsComparesNameAndEntries() {
		SoundSet a = new SoundSet("a", "Farm", entries(2));
		SoundSet b = new SoundSet("b", "Farm", entries(2));
		assertTrue(a.sameContents(b));
		assertFalse(a.sameContents(b.withName("Other")));
		assertFalse(a.sameContents(b.removeEntry("e2")));
		assertFalse(a.sameContents(b.moveEntry("e1", 1)));
	}

	@Test
	void newEntryIdSkipsUsedIds() {
		assertEquals("e1", new SoundSet("s", "S", List.of()).newEntryId());
		SoundSet s = new SoundSet("s", "S", List.of(SetEntry.of("e1", "a"), SetEntry.of("e3", "b")));
		assertEquals("e2", s.newEntryId());
	}

	@Test
	void quickAndMutators() {
		SoundSet q = SoundSet.quick(entries(1));
		assertTrue(q.isQuick());
		assertEquals("", q.name());
		SoundSet s = new SoundSet("s", "S", entries(2));
		assertEquals(2, s.putEntry(SetEntry.of("e1", "z")).entries().size());
		assertEquals(3, s.putEntry(SetEntry.of("e3", "z")).entries().size());
		assertNull(s.entry("nope"));
		assertEquals("e2", s.moveEntry("e2", -1).entries().get(0).id());
		assertEquals("e1", s.moveEntry("e1", -1).entries().get(0).id());
	}
}
