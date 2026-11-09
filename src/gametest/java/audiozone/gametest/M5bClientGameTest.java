package audiozone.gametest;

import audiozone.core.diag.DiagnosticsReport;
import audiozone.core.profile.Profile;
import audiozone.core.rules.Rule;
import audiozone.mc.AudioZoneScreen;
import audiozone.mc.Diagnostics;
import audiozone.mc.ProfileManager;
import audiozone.mc.RecentLog;
import audiozone.mc.TraceScreen;
import audiozone.mc.ui.AzList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the rest of the M5 milestone test (PRD 11.1): the read-only sound trace (FR-14 MVP AC) and
 * Diagnostics with its timings and Copy report (FR-13.3).
 */
public final class M5bClientGameTest implements FabricClientGameTest {
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final String EAT = "minecraft:entity.generic.eat";
	private static final String NEVER_PLAYED = "minecraft:entity.camel.dash";
	private static final int SETTLE = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name())
						.putRule(Rule.of("w", "*villager*").withMute(true))
						.putRule(Rule.of("e", AMBIENT).withVolume(60)));
				RecentLog.get().sounds().clear();
			});
			context.setScreen(AudioZoneScreen::new);
			context.waitForScreen(AudioZoneScreen.class);
			context.runOnClient(mc -> {
				main(mc).selectTab(AudioZoneScreen.TAB_RECENT);
				main(mc).setPaused(false);
			});

			// FR-14 AC 1: *villager* muted plus the exact ID at 60%: Why? shows both, the exact rule wins, final 60%.
			play(context, AMBIENT, false);
			context.waitTicks(10);
			context.runOnClient(mc -> {
				AzList.Row row = main(mc).recentList.findRow(AMBIENT);
				check(row != null, "the villager sound should be listed");
				check(row.buttons().get(2).getMessage().getString().equals("Why does it sound like this?"), "the third row button is Why?");
				row.buttons().get(2).onPress(null);
			});
			context.waitForScreen(TraceScreen.class);
			String trace = context.computeOnClient(mc -> trace(mc).text());
			check(trace.contains("  minecraft:entity.villager.ambient: Volume 60%\n    Decides loudness (Exact sound ID)\n"), "the exact rule should win:\n" + trace);
			check(trace.contains("  *:*villager*: Muted\n    loudness overridden by minecraft:entity.villager.ambient\n"),
					"the wildcard should be shown as overridden:\n" + trace);
			check(trace.contains("Result\n  Volume 60%\n"), "the final volume should be 60%:\n" + trace);
			check(context.computeOnClient(mc -> trace(mc).lines.children().size()) == trace.split("\n").length,
					"the panel should show every line of the trace");
			context.takeScreenshot("m5b-trace-villager");
			// FR-14 AC: Copy trace puts the same text on the clipboard.
			// (The system clipboard may store line breaks as CRLF on Windows, so line endings are normalized.)
			context.runOnClient(mc -> trace(mc).copy());
			String clip = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard()).replace("\r\n", "\n");
			check(clip.equals(trace), "Copy trace should copy the panel's text, clipboard had [" + clip.replace("\r", "\\r").replace("\n", "\\n") + "]");
			context.runOnClient(mc -> trace(mc).onClose());
			context.waitForScreen(AudioZoneScreen.class);

			// FR-14 AC: with Bypass on, the trace leads with it.
			context.runOnClient(mc -> ProfileManager.setBypass(true));
			context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(main(mc), AMBIENT)));
			context.waitForScreen(TraceScreen.class);
			String bypassed = context.computeOnClient(mc -> trace(mc).text());
			check(bypassed.split("\n")[1].equals("  Bypass is on: nothing is changed."), "the trace should lead with Bypass:\n" + bypassed);
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				trace(mc).onClose();
			});
			context.waitForScreen(AudioZoneScreen.class);

			// FR-14 AC: your own eating sound is non-positional.
			play(context, EAT, true);
			context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(main(mc), EAT)));
			context.waitForScreen(TraceScreen.class);
			String eating = context.computeOnClient(mc -> trace(mc).text());
			check(eating.contains("Non-positional: zones never apply"), "eating should be non-positional:\n" + eating);
			context.runOnClient(mc -> trace(mc).onClose());
			context.waitForScreen(AudioZoneScreen.class);

			// FR-14.1: a sound not heard this session shows the rules that would match.
			context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(main(mc), NEVER_PLAYED)));
			context.waitForScreen(TraceScreen.class);
			String notHeard = context.computeOnClient(mc -> trace(mc).text());
			check(notHeard.contains("Not heard yet; zones depend on where it plays") && notHeard.contains("No rule matches this sound"),
					"an unheard sound should say so:\n" + notHeard);
			context.runOnClient(mc -> trace(mc).onClose());
			context.waitForScreen(AudioZoneScreen.class);

			// FR-13.3: Diagnostics shows live timings and Copy report pastes them.
			context.runOnClient(mc -> {
				main(mc).selectTab(AudioZoneScreen.TAB_SETTINGS);
				main(mc).setSettingsPage(AudioZoneScreen.SettingsPage.DIAGNOSTICS);
			});
			// The readout covers the last complete second, so keep sounds coming until one has been measured (slow CI
			// machines can take well over a second for a fixed number of ticks).
			DiagnosticsReport.Data data = null;
			for (int i = 0; i < 200 && (data == null || data.sounds().count() == 0); i++) {
				play(context, AMBIENT, false, 1);
				data = context.computeOnClient(mc -> main(mc).diagnostics());
			}
			check(data.sounds().count() > 0 && data.sounds().averageMicros() > 0, "Diagnostics should show sounds per second and time per sound, was " + data);
			check(data.globalRules() == 2 && !Double.isNaN(data.cacheHitRate()), "Diagnostics should count the rules and the cache, was " + data);
			context.takeScreenshot("m5b-diagnostics");
			context.runOnClient(mc -> main(mc).copyReport());
			String report = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard()).replace("\r\n", "\n");
			check(report.startsWith("Audio Zone ") && report.contains(", Minecraft ") && report.contains("Global rules: 2\n")
					&& report.contains("Per sound: avg "), "the report should hold versions, counts and timings:\n" + report);
			check(Diagnostics.report().contains("Errors this session: "), "the report should count errors");

			// Clean up for later tests.
			context.runOnClient(mc -> {
				main(mc).setSettingsPage(AudioZoneScreen.SettingsPage.GENERAL);
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static void play(ClientGameTestContext context, String id, boolean relative) {
		play(context, id, relative, SETTLE);
	}

	/** Plays a sound 3 blocks east of the listener, or at the listener's own position when {@code relative}. */
	private static void play(ClientGameTestContext context, String id, boolean relative, int settle) {
		context.runOnClient(mc -> {
			Vec3 listener = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = relative
					? new SimpleSoundInstance(Identifier.parse(id), SoundSource.PLAYERS, 1.0F, 1.0F, RandomSource.create(),
							false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true)
					: new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
							false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + 3.0, listener.y(), listener.z(), false);
			mc.getSoundManager().play(sound);
		});
		context.waitTicks(settle);
	}

	private static AudioZoneScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof AudioZoneScreen, "expected the Audio Zone screen, found " + mc.gui.screen());
		return (AudioZoneScreen) mc.gui.screen();
	}

	private static TraceScreen trace(Minecraft mc) {
		check(mc.gui.screen() instanceof TraceScreen, "expected the trace panel, found " + mc.gui.screen());
		return (TraceScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
