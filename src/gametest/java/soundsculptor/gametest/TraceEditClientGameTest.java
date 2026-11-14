package soundsculptor.gametest;

import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryLocation;
import soundsculptor.core.trace.TraceText;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.gametest.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptDialog;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Inline editing in the sound trace (PRD FR-14.4, the FR-14 acceptance criteria), in Sound Set terms: lines grouped by
 * set under each place; a set attached to Everywhere and a zone says "used in 2 places" and lists them in its tooltip;
 * dragging its winning entry's level changes the library set (linked, FR-4.9) with "Predicted for next play" and Undo;
 * "Open full editor" on a library entry opens the set editor at that entry and returning refreshes the trace; two sets
 * on Everywhere name the higher one ("Higher in the list"); turning off the winning Quick changes entry; Add to Quick
 * changes on a "No entry matches" line, and Esc on its choice returning focus to the button; Add to Quick changes on a
 * place's own line, overriding a library set's mute in that place only (FR-4.10); Unmute on a Quick changes line over a
 * library set's mute (FR-1.5); a Quick changes group line's Mute edits its own entry; a level of 250 refused; and "Open
 * full editor" on a zone line.
 */
public final class TraceEditClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int SETTLE = 3;
	private static final SoundSet FARM = new SoundSet("trace-farm", "Trace farm", List.of(SetEntry.of("e1", "*villager*").withVolume(50)));
	private static final PlaceRef PEN = PlaceRef.zone("pen");
	private static final EntryLocation FARM_IN_PEN = new EntryLocation(PEN, FARM.id(), "e1");
	/** Entry line buttons: On/Off, Mute, Level…, Open full editor. */
	private static final int MUTE = 1;
	private static final int OPEN_EDITOR = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> ProfileManager.setBypass(false));
			Vec3 origin = context.computeOnClient(mc -> mc.getSoundManager().getListenerTransform().position());
			Point pen = new Point(origin.x + 6, origin.y, origin.z);
			// The sound plays 4 blocks from the zone's anchor, so a 2-block radius no longer holds it.
			Point spot = new Point(pen.x() + 4, pen.y(), pen.z());
			Zone penZone = new Zone("pen", "Pen", true, key, OVERWORLD, pen, new Shape.Cylinder(10, 20), PlaceSets.EMPTY.attach(FARM.id()));

			usedInTwoPlacesShown(context, spot, penZone);
			twoSetsOnEverywhereNamesHigherSet(context, spot);
			turnOffWinningEntry(context, spot);
			addToQuickChangesMutesNextPlay(context, spot);
			escOnAddChoiceReturnsFocus(context, spot);
			placeLineOverridesLibraryMute(context, spot, penZone);
			unmuteOnQuickLineOverLibraryMute(context, spot);
			groupQuickLineMuteEditsItsOwnEntry(context, spot);
			levelOf250RefusedAndZoneLineOpensZoneEditor(context, spot, penZone);
		}
	}

	/**
	 * AC: an entry from a set attached to two places says "used in 2 places", and the set's line lists the places in its
	 * tooltip; the library line's Level… says an edit changes every place. Drag the winning entry's level from 50% to 20%:
	 * the result updates at once, marked "Predicted for next play"; Undo restores 50%; the next villager sound plays at
	 * 20%. "Open full editor" on the entry opens the set editor at it; returning refreshes the trace.
	 */
	private static void usedInTwoPlacesShown(ClientGameTestContext context, Point spot, Zone penZone) {
		Snapshot before = Snapshot.take(context);
		try {
			setFarm(context, PlaceSets.EMPTY.attach(FARM.id()), penZone);
			check(play(context, spot) == 0.5F, "the zone's set should play the villager at 50%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(!t.isPredicted() && t.trace().volumePercent() == 50, "the trace should show the last play at 50%");
				check(t.text().contains("\n  Trace farm · used in 2 places\n") && t.text().contains("\n    Trace farm · used in 2 places\n"),
						"Everywhere's and the zone's line should say the set is used in 2 places:\n" + t.text());
				SculptList.Row setRow = t.setRow(PEN, FARM.id());
				check(setRow != null && setRow.tooltip() != null, "the zone's set line should have a tooltip");
				String tip = setRow.tooltip().getString();
				check(tip.startsWith("Used in Everywhere and Pen. An edit here changes every one of them."), "the set line's tooltip, was " + tip);
				String opener = NarrationCompat.collect(setRow.buttons().get(0)::updateNarration);
				check(opener.contains("Used in Everywhere and Pen"), "the set line's button reads the places, was " + opener);
				SculptList.Row entryRow = t.row(new TraceText.EntryTarget(FARM_IN_PEN, farmEntry()));
				String level = NarrationCompat.collect(entryRow.buttons().get(2)::updateNarration);
				check(entryRow.buttons().get(2).getMessage().getString().equals("Level…") && level.contains("every place that uses"),
						"a library line's Level… says the edit changes every place, was " + level);

				t.openVolume(FARM_IN_PEN, farmEntry());
				t.volumeSlider.setTypedValue(35);
				t.volumeSlider.setTypedValue(20);
				check(t.trace().volumePercent() == 20, "the result should update at once, was " + t.trace().volumePercent());
				check(t.isPredicted() && t.text().contains("Predicted for next play"), "the result should be a prediction:\n" + t.text());
				check(farmEntry().volume() == 20, "the library set should be saved at once");
				t.closeOverlay();
				check(t.toast() != null && t.toast().undoButton() != null, "closing the Level panel should offer Undo");
				check(t.toast().message().getString().equals("Changed *:*villager*"), "toast was " + t.toast().message().getString());
				t.toast().undoButton().onPress(null);
				check(farmEntry().volume() == 50, "Undo should restore 50% (not 35%)");
				check(t.trace().volumePercent() == 50, "the trace should follow Undo");
				t.openVolume(FARM_IN_PEN, farmEntry());
				t.volumeSlider.setTypedValue(20);
				t.closeOverlay();
			});
			context.takeScreenshot("trace-edit-predicted");
			closeTrace(context);
			check(play(context, spot) == 0.2F, "the next villager sound should play at 20%");
			openTrace(context);
			check(context.computeOnClient(mc -> !trace(mc).isPredicted()), "after the sound played again the trace is no longer a prediction");

			// "Open full editor" on the library entry line: the set editor, at that entry. Turn it off and save (the set is
			// used in two places, so Save confirms): back in the trace, the result is recomputed.
			context.runOnClient(mc -> {
				SculptList.Row row = trace(mc).row(new TraceText.EntryTarget(FARM_IN_PEN, farmEntry()));
				check(row != null && row.buttons().size() == 4, "a library entry line has On/Off, Mute, Level… and Open full editor");
				row.buttons().get(OPEN_EDITOR).onPress(null);
			});
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> {
				SetEditorScreen editor = (SetEditorScreen) mc.gui.screen();
				check(editor.draft().id().equals(FARM.id()), "the set editor opens on the line's set");
				SculptList.Row focused = editor.entryList.getFocused();
				check(focused != null && focused.key().equals("e1"), "the set editor opens at the entry, focused " + (focused == null ? null : focused.key()));
				editor.setEntryEnabled("e1", false);
				editor.save();
			});
			context.waitForScreen(SculptDialog.class);
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
			context.waitForScreen(TraceScreen.class);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(t.trace().volumePercent() == 100 && t.isPredicted(), "returning refreshes the trace, volume " + t.trace().volumePercent());
				check(!t.text().contains("Trace farm"), "an entry that is off no longer matches:\n" + t.text());
			});
			closeTrace(context);
		} finally {
			before.restore(context);
		}
	}

	/** AC: two sets on Everywhere both set the villager: the trace names both and the higher one wins, "Higher in the list". */
	private static void twoSetsOnEverywhereNamesHigherSet(ClientGameTestContext context, Point spot) {
		SoundSet loud = new SoundSet("trace-loud", "Loud villagers", List.of(SetEntry.of("e1", "*villager*").withVolume(80)));
		SoundSet quiet = new SoundSet("trace-quiet", "Quiet villagers", List.of(SetEntry.of("e1", "*villager*").withVolume(20)));
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(loud.id()).attach(quiet.id())), ws.library().put(loud).put(quiet))));
			check(play(context, spot) == 0.8F, "the higher set's 80% should win");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				String expected = "Everywhere\n"
						+ "  Loud villagers\n"
						+ "    *:*villager*: Level 80%\n"
						+ "      Decides loudness (Higher in the list)\n"
						+ "  Quiet villagers\n"
						+ "    *:*villager*: Level 20%\n"
						+ "      loudness overridden by Loud villagers: *:*villager*\n";
				check(t.text().contains(expected), "both sets, the higher one winning:\n" + t.text());
				check(t.setRow(PlaceRef.EVERYWHERE, loud.id()) != null && t.setRow(PlaceRef.EVERYWHERE, quiet.id()) != null,
						"each set has its own line");
				check(t.trace().volumePercent() == 80, "the result is the higher set's level");
			});
			context.takeScreenshot("trace-two-sets");
			closeTrace(context);
		} finally {
			before.restore(context);
		}
	}

	/** AC: turn off the winning entry from the trace: the next entry in line wins at once; Ctrl+Z undoes it. */
	private static void turnOffWinningEntry(ClientGameTestContext context, Point spot) {
		Snapshot before = Snapshot.take(context);
		try {
			SetEntry exact = SetEntry.of("e", VILLAGER).withVolume(60);
			SetEntry wildcard = SetEntry.of("w", "*villager*").withVolume(30);
			setQuick(context, List.of(wildcard, exact));
			check(play(context, spot) == 0.6F, "the single-sound entry should give 60%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				TraceText.EntryTarget target = new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, "e"), exact);
				SculptList.Row row = t.row(target);
				check(row != null && row.buttons().get(0).getMessage().getString().equals("On"), "the exact entry's line should have On/Off");
				row.buttons().get(0).onPress(null);
				check(t.trace().volumePercent() == 30, "the group entry should win now, volume " + t.trace().volumePercent());
				check(t.text().contains("    *:*villager*: Level 30%\n      Decides loudness"), "the group entry should decide:\n" + t.text());
				check(!QuickEntries.get(ProfileManager.profile(), "e").enabled(), "the exact entry should be off");
				// The shared quick-action Undo (FR-12.5): Ctrl+Z presses the toast's Undo.
				t.pressUndo();
				check(QuickEntries.get(ProfileManager.profile(), "e").enabled() && t.trace().volumePercent() == 60, "Undo should turn the exact entry back on");
				check(t.toast() != null && t.toast().message().getString().equals("Undone"), "the toast should say Undone");
			});
			closeTrace(context);
		} finally {
			before.restore(context);
		}
	}

	/**
	 * AC: no matching entry: the "No entry matches" line's Add to Quick changes, then Mute, adds a single-sound entry to
	 * Everywhere's Quick changes: muted on the next play, and the entry is listed in Everywhere's Quick changes.
	 */
	private static void addToQuickChangesMutesNextPlay(ClientGameTestContext context, Point spot) {
		Snapshot before = Snapshot.take(context);
		try {
			setQuick(context, List.of());
			check(play(context, spot) == 1.0F, "with no entries the villager plays at 100%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row none = t.row(new TraceText.NoEntryTarget(PlaceRef.EVERYWHERE));
				check(none != null && none.buttons().size() == 1 && none.buttons().get(0).getMessage().getString().equals("Add to Quick changes"),
						"Everywhere should offer Add to Quick changes");
				none.buttons().get(0).onPress(null);
				check(t.hasOverlay() && t.addQuickMute != null && t.addQuickLevel != null, "Add to Quick changes offers Mute and Level…");
				check(t.addQuickMute.getMessage().getString().equals("Mute") && t.addQuickLevel.getMessage().getString().equals("Level…"),
						"the choices read Mute and Level…");
				check(t.getFocused() == t.addQuickMute, "the first choice has focus");
				String narration = NarrationCompat.collect(t.addQuickMute::updateNarration);
				check(narration.contains("Add to Quick changes in Everywhere"), "the choice reads where it goes, was " + narration);
			});
			context.takeScreenshot("trace-add-to-quick-changes");
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				t.addQuickMute.onPress(null);
				check(!t.hasOverlay(), "choosing closes the choice");
				SetEntry added = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER);
				check(added != null && !added.id().startsWith("exact:"), "a single-sound entry with a new entry ID should be added: " + added);
				check(QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER), "Mute should mute the sound");
				check(t.trace().volumePercent() == 0, "the trace should show it muted");
				SculptList.Row row = t.row(new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, added.id()), added));
				check(row != null && row.buttons().get(MUTE).getMessage().getString().equals("Unmute"), "the new entry's line should offer Unmute");
				check(t.toast() != null && t.toast().message().getString().equals("Added " + VILLAGER + " to Quick changes"),
						"toast was " + (t.toast() == null ? null : t.toast().message().getString()));
			});
			closeTrace(context);
			check(play(context, spot) == 0.0F, "the villager should be muted on its next play");
			context.setScreen(() -> SetEditorScreen.forQuickChanges(null, PlaceRef.EVERYWHERE));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> {
				String id = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER).id();
				check(((SetEditorScreen) mc.gui.screen()).entryList.findRow(id) != null, "the new entry should be in Everywhere's Quick changes");
			});
			context.setScreen(() -> null);

			// Level… instead: the Level panel, whose first change adds the entry.
			setQuick(context, List.of());
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				t.openAddToQuickChanges(PlaceRef.EVERYWHERE);
				t.addQuickLevel.onPress(null);
				check(t.hasOverlay() && t.volumeSlider != null && t.getFocused() == t.volumeSlider, "Level… opens the Level panel");
				t.volumeSlider.setTypedValue(40);
				t.closeOverlay();
				SetEntry added = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER);
				check(added != null && added.volume() == 40 && t.trace().volumePercent() == 40, "Level… adds the entry at 40%: " + added);
			});
			closeTrace(context);
		} finally {
			before.restore(context);
		}
	}

	/**
	 * Keyboard: Enter on Add to Quick changes opens the choice with focus on Mute; Esc closes it without changing
	 * anything and puts focus back on the Add to Quick changes button, so the player can carry on from there.
	 */
	private static void escOnAddChoiceReturnsFocus(ClientGameTestContext context, Point spot) {
		Snapshot before = Snapshot.take(context);
		try {
			setQuick(context, List.of());
			play(context, spot);
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row none = t.row(new TraceText.NoEntryTarget(PlaceRef.EVERYWHERE));
				t.focusPath(t.lines.revealRow(none, 0));
				check(t.lines.getFocused() == none && none.getFocused() == none.buttons().get(0), "Add to Quick changes has focus");
			});
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(SETTLE);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(t.hasOverlay() && t.getFocused() == t.addQuickMute, "Enter opens the choice with focus on Mute");
			});
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(SETTLE);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(!t.hasOverlay() && t.addQuickMute == null && t.addQuickLevel == null, "Esc closes the choice");
				SculptList.Row none = t.row(new TraceText.NoEntryTarget(PlaceRef.EVERYWHERE));
				check(t.getFocused() == t.lines && t.lines.getFocused() == none && none.getFocused() == none.buttons().get(0),
						"focus is back on Add to Quick changes, was " + t.getFocused());
				check(QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER) == null, "Esc changes nothing");
				check(t.toast() == null, "nothing changed, so there is nothing to undo");
			});
			closeTrace(context);
		} finally {
			before.restore(context);
		}
	}

	/**
	 * The controller's ruling on FR-4.10: Add to Quick changes is offered at every place whose Quick changes has no entry
	 * for the sound, also where a library set matches. A library set attached to Everywhere and the Pen mutes the
	 * villager: the Everywhere heading and the Pen's line each offer Add to Quick changes, whose Mute reads Unmute (the
	 * actual state). Unmute on the Pen's line writes "Not muted" into the Pen's Quick changes only: the villager is heard
	 * in the Pen, the library set still mutes, and Everywhere is unchanged. Esc on the choice returns focus to the line's
	 * button.
	 */
	private static void placeLineOverridesLibraryMute(ClientGameTestContext context, Point spot, Zone penZone) {
		SoundSet hush = new SoundSet("trace-hush-all", "Hush villagers", List.of(SetEntry.of("e1", "*villager*").withMute(true)));
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(hush.id())).withZones(List.of(penZone.withSets(PlaceSets.EMPTY.attach(hush.id())))),
					ws.library().put(hush))));
			// With a zone around it, a muted sound starts silently, since a zone could unmute it while it plays.
			check(play(context, spot) <= 1.0E-6F, "the library set mutes the villager");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row heading = t.row(new TraceText.PlaceTarget(PlaceRef.EVERYWHERE));
				check(heading != null && heading.buttons().size() == 1 && heading.buttons().get(0).getMessage().getString().equals("Add to Quick changes"),
						"the Everywhere heading offers Add to Quick changes over the library set");
				SculptList.Row zone = t.row(new TraceText.ZoneTarget(ProfileManager.profile().zone("pen")));
				check(zone != null && zone.buttons().size() == 3 && zone.buttons().get(1).getMessage().getString().equals("Add to Quick changes"),
						"the Pen's line offers Add to Quick changes over the library set");
				t.focusPath(t.lines.revealRow(zone, 1));
				zone.buttons().get(1).onPress(null);
				check(t.hasOverlay() && t.addQuickMute.getMessage().getString().equals("Unmute"), "the choice shows the actual state, Unmute");
				String narration = NarrationCompat.collect(t.addQuickMute::updateNarration);
				check(narration.contains("Add to Quick changes in Pen"), "the choice reads where it goes, was " + narration);
			});
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(SETTLE);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row zone = t.row(new TraceText.ZoneTarget(ProfileManager.profile().zone("pen")));
				check(!t.hasOverlay() && t.lines.getFocused() == zone && zone.getFocused() == zone.buttons().get(1),
						"Esc returns focus to the Pen line's Add to Quick changes");
				t.openAddToQuickChanges(PEN);
				t.addQuickMute.onPress(null);
				SetEntry added = QuickActions.quickEntry(ProfileManager.workspace(), PEN, VILLAGER);
				check(added != null && Boolean.FALSE.equals(added.mute()), "Unmute writes Not muted into the Pen's Quick changes: " + added);
				check(QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER) == null, "Everywhere is unchanged");
				check(Boolean.TRUE.equals(ProfileManager.library().get(hush.id()).entry("e1").mute()), "the library set still mutes");
				check(t.trace().volumePercent() == 100, "the trace shows it heard in the Pen, was " + t.trace().volumePercent());
				check(t.text().contains("    Quick changes\n      " + VILLAGER + ": Not muted"), "the Pen lists its Quick changes first:\n" + t.text());
				SculptList.Row now = t.row(new TraceText.ZoneTarget(ProfileManager.profile().zone("pen")));
				check(now.buttons().size() == 2, "with a matching Quick changes entry the Pen's line no longer offers Add to Quick changes");
			});
			closeTrace(context);
			check(play(context, spot) == 1.0F, "the villager is heard in the Pen on its next play");
		} finally {
			before.restore(context);
		}
	}

	/**
	 * FR-1.5 in the trace: a library set mutes the villager, and Everywhere's Quick changes has its single-sound entry
	 * (Cue only). The Quick changes line comes first and its Mute shows the actual state, Unmute; pressing it writes "Not
	 * muted" into Quick changes, which wins there, so the next play is heard. The library set is not changed.
	 */
	private static void unmuteOnQuickLineOverLibraryMute(ClientGameTestContext context, Point spot) {
		SoundSet hush = new SoundSet("trace-hush", "Hush villagers", List.of(SetEntry.of("e1", VILLAGER).withMute(true)));
		SetEntry cueOnly = SetEntry.of("q", VILLAGER).withCue(true);
		Snapshot before = Snapshot.take(context);
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(hush.id()).withQuickEntries(List.of(cueOnly))), ws.library().put(hush))));
			check(play(context, spot) == 0.0F, "the library set mutes the villager");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(t.text().contains("Everywhere\n  Quick changes\n    " + VILLAGER + ": Cue on\n"),
						"Quick changes is listed first:\n" + t.text());
				EntryLocation where = new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, "q");
				SculptList.Row row = t.row(new TraceText.EntryTarget(where, cueOnly));
				check(row != null && row.buttons().get(MUTE).getMessage().getString().equals("Unmute"),
						"the Quick changes line shows the actual state, Unmute");
				row.buttons().get(MUTE).onPress(null);
				check(Boolean.FALSE.equals(QuickEntries.get(ProfileManager.profile(), "q").mute()), "Unmute writes Not muted into Quick changes");
				check(Boolean.TRUE.equals(ProfileManager.library().get(hush.id()).entry("e1").mute()), "the library set still mutes");
				check(t.trace().volumePercent() == 100, "the trace shows it heard, was " + t.trace().volumePercent());
				SetEntry now = QuickEntries.get(ProfileManager.profile(), "q");
				check(t.row(new TraceText.EntryTarget(where, now)).buttons().get(MUTE).getMessage().getString().equals("Mute"),
						"the line now offers Mute");
			});
			closeTrace(context);
			check(play(context, spot) == 1.0F, "the villager is heard on its next play");
		} finally {
			before.restore(context);
		}
	}

	/** The controller's ruling on FR-14.4: on a Quick changes line whose entry is a group, Mute edits that line's entry. */
	private static void groupQuickLineMuteEditsItsOwnEntry(ClientGameTestContext context, Point spot) {
		SetEntry group = SetEntry.of("g", "*villager*").withMute(true);
		Snapshot before = Snapshot.take(context);
		try {
			setQuick(context, List.of(group));
			check(play(context, spot) == 0.0F, "the group entry mutes the villager");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				EntryLocation where = new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, "g");
				SculptList.Row row = t.row(new TraceText.EntryTarget(where, group));
				check(row != null && row.buttons().get(MUTE).getMessage().getString().equals("Unmute"), "the group entry mutes, so Unmute");
				row.buttons().get(MUTE).onPress(null);
				check(QuickEntries.get(ProfileManager.profile(), "g").mute() == null, "Unmute clears the group entry's own mute");
				check(QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER) == null,
						"no single-sound entry is made");
				check(t.trace().volumePercent() == 100, "the trace shows it heard");
			});
			closeTrace(context);
		} finally {
			before.restore(context);
		}
	}

	/**
	 * AC: type 250 into a level field in the panel: an error shows and nothing changes. AC: "Open full editor" on a zone
	 * line opens the zone editor; change the radius, return: the trace shows it.
	 */
	private static void levelOf250RefusedAndZoneLineOpensZoneEditor(ClientGameTestContext context, Point spot, Zone penZone) {
		Snapshot before = Snapshot.take(context);
		try {
			setFarm(context, PlaceSets.EMPTY, penZone);
			play(context, spot);
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				t.openVolume(FARM_IN_PEN, farmEntry());
				t.setFocused(t.volumeField);
				t.volumeField.setValue("250");
				t.volumeField.revealError();
				check(t.volumeField.isErrorShown(), "250 should show an error");
				check(t.volumeField.error().getString().contains("100"), "the error should give the range, was " + t.volumeField.error().getString());
				check(farmEntry().volume() == 50, "250 must not change the entry");
				t.closeOverlay();
				check(t.toast() == null, "nothing changed, so there is nothing to undo");
			});

			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row row = t.row(new TraceText.ZoneTarget(ProfileManager.profile().zone("pen")));
				// The zone's library set matches and its Quick changes does not, so the line offers Add to Quick changes too.
				check(row != null && row.buttons().size() == 3 && row.buttons().get(1).getMessage().getString().equals("Add to Quick changes"),
						"the zone line should have On/Off, Add to Quick changes and Open full editor");
				row.buttons().get(2).onPress(null);
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
				ProfileManager.flushSaves();
			});
		}
	}

	/** The farm set's entry as the library holds it now. */
	private static SetEntry farmEntry() {
		return ProfileManager.library().get(FARM.id()).entry("e1");
	}

	/** The farm set at 50% in the library, Everywhere's sets and these zones. */
	private static void setFarm(ClientGameTestContext context, PlaceSets everywhere, Zone... zones) {
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withEverywhere(everywhere)
				.withZones(List.of(zones)), ws.library().put(FARM))));
	}

	/** Everywhere's Quick changes holding {@code entries}, and no zones. */
	private static void setQuick(ClientGameTestContext context, List<SetEntry> entries) {
		context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withEverywhere(PlaceSets.EMPTY.withQuickEntries(entries))));
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
