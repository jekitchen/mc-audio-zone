package soundsculptor.gametest;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.rules.Rule;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RuleEditorScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * The M8 dev-client review: readable names for sounds without a subtitle, Settings page tabs, rule rows in the
 * Recently Played status style with match counts only for wildcards, and the rule editor showing what an unset volume
 * or mute currently is instead of "Not set".
 */
public final class M8ReviewClientGameTest implements FabricClientGameTest {
	private static final String BASALT = "minecraft:ambient.basalt_deltas.additions";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())
					.putRule(Rule.of("r1", "minecraft:entity.villager.*").withMute(true))
					.putRule(Rule.of("r2", AMBIENT).withVolume(30))
					.putRule(Rule.of("r3", "*villagr*").withMute(true))));
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

			// Rules: status like Recently Played; counts only for wildcards; a typo is flagged.
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_RULES));
			List<String> wildcard = rowTexts(context, "r1");
			check(wildcard.get(1).equals("✖ Muted") && wildcard.get(2).startsWith("Matches ") && wildcard.get(2).endsWith(" sounds"),
					"a wildcard mute row should show ✖ Muted and its match count, was " + wildcard);
			List<String> exact = rowTexts(context, "r2");
			check(exact.get(1).equals("▶ Volume 30%") && exact.get(2).isEmpty(), "an exact rule has no count, was " + exact);
			List<String> typo = rowTexts(context, "r3");
			check(typo.get(2).equals("⚠ No known sound matches"), "a pattern matching nothing is flagged, was " + typo);
			context.takeScreenshot("m8-review-rules");

			// Rule editor: an exact rule under the villager mute shows "Unchanged (muted)", not "Not set".
			context.runOnClient(mc -> mc.gui.setScreen(RuleEditorScreen.create(main(mc))));
			context.waitForScreen(RuleEditorScreen.class);
			context.getInput().typeChars("minecraft:entity.villager.celebrate");
			context.waitTicks(1);
			check(editor(context).equals(List.of("Mute: Unchanged (muted)", "Volume: Unchanged (muted)")),
					"unset values should show what the sound gets now, was " + editor(context));
			check(context.computeOnClient(mc -> ((RuleEditorScreen) mc.gui.screen()).draft().text(soundsculptor.core.editor.RuleDraft.Field.VOLUME)).isEmpty(),
					"showing the current value must not set a volume");
			context.takeScreenshot("m8-review-editor-unchanged");
			// A wildcard pattern shows the plain default.
			context.runOnClient(mc -> ((RuleEditorScreen) mc.gui.screen()).patternField.setValue("*:block.*.step"));
			context.waitTicks(1);
			check(editor(context).equals(List.of("Mute: Unchanged (not muted)", "Volume: Unchanged (100%)")),
					"a wildcard shows 100% and not muted, was " + editor(context));
			check(context.computeOnClient(mc -> ((RuleEditorScreen) mc.gui.screen()).draft().text(soundsculptor.core.editor.RuleDraft.Field.VOLUME)).isEmpty(),
					"changing the pattern must not set a volume");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitTicks(2);
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof soundsculptor.mc.ui.SculptDialog dialog) {
					dialog.choose(1); // Discard
				}
			});
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

	private static List<String> rowTexts(ClientGameTestContext context, String ruleId) {
		return context.computeOnClient(mc -> main(mc).rulesList.findRow(ruleId).columnTexts());
	}

	/** The Mute dropdown's and the Volume slider's text. */
	private static List<String> editor(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			RuleEditorScreen e = (RuleEditorScreen) mc.gui.screen();
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
