package audiozone.core.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import audiozone.core.Allocations;
import org.junit.jupiter.api.Test;

/** PRD 9.1 / 11.2: the Diagnostics memory readout, refreshed at most once a second. */
class MemoryMeterTest {
	@Test
	void totalIsTheSumOfItsParts() {
		MemoryMeter meter = new MemoryMeter();
		meter.add("recent", () -> 1000);
		meter.add("limits", () -> 234);
		assertEquals(1234, meter.totalBytes(0));
		assertEquals(2, meter.partCount());
		assertEquals("recent", meter.name(0));
		assertEquals(234, meter.bytes(1, 0));
	}

	@Test
	void recomputesAtMostOnceASecond() {
		AtomicLong size = new AtomicLong(100);
		AtomicInteger calls = new AtomicInteger();
		MemoryMeter meter = new MemoryMeter();
		meter.add("recent", () -> {
			calls.incrementAndGet();
			return size.get();
		});
		assertEquals(100, meter.totalBytes(5_000));
		size.set(200);
		assertEquals(100, meter.totalBytes(5_999));
		assertEquals(100, meter.bytes(0, 5_500));
		assertEquals(1, calls.get());
		assertEquals(200, meter.totalBytes(6_000));
		assertEquals(2, calls.get());
	}

	@Test
	void addingAPartRefreshesOnTheNextRead() {
		MemoryMeter meter = new MemoryMeter();
		meter.add("recent", () -> 100);
		assertEquals(100, meter.totalBytes(0));
		meter.add("limits", () -> 50);
		assertEquals(150, meter.totalBytes(1));
	}

	@Test
	void overBudgetComparesWithEightMegabytes() {
		AtomicLong size = new AtomicLong(MemoryEstimate.BUDGET_BYTES);
		MemoryMeter meter = new MemoryMeter();
		meter.add("all", size::get);
		assertFalse(meter.overBudget(0));
		size.set(MemoryEstimate.BUDGET_BYTES + 1);
		assertTrue(meter.overBudget(MemoryMeter.REFRESH_MS));
	}

	@Test
	void namesMustBeGiven() {
		MemoryMeter meter = new MemoryMeter();
		assertThrows(NullPointerException.class, () -> meter.add(null, () -> 1));
		assertThrows(NullPointerException.class, () -> meter.add("x", null));
	}

	@Test
	void readingBetweenRefreshesAllocatesNothing() {
		MemoryMeter meter = new MemoryMeter();
		meter.add("recent", () -> 100);
		meter.totalBytes(0);
		long[] sink = new long[1];
		for (int i = 0; i < 10_000; i++) {
			sink[0] += meter.totalBytes(1) + meter.bytes(0, 1);
		}
		long bytes = Allocations.fewestBytes(round -> {
			for (int i = 0; i < 1000; i++) {
				sink[0] += meter.totalBytes(2) + meter.bytes(0, 2);
			}
		});
		assertEquals(0, bytes);
	}
}
