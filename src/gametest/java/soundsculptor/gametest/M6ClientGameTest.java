package soundsculptor.gametest;

import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.profile.Profile;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.ui.SculptList;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
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

/** Automated version of the M6 milestone test (PRD 11.1): the FR-3 MVP acceptance criteria on the Search tab. */
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
			});
			// A villager sound heard this session gets "Seen as: <category>" (FR-3.2).
			context.runOnClient(mc -> {
				Vec3 listener = mc.getSoundManager().getListenerTransform().position();
				mc.getSoundManager().play(new SimpleSoundInstance(Identifier.parse(VILLAGER_AMBIENT), SoundSource.NEUTRAL, 1.0F, 1.0F,
						RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + 3, listener.y(), listener.z(), false));
			});
			context.waitTicks(5);
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SEARCH);
				main(mc).searchField.setValue("");
				main(mc).runSearch();
			});
			int all = context.computeOnClient(mc -> main(mc).searchCount);
			check(all > 500, "an empty search should list every known sound, was " + all);
			check(context.computeOnClient(mc -> main(mc).searchList.children().stream()
					.noneMatch(row -> String.valueOf(row.key()).startsWith("minecraft:music.") || String.valueOf(row.key()).startsWith("minecraft:ui."))),
					"vanilla-covered sounds (music, UI) must be hidden");

			// FR-3 AC 1: type villager: only villager-related sounds, with a count. Results wait for typing to pause.
			context.runOnClient(mc -> main(mc).setFocused(main(mc).searchField));
			context.getInput().typeChars("villager");
			check(context.computeOnClient(mc -> main(mc).searchCount) == all, "results should wait 150 ms after the last keystroke");
			context.waitTicks(6);
			List<List<String>> villager = rows(context);
			check(!villager.isEmpty() && villager.size() < all, "villager should narrow the list, was " + villager.size());
			check(villager.stream().allMatch(texts -> String.join(" ", texts).toLowerCase(Locale.ROOT).contains("villager")),
					"every result should be about villagers");
			check(context.computeOnClient(mc -> main(mc).searchCount) == villager.size(), "the count should match the rows");
			List<String> ambient = context.computeOnClient(mc -> main(mc).searchList.findRow(VILLAGER_AMBIENT).columnTexts());
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
			check(keys(context).isEmpty() && context.computeOnClient(mc -> main(mc).searchCount) == 0,
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

			// FR-3 AC 6: keyboard: Tab from the field reaches the help button; Enter shows the syntax help.
			context.runOnClient(mc -> main(mc).setFocused(main(mc).searchField));
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> main(mc).getFocused() == main(mc).searchHelpButton), "Tab should reach the help button");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> main(mc).isSearchHelpShown()), "Enter should show the help");
			context.takeScreenshot("m6-search-help");
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> main(mc).searchList.getFocused() != null), "Tab should then reach the results");
			// Quick actions work on results: Enter on the first row's Mute.
			String firstId = context.computeOnClient(mc -> String.valueOf(main(mc).searchList.getFocused().key()));
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.profile(), ApplyTarget.EVERYWHERE, firstId)), "Mute on a result should mute it");
			context.runOnClient(mc -> main(mc).toggleSearchHelp());

			// Clean up for later tests.
			context.runOnClient(mc -> {
				main(mc).searchField.setValue("");
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static void search(ClientGameTestContext context, String text) {
		context.runOnClient(mc -> main(mc).searchField.setValue(text));
		context.waitTicks(6);
	}

	private static List<List<String>> rows(ClientGameTestContext context) {
		return context.computeOnClient(mc -> main(mc).searchList.children().stream().map(SculptList.Row::columnTexts).toList());
	}

	private static List<String> keys(ClientGameTestContext context) {
		return context.computeOnClient(mc -> main(mc).searchList.children().stream().map(row -> String.valueOf(row.key())).toList());
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
