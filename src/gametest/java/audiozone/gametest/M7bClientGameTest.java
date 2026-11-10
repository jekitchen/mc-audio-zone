package audiozone.gametest;

import audiozone.core.editor.ZoneDraft;
import audiozone.core.profile.Profile;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.mc.AudioZoneScreen;
import audiozone.mc.ProfileManager;
import audiozone.mc.RuleEditorScreen;
import audiozone.mc.SoundHook;
import audiozone.mc.ZoneEditorScreen;
import audiozone.mc.ZoneRulesScreen;
import audiozone.mc.ui.AzDialog;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
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
 * Automated version of the M7b tests: the Zones tab and zone editor (PRD 6.1), A-2 core tasks "create a sphere zone
 * at your position" and "change a zone's color" with the keyboard, zone rules (FR-8.6), the FR-10.4 color AC, and the
 * unsaved-changes dialog (FR-12.4).
 */
public final class M7bClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final float SILENT = 1.0E-6F;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
			});
			context.setScreen(AudioZoneScreen::new);
			context.waitForScreen(AudioZoneScreen.class);
			context.runOnClient(mc -> main(mc).selectTab(AudioZoneScreen.TAB_ZONES));
			context.takeScreenshot("m7b-zones-empty");

			// A-2: create a sphere zone at your position, keyboard only: New zone (Enter), then Save.
			context.runOnClient(mc -> main(mc).setFocused(main(mc).zonesPanel().newZoneButton));
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitForScreen(ZoneEditorScreen.class);
			Vec3 feet = context.computeOnClient(mc -> mc.player.position());
			check(editor(context, e -> e.nameField.getValue()).equals("Zone 1"), "a new zone should be named Zone 1");
			check(editor(context, e -> e.draft().shape() == ZoneDraft.ShapeKind.SPHERE && e.radiusField.getValue().equals("10")),
					"a new zone should be a 10-block sphere");
			check(editor(context, e -> Math.abs(Double.parseDouble(e.xField.getValue()) - feet.x) < 0.1
					&& Math.abs(Double.parseDouble(e.zField.getValue()) - feet.z) < 0.1), "a new zone should be centered on the player");
			check(editor(context, e -> e.colorPicker.color()) == 0xE69F00, "the first zone takes the first palette color (orange)");
			// Invalid size: an error and Save disabled; then a valid one.
			context.runOnClient(mc -> editorScreen(mc).radiusField.setValue("300"));
			context.waitTicks(1);
			check(editor(context, e -> !e.saveButton.active && e.radiusField.error() != null), "radius 300 should be refused");
			context.runOnClient(mc -> editorScreen(mc).radiusField.setValue("8"));
			context.waitTicks(1);
			check(editor(context, e -> e.saveButton.active), "radius 8 should be accepted");
			context.takeScreenshot("m7b-zone-editor");

			// FR-10 AC: change the color by swatch (keyboard: focus the swatch, Enter) and by hex; #XYZ is refused.
			context.runOnClient(mc -> editorScreen(mc).setFocused(editorScreen(mc).colorPicker.swatches().get(2)));
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(1);
			int swatch = context.computeOnClient(mc -> editorScreen(mc).colorPicker.swatches().get(2).rgb());
			check(editor(context, e -> e.colorPicker.color()) == swatch, "Enter on a swatch should pick it");
			context.runOnClient(mc -> editorScreen(mc).colorPicker.hexField().setValue("#XYZ"));
			check(editor(context, e -> e.colorPicker.hexField().error() != null), "#XYZ should show an error");
			context.runOnClient(mc -> editorScreen(mc).colorPicker.hexField().setValue("#56B4E9"));
			check(editor(context, e -> e.colorPicker.color()) == 0x56B4E9, "#56B4E9 should set the color");

			// Zone rules: saves the zone, then its rule list; add *villager* Mute.
			context.runOnClient(mc -> editorScreen(mc).openRules());
			context.waitForScreen(ZoneRulesScreen.class);
			String zoneId = context.computeOnClient(mc -> ((ZoneRulesScreen) mc.gui.screen()).zoneId());
			Zone saved = context.computeOnClient(mc -> ProfileManager.profile().zone(zoneId));
			check(saved != null && saved.shape().equals(new Shape.Sphere(8)), "Zone rules should save the zone first, was " + saved);
			check(context.computeOnClient(mc -> ProfileManager.profile().colorOf(zoneId)) == 0x56B4E9, "the color should be saved");
			context.runOnClient(mc -> ((ZoneRulesScreen) mc.gui.screen()).panel.addButton.onPress(null));
			context.waitForScreen(RuleEditorScreen.class);
			context.getInput().typeChars("*villager*");
			context.runOnClient(mc -> {
				RuleEditorScreen e = (RuleEditorScreen) mc.gui.screen();
				e.setMute(RuleEditorScreen.MuteChoice.MUTE);
				e.save();
			});
			context.waitForScreen(ZoneRulesScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone(zoneId).rules().size()) == 1, "the rule should be in the zone");
			check(context.computeOnClient(mc -> ProfileManager.profile().globalRules().isEmpty()), "and not in the global list");
			context.takeScreenshot("m7b-zone-rules");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitForScreen(AudioZoneScreen.class);

			// The zone works: a villager where the player stands is muted; the row shows color, place and rule count.
			check(play(context, feet.x + 2, feet.y, feet.z) <= SILENT, "a villager inside the new zone should be muted");
			List<String> row = context.computeOnClient(mc -> main(mc).zonesPanel().list.findRow(zoneId).columnTexts());
			check(row.get(0).equals("Zone 1") && row.contains("Here") && row.contains("1 rule") && row.contains("Sphere, radius 8, Inside"), "the zone row should describe it, was " + row);
			check(context.computeOnClient(mc -> main(mc).zonesPanel().list.findRow(zoneId).swatch()) == 0x56B4E9, "the row shows the zone color");
			context.takeScreenshot("m7b-zones-list");

			// FR-12.4: change the radius and press Esc: the dialog appears; Keep editing keeps the value.
			context.runOnClient(mc -> mc.gui.setScreen(ZoneEditorScreen.edit(main(mc), ProfileManager.profile().zone(zoneId))));
			context.waitForScreen(ZoneEditorScreen.class);
			context.runOnClient(mc -> editorScreen(mc).radiusField.setValue("5"));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(AzDialog.class);
			context.runOnClient(mc -> ((AzDialog) mc.gui.screen()).choose(2));
			context.waitForScreen(ZoneEditorScreen.class);
			check(editor(context, e -> e.radiusField.getValue()).equals("5"), "Keep editing should keep the radius");
			// Box shape: width, height and depth fields.
			context.runOnClient(mc -> editorScreen(mc).setShape(ZoneDraft.ShapeKind.BOX));
			check(editor(context, e -> e.bodyWidgets().contains(e.widthField) && e.bodyWidgets().contains(e.depthField)
					&& !e.bodyWidgets().contains(e.radiusField)), "a box shows width, height and depth");
			context.runOnClient(mc -> editorScreen(mc).save());
			context.waitForScreen(AudioZoneScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone(zoneId).shape()) instanceof Shape.Box, "Save should store the box");
			check(context.computeOnClient(mc -> ProfileManager.profile().zone(zoneId).rules().size()) == 1, "saving the zone keeps its rules");

			// Off: the zone stops; Delete asks first.
			context.runOnClient(mc -> main(mc).zonesPanel().setEnabled(zoneId, false));
			check(play(context, feet.x + 2, feet.y, feet.z) == 1.0F, "a zone that is off has no effect");
			context.runOnClient(mc -> main(mc).zonesPanel().confirmDelete(ProfileManager.profile().zone(zoneId)));
			context.waitForScreen(AzDialog.class);
			context.runOnClient(mc -> ((AzDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(AudioZoneScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zones().isEmpty()), "Delete should remove the zone");

			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	private static float play(ClientGameTestContext context, double x, double y, double z) {
		float factor = context.computeOnClient(mc -> {
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(VILLAGER), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound) ? SoundHook.lastStartFactor() : 0.0F;
		});
		context.waitTicks(3);
		return factor;
	}

	private static AudioZoneScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof AudioZoneScreen, "expected the Audio Zone screen, found " + mc.gui.screen());
		return (AudioZoneScreen) mc.gui.screen();
	}

	private static ZoneEditorScreen editorScreen(Minecraft mc) {
		check(mc.gui.screen() instanceof ZoneEditorScreen, "expected the zone editor, found " + mc.gui.screen());
		return (ZoneEditorScreen) mc.gui.screen();
	}

	private static <T> T editor(ClientGameTestContext context, Function<ZoneEditorScreen, T> read) {
		return context.computeOnClient(mc -> read.apply(editorScreen(mc)));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
