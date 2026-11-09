package audiozone.core.diag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PRD FR-13.2: fail open, log each error once, more than 10 errors in a minute turns on bypass. */
class ErrorBudgetTest {
	private final ErrorBudget budget = new ErrorBudget();

	@Test
	void eachDistinctErrorIsLoggedOnce() {
		assertTrue(budget.firstTime("NullPointerException at Precedence.decide"));
		assertFalse(budget.firstTime("NullPointerException at Precedence.decide"));
		assertTrue(budget.firstTime("IllegalStateException at RecentSounds.record"));
	}

	@Test
	void tenErrorsInAMinuteDoNotTrip() {
		for (int i = 0; i < 10; i++) {
			assertFalse(budget.recordError(i * 1000L));
		}
		assertFalse(budget.isTripped());
	}

	@Test
	void theEleventhErrorInAMinuteTrips() {
		for (int i = 0; i < 10; i++) {
			budget.recordError(i * 1000L);
		}
		assertTrue(budget.recordError(59_000));
		assertTrue(budget.isTripped());
	}

	@Test
	void errorsSpreadOverMoreThanAMinuteDoNotTrip() {
		for (int i = 0; i < 30; i++) {
			assertFalse(budget.recordError(i * 7_000L), "error " + i);
		}
	}

	@Test
	void tripIsReportedOnlyOnce() {
		for (int i = 0; i < 11; i++) {
			budget.recordError(i);
		}
		assertFalse(budget.recordError(20), "already tripped: the toast shows once");
		assertTrue(budget.isTripped());
	}

	@Test
	void resetStartsAFreshBudget() {
		for (int i = 0; i < 11; i++) {
			budget.recordError(i);
		}
		budget.reset();
		assertFalse(budget.isTripped());
		for (int i = 0; i < 10; i++) {
			assertFalse(budget.recordError(100 + i));
		}
		assertTrue(budget.recordError(200));
	}

	@Test
	void distinctErrorMemoryIsBounded() {
		for (int i = 0; i < ErrorBudget.MAX_DISTINCT_ERRORS + 10; i++) {
			budget.firstTime("error " + i);
		}
		// Past the cap, new errors are no longer logged, so a flood cannot fill the log or the memory.
		assertFalse(budget.firstTime("one more"));
	}
}
