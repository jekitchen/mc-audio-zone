package soundsculptor.mc.compat;

import soundsculptor.SoundSculptorClient;
import com.mojang.blaze3d.Blaze3D;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Path;
import net.minecraft.util.Util;

/**
 * "Open folder" for exports and imports (PRD FR-11.4/11.5) on every supported Minecraft version. 26.3 opens files with
 * the static {@code Blaze3D.openPath(Path)}; 26.2 with {@code Util.getPlatform().openPath(Path)}. The method is found
 * once, when this class loads (docs/PORTING.md, "One jar for every version").
 */
public final class FolderCompat {
	private static final MethodType OPEN_PATH = MethodType.methodType(void.class, Path.class);
	/** {@code (Path) -> void}, or null if neither method exists. */
	private static final MethodHandle OPEN = resolve();

	private FolderCompat() {
	}

	/** Whether this Minecraft version has a way to open folders (false only on a version this jar does not support). */
	public static boolean available() {
		return OPEN != null;
	}

	/**
	 * Opens a folder or file in the system's file browser.
	 *
	 * @throws IllegalStateException if {@link #available()} is false
	 */
	public static void open(Path path) {
		if (OPEN == null) {
			throw new IllegalStateException("this Minecraft version has neither Blaze3D.openPath(Path) nor Util.OS.openPath(Path)");
		}
		try {
			OPEN.invokeExact(path);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	private static MethodHandle resolve() {
		try {
			return RuntimeLookup.staticMethod(Blaze3D.class, "openPath", OPEN_PATH); // 26.3
		} catch (IllegalStateException not26_3) {
			try {
				// 26.2. Util.OS is not named in code: it is final in 26.3 only, and javac records that in this class.
				Method getPlatform = Util.class.getMethod("getPlatform");
				return RuntimeLookup.virtualMethod(getPlatform.getReturnType(), "openPath", OPEN_PATH)
						.bindTo(getPlatform.invoke(null));
			} catch (IllegalStateException | ReflectiveOperationException not26_2) {
				not26_2.addSuppressed(not26_3);
				SoundSculptorClient.LOGGER.warn("Sound Sculptor cannot open folders on this Minecraft version", not26_2);
				return null;
			}
		}
	}
}
