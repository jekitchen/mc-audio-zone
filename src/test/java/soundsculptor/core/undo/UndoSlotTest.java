package soundsculptor.core.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** PRD FR-12.5: a quick action shows a 5-second toast with Undo. */
class UndoSlotTest {
	private static final String MUTED = "soundsculptor.toast.muted";

	@Test
	void emptySlotHasNothingToUndo() {
		UndoSlot<String> slot = new UndoSlot<>();
		assertFalse(slot.isActive(0));
		assertEquals(Optional.empty(), slot.undo("now", 0));
		assertEquals(0, slot.remainingMs(0));
		assertEquals(Optional.empty(), slot.toast(0));
	}

	@Test
	void undoWithinFiveSecondsGivesTheStateBefore() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of("minecraft:entity.cow.ambient"), 1000);
		assertTrue(slot.isActive(1000));
		assertEquals(Optional.of("before"), slot.undo("after", 1000 + UndoSlot.UNDO_MS - 1));
	}

	@Test
	void undoIsUsedUpAfterOneUse() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 0);
		assertEquals(Optional.of("before"), slot.undo("after", 10));
		assertEquals(Optional.empty(), slot.undo("after", 20));
		assertFalse(slot.isActive(20));
	}

	@Test
	void expiresAtFiveSeconds() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 1000);
		assertEquals(5000, UndoSlot.UNDO_MS);
		assertFalse(slot.isActive(1000 + UndoSlot.UNDO_MS));
		assertEquals(Optional.empty(), slot.undo("after", 1000 + UndoSlot.UNDO_MS));
	}

	@Test
	void remainingCountsDownForTheToast() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 1000);
		assertEquals(5000, slot.remainingMs(1000));
		assertEquals(3000, slot.remainingMs(3000));
		assertEquals(0, slot.remainingMs(7000));
	}

	@Test
	void newerActionReplacesTheOlderOne() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("a", "b", MUTED, List.of("x"), 0);
		slot.offer("b", "c", "soundsculptor.toast.unmuted", List.of("y"), 4000);
		// The newer action gets its own full 5 seconds.
		assertEquals(5000, slot.remainingMs(4000));
		UndoSlot.Toast toast = slot.toast(4000).orElseThrow();
		assertEquals("soundsculptor.toast.unmuted", toast.messageKey());
		assertEquals(List.of("y"), toast.args());
		assertEquals(Optional.of("b"), slot.undo("c", 6000));
	}

	@Test
	void staleUndoIsRefusedWhenSomethingElseChangedTheState() {
		// After the quick action, an editor save changed the profile again. Undoing would wipe that save too.
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 0);
		assertEquals(Optional.empty(), slot.undo("edited", 100));
		assertFalse(slot.isActive(100), "a stale undo is dropped, so the toast closes");
	}

	@Test
	void staysWhileTheUndoButtonHasFocus() {
		// Decisions log (A-6): the toast stays while its Undo button has keyboard focus.
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 0);
		slot.hold(1000);
		assertTrue(slot.isActive(60_000));
		assertEquals(4000, slot.remainingMs(60_000), "the countdown is paused");
		slot.release(60_000);
		assertTrue(slot.isActive(63_999));
		assertFalse(slot.isActive(64_000));
	}

	@Test
	void holdAndReleaseTwiceDoNotStackUp() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 0);
		slot.hold(1000);
		slot.hold(2000);
		slot.release(3000);
		slot.release(4000);
		assertEquals(4000, slot.remainingMs(3000));
		assertFalse(slot.isActive(7000));
	}

	@Test
	void newOfferEndsAHold() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("a", "b", MUTED, List.of(), 0);
		slot.hold(1000);
		slot.offer("b", "c", MUTED, List.of(), 2000);
		assertFalse(slot.isActive(7000));
	}

	@Test
	void holdAfterExpiryDoesNotRevive() {
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 0);
		slot.hold(6000);
		assertFalse(slot.isActive(6000));
		assertEquals(Optional.empty(), slot.undo("after", 6000));
	}

	@Test
	void clearDropsTheAction() {
		// For example when the player leaves the server: the change belongs to that profile.
		UndoSlot<String> slot = new UndoSlot<>();
		slot.offer("before", "after", MUTED, List.of(), 0);
		slot.clear();
		assertFalse(slot.isActive(0));
		assertEquals(Optional.empty(), slot.undo("after", 0));
	}

	@Test
	void rejectsMissingValues() {
		UndoSlot<String> slot = new UndoSlot<>();
		assertThrows(NullPointerException.class, () -> slot.offer(null, "a", MUTED, List.of(), 0));
		assertThrows(NullPointerException.class, () -> slot.offer("a", null, MUTED, List.of(), 0));
		assertThrows(NullPointerException.class, () -> slot.offer("a", "b", null, List.of(), 0));
	}

	@Test
	void toastArgsAreCopied() {
		UndoSlot<String> slot = new UndoSlot<>();
		java.util.ArrayList<String> args = new java.util.ArrayList<>(List.of("x"));
		slot.offer("a", "b", MUTED, args, 0);
		args.add("y");
		assertEquals(List.of("x"), slot.toast(0).orElseThrow().args());
	}
}
