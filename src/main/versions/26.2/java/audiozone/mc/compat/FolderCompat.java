package audiozone.mc.compat;

import java.nio.file.Path;
import net.minecraft.util.Util;

/**
 * Minecraft 26.2 version of "Open folder" (export and import, PRD FR-11.4/11.5). Keep the public API identical to the
 * 26.3 copy.
 */
public final class FolderCompat {
	private FolderCompat() {
	}

	/** Opens a folder or file in the system's file browser. */
	public static void open(Path path) {
		Util.getPlatform().openPath(path);
	}
}
