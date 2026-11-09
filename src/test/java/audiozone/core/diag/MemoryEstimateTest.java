package audiozone.core.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Object sizes for a 64-bit JVM with compressed references (the default below 32 GB of heap). */
class MemoryEstimateTest {
	@Test
	void objectsHaveA12ByteHeaderAndRoundUpTo8() {
		assertEquals(16, MemoryEstimate.object(0));
		assertEquals(16, MemoryEstimate.object(4));
		assertEquals(24, MemoryEstimate.object(5));
		assertEquals(24, MemoryEstimate.object(12));
	}

	@Test
	void arraysHaveA16ByteHeader() {
		assertEquals(16, MemoryEstimate.array(8, 0));
		assertEquals(1456, MemoryEstimate.array(8, 180));
		assertEquals(736, MemoryEstimate.array(4, 180));
		assertEquals(24, MemoryEstimate.array(1, 3));
	}

	@Test
	void latin1StringsAreAnObjectPlusTheirBytes() {
		assertEquals(24 + 56, MemoryEstimate.string(40));
		assertEquals(24 + 16, MemoryEstimate.string(0));
		assertEquals(0, MemoryEstimate.string(null));
		assertEquals(24 + 56, MemoryEstimate.string("x".repeat(40)));
	}

	@Test
	void hashTablesDoubleWhenThreeQuartersFull() {
		assertEquals(0, MemoryEstimate.hashTableLength(0));
		assertEquals(16, MemoryEstimate.hashTableLength(1));
		assertEquals(16, MemoryEstimate.hashTableLength(12));
		assertEquals(32, MemoryEstimate.hashTableLength(13));
		assertEquals(8192, MemoryEstimate.hashTableLength(4096));
		assertEquals(MemoryEstimate.array(4, 32), MemoryEstimate.hashTable(13));
		assertEquals(0, MemoryEstimate.hashTable(0));
	}

	@Test
	void megabytesUseBinaryUnitsAndOneDecimal() {
		assertEquals("0.0", MemoryEstimate.megabytes(0));
		assertEquals("8.0", MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES));
		assertEquals("1.5", MemoryEstimate.megabytes(3 * 512 * 1024));
		assertEquals("0.1", MemoryEstimate.megabytes(100 * 1024));
	}
}
