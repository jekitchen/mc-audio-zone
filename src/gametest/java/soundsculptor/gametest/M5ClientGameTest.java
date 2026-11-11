package soundsculptor.gametest;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.rules.Rule;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.RuleEditorScreen;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.ui.SculptDialog;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Function;
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
 * Automated version of the M5 milestone test (PRD 11.1): the FR-4 and FR-5 MVP acceptance criteria, through the
 * Rules tab and the rule editor.
 */
public final class M5ClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER_AMBIENT = "minecraft:entity.villager.ambient";
	private static final String VILLAGER_HURT = "minecraft:entity.villager.hurt";
	private static final String TRADER_AMBIENT = "minecraft:entity.wandering_trader.ambient";
	private static final String STONE_STEP = "minecraft:block.stone.step";
	private static final String WOOD_STEP = "minecraft:block.wood.step";
	private static final String UI_CLICK = "minecraft:ui.button.click";
	private static final int SETTLE = 8;
	/** The start factor of the last sound {@link #play} started. */
	private static volatile float lastStartFactor;

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
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_RULES));
			context.takeScreenshot("m5-rules-empty");

			// FR-4 AC 1: rule minecraft:entity.villager.* set to Mute, made with the keyboard from the Rules tab.
			context.runOnClient(mc -> main(mc).setFocused(main(mc).addRuleButton));
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitForScreen(RuleEditorScreen.class);
			// 6.2: a fresh editor shows no error yet, but Save is disabled until the pattern is valid.
			check(editor(context, e -> !e.patternField.isErrorShown() && !e.saveButton.active),
					"a new rule should open without an error shown and with Save disabled");
			context.takeScreenshot("m5-editor-new");
			// FR-4 AC 4: uppercase becomes lowercase as it is typed.
			context.getInput().typeChars("Minecraft:Entity.Villager.*");
			context.waitTicks(1);
			check(editor(context, e -> e.patternField.getValue()).equals("minecraft:entity.villager.*"),
					"the pattern should be lowercased, was " + editor(context, e -> e.patternField.getValue()));
			check(editor(context, e -> e.preview().matches().contains(VILLAGER_AMBIENT) && e.preview().matches().contains(VILLAGER_HURT)
					&& !e.preview().matches().contains(TRADER_AMBIENT)), "the preview should list villager sounds only");
			check(editor(context, e -> e.preview().count() == e.matchList.children().size()), "the match list should show every match");
			// FR-4 AC 4: an invalid character shows an error and disables Save.
			context.getInput().typeChars("#");
			context.waitTicks(1);
			check(editor(context, e -> e.patternField.isErrorShown() && !e.saveButton.active), "# should show an error and disable Save");
			context.takeScreenshot("m5-editor-pattern-error");
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.waitTicks(1);
			check(editor(context, e -> !e.patternField.isErrorShown() && e.saveButton.active), "removing # should clear the error");
			context.runOnClient(mc -> editor(mc).setMute(RuleEditorScreen.MuteChoice.MUTE));
			context.takeScreenshot("m5-editor-villager-mute");
			context.runOnClient(mc -> editor(mc).save());
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().size() == 1), "Save should add the rule");
			String wildcardId = context.computeOnClient(mc -> ProfileManager.profile().globalRules().get(0).id());
			int listed = context.computeOnClient(mc -> soundsculptor.core.lists.RuleSummary.matchCount(ProfileManager.profile().rule(wildcardId),
					soundsculptor.mc.KnownSounds.ids()));
			int previewed = context.computeOnClient(mc -> soundsculptor.core.editor.PatternPreview.of("minecraft:entity.villager.*",
					soundsculptor.mc.KnownSounds.ids(), soundsculptor.mc.KnownSounds.seenExcluded()).count());
			check(listed == previewed && listed > 0, "the list's match count (" + listed + ") should equal the preview's (" + previewed + ")");

			check(!play(context, VILLAGER_AMBIENT, SoundSource.NEUTRAL), "villager ambient should be muted");
			check(!play(context, VILLAGER_HURT, SoundSource.NEUTRAL), "villager hurt should be muted");
			check(play(context, TRADER_AMBIENT, SoundSource.NEUTRAL), "the wandering trader is not a villager and should play");

			// FR-4 AC 3: an exact rule at 100% below the mute rule wins for that one sound.
			context.runOnClient(mc -> mc.gui.setScreen(RuleEditorScreen.create(main(mc))));
			context.waitForScreen(RuleEditorScreen.class);
			context.getInput().typeChars(VILLAGER_AMBIENT);
			context.runOnClient(mc -> editor(mc).setFocused(editor(mc).volumeField));
			context.getInput().typeChars("100");
			context.waitTicks(1);
			check(editor(context, e -> e.draft().build().volume() == 100), "typing 100 should set the volume");
			context.runOnClient(mc -> editor(mc).save());
			context.waitForScreen(SoundSculptorScreen.class);
			check(play(context, VILLAGER_AMBIENT, SoundSource.NEUTRAL), "the exact rule should let villager ambient play");
			check(lastStartFactor == 1.0F, "at 100% the volume is unchanged");
			check(!play(context, VILLAGER_HURT, SoundSource.NEUTRAL), "other villager sounds stay muted");
			context.runOnClient(mc -> mc.gui.setScreen(RuleEditorScreen.edit(main(mc), ProfileManager.profile().rule(wildcardId))));
			context.waitForScreen(RuleEditorScreen.class);
			String overridden = editor(context, e -> e.overriddenText().getString());
			check(overridden.equals("Partly overridden by: " + VILLAGER_AMBIENT), "the wildcard rule should say it is overridden, was " + overridden);
			context.takeScreenshot("m5-editor-overridden");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(SoundSculptorScreen.class);

			// FR-4 AC 2 and FR-5 AC: *:block.*.step at 50% makes every footstep quieter, by the rule's factor.
			context.runOnClient(mc -> ProfileManager.update(p -> p.putRule(Rule.of(p.newRuleId(), "*:block.*.step").withVolume(50))));
			check(play(context, STONE_STEP, SoundSource.BLOCKS), "a 50% sound still plays");
			check(lastStartFactor == 0.5F, "stone steps should play at 50%");
			check(play(context, WOOD_STEP, SoundSource.BLOCKS), "a 50% sound still plays");
			check(lastStartFactor == 0.5F, "wood steps should play at 50%");
			// The volume is applied again whenever vanilla recalculates a playing sound's volume.
			check(context.computeOnClient(mc -> SoundHook.applyVolume(instance(mc, STONE_STEP, SoundSource.BLOCKS), 0.8F)) == 0.4F,
					"recalculated volumes get the rule factor too");

			// A-2: edit a rule's volume with the keyboard only: the slider moves in 5% steps.
			String stepId = context.computeOnClient(mc -> ProfileManager.profile().globalRules().get(2).id());
			context.runOnClient(mc -> mc.gui.setScreen(RuleEditorScreen.edit(main(mc), ProfileManager.profile().rule(stepId))));
			context.waitForScreen(RuleEditorScreen.class);
			context.runOnClient(mc -> editor(mc).setFocused(editor(mc).volumeSlider));
			context.getInput().pressKey(InputConstants.KEY_LEFT);
			context.getInput().pressKey(InputConstants.KEY_LEFT);
			context.waitTicks(1);
			check(editor(context, e -> e.volumeField.getValue()).equals("40"), "two Left presses should give 40%, was " + editor(context, e -> e.volumeField.getValue()));
			// FR-12.4: Esc with unsaved changes asks; Keep editing returns with the value intact.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(SculptDialog.class);
			context.takeScreenshot("m5-editor-unsaved-dialog");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(2));
			context.waitForScreen(RuleEditorScreen.class);
			check(editor(context, e -> e.volumeField.getValue()).equals("40"), "Keep editing should keep 40%");
			context.runOnClient(mc -> editor(mc).save());
			context.waitForScreen(SoundSculptorScreen.class);
			check(play(context, STONE_STEP, SoundSource.BLOCKS), "a 40% sound still plays");
			check(lastStartFactor == 0.4F, "stone steps should now play at 40%");

			// FR-5.1: 0% (not muted) plays nothing, like a zero vanilla slider.
			context.runOnClient(mc -> ProfileManager.update(p -> p.putRule(p.rule(stepId).withVolume(0))));
			check(!play(context, STONE_STEP, SoundSource.BLOCKS), "a 0% sound should not start");
			context.runOnClient(mc -> ProfileManager.update(p -> p.putRule(p.rule(stepId).withVolume(50))));

			// FR-4 AC 5: turning a rule off stops its effect at once; on again brings it back.
			context.runOnClient(mc -> main(mc).refreshRules());
			context.takeScreenshot("m5-rules-list");
			context.runOnClient(mc -> main(mc).setRuleEnabled(wildcardId, false));
			check(play(context, VILLAGER_HURT, SoundSource.NEUTRAL), "with the rule off, villager hurt should play");
			context.runOnClient(mc -> main(mc).setRuleEnabled(wildcardId, true));
			check(!play(context, VILLAGER_HURT, SoundSource.NEUTRAL), "with the rule on again, villager hurt is muted");

			// Vanilla-covered sounds are never changed, not even by *.
			context.runOnClient(mc -> ProfileManager.update(p -> p.putRule(Rule.of(p.newRuleId(), "*").withMute(true))));
			check(context.computeOnClient(mc -> {
				SoundInstance click = SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(UI_CLICK)), 1.0F);
				mc.getSoundManager().play(click);
				return mc.getSoundManager().isActive(click);
			}), "a UI click must not be muted by *");
			check(!play(context, TRADER_AMBIENT, SoundSource.NEUTRAL), "* mutes ordinary sounds");

			// Move and delete from the list.
			String starId = context.computeOnClient(mc -> ProfileManager.profile().globalRules().get(3).id());
			context.runOnClient(mc -> main(mc).moveRule(starId, -1));
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().get(2).id()).equals(starId), "Move up should move the rule");
			context.runOnClient(mc -> main(mc).confirmDelete(ProfileManager.profile().rule(starId)));
			context.waitForScreen(SculptDialog.class);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().rule(starId) != null), "Esc in the delete dialog must keep the rule");
			context.runOnClient(mc -> main(mc).confirmDelete(ProfileManager.profile().rule(starId)));
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().rule(starId) == null), "Delete should remove the rule");
			check(context.computeOnClient(mc -> main(mc).rulesList.findRow(starId) == null), "the deleted rule's row should be gone at once");
			check(play(context, TRADER_AMBIENT, SoundSource.NEUTRAL), "after deleting *, the trader plays again");

			// Edit from Recently Played opens the sound's exact rule.
			context.runOnClient(mc -> mc.gui.setScreen(RuleEditorScreen.forSound(main(mc), VILLAGER_AMBIENT)));
			context.waitForScreen(RuleEditorScreen.class);
			check(editor(context, e -> e.draft().build().volume() == 100), "Edit should open the existing exact rule");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(SoundSculptorScreen.class);

			// Clean up for later tests.
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static SoundInstance instance(Minecraft mc, String id, SoundSource source) {
		Vec3 listener = mc.getSoundManager().getListenerTransform().position();
		return new SimpleSoundInstance(Identifier.parse(id), source, 1.0F, 1.0F, RandomSource.create(),
				false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + 3.0, listener.y(), listener.z(), false);
	}

	/** Plays a sound 3 blocks east of the listener; returns whether it actually started. */
	private static boolean play(ClientGameTestContext context, String id, SoundSource source) {
		boolean started = context.computeOnClient(mc -> {
			SoundInstance sound = instance(mc, id, source);
			mc.getSoundManager().play(sound);
			// Read the factor in the same step: an ambient world sound starting later would replace it.
			lastStartFactor = SoundHook.lastStartFactor();
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(SETTLE);
		return started;
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static RuleEditorScreen editor(Minecraft mc) {
		check(mc.gui.screen() instanceof RuleEditorScreen, "expected the rule editor, found " + mc.gui.screen());
		return (RuleEditorScreen) mc.gui.screen();
	}

	private static <T> T editor(ClientGameTestContext context, Function<RuleEditorScreen, T> read) {
		return context.computeOnClient(mc -> read.apply(editor(mc)));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
