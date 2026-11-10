package audiozone.gametest;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a hung client game test run into a fast, diagnosable failure. If the game stops ticking for
 * {@value #LIMIT_SECONDS} seconds, every thread's stack (with the locks it holds and waits for) goes to the log and
 * the game exits with an error, instead of hanging silently until CI cancels the job after 20 minutes.
 *
 * <p>Active only in automated game test runs (Fabric sets {@code fabric.client.gametest}), never in the dev client.
 */
public final class GameTestWatchdog implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("audiozone-gametest");
	static final int LIMIT_SECONDS = 120;

	private static volatile long lastTickNanos = System.nanoTime();

	@Override
	public void onInitializeClient() {
		if (System.getProperty("fabric.client.gametest") == null) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> lastTickNanos = System.nanoTime());
		Thread watchdog = new Thread(GameTestWatchdog::watch, "Audio Zone game test watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		LOGGER.info("Game test watchdog on: a run that stops ticking for {} s is dumped and stopped", LIMIT_SECONDS);
	}

	private static void watch() {
		while (true) {
			try {
				Thread.sleep(5_000);
			} catch (InterruptedException e) {
				return;
			}
			long silentSeconds = (System.nanoTime() - lastTickNanos) / 1_000_000_000L;
			if (silentSeconds >= LIMIT_SECONDS) {
				StringBuilder dump = new StringBuilder("The game has not ticked for " + silentSeconds + " s. Thread dump:\n");
				for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
					dump.append(fullInfo(info)).append('\n');
				}
				LOGGER.error("{}", dump);
				System.err.println(dump);
				System.err.flush();
				// halt, not exit: a hung game may also hang its shutdown hooks.
				Runtime.getRuntime().halt(3);
			}
		}
	}

	/** {@link ThreadInfo#toString()} cuts stacks at 8 frames; this prints them whole. */
	private static String fullInfo(ThreadInfo info) {
		StringBuilder out = new StringBuilder();
		out.append('"').append(info.getThreadName()).append("\" ").append(info.getThreadState());
		if (info.getLockName() != null) {
			out.append(" on ").append(info.getLockName());
		}
		if (info.getLockOwnerName() != null) {
			out.append(" owned by \"").append(info.getLockOwnerName()).append('"');
		}
		out.append('\n');
		for (StackTraceElement frame : info.getStackTrace()) {
			out.append("\tat ").append(frame).append('\n');
		}
		return out.toString();
	}
}
