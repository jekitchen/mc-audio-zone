package soundsculptor.gametest;

import java.util.ArrayList;
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
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.AttachSetScreen;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SetNameScreen;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ZoneListPanel;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;

/**
 * Sound Sets attached to every place (PRD FR-8.6, FR-8.10, FR-4.10): the pinned Quick changes line, order between sets
 * (matrix case P13), Save to library, Attach set… never offering a set twice, the Everywhere row of the Zones tab and the
 * 32-set limit. Every change applies at once with an Undo toast (FR-12.5).
 */
public final class PlaceSetsClientGameTest implements FabricClientGameTest {
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String PIG = "minecraft:entity.pig.ambient";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final SoundSet LOUD = new SoundSet("place-loud", "Villagers at 70", List.of(SetEntry.of("e1", VILLAGER).withVolume(70)));
	private static final SoundSet QUIET = new SoundSet("place-quiet", "Villagers at 30", List.of(SetEntry.of("e1", VILLAGER).withVolume(30)));

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> ProfileManager.setBypass(false));
			quickLineHasNoMoveAndNothingPassesIt(context);
			swapOrderFlipsResult(context);
			saveToLibraryKeepsAttachedAndNextMuteMakesNewQuick(context);
			attachSameSetTwiceNotOffered(context);
			everywhereRowFirstAndNotEditable(context, key);
			thirtyThirdAttachShowsMessage(context);
		}
	}

	/**
	 * FR-4.10: the Quick changes line has no move buttons, the set under it cannot move up past it, and switching it off
	 * then pressing Mute on a sound row switches it back on, so the mute takes effect.
	 */
	private static void quickLineHasNoMoveAndNothingPassesIt(ClientGameTestContext context) {
		Workspace before = context.computeOnClient(mc -> ProfileManager.workspace());
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", COW).withMute(true)))
							.attach(LOUD.id()).attach(QUIET.id())), ws.library().put(LOUD).put(QUIET))));
			openPlace(context, PlaceRef.EVERYWHERE);
			List<String> quick = context.computeOnClient(mc -> labels(row(mc, SoundSet.QUICK_ID)));
			check(quick.equals(List.of("On", "Edit set", "Save to library", "Detach")), "the Quick changes line's buttons, was " + quick);
			List<String> columns = context.computeOnClient(mc -> row(mc, SoundSet.QUICK_ID).columnTexts());
			check(columns.get(0).equals("Quick changes") && columns.get(2).equals("1 entry · Always first here"),
					"the Quick changes line, was " + columns);
			List<String> loud = context.computeOnClient(mc -> labels(row(mc, LOUD.id())));
			check(loud.equals(List.of("On", "Edit set", "Move up", "Move down", "Detach")), "a library set's buttons, was " + loud);
			check(!context.computeOnClient(mc -> button(row(mc, LOUD.id()), "Move up").active), "nothing moves above Quick changes");
			context.takeScreenshot("place-sets-everywhere");

			context.runOnClient(mc -> places(mc).panel.move(LOUD.id(), -1));
			check(context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())).equals(List.of(SoundSet.QUICK_ID, LOUD.id(), QUIET.id())),
					"Move up on the set under Quick changes changes nothing");
			check(context.computeOnClient(mc -> places(mc).toast()) == null, "and offers no Undo");

			// FR-8.6 On/Off on the pinned line; then a quick Mute switches it back on, so Mute never silently does nothing.
			context.runOnClient(mc -> places(mc).panel.setEnabled(SoundSet.QUICK_ID, false));
			check(context.computeOnClient(mc -> row(mc, SoundSet.QUICK_ID).columnTexts().get(1)).equals("○ Off"), "Off shows as text");
			check(toast(context).equals("Turned off 'Quick changes' here"), "the Off toast, was " + toast(context));
			check(!context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, COW)),
					"with Quick changes off, its mute does not apply");
			// Detach on the Quick changes line removes its entries; Undo puts them back, still pinned and still off.
			List<SetEntry> quickEntries = context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickEntries());
			context.runOnClient(mc -> button(row(mc, SoundSet.QUICK_ID), "Detach").onPress(null));
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickChanges()) == null, "Detach removes Quick changes");
			check(context.computeOnClient(mc -> places(mc).panel.list.findRow(SoundSet.QUICK_ID)) == null, "and its line");
			check(toast(context).equals("Detached 'Quick changes'"), "the Detach toast, was " + toast(context));
			context.runOnClient(mc -> places(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickEntries()).equals(quickEntries)
					&& context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere()))
							.equals(List.of(SoundSet.QUICK_ID, LOUD.id(), QUIET.id()))
					&& !context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).enabled()),
					"Undo restores Quick changes' entries, pinned first and still off");
			check(context.computeOnClient(mc -> places(mc).panel.list.findRow(SoundSet.QUICK_ID)) != null, "the line is listed again");

			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> {
				SoundSculptorScreen main = (SoundSculptorScreen) mc.gui.screen();
				soundsculptor.mc.SoundsPanel.resetRememberedState();
				main.selectTab(SoundSculptorScreen.TAB_SOUNDS);
				main.sounds().toggleMute(COW);
			});
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).enabled()),
					"Mute on a sound row switches Quick changes back on");
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, COW)), "and the sound is muted");
		} finally {
			restore(context, before, LOUD.id(), QUIET.id());
		}
	}

	/** P13 in game: two sets setting the same sound's level; the higher one wins, and moving it down flips the result. */
	private static void swapOrderFlipsResult(ClientGameTestContext context) {
		Workspace before = context.computeOnClient(mc -> ProfileManager.workspace());
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(LOUD.id()).attach(QUIET.id())), ws.library().put(LOUD).put(QUIET))));
			check(near(play(context), 0.7F), "the set on top (70%) wins");
			openPlace(context, PlaceRef.EVERYWHERE);
			check(!context.computeOnClient(mc -> button(row(mc, LOUD.id()), "Move up").active), "the top set cannot move up");
			check(!context.computeOnClient(mc -> button(row(mc, QUIET.id()), "Move down").active), "the bottom set cannot move down");
			context.runOnClient(mc -> button(row(mc, QUIET.id()), "Move up").onPress(null));
			check(context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())).equals(List.of(QUIET.id(), LOUD.id())), "Move up applies at once");
			check(toast(context).equals("Moved 'Villagers at 30' up"), "the Move toast, was " + toast(context));
			check(near(play(context), 0.3F), "after the swap the 30% set wins");
			context.runOnClient(mc -> places(mc).pressUndo());
			check(near(play(context), 0.7F), "Undo puts the order and the result back");
		} finally {
			restore(context, before, LOUD.id(), QUIET.id());
		}
	}

	/**
	 * FR-4.10: Save to library names Quick changes ("Quick changes" by default) and keeps it attached in the same slot;
	 * the next quick action starts a new Quick changes, pinned above it.
	 */
	private static void saveToLibraryKeepsAttachedAndNextMuteMakesNewQuick(ClientGameTestContext context) {
		Workspace before = context.computeOnClient(mc -> ProfileManager.workspace());
		String[] savedId = new String[1];
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e1", COW).withMute(true))).attach(LOUD.id())),
					ws.library().put(LOUD))));
			openPlace(context, PlaceRef.EVERYWHERE);
			context.runOnClient(mc -> button(row(mc, SoundSet.QUICK_ID), "Save to library").onPress(null));
			context.waitForScreen(SetNameScreen.class);
			check(context.computeOnClient(mc -> ((SetNameScreen) mc.gui.screen()).nameField.getValue()).equals("Quick changes"),
					"the name starts as Quick changes");
			context.takeScreenshot("place-sets-save-to-library");
			context.runOnClient(mc -> ((SetNameScreen) mc.gui.screen()).save());
			context.waitForScreen(PlaceSetsScreen.class);
			savedId[0] = context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).setId());
			check(!savedId[0].equals(SoundSet.QUICK_ID), "the saved set takes Quick changes' slot");
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickChanges()) == null, "the place has no Quick changes now");
			SoundSet saved = context.computeOnClient(mc -> ProfileManager.library().get(savedId[0]));
			check(saved != null && saved.name().equals("Quick changes") && saved.entries().size() == 1, "the library has the set, was " + saved);
			check(toast(context).equals("Saved Quick changes as 'Quick changes'"), "the Save toast, was " + toast(context));
			check(context.computeOnClient(mc -> places(mc).panel.list.findRow(savedId[0])) != null, "the saved set is listed");

			// Undo after Save to library: Quick changes is back in its slot and the saved set leaves the library.
			context.runOnClient(mc -> places(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.library().get(savedId[0])) == null, "Undo removes the saved set");
			check(context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())).equals(List.of(SoundSet.QUICK_ID, LOUD.id()))
					&& context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickEntries().size()) == 1,
					"Undo brings Quick changes back, pinned first, with its entry");
			check(context.computeOnClient(mc -> places(mc).panel.list.findRow(SoundSet.QUICK_ID)) != null, "the Quick changes line is back");
			// Save again, to go on.
			context.runOnClient(mc -> places(mc).panel.saveToLibrary("Quick changes"));
			savedId[0] = context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).setId());
			check(!savedId[0].equals(SoundSet.QUICK_ID) && context.computeOnClient(mc -> ProfileManager.library().contains(savedId[0])),
					"saved again");

			// The next quick action makes a new Quick changes, pinned above the saved set.
			context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.apply(ws, PlaceRef.EVERYWHERE, PIG,
					QuickActions.toggleMute(ws, PlaceRef.EVERYWHERE, PIG, false)).after()));
			check(context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())).equals(List.of(SoundSet.QUICK_ID, savedId[0], LOUD.id())),
					"a new Quick changes is pinned first, was " + context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())));
			context.runOnClient(mc -> places(mc).refreshLists());
			check(!context.computeOnClient(mc -> button(row(mc, savedId[0]), "Move up").active), "the saved set cannot pass the new Quick changes");
		} finally {
			restore(context, before, LOUD.id(), savedId[0]);
		}
	}

	/**
	 * FR-8.6: Attach set… lists only the library sets not attached here; attaching one twice is refused; New set makes a
	 * set and attaches it in one change, which one Undo takes back.
	 */
	private static void attachSameSetTwiceNotOffered(ClientGameTestContext context) {
		Workspace before = context.computeOnClient(mc -> ProfileManager.workspace());
		String[] madeId = new String[1];
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(LOUD.id())), ws.library().put(LOUD).put(QUIET))));
			openPlace(context, PlaceRef.EVERYWHERE);
			context.runOnClient(mc -> places(mc).panel.attachButton.onPress(null));
			context.waitForScreen(AttachSetScreen.class);
			check(context.computeOnClient(mc -> attach(mc).list.findRow(LOUD.id())) == null, "a set already attached here is not offered");
			check(context.computeOnClient(mc -> attach(mc).list.findRow(QUIET.id())) != null, "a set not attached here is offered");
			context.takeScreenshot("place-sets-attach");
			context.runOnClient(mc -> attach(mc).attach(QUIET.id()));
			context.waitForScreen(PlaceSetsScreen.class);
			check(context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())).equals(List.of(LOUD.id(), QUIET.id())),
					"Attach adds the set at the bottom");
			check(toast(context).equals("Attached 'Villagers at 30'"), "the Attach toast, was " + toast(context));

			// The same set again: refused with a message, nothing changes.
			Profile attached = context.computeOnClient(mc -> ProfileManager.profile());
			check(!context.computeOnClient(mc -> places(mc).panel.attach(QUIET.id())), "attaching the same set twice is refused");
			check(toast(context).equals("This set is already attached here."), "the refusal says why, was " + toast(context));
			check(context.computeOnClient(mc -> ProfileManager.profile()).equals(attached), "and nothing changes");

			// New set from Attach set…: made and attached in one change.
			context.runOnClient(mc -> places(mc).panel.openAttach());
			context.waitForScreen(AttachSetScreen.class);
			check(context.computeOnClient(mc -> attach(mc).list.findRow(LOUD.id()) == null && attach(mc).list.findRow(QUIET.id()) == null),
					"neither attached set is offered");
			context.runOnClient(mc -> attach(mc).openNew());
			context.waitForScreen(SetNameScreen.class);
			context.runOnClient(mc -> {
				((SetNameScreen) mc.gui.screen()).setName("Brand new");
				((SetNameScreen) mc.gui.screen()).save();
			});
			context.waitForScreen(PlaceSetsScreen.class);
			madeId[0] = context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(2).setId());
			check(context.computeOnClient(mc -> ProfileManager.library().get(madeId[0]).name()).equals("Brand new"), "New set adds the set");
			check(toast(context).equals("Created and attached 'Brand new'"), "the New set toast, was " + toast(context));
			context.runOnClient(mc -> places(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.library().get(madeId[0])) == null
					&& context.computeOnClient(mc -> ids(ProfileManager.profile().everywhere())).equals(List.of(LOUD.id(), QUIET.id())),
					"one Undo takes back the set and its attachment");
		} finally {
			restore(context, before, LOUD.id(), QUIET.id(), madeId[0]);
		}
	}

	/** FR-8.10: Everywhere is the first row of the Zones tab, with no color and nothing to edit, move or delete: only Sound Sets. */
	private static void everywhereRowFirstAndNotEditable(ClientGameTestContext context, String key) {
		Workspace before = context.computeOnClient(mc -> ProfileManager.workspace());
		try {
			// With no zones, a hint row under Everywhere says how to make one (it has no buttons, so it takes focus itself).
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library())));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_ZONES));
			List<SculptList.Row> empty = context.computeOnClient(mc -> List.copyOf(main(mc).zonesPanel().list.children()));
			check(empty.size() == 2 && empty.get(1).key() == ZoneListPanel.NO_ZONES_ROW
					&& empty.get(1).narrationText().getString().startsWith("No zones yet"), "the no-zones hint follows Everywhere");
			context.setScreen(() -> null);

			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.attach(LOUD.id()))
					.withZones(List.of(new Zone("pen", "Pen", true, key, "minecraft:overworld", new Point(0, 64, 0), new Shape.Sphere(8),
							PlaceSets.EMPTY.attach(LOUD.id()).attach(QUIET.id())))), ws.library().put(LOUD).put(QUIET))));
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_ZONES));
			SculptList.Row first = context.computeOnClient(mc -> main(mc).zonesPanel().list.children().get(0));
			check(first.key().equals(PlaceRef.EVERYWHERE), "Everywhere is the first row");
			check(first.swatch() == -1, "Everywhere has no color");
			check(labels(first).equals(List.of("Sound Sets")), "Everywhere's only control is Sound Sets, was " + labels(first));
			check(first.columnTexts().get(0).equals("Everywhere") && first.columnTexts().contains("1 Sound Set"),
					"Everywhere's row, was " + first.columnTexts());
			check(context.computeOnClient(mc -> main(mc).zonesPanel().list.findRow(ZoneListPanel.NO_ZONES_ROW)) == null,
					"with a zone, the hint is gone");
			SculptList.Row pen = context.computeOnClient(mc -> main(mc).zonesPanel().list.findRow("pen"));
			check(pen.columnTexts().contains("2 Sound Sets"), "a zone row counts its sets, was " + pen.columnTexts());
			check(labels(pen).contains("Sound Sets") && !button(pen, "Move up").active, "the first zone still cannot move above Everywhere");
			context.takeScreenshot("place-sets-zones-tab");

			context.runOnClient(mc -> button(main(mc).zonesPanel().list.children().get(0), "Sound Sets").onPress(null));
			context.waitForScreen(PlaceSetsScreen.class);
			check(context.computeOnClient(mc -> places(mc).where()).equals(PlaceRef.EVERYWHERE), "Sound Sets opens Everywhere's sets");
			context.runOnClient(mc -> places(mc).onClose());
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> button(main(mc).zonesPanel().list.findRow("pen"), "Sound Sets").onPress(null));
			context.waitForScreen(PlaceSetsScreen.class);
			check(context.computeOnClient(mc -> places(mc).where()).equals(PlaceRef.zone("pen")), "a zone's Sound Sets opens its sets");
			check(context.computeOnClient(mc -> places(mc).panel.list.children().size()) == 2, "the zone lists its two sets");
		} finally {
			restore(context, before, LOUD.id(), QUIET.id());
		}
	}

	/** FR-8.6: a place holds up to 32 sets; a 33rd attach shows the limit and changes nothing. */
	private static void thirtyThirdAttachShowsMessage(ClientGameTestContext context) {
		Workspace before = context.computeOnClient(mc -> ProfileManager.workspace());
		List<String> ids = new ArrayList<>();
		for (int i = 1; i <= PlaceSets.MAX_SETS + 1; i++) {
			ids.add("place-limit-" + i);
		}
		try {
			context.runOnClient(mc -> ProfileManager.change(ws -> {
				SetLibrary library = ws.library();
				PlaceSets place = PlaceSets.EMPTY;
				for (int i = 0; i < ids.size(); i++) {
					library = library.put(new SoundSet(ids.get(i), "Limit " + (i + 1), List.of()));
					if (i < PlaceSets.MAX_SETS) {
						place = place.attach(ids.get(i));
					}
				}
				return new Workspace(Profile.empty(ws.profile().name()).withEverywhere(place), library);
			}));
			String extra = ids.get(PlaceSets.MAX_SETS);
			openPlace(context, PlaceRef.EVERYWHERE);
			Profile full = context.computeOnClient(mc -> ProfileManager.profile());
			context.runOnClient(mc -> places(mc).panel.attachButton.onPress(null));
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof PlaceSetsScreen), "a full place does not open Attach set…");
			check(toast(context).equals("A place can have up to 32 Sound Sets."), "the limit is shown, was " + toast(context));

			context.runOnClient(mc -> mc.gui.setScreen(new AttachSetScreen(mc.gui.screen(), PlaceRef.EVERYWHERE)));
			context.waitForScreen(AttachSetScreen.class);
			context.runOnClient(mc -> attach(mc).attach(extra));
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof AttachSetScreen), "the 33rd attach keeps Attach set… open");
			String message = context.computeOnClient(mc -> attach(mc).toast() == null ? "(no toast)" : attach(mc).toast().message().getString());
			check(message.equals("A place can have up to 32 Sound Sets."), "the 33rd attach shows the limit, was " + message);
			context.runOnClient(mc -> attach(mc).openNew());
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof AttachSetScreen), "New set is refused too, before asking a name");
			check(context.computeOnClient(mc -> ProfileManager.profile()).equals(full), "nothing changes");
			check(context.computeOnClient(mc -> ProfileManager.library().size()) == context.computeOnClient(mc -> before.library().size()) + ids.size(),
					"no set is added");
		} finally {
			restore(context, before, ids.toArray(String[]::new));
		}
	}

	// ---- Helpers ----

	private static void openPlace(ClientGameTestContext context, PlaceRef where) {
		context.setScreen(() -> new PlaceSetsScreen(null, where));
		context.waitForScreen(PlaceSetsScreen.class);
	}

	/** Puts the profile back and removes the sets this case added to the library. */
	private static void restore(ClientGameTestContext context, Workspace before, String... added) {
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			ProfileManager.change(ws -> {
				SetLibrary library = ws.library();
				for (String id : added) {
					if (id != null && !before.library().contains(id)) {
						library = library.remove(id);
					}
				}
				return new Workspace(before.profile(), library);
			});
			ProfileManager.flushSaves();
		});
	}

	/** Plays a villager 2 blocks from the listener; its start factor (0 if it did not start). */
	private static float play(ClientGameTestContext context) {
		float factor = context.computeOnClient(mc -> {
			Vec3 at = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(VILLAGER), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, at.x() + 2, at.y(), at.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound) ? SoundHook.lastStartFactor() : 0.0F;
		});
		context.waitTicks(3);
		return factor;
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.001F;
	}

	private static List<String> ids(PlaceSets place) {
		return place.attached().stream().map(Attachment::setId).toList();
	}

	private static PlaceSetsScreen places(Minecraft mc) {
		check(mc.gui.screen() instanceof PlaceSetsScreen, "expected a place's Sound Sets, found " + mc.gui.screen());
		return (PlaceSetsScreen) mc.gui.screen();
	}

	private static AttachSetScreen attach(Minecraft mc) {
		check(mc.gui.screen() instanceof AttachSetScreen, "expected Attach set…, found " + mc.gui.screen());
		return (AttachSetScreen) mc.gui.screen();
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static SculptList.Row row(Minecraft mc, String setId) {
		SculptList.Row row = places(mc).panel.list.findRow(setId);
		check(row != null, "no line for " + setId);
		return row;
	}

	private static List<String> labels(SculptList.Row row) {
		return row.buttons().stream().map(b -> b.getMessage().getString()).toList();
	}

	private static SculptButton button(SculptList.Row row, String label) {
		for (SculptButton button : row.buttons()) {
			if (button.getMessage().getString().equals(label)) {
				return button;
			}
		}
		throw new AssertionError("no " + label + " button in " + labels(row));
	}

	/** The toast on the open screen. */
	private static String toast(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			SculptScreen.Toast toast = ((SculptScreen) mc.gui.screen()).toast();
			return toast == null ? "(no toast)" : toast.message().getString();
		});
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
