package soundsculptor.gametest;

import soundsculptor.SoundSculptorClient;
import soundsculptor.mc.SoundSculptorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/**
 * Automated version of the M0 milestone test (PRD 11.1) and the FR-12 hotkey acceptance criteria:
 * the mod is loaded, the default key opens an empty Sound Sculptor screen, and after rebinding only the new key
 * opens it. The rebind target is apostrophe, which no vanilla or popular-mod key uses; the PRD's original "L"
 * is vanilla's Advancements key, which opens its own screen first.
 */
public final class M0ClientGameTest implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		check(FabricLoader.getInstance().isModLoaded(SoundSculptorClient.MOD_ID), "soundsculptor is not in the mod list");

		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();

			// Default key opens the screen.
			context.getInput().pressKey(SoundSculptorClient.DEFAULT_OPEN_KEY);
			expectSoundSculptorScreen(context, "after pressing the default key");
			context.takeScreenshot("m0-soundsculptor-screen");
			closeScreen(context);

			// Rebind to apostrophe: it opens the screen, the old default does nothing.
			rebind(context, "key.keyboard.apostrophe");
			context.getInput().pressKey(InputConstants.KEY_APOSTROPHE);
			expectSoundSculptorScreen(context, "after rebinding to apostrophe and pressing it");
			closeScreen(context);

			context.getInput().pressKey(SoundSculptorClient.DEFAULT_OPEN_KEY);
			context.waitTicks(SETTLE_TICKS);
			check(context.computeOnClient(mc -> mc.gui.screen() == null),
					"old default key still opened a screen after rebinding");

			context.runOnClient(mc -> {
				SoundSculptorClient.openKey.setKey(SoundSculptorClient.openKey.getDefaultKey());
				KeyMapping.resetMapping();
			});
		}
	}

	/** Uses key names ("key.keyboard.apostrophe"), which are the same in 26.2 and 26.3. */
	private static void rebind(ClientGameTestContext context, String keyName) {
		context.runOnClient(mc -> {
			SoundSculptorClient.openKey.setKey(InputConstants.getKey(keyName));
			KeyMapping.resetMapping();
		});
	}

	private static void expectSoundSculptorScreen(ClientGameTestContext context, String when) {
		context.waitTicks(SETTLE_TICKS);
		String open = context.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName());
		check(open.equals(SoundSculptorScreen.class.getName()), "expected Sound Sculptor screen " + when + ", but open screen was: " + open);
	}

	private static void closeScreen(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.waitTicks(SETTLE_TICKS);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
