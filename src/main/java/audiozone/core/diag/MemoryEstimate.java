package audiozone.core.diag;

import java.util.Locale;

/**
 * Rough object sizes for the Diagnostics memory readout (PRD 9.1: "Memory used by recent log, linker and limiters:
 * under 8 MB").
 *
 * <p>The sizes assume a 64-bit JVM with compressed references, which is the default for heaps under 32 GB: a 12-byte
 * object header, 4-byte references, a 16-byte array header, and every object rounded up to 8 bytes. Strings are
 * Latin-1 (sound IDs are ASCII). Each structure counts every string it keeps as its own copy, even when another
 * structure holds the same one, so the total errs high rather than low.
 */
public final class MemoryEstimate {
	/** The PRD 9.1 budget for Audio Zone's own data. */
	public static final long BUDGET_BYTES = 8L * 1024 * 1024;

	public static final int REFERENCE = 4;
	private static final int OBJECT_HEADER = 12;
	private static final int ARRAY_HEADER = 16;

	/** {@code HashMap.Node}: hash, key, value, next. */
	public static final long HASH_NODE = object(4 + 3 * REFERENCE);
	/** {@code LinkedHashMap.Entry}: a node plus before and after. */
	public static final long LINKED_HASH_NODE = object(4 + 5 * REFERENCE);
	/** A {@code HashMap} or {@code HashSet}'s own fields, without its table. */
	public static final long HASH_MAP = object(4 * REFERENCE + 3 * 4 + 4);
	/** A {@code LinkedHashMap}'s own fields, without its table. */
	public static final long LINKED_HASH_MAP = object(6 * REFERENCE + 3 * 4 + 4 + 1);
	/** A {@code HashSet}: a wrapper around a {@code HashMap}. */
	public static final long HASH_SET = object(REFERENCE) + HASH_MAP;
	/** A {@code ConcurrentHashMap}'s own fields, without its table. */
	public static final long CONCURRENT_HASH_MAP = object(5 * REFERENCE + 8 + 3 * 4);

	private MemoryEstimate() {
	}

	/** An object with {@code fieldBytes} of fields. */
	public static long object(int fieldBytes) {
		return align(OBJECT_HEADER + (long) fieldBytes);
	}

	/** An array of {@code length} elements of {@code elementBytes} each (use {@link #REFERENCE} for objects). */
	public static long array(int elementBytes, int length) {
		return align(ARRAY_HEADER + (long) elementBytes * length);
	}

	/** A Latin-1 string of {@code length} characters: the {@code String} object and its byte array. */
	public static long string(int length) {
		return object(REFERENCE + 4 + 1 + 1) + array(1, length);
	}

	/** {@code s}'s size, or 0 for {@code null}. */
	public static long string(String s) {
		return s == null ? 0 : string(s.length());
	}

	/** The table length a hash map grown one entry at a time reaches for {@code size} entries (0 if empty). */
	public static int hashTableLength(int size) {
		if (size == 0) {
			return 0;
		}
		int length = 16;
		while (size > length * 3 / 4) {
			length <<= 1;
		}
		return length;
	}

	/** The table array of a hash map holding {@code size} entries. */
	public static long hashTable(int size) {
		int length = hashTableLength(size);
		return length == 0 ? 0 : array(REFERENCE, length);
	}

	/** Bytes as megabytes (1 MB = 1024 × 1024 bytes) with one decimal, for the readout, e.g. {@code "1.5"}. */
	public static String megabytes(long bytes) {
		return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
	}

	private static long align(long bytes) {
		return (bytes + 7) & ~7L;
	}
}
