package soundsculptor.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes a text file so a crash never leaves half of it: temp file, then rename, keeping the old file as {@code .bak}. */
public final class AtomicFiles {
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
		try {
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
