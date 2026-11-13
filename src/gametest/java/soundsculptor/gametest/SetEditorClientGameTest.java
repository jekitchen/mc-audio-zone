package soundsculptor.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.AttachedToPanel;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SetUsageIndex;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;

/**
 * The set editor and the entry editor (FR-4.3, FR-4.6, FR-4.9, FR-12.4, FR-12.7): edits stay in the draft until Save;
 * Attach and Detach in the Attached to panel apply with Save; a set used in several places confirms with their names;
 * "Partly overridden by" looks within the set; invalid input blocks Save; a place's Quick changes has its own editor.
 */
public final class SetEditorClientGameTest implements FabricClientGameTest {
	private static final String OTHER = "set-editor.example.net";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String PIG = "minecraft:entity.pig.ambient";
	private static final Point MAIN_BASE = new Point(0, 64, 0);
	private static final Point MOB_FARM = new Point(200, 64, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			farmAnimalsLinkedAcrossZones(context, key);
			partlyOverriddenByShownInEntryEditor(context);
			entryOffStopsEffectAfterSave(context, key);
			uppercaseGroupLowercasedAndBadCharBlocksSave(context);
			quickChangesEditorWritesThePlace(context, key);
		}
	}

	/**
	 * The FR-4 AC: attach "Farm animals" to Main base and Mob farm, edit cows to 60%; the save confirmation names both
	 * zones and the other world, and afterwards a cow in either zone plays at 60%.
	 */
	private static void farmAnimalsLinkedAcrossZones(ClientGameTestContext context, String key) {
		SoundSet farm = new SoundSet("set-editor-farm", "Farm animals", List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withVolume(40)));
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
						.withZones(List.of(zone("z-main", "Main base", key, MAIN_BASE), zone("z-mob", "Mob farm", key, MOB_FARM))),
						ws.library().put(farm)));
				ProfileManager.saveOther(OTHER, Profile.empty("Creative test world").withEverywhere(PlaceSets.EMPTY.attach(farm.id())));
				ProfileManager.flushSaves();
			});
			waitForOthers(context, farm.id(), key);

			// Edit on the Sound Sets tab opens the set editor.
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				SoundSculptorScreen main = (SoundSculptorScreen) mc.gui.screen();
				main.selectTab(SoundSculptorScreen.TAB_SETS);
				pressButton(main.setsPanel().list.findRow(farm.id()), "Edit");
			});
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> editor(mc).nameField.getValue()).equals("Farm animals"), "the editor shows the set's name");
			check(context.computeOnClient(mc -> panel(mc).othersButton.getMessage().getString()).equals("Also used in 1 other world or server"),
					"the panel counts the other world");
			check(context.computeOnClient(mc -> panel(mc).list.children().isEmpty()), "not attached in this world yet");

			// Attach to… offers every place here; attaching is staged until Save.
			context.runOnClient(mc -> panel(mc).openAttachMenu());
			List<String> offered = context.computeOnClient(mc -> firstColumns(panel(mc).attachMenu));
			check(offered.equals(List.of("Everywhere", "Main base", "Mob farm")), "Attach to… offers every place, was " + offered);
			context.takeScreenshot("set-editor-attach-menu");
			context.runOnClient(mc -> pressButton(panel(mc).attachMenu.findRow("z-main"), "Attach"));
			check(!context.computeOnClient(mc -> editor(mc).hasOverlay()), "attaching closes the menu");
			context.runOnClient(mc -> panel(mc).openAttachMenu());
			check(context.computeOnClient(mc -> firstColumns(panel(mc).attachMenu)).equals(List.of("Everywhere", "Mob farm")),
					"a place that has the set is not offered again");
			context.runOnClient(mc -> pressButton(panel(mc).attachMenu.findRow("z-mob"), "Attach"));
			check(context.computeOnClient(mc -> panel(mc).places()).equals(List.of(PlaceRef.zone("z-main"), PlaceRef.zone("z-mob"))),
					"both zones are listed");
			check(context.computeOnClient(mc -> panel(mc).list.findRow("z-main").columnTexts()).equals(List.of("Main base", "Attached when you save")),
					"a staged attach says when it applies");
			check(!context.computeOnClient(mc -> ProfileManager.profile().zone("z-main").sets().uses(farm.id())),
					"nothing is attached before Save");

			// Edit cows to 60%: the entry editor saves into the draft only.
			context.runOnClient(mc -> editor(mc).editEntry("e1"));
			context.waitForScreen(EntryEditorScreen.class);
			context.runOnClient(mc -> {
				EntryEditorScreen entry = (EntryEditorScreen) mc.gui.screen();
				entry.volumeField.setValue("60");
				entry.save();
			});
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> editor(mc).draft().entry("e1").volume()) == 60, "the draft has 60%");
			check(context.computeOnClient(mc -> ProfileManager.library().get(farm.id()).entry("e1").volume()) == 40,
					"the library is unchanged until Save");
			check(context.computeOnClient(mc -> editor(mc).entryList.findRow("e1").columnTexts().get(1)).equals("▶ Level 60%"),
					"the row shows the new level");

			// Save: the set is used in three places, so the confirmation lists them.
			context.runOnClient(mc -> editor(mc).save());
			context.waitForScreen(SculptDialog.class);
			String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
			check(message.equals("This changes Main base, Mob farm and 1 other world."), "the save confirmation, was " + message);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Save 'Farm animals'?"), "the confirmation title");
			context.takeScreenshot("set-editor-linked-save");
			// Keep editing returns with everything still staged.
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> panel(mc).hasChanges() && editor(mc).draft().changed()), "Keep editing keeps the changes");
			context.runOnClient(mc -> editor(mc).save());
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(SoundSculptorScreen.class);

			check(context.computeOnClient(mc -> ProfileManager.library().get(farm.id()).entry("e1").volume()) == 60, "Save writes the set");
			check(context.computeOnClient(mc -> ProfileManager.profile().placesUsing(farm.id()))
					.equals(List.of(PlaceRef.zone("z-main"), PlaceRef.zone("z-mob"))), "Save attaches it to both zones");
			check(volumeAt(context, COW, key, MAIN_BASE) == 60F, "a cow in Main base plays at 60%");
			check(volumeAt(context, COW, key, MOB_FARM) == 60F, "a cow in Mob farm plays at 60%");
			check(volumeAt(context, COW, key, new Point(100, 64, 0)) == 100F, "outside the zones nothing changes");

			// Detach is staged too, and Cancel drops it.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(mc.gui.screen(), farm.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> pressButton(panel(mc).list.findRow("z-mob"), "Detach"));
			check(context.computeOnClient(mc -> panel(mc).places()).equals(List.of(PlaceRef.zone("z-main"))), "Detach takes the row away");
			context.runOnClient(mc -> editor(mc).onClose());
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Unsaved changes"), "a detach is an unsaved change");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1)); // Discard
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z-mob").sets().uses(farm.id())), "Discard keeps Mob farm attached");

			// Detach and Save, with no change to the set itself: no confirmation, and a change made to the set behind the
			// editor (as a quick action would) survives, because the unchanged draft is not written back.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(mc.gui.screen(), farm.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(),
					ws.library().put(ws.library().get(farm.id()).putEntry(SetEntry.of("e1", "minecraft:entity.cow.*").withVolume(70))))));
			context.runOnClient(mc -> {
				pressButton(panel(mc).list.findRow("z-mob"), "Detach");
				editor(mc).save();
			});
			context.waitForScreen(SoundSculptorScreen.class);
			check(!context.computeOnClient(mc -> ProfileManager.profile().zone("z-mob").sets().uses(farm.id())), "Save detaches Mob farm");
			check(context.computeOnClient(mc -> ProfileManager.library().get(farm.id()).entry("e1").volume()) == 70,
					"a Save that only detaches keeps the change made to the set behind the editor");
			check(volumeAt(context, COW, key, MOB_FARM) == 100F, "a cow in Mob farm is back to 100%");
			check(volumeAt(context, COW, key, MAIN_BASE) == 70F, "Main base gets the change made behind the editor");

			// While the other worlds are still being counted, a changed set always confirms (nobody can tell yet that no
			// other world uses it), and the text says so.
			check(context.computeOnClient(mc -> SetEditorScreen.confirms(1, 0, false) && SetEditorScreen.confirms(0, 0, false)
					&& !SetEditorScreen.confirms(1, 0, true) && SetEditorScreen.confirms(2, 0, true) && SetEditorScreen.confirms(1, 1, true)),
					"when Save confirms");
			String counting = context.computeOnClient(mc -> SetEditorScreen.linkedMessage(List.of("Main base"), 0, false).getString());
			check(counting.equals("This changes Main base.\nOther worlds are still being counted; any that use this set change too."),
					"the counting confirmation, was " + counting);
			String nowhere = context.computeOnClient(mc -> SetEditorScreen.linkedMessage(List.of(), 0, false).getString());
			check(nowhere.equals("It is not attached in this world.\nOther worlds are still being counted; any that use this set change too."),
					"the counting confirmation for a set not used here, was " + nowhere);
		} finally {
			before.restore(context);
		}
	}

	/** FR-4.6: a more specific entry in the same set is named; one in another set is not. */
	private static void partlyOverriddenByShownInEntryEditor(ClientGameTestContext context) {
		SoundSet cows = new SoundSet("set-editor-cows", "Cows", List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withMute(true),
				SetEntry.of("e2", COW).withVolume(30)));
		SoundSet broad = new SoundSet("set-editor-broad", "Broad", List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withMute(true)));
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(cows).put(broad))));
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, cows.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> editor(mc).editEntry("e1"));
			context.waitForScreen(EntryEditorScreen.class);
			String overridden = context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).overriddenText().getString());
			check(overridden.equals("Partly overridden by: " + COW), "the group entry is overridden by the cow entry, was " + overridden);
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).getTitle().getString()).equals("Edit entry"), "the title");
			context.takeScreenshot("set-editor-overridden");
			// The unchanged value is just "Unchanged": the inherited value is no longer shown.
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).volumeSlider.getMessage().getString())
					.equals("Level: Unchanged"), "an unset level reads Unchanged");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitForScreen(SetEditorScreen.class);

			// The same group in a set with no more specific entry is not overridden, whatever other sets say.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, broad.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> editor(mc).editEntry("e1"));
			context.waitForScreen(EntryEditorScreen.class);
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).overriddenText().getString()).isEmpty(),
					"another set's entry does not count");
		} finally {
			before.restore(context);
		}
	}

	/** Turning an entry off changes nothing until Save; after Save its effect stops. Discard drops the change. */
	private static void entryOffStopsEffectAfterSave(ClientGameTestContext context, String key) {
		SoundSet pigs = new SoundSet("set-editor-pigs", "Quiet pigs", List.of(SetEntry.of("e1", PIG).withMute(true)));
		Point anywhere = new Point(50, 64, 50);
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(pigs.id())), ws.library().put(pigs))));
			check(volumeAt(context, PIG, key, anywhere) == 0F, "the pig is muted to start with");

			// Off, then Discard: nothing changes.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, pigs.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> pressButton(editor(mc).entryList.findRow("e1"), "On"));
			check(context.computeOnClient(mc -> editor(mc).entryList.findRow("e1").columnTexts().get(1)).equals("○ Off"), "the row says Off");
			check(volumeAt(context, PIG, key, anywhere) == 0F, "nothing changes in game before Save");
			context.runOnClient(mc -> editor(mc).onClose());
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1)); // Discard
			check(context.computeOnClient(mc -> ProfileManager.library().get(pigs.id()).entry("e1").enabled()), "Discard keeps the entry on");

			// Off, then Save: one place uses the set, so it saves at once and the pig plays again.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, pigs.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> {
				pressButton(editor(mc).entryList.findRow("e1"), "On");
				editor(mc).save();
			});
			check(context.computeOnClient(mc -> !(mc.gui.screen() instanceof SetEditorScreen) && !(mc.gui.screen() instanceof SculptDialog)),
					"a set used in one place saves without a confirmation");
			check(!context.computeOnClient(mc -> ProfileManager.library().get(pigs.id()).entry("e1").enabled()), "Save writes the entry off");
			check(volumeAt(context, PIG, key, anywhere) == 100F, "an entry that is off has no effect after Save");
		} finally {
			before.restore(context);
		}
	}

	/**
	 * FR-4.2 and FR-12.7 through Add sounds…: uppercase is lowercased as typed; a character a pattern can't have shows an
	 * error and disables Save. Also the 512-entry limit: Add sounds… on a full set says so and opens nothing.
	 */
	private static void uppercaseGroupLowercasedAndBadCharBlocksSave(ClientGameTestContext context) {
		SoundSet typing = new SoundSet("set-editor-typing", "Typing", List.of());
		List<SetEntry> many = new ArrayList<>();
		for (int i = 0; i < SoundSet.MAX_ENTRIES; i++) {
			many.add(SetEntry.of("e" + (i + 1), "minecraft:sound." + i));
		}
		SoundSet full = new SoundSet("set-editor-full", "Full", many);
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(typing).put(full))));
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, typing.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> editor(mc).addButton.onPress(null));
			context.waitForScreen(EntryEditorScreen.class);
			check(context.computeOnClient(mc -> ((EntryEditorScreen) mc.gui.screen()).getTitle().getString()).equals("New entry"), "a new entry");
			context.getInput().typeChars("*:ENTITY.COW.*");
			context.waitTicks(1);
			check(context.computeOnClient(mc -> entryEditor(mc).patternField.getValue()).equals("*:entity.cow.*"), "uppercase becomes lowercase");
			check(context.computeOnClient(mc -> entryEditor(mc).saveButton.active), "a valid group can be saved");
			context.getInput().typeChars("#");
			context.waitTicks(1);
			check(context.computeOnClient(mc -> entryEditor(mc).patternField.isErrorShown()), "a bad character shows an error");
			check(!context.computeOnClient(mc -> entryEditor(mc).saveButton.active), "and disables Save");
			context.runOnClient(mc -> entryEditor(mc).save());
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof EntryEditorScreen), "Save is refused while the error shows");
			context.takeScreenshot("set-editor-bad-char");
			context.runOnClient(mc -> {
				entryEditor(mc).patternField.setValue("*:entity.cow.*");
				entryEditor(mc).setMute(EntryEditorScreen.MuteChoice.MUTE);
				entryEditor(mc).save();
			});
			context.waitForScreen(SetEditorScreen.class);
			List<SetEntry> drafted = context.computeOnClient(mc -> editor(mc).draft().entries());
			check(drafted.size() == 1 && drafted.get(0).pattern().text().equals("*:entity.cow.*") && Boolean.TRUE.equals(drafted.get(0).mute()),
					"the new entry is in the draft, was " + drafted);
			check(context.computeOnClient(mc -> ProfileManager.library().get(typing.id()).entries().isEmpty()), "and not yet in the library");
			context.runOnClient(mc -> editor(mc).save());
			check(context.computeOnClient(mc -> ProfileManager.library().get(typing.id()).entries().size()) == 1, "Save adds it to the set");

			// A full set: Add sounds… shows the limit and stays in the set editor.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, full.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> editor(mc).addSounds());
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof SetEditorScreen), "a full set opens no entry editor");
			check(context.computeOnClient(mc -> editor(mc).toast().message().getString()).equals("A Sound Set can have up to 512 entries."),
					"the toast names the limit");
			check(!context.computeOnClient(mc -> editor(mc).draft().changed()), "and nothing changed");
		} finally {
			before.restore(context);
		}
	}

	/** {@link SetEditorScreen#forQuickChanges}: no name and no Attached to panel; Save writes the place's Quick changes. */
	private static void quickChangesEditorWritesThePlace(ClientGameTestContext context, String key) {
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())
					.withZones(List.of(zone("z-main", "Main base", key, MAIN_BASE)))));
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.forQuickChanges(null, PlaceRef.zone("z-main"))));
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> editor(mc).attachedPanel() == null && editor(mc).nameField == null),
					"Quick changes has no Attached to panel and no name");
			context.takeScreenshot("set-editor-quick");
			context.runOnClient(mc -> editor(mc).addSounds());
			context.waitForScreen(EntryEditorScreen.class);
			context.runOnClient(mc -> {
				entryEditor(mc).patternField.setValue(COW);
				entryEditor(mc).volumeField.setValue("25");
				entryEditor(mc).save();
			});
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z-main").sets().quickChanges()) == null, "nothing before Save");
			context.runOnClient(mc -> editor(mc).save());
			PlaceSets sets = context.computeOnClient(mc -> ProfileManager.profile().zone("z-main").sets());
			check(sets.quickChanges() != null && sets.attached().get(0).setId().equals(SoundSet.QUICK_ID)
					&& sets.quickEntries().size() == 1 && sets.quickEntries().get(0).volume() == 25, "Save writes Quick changes, pinned first");
			check(volumeAt(context, COW, key, MAIN_BASE) == 25F, "and it applies in the zone");

			// Removing the last entry and saving removes the Quick changes set.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.forQuickChanges(null, PlaceRef.zone("z-main"))));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> {
				pressButton(editor(mc).entryList.findRow("e1"), "Remove entry");
				editor(mc).save();
			});
			check(context.computeOnClient(mc -> ProfileManager.profile().zone("z-main").sets()).equals(PlaceSets.EMPTY),
					"an emptied Quick changes set is removed");
		} finally {
			before.restore(context);
		}
	}

	/** The profile and library before a case, put back in {@code finally}. */
	private record Snapshot(Profile profile, SetLibrary library) {
		static Snapshot take(ClientGameTestContext context) {
			return context.computeOnClient(mc -> new Snapshot(ProfileManager.profile(), ProfileManager.library()));
		}

		void restore(ClientGameTestContext context) {
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ProfileManager.change(ws -> new Workspace(profile, library));
				ProfileManager.delete(OTHER);
				ProfileManager.flushSaves();
			});
		}
	}

	/** The volume a positional sound at {@code at} gets in this world now, in percent (0 when muted). */
	private static float volumeAt(ClientGameTestContext context, String soundId, String key, Point at) {
		return context.computeOnClient(mc -> {
			SoundDecision out = new SoundDecision();
			ProfileManager.precedence().decide(soundId, true, key, "minecraft:overworld", at.x(), at.y(), at.z(), out);
			return out.volumePercent();
		});
	}

	/** Waits for the background usage index to see the other profile using the set. */
	private static void waitForOthers(ClientGameTestContext context, String setId, String key) {
		boolean seen = false;
		for (int i = 0; i < 100 && !seen; i++) {
			context.waitTicks(2);
			seen = context.computeOnClient(mc -> SetUsageIndex.current() != null && SetUsageIndex.current().otherCount(setId, key) == 1);
		}
		check(seen, "the usage index should see the other profile using " + setId);
	}

	private static Zone zone(String id, String name, String worldKey, Point center) {
		return new Zone(id, name, true, worldKey, "minecraft:overworld", center, new Shape.Sphere(8), PlaceSets.EMPTY);
	}

	private static SetEditorScreen editor(Minecraft mc) {
		check(mc.gui.screen() instanceof SetEditorScreen, "expected the set editor, found " + mc.gui.screen());
		return (SetEditorScreen) mc.gui.screen();
	}

	private static AttachedToPanel panel(Minecraft mc) {
		return editor(mc).attachedPanel();
	}

	private static EntryEditorScreen entryEditor(Minecraft mc) {
		check(mc.gui.screen() instanceof EntryEditorScreen, "expected the entry editor, found " + mc.gui.screen());
		return (EntryEditorScreen) mc.gui.screen();
	}

	private static List<String> firstColumns(SculptList list) {
		List<String> texts = new ArrayList<>();
		for (SculptList.Row row : list.children()) {
			texts.add(row.columnTexts().get(0));
		}
		return texts;
	}

	private static void pressButton(SculptList.Row row, String label) {
		if (row == null) {
			throw new AssertionError("no such row");
		}
		List<String> labels = new ArrayList<>();
		for (SculptButton button : row.buttons()) {
			String text = button.getMessage().getString();
			labels.add(text);
			if (text.equals(label)) {
				button.onPress(null);
				return;
			}
		}
		throw new AssertionError("no " + label + " button in " + labels);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
