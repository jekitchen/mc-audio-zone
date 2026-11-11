package audiozone.mc.compat;

import com.mojang.blaze3d.Blaze3D;
import java.nio.file.Path;

/**
 * Minecraft 26.3 version of "Open folder" (export and import, PRD FR-11.4/11.5). Keep the public API identical to the
 * 26.2 copy. 26.3 moved opening files from {@code Util.getPlatform()} to {@link Blaze3D}.
 */
public final class FolderCompat {
	private FolderCompat() {
	}

	/** Opens a folder or file in the system's file browser. */
	public static void open(Path path) {
		Blaze3D.openPath(path);
	}
}
