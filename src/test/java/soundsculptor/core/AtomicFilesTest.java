package soundsculptor.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Atomic writes with a {@code .bak} (PRD 9.2), and a rename that waits out a file held open for a moment (Windows). */
class AtomicFilesTest {
	@TempDir
	Path dir;

	@Test
	void writesAndKeepsThePreviousFileAsBak() throws IOException {
		Path file = dir.resolve("a.json");
		AtomicFiles.write(file, "one");
		AtomicFiles.write(file, "two");
		assertEquals("two", Files.readString(file));
		assertEquals("one", Files.readString(dir.resolve("a.json.bak")));
		assertTrue(Files.notExists(dir.resolve("a.json.tmp")), "the temp file is renamed into place");
	}

	/**
	 * On Windows a virus scanner or the search indexer may hold a file that was just written, without letting it be
	 * replaced; the rename then fails with "access denied" for a moment. The write waits it out instead of losing the save.
	 */
	@Test
	@EnabledOnOs(OS.WINDOWS)
	void renameWaitsWhileTheFileIsHeldForAMoment() throws Exception {
		Path file = dir.resolve("held.json");
		AtomicFiles.write(file, "old");
		SeekableByteChannel held = Files.newByteChannel(file, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE);
		CompletableFuture<Void> release = CompletableFuture.runAsync(() -> {
			try {
				held.close();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}, CompletableFuture.delayedExecutor(40, TimeUnit.MILLISECONDS));
		AtomicFiles.write(file, "new");
		release.get(5, TimeUnit.SECONDS);
		assertEquals("new", Files.readString(file, StandardCharsets.UTF_8));
	}

	/** A file that stays held fails after the retries, with the reason, and keeps its old content. */
	@Test
	@EnabledOnOs(OS.WINDOWS)
	void renameFailsWhenTheFileStaysHeld() throws IOException {
		Path file = dir.resolve("stuck.json");
		AtomicFiles.write(file, "old");
		try (SeekableByteChannel held = Files.newByteChannel(file, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			assertThrows(IOException.class, () -> AtomicFiles.write(file, "new"));
		}
		assertEquals("old", Files.readString(file, StandardCharsets.UTF_8));
	}

	/** A folder in the file's place is not a passing lock: the write fails and the folder stays. */
	@Test
	void folderInTheWayFails() throws IOException {
		Path file = dir.resolve("folder.json");
		Files.createDirectories(file.resolve("inside"));
		assertThrows(IOException.class, () -> AtomicFiles.write(file, "new"));
		assertTrue(Files.isDirectory(file.resolve("inside")));
	}
}
