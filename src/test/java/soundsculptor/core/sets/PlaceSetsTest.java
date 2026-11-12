package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import soundsculptor.core.Message;

class PlaceSetsTest {
	private static final SoundSet Q = SoundSet.quick(List.of(SetEntry.of("e1", "a")));

	private static List<String> ids(PlaceSets p) {
		return p.attached().stream().map(Attachment::setId).toList();
	}

	@Test
	void quickIsPinnedFirst() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").attach("B").withQuickChanges(Q);
		assertEquals(List.of("quick", "A", "B"), ids(p));
		assertSame(Q, p.quickChanges());
	}

	@Test
	void moveUpNeverPassesQuick() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").withQuickChanges(Q);
		assertEquals(ids(p), ids(p.move("A", -1)));
	}

	@Test
	void quickNeverMoves() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").attach("B").withQuickChanges(Q);
		assertEquals(ids(p), ids(p.move("quick", 1)));
		assertEquals(List.of("quick", "B", "A"), ids(p.move("A", 1)));
	}

	@Test
	void attachTwiceReturnsProblem() {
		PlaceSets p = PlaceSets.EMPTY.attach("A");
		Message m = p.canAttach("A");
		assertEquals("soundsculptor.sets.error.already_attached", m.key());
		assertSame(p, p.attach("A"));
	}

	@Test
	void attachBeyond32ReturnsProblem() {
		PlaceSets p = PlaceSets.EMPTY;
		for (int i = 0; i < 32; i++) {
			p = p.attach("s" + i);
		}
		Message m = p.canAttach("extra");
		assertEquals("soundsculptor.sets.error.place_full", m.key());
		assertEquals(List.of(32), m.args());
		assertSame(p, p.attach("extra"));
		PlaceSets withQuick = p.withQuickChanges(Q);
		assertEquals(32, withQuick.librarySetCount());
		assertEquals(33, withQuick.attached().size());
	}

	@Test
	void detachQuickRemovesQuickChanges() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").withQuickChanges(Q).detach("quick");
		assertNull(p.quickChanges());
		assertEquals(List.of("A"), ids(p));
	}

	@Test
	void quickSavedAsTakesQuickSlot() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").withQuickChanges(Q).quickSavedAs("S");
		assertEquals(List.of("S", "A"), ids(p));
		assertNull(p.quickChanges());
	}

	@Test
	void dropMissingKeepsQuick() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").attach("B").withQuickChanges(Q).dropMissing(Set.of("B"));
		assertEquals(List.of("quick", "B"), ids(p));
	}

	@Test
	void invariantsAreEnforced() {
		assertThrows(IllegalArgumentException.class,
				() -> new PlaceSets(List.of(new Attachment("A", true), new Attachment("A", true)), null));
		assertThrows(IllegalArgumentException.class, () -> new PlaceSets(List.of(new Attachment("quick", true)), null));
		assertThrows(IllegalArgumentException.class, () -> new PlaceSets(List.of(new Attachment("A", true)), Q));
	}

	@Test
	void enabledAndUses() {
		PlaceSets p = PlaceSets.EMPTY.attach("A").setEnabled("A", false);
		assertFalse(p.attached().get(0).enabled());
		assertTrue(p.uses("A"));
		assertFalse(p.uses("B"));
		assertNull(p.withQuickChanges(Q).withQuickChanges(null).quickChanges());
	}
}
