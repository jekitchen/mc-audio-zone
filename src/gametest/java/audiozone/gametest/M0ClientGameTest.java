package audiozone.gametest;

import audiozone.AudioZoneClient;
import audiozone.mc.AudioZoneScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/**
 * Automated version of the M0 milestone test (PRD 11.1) and the FR-12 hotkey acceptance criteria:
 * the mod is loaded, the default key opens an empty Audio Zone screen, and after rebinding only the new key
 * opens it. The rebind target is apostrophe, which no vanilla or popular-mod key uses; the PRD's original "L"
 * is vanilla's Advancements key, which opens its own screen first.
 */
public final class M0ClientGameTest implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		check(FabricLoader.getInstance().isModLoaded(AudioZoneClient.MOD_ID), "audiozone is not in the mod list");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();

			// Default key opens the screen.
			context.getInput().pressKey(AudioZoneClient.DEFAULT_OPEN_KEY);
			expectAudioZoneScreen(context, "after pressing the default key");
			context.takeScreenshot("m0-audiozone-screen");
			closeScreen(context);

			// Rebind to apostrophe: it opens the screen, the old default does nothing.
			rebind(context, "key.keyboard.apostrophe");
			context.getInput().pressKey(InputConstants.KEY_APOSTROPHE);
			expectAudioZoneScreen(context, "after rebinding to apostrophe and pressing it");
			closeScreen(context);

			context.getInput().pressKey(AudioZoneClient.DEFAULT_OPEN_KEY);
			context.waitTicks(SETTLE_TICKS);
			check(context.computeOnClient(mc -> mc.gui.screen() == null),
					"old default key still opened a screen after rebinding");

			context.runOnClient(mc -> {
				AudioZoneClient.openKey.setKey(AudioZoneClient.openKey.getDefaultKey());
				KeyMapping.resetMapping();
			});
		}
	}

	/** Uses key names ("key.keyboard.apostrophe"), which are the same in 26.2 and 26.3. */
	private static void rebind(ClientGameTestContext context, String keyName) {
		context.runOnClient(mc -> {
			AudioZoneClient.openKey.setKey(InputConstants.getKey(keyName));
			KeyMapping.resetMapping();
		});
	}

	private static void expectAudioZoneScreen(ClientGameTestContext context, String when) {
		context.waitTicks(SETTLE_TICKS);
		String open = context.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName());
		check(open.equals(AudioZoneScreen.class.getName()), "expected Audio Zone screen " + when + ", but open screen was: " + open);
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
