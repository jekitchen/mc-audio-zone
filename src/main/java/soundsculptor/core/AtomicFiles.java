package soundsculptor.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a text file so a crash never leaves half of it: temp file, then rename, keeping the old file as {@code .bak}.
 *
 * <p>On Windows a file cannot be replaced while another program holds it open without sharing it, and a virus scanner
 * or the search indexer often does that for a moment right after a write: the rename then fails with "access denied".
 * The rename is tried again for about 0.3 seconds before the write gives up, so such a moment does not cost a save.
 */
public final class AtomicFiles {
	/** Pauses between rename attempts, in milliseconds. */
	private static final int[] RETRY_PAUSES_MILLIS = {10, 20, 40, 80, 150};

	private AtomicFiles() {
	}

	/** Writes {@code text} (UTF-8) to {@code file}; an existing file is copied to {@code <name>.bak} first. */
	public static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, text, StandardCharsets.UTF_8);
		if (Files.exists(file)) {
			Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		for (int attempt = 0; ; attempt++) {
			try {
				move(temp, file);
				return;
			} catch (AccessDeniedException e) {
				// A folder in the way is not a passing lock.
				if (attempt >= RETRY_PAUSES_MILLIS.length || Files.isDirectory(file)) {
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
