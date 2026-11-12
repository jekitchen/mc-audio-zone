package soundsculptor.gametest;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.profile.Profile;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.Notices;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundObserver;
import soundsculptor.mc.ui.SculptList;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Automated version of the M4 milestone test (PRD 11.1): mute from the list; the mute survives a restart (the
 * profile file is re-read); Bypass (FR-13.1) and fail-open (FR-13.2); a corrupt profile file (FR-11.8).
 */
public final class M4ClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String ANVIL = "minecraft:block.anvil.land";
	private static final int SETTLE = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				RecentLog.get().sounds().clear();
			});
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				screen(mc).selectTab(SoundSculptorScreen.TAB_RECENT);
				screen(mc).setPaused(false);
			});
			check(context.computeOnClient(mc -> screen(mc).getNarrationMessage().getString()).contains("Sound Sculptor"), "screen title");

			// Mute from the list, with the keyboard only: Tab into the list, Enter on the row's Mute button.
			check(play(context, VILLAGER, SoundSource.NEUTRAL, 3.0), "an unmuted villager sound should start");
			context.runOnClient(mc -> screen(mc).setFocused(screen(mc).filterField));
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(2);
			String label = context.computeOnClient(mc -> {
				SculptList.Row row = screen(mc).recentList.getFocused();
				return row == null ? "none" : row.buttons().get(0).getMessage().getString();
			});
			check(label.equals("Mute"), "the focused row's button should be Mute, was " + label);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(2);
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.profile(), ApplyTarget.EVERYWHERE, VILLAGER)), "Enter on Mute should create a mute rule");

			// The next play is silent and the row says Muted (FR-1 AC row 3); muted plays are still listed.
			check(!play(context, VILLAGER, SoundSource.NEUTRAL, 3.0), "a muted sound must not start");
			List<String> row = rowTexts(context, VILLAGER);
			check(row.stream().anyMatch(t -> t.contains("Muted")) && row.stream().anyMatch(t -> t.startsWith("×2 ·")),
					"the row should show Muted and count the muted play, was " + row);
			check(context.computeOnClient(mc -> screen(mc).recentList.findRow(VILLAGER).buttons().get(0).getMessage().getString()).equals("Unmute"),
					"the button should now say Unmute");
			context.takeScreenshot("m4-muted-row");

			// Bypass from the header: audible again, no rule changed; off again: muted again (FR-13 AC).
			context.runOnClient(mc -> screen(mc).bypassToggle.onPress(null));
			check(context.computeOnClient(mc -> ProfileManager.isBypassed()), "the header toggle should turn on Bypass");
			check(play(context, VILLAGER, SoundSource.NEUTRAL, 3.0), "with Bypass on, the muted sound should play");
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.profile(), ApplyTarget.EVERYWHERE, VILLAGER)), "Bypass must not change rules");
			context.takeScreenshot("m4-bypass-header");

			// The hotkey works in the screen too.
			context.runOnClient(mc -> screen(mc).setFocused(screen(mc).recentList));
			context.getInput().pressKey(InputConstants.KEY_F10);
			context.waitTicks(2);
			check(!context.computeOnClient(mc -> ProfileManager.isBypassed()), "F10 in the screen should turn Bypass off");
			check(!context.computeOnClient(mc -> screen(mc).bypassToggle.value()), "the header toggle should follow the hotkey");
			check(!play(context, VILLAGER, SoundSource.NEUTRAL, 3.0), "with Bypass off, the sound is muted again");

			// And in the world, with the HUD label.
			context.setScreen(() -> null);
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_F10);
			context.waitTicks(2);
			check(context.computeOnClient(mc -> ProfileManager.isBypassed()), "F10 in the world should turn on Bypass");
			check(play(context, VILLAGER, SoundSource.NEUTRAL, 3.0), "Bypass from the world: the sound plays");
			context.takeScreenshot("m4-bypass-hud-label");
			context.getInput().pressKey(InputConstants.KEY_F10);
			context.waitTicks(2);
			check(!context.computeOnClient(mc -> ProfileManager.isBypassed()), "F10 again should turn Bypass off");

			// The mute survives a restart: it is in the file, and re-reading the file brings it back.
			context.runOnClient(mc -> ProfileManager.flushSaves());
			Path file = context.computeOnClient(mc -> ProfileManager.store().fileFor(ProfileManager.key()));
			String json = read(file);
			check(json.contains("\"schemaVersion\": " + Profile.SCHEMA_VERSION) && json.contains(VILLAGER) && json.contains("\"mute\": true"),
					"the profile file should hold the mute rule, was:\n" + json);
			// A restart only reads the file. Prove re-reading uses the disk, not memory: unmute in memory (which saves the
			// unmuted profile), put the muted file back on disk, then re-read it.
			context.runOnClient(mc -> {
				ProfileManager.update(p -> p.withEverywhere(PlaceSets.EMPTY));
				ProfileManager.flushSaves();
			});
			write(file, json);
			context.runOnClient(mc -> ProfileManager.reload());
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.profile(), ApplyTarget.EVERYWHERE, VILLAGER)), "after re-reading the file the sound is still muted");
			check(Files.exists(file.resolveSibling(file.getFileName() + ".bak")), "the previous file should be kept as .bak");

			// Fail open (FR-13.2): errors never stop a sound; more than 10 in a minute turn on Bypass with a notice.
			SoundObserver broken = o -> {
				if (o.idString().equals(ANVIL)) {
					throw new IllegalStateException("test: broken observer");
				}
			};
			context.runOnClient(mc -> SoundHook.addObserver(broken));
			try {
				long errorsBefore = context.computeOnClient(mc -> SoundHook.errorCount());
				for (int i = 0; i < 11; i++) {
					check(play(context, ANVIL, SoundSource.BLOCKS, 3.0, 1), "a sound whose handling fails must still play (try " + (i + 1) + ")");
				}
				check(context.computeOnClient(mc -> SoundHook.errorCount()) - errorsBefore == 11, "11 errors should be counted");
				check(context.computeOnClient(mc -> ProfileManager.isBypassed()), "11 errors in a minute should turn on Bypass");
				context.waitTicks(5);
				check(context.computeOnClient(mc -> Notices.shownTitles()).contains("Sound Sculptor turned itself off"), "a notice should ask to report the bug");
				context.takeScreenshot("m4-fail-open-toast");
			} finally {
				context.runOnClient(mc -> {
					SoundHook.removeObserver(broken);
					ProfileManager.setBypass(false);
				});
			}

			// A corrupt profile file: set aside as .broken, a notice explains, an empty profile is used (FR-11.8 AC).
			context.runOnClient(mc -> ProfileManager.flushSaves());
			write(file, "{ \"schemaVersion\": 1, \"globalRules\": [ oops");
			context.runOnClient(mc -> ProfileManager.reload());
			Path brokenCopy = file.resolveSibling(file.getFileName() + ".broken");
			check(Files.exists(brokenCopy), "the corrupt file should be kept as .broken");
			check(context.computeOnClient(mc -> RuleTarget.GLOBAL.rules(ProfileManager.profile()).isEmpty()), "an empty profile should be used");
			context.waitTicks(5);
			check(context.computeOnClient(mc -> Notices.shownTitles()).contains("Sound Sculptor profile could not be loaded"), "a notice should explain");
			context.takeScreenshot("m4-broken-profile-toast");

			// Clean up for later tests.
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
			delete(brokenCopy);
		}
	}

	/** Plays a sound east of the listener; returns whether it actually started. */
	private static boolean play(ClientGameTestContext context, String id, SoundSource source, double blocksAway) {
		return play(context, id, source, blocksAway, SETTLE);
	}

	private static boolean play(ClientGameTestContext context, String id, SoundSource source, double blocksAway, int settle) {
		boolean started = context.computeOnClient(mc -> {
			Vec3 listener = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), source, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + blocksAway, listener.y(), listener.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(settle);
		return started;
	}

	private static List<String> rowTexts(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> {
			SculptList.Row row = screen(mc).recentList.findRow(id);
			return row == null ? null : row.columnTexts();
		});
	}

	private static SoundSculptorScreen screen(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new AssertionError("could not read " + file, e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new AssertionError("could not write " + file, e);
		}
	}

	private static void delete(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new AssertionError("could not delete " + file, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
