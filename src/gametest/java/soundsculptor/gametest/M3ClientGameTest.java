package soundsculptor.gametest;

import soundsculptor.core.recent.SortOrder;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundSculptorSettings;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptList;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M3 milestone test (PRD 11.1: FR-1 acceptance criteria rows 1, 2, 4 and 5) on the Sounds
 * tab with the Recently played filter on, plus its controls: Pause (and P), sort, search, refresh rate, keyboard access
 * and narration.
 */
public final class M3ClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String ANVIL = "minecraft:block.anvil.land";
	private static final int SETTLE = 8; // more than one refresh interval

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				RecentLog.get().sounds().clear();
				SoundSculptorSettings.setShowDistance(true);
				SoundsPanel.resetRememberedState();
			});
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> screen(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS));
			check(context.computeOnClient(mc -> sounds(mc).isRecentOnly()), "the Recently played filter is on when the tab first opens");
			check(context.computeOnClient(mc -> sounds(mc).list.children().isEmpty()), "the list should start empty in a new world");
			context.takeScreenshot("m3-empty");

			// AC 4: the Sounds tab does not pause the game.
			check(!context.computeOnClient(mc -> screen(mc).isPauseScreen()), "the Sounds tab must not pause the game");
			long before = context.computeOnClient(mc -> mc.level.getGameTime());
			context.waitTicks(20);
			long after = context.computeOnClient(mc -> mc.level.getGameTime());
			check(after - before >= 10, "the world should keep running with the list open (game time " + before + " -> " + after + ")");

			// AC 1: a villager sound 3 blocks away appears with its subtitle, category and band.
			play(context, VILLAGER, SoundSource.NEUTRAL, 3.0);
			List<String> villager = rowTexts(context, VILLAGER);
			check(villager != null, "a villager sound 3 blocks away should be listed");
			check(villager.get(0).equals("Villager mumbles"), "the row should show the subtitle, was " + villager);
			check(villager.contains(VILLAGER), "the row should show the full ID, was " + villager);
			check(villager.contains("Friendly Mobs"), "the row should show the category name, was " + villager);
			check(villager.contains("Close"), "3 blocks should be the Close band, was " + villager);
			check(villager.stream().anyMatch(t -> t.startsWith("×1 ·")), "the row should show 1 play, was " + villager);
			check(villager.stream().anyMatch(t -> t.contains("Playing")), "the row should show the Playing status, was " + villager);

			// AC 2: sounds out of vanilla range are not listed and do not count.
			play(context, ANVIL, SoundSource.BLOCKS, 30.0);
			check(rowTexts(context, ANVIL) == null, "an anvil 30 blocks away (range 16) must not be listed");
			play(context, VILLAGER, SoundSource.NEUTRAL, 30.0);
			check(rowTexts(context, VILLAGER).stream().anyMatch(t -> t.startsWith("×1 ·")), "a villager 30 blocks away must not add to the count");

			// Another audible play: count 2, band updates to Near.
			play(context, VILLAGER, SoundSource.NEUTRAL, 10.0);
			List<String> again = rowTexts(context, VILLAGER);
			check(again.stream().anyMatch(t -> t.startsWith("×2 ·")) && again.contains("Near"), "second play at 10 blocks: ×2 and Near, was " + again);

			// Vanilla-covered sounds (UI) are never listed.
			context.runOnClient(mc -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)));
			context.waitTicks(SETTLE);
			check(rowTexts(context, "minecraft:ui.button.click") == null, "UI sounds must never be listed");

			// Pause freezes the list; P (with the list focused) resumes it.
			context.runOnClient(mc -> sounds(mc).setPaused(true));
			play(context, ANVIL, SoundSource.BLOCKS, 2.0);
			check(rowTexts(context, ANVIL) == null, "a paused list must not change");
			// Tab order: search field, ?, Recently played, Apply to, Pause, Sort, then the list.
			context.runOnClient(mc -> screen(mc).setFocused(sounds(mc).searchField));
			for (int i = 0; i < 6; i++) {
				context.getInput().pressKey(InputConstants.KEY_TAB);
			}
			context.waitTicks(2);
			check(context.computeOnClient(mc -> screen(mc).getFocused() == sounds(mc).list), "Tab from the search field should reach the list after the toolbar");
			context.getInput().pressKey(InputConstants.KEY_P);
			context.waitTicks(SETTLE);
			check(!context.computeOnClient(mc -> sounds(mc).isPaused()), "P with the list focused should resume");
			check(rowTexts(context, ANVIL) != null, "after resuming, the anvil sound should appear");

			// Keyboard focus and narration of a read-only row.
			String narration = context.computeOnClient(mc -> NarrationCompat.collect(sounds(mc).list::updateNarration));
			// Focus stays on the same sound when newer rows arrive above it, so the position can be 1 or 2.
			check(narration.matches("(?s).*row [12] out of 2.*") && (narration.contains("Anvil landed") || narration.contains("Villager mumbles"))
					&& narration.contains("Friendly Mobs") == narration.contains("Villager mumbles"),
					"the focused row should be narrated with its text and position, was: " + narration);

			// Refresh at most 4 times per second (FR-1.6).
			int refreshesBefore = context.computeOnClient(mc -> sounds(mc).refreshCount);
			context.waitTicks(40);
			int refreshes = context.computeOnClient(mc -> sounds(mc).refreshCount) - refreshesBefore;
			check(refreshes >= 6 && refreshes <= 9, "expected about 8 refreshes in 2 seconds, got " + refreshes);

			// Sort: by play count the villager (2 plays) is first; by name "Anvil landed" is first.
			context.runOnClient(mc -> sounds(mc).setSort(SortOrder.PLAY_COUNT));
			check(firstRowKey(context).equals(VILLAGER), "by play count the villager (2 plays) comes first");
			context.runOnClient(mc -> sounds(mc).setSort(SortOrder.NAME));
			check(firstRowKey(context).equals(ANVIL), "by name 'Anvil landed' comes first");
			context.runOnClient(mc -> sounds(mc).setSort(SortOrder.MOST_RECENT));

			// Search with the filter on.
			context.runOnClient(mc -> sounds(mc).searchField.setValue("villager"));
			context.waitTicks(SETTLE);
			check(context.computeOnClient(mc -> sounds(mc).list.children().size()) == 1 && rowTexts(context, VILLAGER) != null,
					"searching for 'villager' should leave only the villager row");
			context.runOnClient(mc -> sounds(mc).searchField.setValue(""));
			context.waitTicks(SETTLE);
			context.takeScreenshot("m3-recently-played");

			// AC 5: "Show distance" off hides the distance column; on shows it again.
			context.runOnClient(mc -> SoundSculptorSettings.setShowDistance(false));
			context.waitTicks(SETTLE);
			List<String> hidden = rowTexts(context, VILLAGER);
			check(!hidden.contains("Near") && !hidden.contains("Close") && !hidden.contains("Far"), "with Show distance off, no band should be shown, was " + hidden);
			context.takeScreenshot("m3-no-distance");
			context.runOnClient(mc -> SoundSculptorSettings.setShowDistance(true));
			context.waitTicks(SETTLE);
			check(rowTexts(context, VILLAGER).contains("Near"), "with Show distance on, the band should be back");

			// The Settings tab has the Show distance toggle.
			context.runOnClient(mc -> screen(mc).selectTab(SoundSculptorScreen.TAB_SETTINGS));
			context.waitTicks(2);
			context.takeScreenshot("m3-settings");
			context.runOnClient(mc -> screen(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS));
			context.setScreen(() -> null);
		}
	}

	private static String firstRowKey(ClientGameTestContext context) {
		return context.computeOnClient(mc -> (String) sounds(mc).list.children().get(0).key());
	}

	/** Plays a sound {@code blocksAway} east of the listener, then waits for the list to refresh. */
	private static void play(ClientGameTestContext context, String id, SoundSource source, double blocksAway) {
		context.runOnClient(mc -> {
			Vec3 listener = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), source, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + blocksAway, listener.y(), listener.z(), false);
			mc.getSoundManager().play(sound);
		});
		context.waitTicks(SETTLE);
	}

	/** The column texts of the row for {@code id}, or null if it is not listed. */
	private static List<String> rowTexts(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> {
			SculptList.Row row = sounds(mc).list.findRow(id);
			return row == null ? null : row.columnTexts();
		});
	}

	private static SoundSculptorScreen screen(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static SoundsPanel sounds(Minecraft mc) {
		return screen(mc).sounds();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
