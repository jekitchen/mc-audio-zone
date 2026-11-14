package soundsculptor.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a text file so a crash never leaves half of it: temp file, then rename, keeping the old file as {@code .bak}.
 *
 * <p>On Windows a file cannot be replaced while another program holds it open without sharing it, and a virus scanner
 * or the search indexer often does that for a moment right after a write: the copy to {@code .bak} or the rename then
 * fails with "access denied" or "used by another process". Each is tried again for about 0.3 seconds before the write
 * gives up, so such a moment does not cost a save.
 */
public final class AtomicFiles {
	/** Pauses between attempts of a step that a brief hold refused, in milliseconds. */
	private static final int[] RETRY_PAUSES_MILLIS = {10, 20, 40, 80, 150};

	private AtomicFiles() {
	}

	/** Writes {@code text} (UTF-8) to {@code file}; an existing file is copied to {@code <name>.bak} first. */
	public static void write(Path file, String text) throws IOException {
		write(file, text, true);
	}

	/**
	 * Like {@link #write(Path, String)}.
	 *
	 * @param waitOutLock whether a refused copy or rename is tried again for about 0.3 seconds; {@code false} makes one
	 *                    attempt, for a file that already failed and is tried again later anyway
	 */
	public static void write(Path file, String text, boolean waitOutLock) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, text, StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Path bak = file.resolveSibling(file.getFileName() + ".bak");
			waitingOutLock(bak, waitOutLock, () -> Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING));
		}
		waitingOutLock(file, waitOutLock, () -> move(temp, file));
	}

	/** One step of a write that a brief hold on a file can refuse. */
	@FunctionalInterface
	private interface Step {
		void run() throws IOException;
	}

	/**
	 * Runs {@code step}, and while Windows refuses it because another program holds a file ("access denied", or "used by
	 * another process", which Java reports as a plain {@link FileSystemException}), tries again after each pause.
	 *
	 * @param target the file the step replaces; a folder in its place is not a passing lock
	 */
	private static void waitingOutLock(Path target, boolean waitOutLock, Step step) throws IOException {
		for (int attempt = 0; ; attempt++) {
			try {
				step.run();
				return;
			} catch (FileSystemException e) {
				boolean held = e instanceof AccessDeniedException || e.getClass() == FileSystemException.class;
				if (!held || !waitOutLock || attempt >= RETRY_PAUSES_MILLIS.length || Files.isDirectory(target)) {
					throw e;
				}
				try {
					Thread.sleep(RETRY_PAUSES_MILLIS[attempt]);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	private static void move(Path temp, Path file) throws IOException {
		try {
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
