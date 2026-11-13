package soundsculptor.gametest;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.SetEditorScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * The M8 dev-client review: readable names for sounds without a subtitle, Settings page tabs, entry rows in the
 * Recently Played status style with match counts only for groups, and the entry editor showing "Unchanged" for an
 * unset volume or mute.
 */
public final class M8ReviewClientGameTest implements FabricClientGameTest {
	private static final String BASALT = "minecraft:ambient.basalt_deltas.additions";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true),
							SetEntry.of("r2", AMBIENT).withVolume(30),
							SetEntry.of("r3", "*villagr*").withMute(true))))));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);

			// Search: a sound without a subtitle gets a readable name; its ID stays on the second line.
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SEARCH);
				main(mc).searchField.setValue("basalt additions");
				main(mc).runSearch();
			});
			List<String> basalt = context.computeOnClient(mc -> main(mc).searchList.findRow(BASALT).columnTexts());
			check(basalt.get(0).equals("Basalt deltas: additions (ambient)") && basalt.contains(BASALT),
					"an unnamed sound should get a readable name, was " + basalt);
			context.takeScreenshot("m8-review-search-names");
			context.runOnClient(mc -> main(mc).searchField.setValue(""));

			// Entry rows (Everywhere's Quick changes): status like Recently Played; counts only for groups; a typo is flagged.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.forQuickChanges(main(mc), PlaceRef.EVERYWHERE)));
			context.waitForScreen(SetEditorScreen.class);
			List<String> wildcard = rowTexts(context, "r1");
			check(wildcard.get(1).equals("✖ Muted") && wildcard.get(2).startsWith("Matches ") && wildcard.get(2).endsWith(" sounds"),
					"a group mute row should show ✖ Muted and its match count, was " + wildcard);
			List<String> exact = rowTexts(context, "r2");
			check(exact.get(1).equals("▶ Level 30%") && exact.get(2).equals(AMBIENT), "a single-sound entry has no count, was " + exact);
			List<String> typo = rowTexts(context, "r3");
			check(typo.get(2).equals("⚠ No known sound matches"), "a pattern matching nothing is flagged, was " + typo);
			context.takeScreenshot("m8-review-entries");

			// Entry editor: a value the entry leaves alone reads "Unchanged" (Task 12 removed the inherited value; the
			// trace shows what a sound gets).
			context.runOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).addSounds());
			context.waitForScreen(EntryEditorScreen.class);
			context.getInput().typeChars("minecraft:entity.villager.celebrate");
			context.waitTicks(1);
			check(editor(context).equals(List.of("Mute: Unchanged", "Level: Unchanged")),
					"unset values should read Unchanged, was " + editor(context));
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).draft().text(soundsculptor.core.editor.EntryDraft.Field.VOLUME)).isEmpty(),
					"showing the current value must not set a volume");
			context.takeScreenshot("m8-review-editor-unchanged");
			// A wildcard pattern shows the plain default.
			context.runOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).patternField.setValue("*:block.*.step"));
			context.waitTicks(1);
			check(editor(context).equals(List.of("Mute: Unchanged", "Level: Unchanged")),
					"a wildcard reads Unchanged too, was " + editor(context));
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).draft().text(soundsculptor.core.editor.EntryDraft.Field.VOLUME)).isEmpty(),
					"changing the pattern must not set a volume");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitTicks(2);
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof soundsculptor.mc.ui.SculptDialog dialog) {
					dialog.choose(1); // Discard
				}
			});
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitForScreen(SoundSculptorScreen.class);

			// Settings: page tabs, the current one selected; choosing one switches the page.
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_SETTINGS));
			check(context.computeOnClient(mc -> main(mc).pageTabs.size()) == 3, "three page tabs: General, Zones, Diagnostics");
			check(context.computeOnClient(mc -> main(mc).pageTabs.get(0).isSelected()), "General is selected first");
			context.runOnClient(mc -> main(mc).pageTabs.get(2).onPress(null));
			check(context.computeOnClient(mc -> main(mc).settingsPage()) == SoundSculptorScreen.SettingsPage.DIAGNOSTICS,
					"pressing the Diagnostics tab should open that page");
			check(context.computeOnClient(mc -> main(mc).pageTabs.get(2).isSelected()), "the Diagnostics tab is now selected");
			context.takeScreenshot("m8-review-settings-tabs");

			context.runOnClient(mc -> {
				main(mc).setSettingsPage(SoundSculptorScreen.SettingsPage.GENERAL);
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static List<String> rowTexts(ClientGameTestContext context, String entryId) {
		return context.computeOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).entryList.findRow(entryId).columnTexts());
	}


	/** The Mute dropdown's and the Volume slider's text. */
	private static List<String> editor(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			EntryEditorScreen e = (EntryEditorScreen) mc.gui.screen();
			return List.of(e.muteDropdown.getMessage().getString(), e.volumeSlider.getMessage().getString());
		});
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
