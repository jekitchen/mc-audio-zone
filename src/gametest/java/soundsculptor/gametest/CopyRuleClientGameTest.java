package soundsculptor.gametest;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.RuleTarget;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RuleCopyScreen;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.ZoneRulesScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * "Add existing rule" on a zone's rules (maxismagical's request): the picker lists the global rules and the other
 * zones' rules, Copy adds an independent copy, a pattern the zone already has cannot be copied again, and editing the
 * copy leaves the original alone.
 */
public final class CopyRuleClientGameTest implements FabricClientGameTest {
	private static final String VILLAGERS = "minecraft:entity.villager.*";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())
					.putRule(SetEntry.of("r1", VILLAGERS).withMute(true))
					.withZones(List.of(zone("a", key, SetEntry.of("r1", "*cow*").withVolume(40)), zone("b", key)))));

			// The global Rules tab has no "Add existing rule"; a zone's rules do.
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> ((SoundSculptorScreen) mc.gui.screen()).selectTab(SoundSculptorScreen.TAB_RULES));
			check(context.computeOnClient(mc -> ((SoundSculptorScreen) mc.gui.screen()).rulesList != null
					&& mc.gui.screen().children().stream().noneMatch(c -> c instanceof soundsculptor.mc.ui.SculptButton b
							&& b.getMessage().getString().equals("Add existing rule"))), "the global list has no Add existing rule");
			context.runOnClient(mc -> mc.gui.setScreen(new ZoneRulesScreen(mc.gui.screen(), "b")));
			context.waitForScreen(ZoneRulesScreen.class);
			check(context.computeOnClient(mc -> ((ZoneRulesScreen) mc.gui.screen()).panel.addExistingButton != null),
					"a zone's rules have Add existing rule");
			context.runOnClient(mc -> ((ZoneRulesScreen) mc.gui.screen()).panel.addExistingButton.onPress(null));
			context.waitForScreen(RuleCopyScreen.class);

			// The picker: the global rule first, then zone a's rule; zone b's own rules are not offered.
			List<List<String>> rows = context.computeOnClient(mc -> ((RuleCopyScreen) mc.gui.screen()).list.children().stream()
					.map(soundsculptor.mc.ui.SculptList.Row::columnTexts).toList());
			check(rows.size() == 2, "two rules to copy, was " + rows);
			check(rows.get(0).get(0).equals(VILLAGERS) && rows.get(0).get(2).startsWith("From the global rules"),
					"the global rule is listed with where it comes from, was " + rows.get(0));
			check(rows.get(1).get(2).startsWith("From zone Zone a"), "zone a's rule says which zone it is from, was " + rows.get(1));
			context.takeScreenshot("copy-rule-picker");

			// Copy the global rule: back on zone b's rules, with a toast; the copy has its own ID in zone b.
			context.runOnClient(mc -> ((RuleCopyScreen) mc.gui.screen()).list.children().get(0).buttons().get(0).onPress(null));
			context.waitForScreen(ZoneRulesScreen.class);
			check(context.computeOnClient(mc -> ((ZoneRulesScreen) mc.gui.screen()).toast() != null
					&& ((ZoneRulesScreen) mc.gui.screen()).toast().message().getString().equals("Copied " + VILLAGERS + " into this zone")),
					"a toast should confirm the copy");
			List<SetEntry> copied = context.computeOnClient(mc -> RuleTarget.zone("b").rules(ProfileManager.profile()));
			check(copied.size() == 1 && copied.get(0).pattern().text().equals(VILLAGERS) && Boolean.TRUE.equals(copied.get(0).mute()),
					"zone b should hold a copy of the villager mute, was " + copied);
			context.takeScreenshot("copy-rule-zone-rules");

			// The same pattern cannot be copied twice.
			context.runOnClient(mc -> ((ZoneRulesScreen) mc.gui.screen()).panel.addExistingButton.onPress(null));
			context.waitForScreen(RuleCopyScreen.class);
			check(!context.computeOnClient(mc -> ((RuleCopyScreen) mc.gui.screen()).list.children().get(0).buttons().get(0).active),
					"Copy is off for a pattern the zone already has");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitForScreen(ZoneRulesScreen.class);

			// Not linked: change the copy in the editor; the global rule keeps its mute.
			context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.edit(mc.gui.screen(), RuleTarget.zone("b"),
					RuleTarget.zone("b").rules(ProfileManager.profile()).get(0))));
			context.waitForScreen(EntryEditorScreen.class);
			context.runOnClient(mc -> {
				EntryEditorScreen editor = (EntryEditorScreen) mc.gui.screen();
				editor.setMute(EntryEditorScreen.MuteChoice.DO_NOT_MUTE);
				editor.save();
			});
			context.waitForScreen(ZoneRulesScreen.class);
			check(context.computeOnClient(mc -> Boolean.FALSE.equals(RuleTarget.zone("b").rules(ProfileManager.profile()).get(0).mute())),
					"the copy should change");
			check(context.computeOnClient(mc -> Boolean.TRUE.equals(ProfileManager.profile().rule("r1").mute())),
					"the original global rule must not change");

			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static Zone zone(String id, String key, SetEntry... rules) {
		return new Zone(id, "Zone " + id, true, key, "minecraft:overworld", new Point(0, -60, 0), new Shape.Sphere(8), List.of(rules));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
