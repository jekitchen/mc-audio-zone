package soundsculptor.gametest;

import soundsculptor.mc.compat.FolderCompat;
import soundsculptor.mc.compat.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The runtime lookups that let one jar run on every supported Minecraft version (docs/PORTING.md, "One jar for every
 * version") find what this version's compiler would have used. This test is compiled per version, so
 * {@code InputConstants.KEY_*} here are this version's compile-time values.
 */
public final class OneJarClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			same("TAB", Keys.TAB, InputConstants.KEY_TAB);
			same("ESCAPE", Keys.ESCAPE, InputConstants.KEY_ESCAPE);
			same("BACKSPACE", Keys.BACKSPACE, InputConstants.KEY_BACKSPACE);
			same("UP", Keys.UP, InputConstants.KEY_UP);
			same("DOWN", Keys.DOWN, InputConstants.KEY_DOWN);
			same("LEFT", Keys.LEFT, InputConstants.KEY_LEFT);
			same("RIGHT", Keys.RIGHT, InputConstants.KEY_RIGHT);
			same("PAGE_UP", Keys.PAGE_UP, InputConstants.KEY_PAGEUP);
			same("PAGE_DOWN", Keys.PAGE_DOWN, InputConstants.KEY_PAGEDOWN);
			same("P", Keys.P, InputConstants.KEY_P);
			same("Z", Keys.Z, InputConstants.KEY_Z);
			same("SEMICOLON", Keys.SEMICOLON, InputConstants.KEY_SEMICOLON);
			same("F10", Keys.F10, InputConstants.KEY_F10);

			check(FolderCompat.available(), "FolderCompat found no way to open folders on this Minecraft version");
			try {
				// Resolves the sprite drawing method handle; a failure would stop every screen from drawing.
				Class.forName("soundsculptor.mc.ui.Draw", true, OneJarClientGameTest.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static void same(String name, int runtime, int compiled) {
		check(runtime == compiled, "Keys." + name + " is " + runtime + " but this version's InputConstants value is " + compiled);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
