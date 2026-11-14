package soundsculptor.gametest;

import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.profile.Profile;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentSounds;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.gametest.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptList;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Automated version of the M6 milestone test (PRD 11.1): the FR-3 MVP acceptance criteria on the Sounds tab with the
 * Recently played filter off (every known sound), including the "?" search help and keyboard access.
 */
public final class M6ClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER_AMBIENT = "minecraft:entity.villager.ambient";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				RecentLog.get().sounds().clear();
				SoundsPanel.resetRememberedState();
			});
			// A villager sound heard this session, more than 3 minutes ago, gets "Seen as: <category>" (FR-3.2); a sound
			// heard in the last 3 minutes shows the recently played columns instead.
			context.runOnClient(mc -> RecentLog.get().sounds().record(VILLAGER_AMBIENT, "neutral", true, 3.0, true, PlayStatus.PLAYING, null,
					Util.getMillis() - RecentSounds.WINDOW_MS - 1000));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS);
				sounds(mc).setRecentOnly(false);
			});
			int all = context.computeOnClient(mc -> sounds(mc).count());
			check(all > 500, "an empty search should list every known sound, was " + all);
			check(context.computeOnClient(mc -> sounds(mc).list.children().stream()
					.noneMatch(row -> String.valueOf(row.key()).startsWith("minecraft:music.") || String.valueOf(row.key()).startsWith("minecraft:ui."))),
					"vanilla-covered sounds (music, UI) must be hidden");

			// FR-3 AC 1: type villager: only villager-related sounds, with a count. Results wait for typing to pause.
			context.runOnClient(mc -> main(mc).setFocused(sounds(mc).searchField));
			context.getInput().typeChars("villager");
			check(context.computeOnClient(mc -> sounds(mc).count()) == all, "results should wait 150 ms after the last keystroke");
			context.waitTicks(6);
			List<List<String>> villager = rows(context);
			check(!villager.isEmpty() && villager.size() < all, "villager should narrow the list, was " + villager.size());
			check(villager.stream().allMatch(texts -> String.join(" ", texts).toLowerCase(Locale.ROOT).contains("villager")),
					"every result should be about villagers");
			check(context.computeOnClient(mc -> sounds(mc).count()) == villager.size(), "the count should match the rows");
			List<String> ambient = context.computeOnClient(mc -> sounds(mc).list.findRow(VILLAGER_AMBIENT).columnTexts());
			check(ambient.stream().anyMatch(t -> t.startsWith("Seen as: ")), "a heard sound should say where it was seen, was " + ambient);
			context.takeScreenshot("m6-search-villager");

			// FR-3 AC 2: villager|zombie finds both families.
			search(context, "villager|zombie");
			List<String> either = keys(context);
			check(either.stream().anyMatch(id -> id.contains("villager")) && either.stream().anyMatch(id -> id.contains("zombie")),
					"villager|zombie should find both families");
			// AND: both words must appear. "villager zombie" finds only the zombie villager family; "villager cow" finds none.
			search(context, "villager zombie");
			List<String> both = keys(context);
			check(!both.isEmpty() && both.stream().allMatch(id -> id.contains("villager") && id.contains("zombie")),
					"villager zombie should find only sounds with both words, found " + both);
			search(context, "villager cow");
			check(keys(context).isEmpty() && context.computeOnClient(mc -> sounds(mc).count()) == 0,
					"no ID contains both villager and cow, found " + keys(context));
			context.takeScreenshot("m6-search-none");

			// FR-3 AC 3: villager -ambient: no result contains ambient.
			search(context, "villager -ambient");
			List<List<String>> excluded = rows(context);
			check(!excluded.isEmpty() && excluded.stream().noneMatch(texts -> String.join(" ", texts).toLowerCase(Locale.ROOT).contains("ambient")),
					"no result may contain ambient");

			// Phrases and wildcards (FR-3.5).
			search(context, "block.*.step");
			List<String> steps = keys(context);
			check(steps.contains("minecraft:block.stone.step") && steps.stream().allMatch(id -> id.matches(".*block\\..*\\.step.*")),
					"block.*.step should find footsteps, was " + steps.size());

			// FR-3 AC 6: keyboard: Tab from the field reaches the help button; Enter shows the syntax examples, each read
			// by the narrator; Esc closes them and returns to the button.
			context.runOnClient(mc -> main(mc).setFocused(sounds(mc).searchField));
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> main(mc).getFocused() == sounds(mc).helpButton), "Tab should reach the help button");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> sounds(mc).isHelpShown()), "Enter should show the help");
			check(context.computeOnClient(mc -> sounds(mc).helpList.children().size()) == 7, "the help lists the 7 examples");
			String example = context.computeOnClient(mc -> NarrationCompat.collect(sounds(mc).helpList::updateNarration));
			check(example.contains("villager") && example.contains("contains"), "the focused example is read with its meaning, was " + example);
			context.takeScreenshot("m6-search-help");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> !sounds(mc).isHelpShown() && main(mc).getFocused() == sounds(mc).helpButton),
					"Esc closes the help and returns to its button");
			// Tab goes on through Recently played and Apply to (Pause and Sort are hidden with the filter off) to the results.
			for (int i = 0; i < 3; i++) {
				context.getInput().pressKey(InputConstants.KEY_TAB);
			}
			context.waitTicks(1);
			check(context.computeOnClient(mc -> sounds(mc).list.getFocused() != null), "Tab should then reach the results");
			// Quick actions work on results: Enter on the first row's Mute.
			String firstId = context.computeOnClient(mc -> String.valueOf(sounds(mc).list.getFocused().key()));
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, firstId)), "Mute on a result should mute it");
			check(context.computeOnClient(mc -> {
				SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, firstId);
				return entry != null && Boolean.TRUE.equals(entry.mute())
						&& ProfileManager.profile().everywhere().attached().get(0).setId().equals(SoundSet.QUICK_ID);
			}), "the mute is a single-sound entry in Everywhere's Quick changes, pinned first (FR-4.10)");

			// Clean up for later tests.
			context.runOnClient(mc -> {
				SoundsPanel.resetRememberedState();
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static void search(ClientGameTestContext context, String text) {
		context.runOnClient(mc -> sounds(mc).searchField.setValue(text));
		context.waitTicks(6);
	}

	private static List<List<String>> rows(ClientGameTestContext context) {
		return context.computeOnClient(mc -> sounds(mc).list.children().stream().map(SculptList.Row::columnTexts).toList());
	}

	private static List<String> keys(ClientGameTestContext context) {
		return context.computeOnClient(mc -> sounds(mc).list.children().stream().map(row -> String.valueOf(row.key())).toList());
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static SoundsPanel sounds(Minecraft mc) {
		return main(mc).sounds();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
