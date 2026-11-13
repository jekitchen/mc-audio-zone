package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** PRD 9.2: a profile whose save failed is kept, offered to readers and written again; retries stay quick. */
class ProfileWriterTest {
	private static final String KEY = "play.example.net";

	@TempDir
	Path dir;

	private final List<String> failures = new ArrayList<>();

	private ProfileStore store() {
		return new ProfileStore(dir.resolve("profiles"));
	}

	private void failed(Path file, IOException e) {
		failures.add(file.getFileName().toString());
	}

	/** Saves fail while a plain file stands where the profiles folder should be. */
	private void blockSaves() throws IOException {
		Files.writeString(dir.resolve("profiles"), "in the way");
	}

	private void unblockSaves() throws IOException {
		Files.delete(dir.resolve("profiles"));
	}

	private String nameOnDisk() {
		return store().load(KEY, "(none)").profile().name();
	}

	@Test
	void queuedProfileIsOfferedUntilWritten() {
		ProfileWriter writer = new ProfileWriter(store());
		Profile p = Profile.empty("Queued");
		writer.queue(KEY, p);
		assertEquals(p, writer.latest(KEY), "readers get the queued profile, not the older file");
		assertTrue(writer.save(KEY, p, this::failed));
		assertNull(writer.latest(KEY), "once on disk, nothing is pending");
		assertEquals("Queued", nameOnDisk());
	}

	@Test
	void failedSaveIsKeptOfferedAndWrittenByRetry() throws IOException {
		ProfileWriter writer = new ProfileWriter(store());
		blockSaves();
		Profile first = Profile.empty("First");
		writer.queue(KEY, first);
		assertFalse(writer.save(KEY, first, this::failed));
		assertEquals(first, writer.latest(KEY), "a failed save is still offered to readers");
		assertEquals(Set.of(KEY), writer.unsavedKeys());

		Profile second = Profile.empty("Second");
		writer.queue(KEY, second);
		assertEquals(second, writer.latest(KEY), "the newest queued profile wins over the unsaved one");
		assertFalse(writer.save(KEY, second, this::failed));
		assertEquals(Map.of(KEY, second), writer.notOnDisk());

		writer.retry(null, this::failed);
		assertEquals(3, failures.size(), "every attempt is reported");
		unblockSaves();
		writer.retry(null, this::failed);
		assertEquals("Second", nameOnDisk(), "the retry writes the newest profile");
		assertNull(writer.latest(KEY));
		assertTrue(writer.unsavedKeys().isEmpty());
	}

	@Test
	void notOnDiskPrefersQueuedOverUnsaved() throws IOException {
		ProfileWriter writer = new ProfileWriter(store());
		blockSaves();
		Profile older = Profile.empty("Older");
		writer.queue(KEY, older);
		assertFalse(writer.save(KEY, older, this::failed));
		Profile newer = Profile.empty("Newer");
		writer.queue(KEY, newer);
		assertEquals(Map.of(KEY, newer), writer.notOnDisk(), "a key in both maps gives the queued profile");
	}

	@Test
	void retrySkipsTheKeyJustSaved() throws IOException {
		ProfileWriter writer = new ProfileWriter(store());
		blockSaves();
		writer.save(KEY, Profile.empty("A"), this::failed);
		failures.clear();
		writer.retry(KEY, this::failed);
		assertTrue(failures.isEmpty(), "the skipped key is not tried again in the same pass");
	}

	@Test
	void forgottenProfileIsNotOfferedOrRetried() throws IOException {
		ProfileWriter writer = new ProfileWriter(store());
		blockSaves();
		writer.save(KEY, Profile.empty("Deleted"), this::failed);
		writer.queue(KEY, Profile.empty("Deleted too"));
		writer.forget(KEY);
		assertNull(writer.latest(KEY));
		unblockSaves();
		writer.retry(null, this::failed);
		assertFalse(Files.exists(store().fileFor(KEY)), "a deleted profile is not written back");
	}

	/**
	 * The review's case: a file held open (Windows) while the player drags a level slider, one save per step. Only the
	 * first save waits out the lock; the rest make one quick attempt each, so the queue (and a flush behind it) stays
	 * short, and the newest profile is written once the file is free.
	 */
	@Test
	@EnabledOnOs(OS.WINDOWS)
	void manySavesToAHeldFileWaitOnlyOnce() throws Exception {
		ProfileStore store = store();
		ProfileWriter writer = new ProfileWriter(store);
		writer.save(KEY, Profile.empty("Step 0"), this::failed);
		long start = System.nanoTime();
		try (SeekableByteChannel held = Files.newByteChannel(store.fileFor(KEY), StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			for (int step = 1; step <= 40; step++) {
				Profile p = Profile.empty("Step " + step);
				writer.queue(KEY, p);
				writer.save(KEY, p, this::failed);
			}
		}
		long millis = (System.nanoTime() - start) / 1_000_000;
		assertTrue(millis < 2000, "40 saves to a held file should cost about one wait (0.3 s), took " + millis + " ms");
		assertEquals(40, failures.size());
		writer.retry(null, this::failed);
		assertEquals("Step 40", nameOnDisk());
	}
}
