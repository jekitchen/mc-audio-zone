package soundsculptor.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/**
 * Creates the test's singleplayer world, and closes any open screen before the world closes.
 *
 * <p>Why: closing a world while a screen has paused singleplayer can deadlock Fabric's client game test threading on
 * Minecraft 26.2 (seen in CI): the render thread halts the integrated server and waits for it, while the paused server
 * thread and the test thread wait for each other at Fabric's tick barrier. Every Sound Sculptor tab that pauses the game
 * can leave a test in that state. Unpausing first (no screen, then one tick) avoids it.
 */
final class TestWorlds {
	private TestWorlds() {
	}

	static TestSingleplayerContext create(ClientGameTestContext context) {
		TestSingleplayerContext world = context.worldBuilder().create();
		return new TestSingleplayerContext() {
			@Override
			public TestWorldSave getWorldSave() {
				return world.getWorldSave();
			}

			@Override
			public TestServerConnection getConnection() {
				return world.getConnection();
			}

			@Override
			public TestServerContext getServer() {
				return world.getServer();
			}

			@Override
			public void close() {
				context.setScreen(() -> null);
				context.waitTicks(1);
				world.close();
			}
		};
	}
}
