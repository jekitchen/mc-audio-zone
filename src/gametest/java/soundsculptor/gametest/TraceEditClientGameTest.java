package soundsculptor.gametest;

import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryLocation;
import soundsculptor.core.trace.TraceText;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SetEditorScreen;
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
 * Inline editing in the sound trace (PRD FR-14.4, the FR-14 acceptance criteria), in Sound Set terms: a library set
 * attached to Everywhere and a zone says "used in 2 places", and dragging its winning entry's level in the Volume panel
 * changes the library set (linked, FR-4.9) with "Predicted for next play" and Undo; turning off the winning Quick
 * changes entry; Mute on a "No entry matches" line adds to Quick changes; a level of 250 refused; and "Open full
 * editor" on a zone line.
 */
public final class TraceEditClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int SETTLE = 3;
	private static final SoundSet FARM = new SoundSet("trace-farm", "Trace farm", List.of(SetEntry.of("e1", "*villager*").withVolume(50)));
	private static final PlaceRef PEN = PlaceRef.zone("pen");
	private static final EntryLocation FARM_IN_PEN = new EntryLocation(PEN, FARM.id(), "e1");

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
			Zone penZone = new Zone("pen", "Pen", true, key, OVERWORLD, pen, new Shape.Sphere(10), PlaceSets.EMPTY.attach(FARM.id()));

			// AC: an entry from a set attached to two places says "used in 2 places". Drag the winning entry's level from
			// 50% to 20%: the result updates at once, marked "Predicted for next play"; Undo restores 50%; the next
			// villager sound plays at 20%.
			setFarm(context, PlaceSets.EMPTY.attach(FARM.id()), penZone);
			check(play(context, spot) == 0.5F, "the zone's set should play the villager at 50%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				check(!t.isPredicted() && t.trace().volumePercent() == 50, "the trace should show the last play at 50%");
				check(t.text().contains("\n  Trace farm · used in 2 places\n") && t.text().contains("\n    Trace farm · used in 2 places\n"),
						"Everywhere's and the zone's line should say the set is used in 2 places:\n" + t.text());
				t.openVolume(FARM_IN_PEN, farmEntry());
				t.volumeSlider.setTypedValue(35);
				t.volumeSlider.setTypedValue(20);
				check(t.trace().volumePercent() == 20, "the result should update at once, was " + t.trace().volumePercent());
				check(t.isPredicted() && t.text().contains("Predicted for next play"), "the result should be a prediction:\n" + t.text());
				check(farmEntry().volume() == 20, "the library set should be saved at once");
				t.closeOverlay();
				check(t.toast() != null && t.toast().undoButton() != null, "closing the Volume panel should offer Undo");
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
			closeTrace(context);

			// AC: turn off the winning entry from the trace: the next entry in line wins at once.
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
				check(t.text().contains("    *:*villager*: Volume 30%\n      Decides loudness"), "the group entry should decide:\n" + t.text());
				check(!QuickEntries.get(ProfileManager.profile(), "e").enabled(), "the exact entry should be off");
				// The shared quick-action Undo (FR-12.5): Ctrl+Z presses the toast's Undo.
				t.pressUndo();
				check(QuickEntries.get(ProfileManager.profile(), "e").enabled() && t.trace().volumePercent() == 60, "Undo should turn the exact entry back on");
				check(t.toast() != null && t.toast().message().getString().equals("Undone"), "the toast should say Undone");
				row = t.row(target);
				row.buttons().get(0).onPress(null);
			});
			closeTrace(context);

			// AC: no matching entry: Mute on the "No entry matches" line adds a single-sound entry to Everywhere's Quick
			// changes: muted on the next play, and the entry is listed in Everywhere's Quick changes.
			setQuick(context, List.of());
			check(play(context, spot) == 1.0F, "with no entries the villager plays at 100%");
			openTrace(context);
			context.runOnClient(mc -> {
				TraceScreen t = trace(mc);
				SculptList.Row none = t.row(new TraceText.NoEntryTarget(PlaceRef.EVERYWHERE));
				check(none != null && none.buttons().get(0).getMessage().getString().equals("Mute"), "Everywhere should offer Mute");
				none.buttons().get(0).onPress(null);
				SetEntry added = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER);
				check(added != null && !added.id().startsWith("exact:"), "a single-sound entry with a new entry ID should be added: " + added);
				check(QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER), "Mute should mute the sound");
				check(t.trace().volumePercent() == 0, "the trace should show it muted");
				SculptList.Row row = t.row(new TraceText.EntryTarget(new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, added.id()), added));
				check(row != null && row.buttons().get(1).getMessage().getString().equals("Unmute"), "the new entry's line should offer Unmute");
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

			// AC: type 250 into a volume field in the panel: an error shows and nothing changes.
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
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library().remove(FARM.id())));
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
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile().withEverywhere(everywhere).withZones(List.of(zones)),
				ws.library().put(FARM))));
	}

	/** Everywhere's Quick changes holding {@code entries}, and no zones. */
	private static void setQuick(ClientGameTestContext context, List<SetEntry> entries) {
		context.runOnClient(mc -> ProfileManager.update(p -> p.withEverywhere(PlaceSets.EMPTY.withQuickEntries(entries)).withZones(List.of())));
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
