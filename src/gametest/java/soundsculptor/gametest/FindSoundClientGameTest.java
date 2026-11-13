package soundsculptor.gametest;

import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.SetEditorScreen;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Plain-word search in the entry editor (FR-4.8, maxismagical's review): words in the pattern field list matching sounds
 * with This sound and Like this; patterns still work as before; it works in a zone's Quick changes too.
 */
public final class FindSoundClientGameTest implements FabricClientGameTest {
	private static final String HURT = "minecraft:entity.villager.hurt";
	private static final String BASALT = "minecraft:ambient.basalt_deltas.additions";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withZones(List.of(
					new Zone("z1", "Pen", true, key, "minecraft:overworld", new Point(0.5, -59.5, 0.5), new Shape.Box(9, 3, 9), PlaceSets.EMPTY)))));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);

			// Words in the pattern field: matching sounds, no pattern error, Save off until a sound is picked.
			addEntry(context, PlaceRef.EVERYWHERE);
			context.getInput().typeChars("villager hurt");
			context.waitTicks(1);
			check(editor(context, e -> e.searching() && !e.patternField.isErrorShown() && !e.saveButton.active),
					"words should search, without a pattern error, and Save stays off");
			List<String> row = editor(context, e -> e.matchList.findRow(HURT).columnTexts());
			check(row.get(0).equals("Villager hurts") && row.get(1).equals(HURT), "the villager hurt sound should be listed by name, was " + row);
			check(editor(context, e -> e.searchResults().stream().allMatch(s -> (s.subtitle() + " " + s.id()).toLowerCase().contains("hurt"))),
					"every result should match the words");
			context.takeScreenshot("find-sound-results");

			// This sound: the exact ID; the editor then previews it as a pattern.
			context.runOnClient(mc -> editorScreen(mc).matchList.findRow(HURT).buttons().get(0).onPress(null));
			context.waitTicks(1);
			check(editor(context, e -> e.patternField.getValue().equals(HURT) && !e.searching() && e.saveButton.active),
					"This sound should fill in the exact ID and allow Save");
			check(editor(context, e -> e.preview().count() == 1), "the exact ID matches 1 sound");

			// Like this: the sound's families; the first is the closest (all cow sounds).
			context.runOnClient(mc -> editorScreen(mc).patternField.setValue("cow"));
			context.waitTicks(1);
			String cow = "minecraft:entity.cow.ambient";
			context.runOnClient(mc -> editorScreen(mc).matchList.findRow(cow).buttons().get(1).onPress(null));
			context.waitTicks(1);
			List<String> families = editor(context, e -> e.familyButtons().stream().map(b -> b.getMessage().getString()).toList());
			check(!families.isEmpty() && families.get(0).startsWith("minecraft:entity.cow.* · "), "the closest family comes first, was " + families);
			context.takeScreenshot("find-sound-families");
			context.runOnClient(mc -> ((soundsculptor.mc.ui.SculptButton) editorScreen(mc).familyButtons().get(0)).onPress(null));
			context.waitTicks(1);
			check(editor(context, e -> e.patternField.getValue().equals("minecraft:entity.cow.*")), "picking a family fills in its pattern");
			check(editor(context, e -> !e.preview().matches().isEmpty() && e.preview().matches().stream().allMatch(id -> id.startsWith("minecraft:entity.cow."))),
					"the preview lists only cow sounds");
			context.runOnClient(mc -> {
				editorScreen(mc).setMute(EntryEditorScreen.MuteChoice.MUTE);
				editorScreen(mc).save();
			});
			saveSet(context);
			check(context.computeOnClient(mc -> QuickEntries.of(ProfileManager.profile()).stream()
					.anyMatch(r -> r.pattern().text().equals("minecraft:entity.cow.*"))), "the family entry is saved");

			// In a zone's Quick changes too, and readable names count: "basalt deltas" finds a sound without a subtitle.
			addEntry(context, PlaceRef.zone("z1"));
			context.getInput().typeChars("basalt deltas additions");
			context.waitTicks(1);
			check(editor(context, e -> e.matchList.findRow(BASALT) != null), "readable names should be searched too");
			context.runOnClient(mc -> {
				editorScreen(mc).matchList.findRow(BASALT).buttons().get(0).onPress(null);
				editorScreen(mc).setMute(EntryEditorScreen.MuteChoice.MUTE);
				editorScreen(mc).save();
			});
			saveSet(context);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z1").sets().quickEntries().stream()
					.anyMatch(r -> r.pattern().text().equals(BASALT) && Boolean.TRUE.equals(r.mute()))), "the zone gets the entry");

			// Patterns still work as before: a typo with a colon is an error, not a search.
			addEntry(context, PlaceRef.EVERYWHERE);
			context.getInput().typeChars("minecraft:entity.cow.#");
			context.waitTicks(1);
			check(editor(context, e -> !e.searching() && e.patternField.isErrorShown()), "a pattern with a bad character still shows an error");
			context.runOnClient(mc -> editorScreen(mc).onClose());
			context.waitTicks(2);
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof soundsculptor.mc.ui.SculptDialog dialog) {
					dialog.choose(1);
				}
			});
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> mc.gui.screen().onClose());

			// The Sounds tab finds readable names too.
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				SoundSculptorScreen screen = (SoundSculptorScreen) mc.gui.screen();
				screen.selectTab(SoundSculptorScreen.TAB_SOUNDS);
				screen.sounds().setRecentOnly(false);
				screen.sounds().searchField.setValue("basalt deltas additions");
				screen.sounds().runSearch();
			});
			check(context.computeOnClient(mc -> ((SoundSculptorScreen) mc.gui.screen()).sounds().list.findRow(BASALT) != null),
					"the Sounds tab should find a sound by its readable name");

			context.runOnClient(mc -> {
				SoundsPanel.resetRememberedState();
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
	}

	/** Opens the place's Quick changes in the set editor, then Type a pattern…: the entry editor for a new entry. */
	private static void addEntry(ClientGameTestContext context, PlaceRef where) {
		context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.forQuickChanges(mc.gui.screen(), where)));
		context.waitForScreen(SetEditorScreen.class);
		context.runOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).typePattern());
		context.waitForScreen(EntryEditorScreen.class);
	}

	/** Back in the set editor after the entry editor's Save: saves the set. */
	private static void saveSet(ClientGameTestContext context) {
		context.waitForScreen(SetEditorScreen.class);
		context.runOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).save());
		context.waitForScreen(SoundSculptorScreen.class);
	}

	private static EntryEditorScreen editorScreen(net.minecraft.client.Minecraft mc) {
		check(mc.gui.screen() instanceof EntryEditorScreen, "expected the entry editor, found " + mc.gui.screen());
		return (EntryEditorScreen) mc.gui.screen();
	}

	private static <T> T editor(ClientGameTestContext context, Function<EntryEditorScreen, T> read) {
		return context.computeOnClient(mc -> read.apply(editorScreen(mc)));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
