package soundsculptor.gametest;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.rules.Rule;
import soundsculptor.core.trace.RuleLocation;
import soundsculptor.core.trace.TraceText;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.mc.ui.SculptList;
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
 * Inline editing in the sound trace (PRD FR-14.4, the FR-14 v1 acceptance criteria): the Volume panel on a zone rule
 * with "Predicted for next play" and Undo, turning off the winning global rule, "Add rule for this sound" then Mute,
 * a volume of 250 refused, and "Open full editor" on a zone line.
 */
public final class TraceEditClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int SETTLE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
			});
			Vec3 origin = context.computeOnClient(mc -> mc.getSoundManager().getListenerTransform().position());
			Point pen = new Point(origin.x + 6, origin.y, origin.z);
			// The sound plays 4 blocks from the zone's anchor, so a 2-block radius no longer holds it.
			Point spot = new Point(pen.x() + 4, pen.y(), pen.z());
			Zone penZone = new Zone("pen", "Pen", true, key, OVERWORLD, pen, new Shape.Sphere(10),
					List.of(Rule.of("zr", "*villager*").withVolume(50)));

			// AC: drag the winning zone's volume from 50% to 20%: the result updates at once, marked "Predicted for next
			// play"; Undo restores 50%; the next villager sound plays at 20%.
			setRules(context, List.of(), penZone);
			check(play(context, spot) == 0.5F, "the zone should play the villager at 50%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(!t.isPredicted() && t.trace().volumePercent() == 50, "the trace should show the last play at 50%");
				t.openVolume(RuleLocation.inZone("pen", "zr"), ProfileManager.profile().zone("pen").rules().get(0));
				t.volumeSlider.setTypedValue(35);
				t.volumeSlider.setTypedValue(20);
				check(t.trace().volumePercent() == 20, "the result should update at once, was " + t.trace().volumePercent());
				check(t.isPredicted() && t.text().contains("Predicted for next play"), "the result should be a prediction:\n" + t.text());
				check(ProfileManager.profile().zone("pen").rules().get(0).volume() == 20, "the change should be saved at once");
				t.closeOverlay();
				check(t.toast() != null && t.toast().undoButton() != null, "closing the Volume panel should offer Undo");
				check(t.toast().message().getString().equals("Changed *:*villager*"), "toast was " + t.toast().message().getString());
				t.toast().undoButton().onPress(null);
				check(ProfileManager.profile().zone("pen").rules().get(0).volume() == 50, "Undo should restore 50% (not 35%)");
				check(t.trace().volumePercent() == 50, "the trace should follow Undo");
				t.openVolume(RuleLocation.inZone("pen", "zr"), ProfileManager.profile().zone("pen").rules().get(0));
				t.volumeSlider.setTypedValue(20);
				t.closeOverlay();
			});
			context.takeScreenshot("trace-edit-predicted");
			closeTrace(context);
			check(play(context, spot) == 0.2F, "the next villager sound should play at 20%");
			openTrace(context);
			check(context.computeOnClient(mc -> !trace(mc).isPredicted()), "after the sound played again the trace is no longer a prediction");
			closeTrace(context);

			// AC: turn off the winning global rule from the trace: the next rule in line wins at once.
			Rule exact = Rule.of("e", VILLAGER).withVolume(60);
			Rule wildcard = Rule.of("w", "*villager*").withVolume(30);
			setRules(context, List.of(wildcard, exact));
			check(play(context, spot) == 0.6F, "the exact rule should give 60%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row row = t.row(new TraceText.RuleTarget(RuleLocation.global("e"), exact));
				check(row != null && row.buttons().get(0).getMessage().getString().equals("On"), "the exact rule's line should have On/Off");
				row.buttons().get(0).onPress(null);
				check(t.trace().volumePercent() == 30, "the wildcard should win now, volume " + t.trace().volumePercent());
				check(t.text().contains("  *:*villager*: Volume 30%\n    Decides loudness"), "the wildcard should decide:\n" + t.text());
				check(!ProfileManager.profile().rule("e").enabled(), "the exact rule should be off");
				// The shared quick-action Undo (FR-12.5): Ctrl+Z presses the toast's Undo.
				t.pressUndo();
				check(ProfileManager.profile().rule("e").enabled() && t.trace().volumePercent() == 60, "Undo should turn the exact rule back on");
				check(t.toast() != null && t.toast().message().getString().equals("Undone"), "the toast should say Undone");
				row = t.row(new TraceText.RuleTarget(RuleLocation.global("e"), exact));
				row.buttons().get(0).onPress(null);
			});
			closeTrace(context);

			// AC: no matching rule: "Add rule for this sound" in the global list, then Mute: muted on the next play, and
			// the rule is on the Rules tab.
			setRules(context, List.of());
			check(play(context, spot) == 1.0F, "with no rules the villager plays at 100%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row none = t.row(new TraceText.NoRuleTarget(null));
				check(none != null && none.buttons().get(0).getMessage().getString().equals("Add rule"), "the global list should offer Add rule");
				none.buttons().get(0).onPress(null);
				Rule added = ProfileManager.profile().exactRule(VILLAGER);
				check(added != null, "an exact-ID rule should be added");
				SculptList.Row row = t.row(new TraceText.RuleTarget(RuleLocation.global(added.id()), added));
				check(row != null && row.buttons().get(1).getMessage().getString().equals("Mute"), "the new rule's line should offer Mute");
				row.buttons().get(1).onPress(null);
				check(ProfileManager.profile().isExactMuted(VILLAGER), "Mute should mute the new rule");
				check(t.trace().volumePercent() == 0, "the trace should show it muted");
			});
			closeTrace(context);
			check(play(context, spot) == 0.0F, "the villager should be muted on its next play");
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				SoundSculptorScreen main = (SoundSculptorScreen) mc.gui.screen();
				main.selectTab(SoundSculptorScreen.TAB_RULES);
				String id = ProfileManager.profile().exactRule(VILLAGER).id();
				check(main.bodyWidgets().stream().anyMatch(w -> w instanceof SculptList list && list.findRow(id) != null),
						"the new rule should be on the Rules tab");
			});
			context.setScreen(() -> null);

			// AC: type 250 into a volume field in the panel: an error shows and nothing changes.
			setRules(context, List.of(), penZone);
			play(context, spot);
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				t.openVolume(RuleLocation.inZone("pen", "zr"), ProfileManager.profile().zone("pen").rules().get(0));
				t.setFocused(t.volumeField);
				t.volumeField.setValue("250");
				t.volumeField.revealError();
				check(t.volumeField.isErrorShown(), "250 should show an error");
				check(t.volumeField.error().getString().contains("100"), "the error should give the range, was " + t.volumeField.error().getString());
				check(ProfileManager.profile().zone("pen").rules().get(0).volume() == 50, "250 must not change the rule");
				t.closeOverlay();
				check(t.toast() == null, "nothing changed, so there is nothing to undo");
			});

			// AC: "Open full editor" on a zone line opens the zone editor; change the radius, return: the trace shows it.
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row row = t.row(new TraceText.ZoneTarget(ProfileManager.profile().zone("pen")));
				check(row != null && row.buttons().size() == 2, "the zone line should have On/Off and Open full editor");
				row.buttons().get(1).onPress(null);
			});
			context.waitForScreen(ZoneEditorScreen.class);
			context.runOnClient(mc -> {
				ZoneEditorScreen editor = (ZoneEditorScreen) mc.gui.screen();
				editor.radiusField.setValue("2");
				editor.save();
			});
			context.waitForScreen(TraceScreen.class);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(!t.text().contains("Pen (Inside)"), "the smaller zone no longer holds the sound:\n" + t.text());
				check(t.trace().volumePercent() == 100 && t.isPredicted(), "the trace should be recomputed as a prediction");
			});
			closeTrace(context);

			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static void setRules(ClientGameTestContext context, List<Rule> globalRules, Zone... zones) {
		context.runOnClient(mc -> ProfileManager.update(p -> p.withGlobalRules(globalRules).withZones(List.of(zones))));
	}

	/** Plays the villager sound at {@code at}; returns the start factor (0 when it did not start). */
	private static float play(ClientGameTestContext context, Point at) {
		float factor = context.computeOnClient(mc -> {
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(VILLAGER), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, at.x(), at.y(), at.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound) ? SoundHook.lastStartFactor() : 0.0F;
		});
		context.waitTicks(SETTLE);
		return factor;
	}

	private static void openTrace(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(null, VILLAGER)));
		context.waitForScreen(TraceScreen.class);
	}

	private static void closeTrace(ClientGameTestContext context) {
		context.runOnClient(mc -> trace(mc).onClose());
		context.waitForScreen(null);
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
