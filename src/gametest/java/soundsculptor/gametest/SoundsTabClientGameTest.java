package soundsculptor.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.AddToSetScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SetNameScreen;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundSculptorSettings;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;

/**
 * The Sounds tab (PRD FR-1, FR-3, FR-12.1, M8d), one browser replacing Recently Played and Search:
 * <ul>
 *   <li>{@code villagerFilterOnThenOff}: the FR-1 AC "type villager with the filter on, then off", with the count and
 *   the FR-1.2 columns on rows that have recent data; Pause and Sort only with the filter on.</li>
 *   <li>{@code orAndNotSyntax}: the FR-3 AC syntax (OR, AND, NOT) with the filter on and off (FR-3.5).</li>
 *   <li>{@code muteRowWritesEverywhereQuick}: the FR-1 AC Mute row, even with a "Not muted" library set attached, and
 *   the Level… pop-up, each with Undo.</li>
 *   <li>{@code zoneApplyToWritesZoneQuick}: the FR-1 and FR-3 AC Apply to a zone.</li>
 *   <li>{@code addToSetCreatesEntryInChosenSet}: Add to set… with a library set and a Like this group, "In this
 *   set", a new set, and a full set (FR-1.5, FR-4.8).</li>
 *   <li>{@code filterStateRememberedAcrossReopen}: the filter starts on, then stays as the player left it (FR-1.0).</li>
 * </ul>
 */
public final class SoundsTabClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String VILLAGER_HURT = "minecraft:entity.villager.hurt";
	private static final String ZOMBIE = "minecraft:entity.zombie.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String VILLAGER_GROUP = "minecraft:entity.villager.*";
	private static final SoundSet NOT_MUTED = new SoundSet("sounds-not-muted", "Villagers audible",
			List.of(SetEntry.of("e1", VILLAGER_GROUP).withMute(false)));
	private static final SoundSet FARM = new SoundSet("sounds-farm", "Farm", List.of(SetEntry.of("e1", "minecraft:entity.cow.*").withVolume(40)));
	private static final SoundSet OTHER = new SoundSet("sounds-other", "Other", List.of());
	private static final int SETTLE = 8; // more than one refresh interval

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			villagerFilterOnThenOff(context);
			orAndNotSyntax(context);
			muteRowWritesEverywhereQuick(context);
			zoneApplyToWritesZoneQuick(context);
			addToSetCreatesEntryInChosenSet(context);
			filterStateRememberedAcrossReopen(context);
			idleRefreshRebuildsNothing(context);
		}
	}

	/**
	 * PRD 9.1, 11.2: with the filter off (every known sound), a refresh with nothing new rebuilds no row and keeps the
	 * list as it is; a new play rebuilds only the rows that changed. Prints the time of one refresh, for the report.
	 */
	private static void idleRefreshRebuildsNothing(ClientGameTestContext context) {
		open(context);
		try {
			context.runOnClient(mc -> sounds(mc).setRecentOnly(false));
			context.runOnClient(mc -> {
				SoundsPanel panel = sounds(mc);
				// Show distance changes the columns, so every row is rebuilt: the cost of a full rebuild.
				SoundSculptorSettings.setShowDistance(false);
				int before = panel.rowsBuilt;
				long start = System.nanoTime();
				panel.refresh();
				long full = (System.nanoTime() - start) / 1000;
				check(panel.rowsBuilt - before == panel.count(), "a Show distance change rebuilds every row");
				SoundSculptorSettings.setShowDistance(true);
				panel.refresh();
				System.out.println("SOUNDS refresh, filter off, " + panel.count() + " rows: full rebuild " + full + " us");

				List<SculptList.Row> shown = List.copyOf(panel.list.children());
				int built = panel.rowsBuilt;
				long idle = medianRefreshMicros(panel, 21);
				check(panel.rowsBuilt == built, "an idle refresh rebuilds no row, built " + (panel.rowsBuilt - built));
				List<SculptList.Row> after = panel.list.children();
				boolean same = shown.size() == after.size();
				for (int i = 0; same && i < shown.size(); i++) {
					same = shown.get(i) == after.get(i);
				}
				check(same, "an idle refresh keeps the very same rows");
				System.out.println("SOUNDS refresh, filter off, idle median: " + idle + " us");
			});
			play(context, COW, 3.0);
			context.runOnClient(mc -> {
				SoundsPanel panel = sounds(mc);
				Vec3 listener = mc.getSoundManager().getListenerTransform().position();
				mc.getSoundManager().play(new SimpleSoundInstance(Identifier.parse(VILLAGER), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
						false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + 3, listener.y(), listener.z(), false));
				int built = panel.rowsBuilt;
				long start = System.nanoTime();
				panel.refresh();
				long micros = (System.nanoTime() - start) / 1000;
				int rebuilt = panel.rowsBuilt - built;
				check(rebuilt >= 1 && rebuilt <= 2, "a new play rebuilds only its row (and the cow row if its \"ago\" changed), rebuilt " + rebuilt);
				check(panel.list.findRow(VILLAGER).columnTexts().contains("Friendly Mobs"), "the villager row shows its new recent data");
				System.out.println("SOUNDS refresh, filter off, after one new play: " + micros + " us, " + rebuilt + " rows rebuilt");
			});
		} finally {
			cleanUp(context);
		}
	}

	private static long medianRefreshMicros(SoundsPanel panel, int runs) {
		long[] micros = new long[runs];
		for (int i = 0; i < runs; i++) {
			long start = System.nanoTime();
			panel.refresh();
			micros[i] = (System.nanoTime() - start) / 1000;
		}
		java.util.Arrays.sort(micros);
		return micros[runs / 2];
	}

	/** FR-1 AC: type villager with the filter on: only recently played villager sounds; off: every villager sound. */
	private static void villagerFilterOnThenOff(ClientGameTestContext context) {
		open(context);
		try {
			play(context, VILLAGER, 3.0);
			play(context, COW, 3.0);
			search(context, "villager");
			check(keys(context).equals(List.of(VILLAGER)), "with the filter on, villager lists only the villager sound that played, was " + keys(context));
			check(labels(context).contains("1 sound"), "the count says 1 sound, was " + labels(context));
			check(context.computeOnClient(mc -> sounds(mc).pauseToggle.visible && sounds(mc).sortDropdown.visible
					&& sounds(mc).applyToDropdown.visible), "Pause, Sort and Apply to show with the filter on");
			context.takeScreenshot("sounds-villager-recent");

			context.runOnClient(mc -> sounds(mc).recentToggle.onPress(null));
			List<String> all = keys(context);
			check(all.size() > 5 && all.contains(VILLAGER) && all.contains(VILLAGER_HURT),
					"with the filter off, every known villager sound is listed, was " + all);
			check(rows(context).stream().allMatch(texts -> String.join(" ", texts).toLowerCase(Locale.ROOT).contains("villager")),
					"every result is about villagers");
			check(labels(context).contains(all.size() + " sounds"), "the count matches the rows, was " + labels(context));
			List<String> heard = rowTexts(context, VILLAGER);
			check(heard.contains("Friendly Mobs") && heard.contains("Close") && heard.stream().anyMatch(t -> t.startsWith("×1 ·")),
					"a row with recent data shows the FR-1.2 columns, was " + heard);
			List<String> unheard = rowTexts(context, VILLAGER_HURT);
			check(unheard.size() == 3 && unheard.contains(VILLAGER_HURT) && unheard.stream().noneMatch(t -> t.startsWith("×")),
					"a row without recent data shows its name and ID, was " + unheard);
			check(context.computeOnClient(mc -> !sounds(mc).pauseToggle.visible && !sounds(mc).sortDropdown.visible
					&& sounds(mc).applyToDropdown.visible), "with the filter off, Pause and Sort hide; Apply to stays");
			context.takeScreenshot("sounds-villager-all");

			context.runOnClient(mc -> sounds(mc).recentToggle.onPress(null));
			check(keys(context).equals(List.of(VILLAGER)), "the filter on again lists only the villager sound that played");
		} finally {
			cleanUp(context);
		}
	}

	/** FR-3 AC syntax, with the filter on (only sounds that played) and off (every known sound). */
	private static void orAndNotSyntax(ClientGameTestContext context) {
		open(context);
		try {
			for (String id : List.of(VILLAGER, VILLAGER_HURT, ZOMBIE, COW)) {
				play(context, id, 3.0);
			}
			search(context, "villager|zombie");
			check(Set.copyOf(keys(context)).equals(Set.of(VILLAGER, VILLAGER_HURT, ZOMBIE)), "OR with the filter on, was " + keys(context));
			search(context, "villager -ambient");
			check(keys(context).equals(List.of(VILLAGER_HURT)), "NOT with the filter on, was " + keys(context));
			search(context, "zombie villager");
			check(keys(context).isEmpty() && labels(context).contains("0 sounds"), "AND with the filter on: no zombie villager played, was " + keys(context));

			context.runOnClient(mc -> sounds(mc).setRecentOnly(false));
			search(context, "villager|zombie");
			List<String> either = keys(context);
			check(either.stream().anyMatch(id -> id.contains("villager") && !id.contains("zombie"))
					&& either.stream().anyMatch(id -> id.contains("zombie") && !id.contains("villager"))
					&& either.stream().noneMatch(id -> id.contains("cow")), "OR with the filter off finds both families and no cows");
			search(context, "villager zombie");
			List<String> both = keys(context);
			check(!both.isEmpty() && both.stream().allMatch(id -> id.contains("villager") && id.contains("zombie")),
					"AND with the filter off finds only the zombie villager family, was " + both);
			search(context, "villager -ambient");
			List<List<String>> excluded = rows(context);
			check(!excluded.isEmpty() && excluded.stream().noneMatch(texts -> String.join(" ", texts).toLowerCase(Locale.ROOT).contains("ambient")),
					"NOT with the filter off: no result contains ambient");
		} finally {
			cleanUp(context);
		}
	}

	/**
	 * FR-1 AC: Mute on a row mutes the next play, the row says Muted and Everywhere's Quick changes has the entry, even
	 * with a library set that says "Not muted"; Undo puts it back. The Level… pop-up writes the level the same way.
	 */
	private static void muteRowWritesEverywhereQuick(ClientGameTestContext context) {
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
				.withEverywhere(PlaceSets.EMPTY.attach(NOT_MUTED.id())), ws.library().put(NOT_MUTED))));
		open(context);
		try {
			play(context, VILLAGER, 3.0);
			context.runOnClient(mc -> sounds(mc).setApplyTarget(ApplyTarget.EVERYWHERE));
			check(button(context, VILLAGER, SoundsPanel.BUTTON_MUTE).equals("Mute"), "the row offers Mute");
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_MUTE));
			check(context.computeOnClient(mc -> {
				SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER);
				return entry != null && Boolean.TRUE.equals(entry.mute())
						&& ProfileManager.profile().everywhere().attached().get(0).setId().equals(SoundSet.QUICK_ID);
			}), "the mute is a single-sound entry in Everywhere's Quick changes, pinned first");
			check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)),
					"Quick changes wins over the library set that says Not muted");
			check(toast(context).equals("Muted Villager mumbles"), "the toast names the sound, was " + toast(context));
			check(button(context, VILLAGER, SoundsPanel.BUTTON_MUTE).equals("Unmute"), "the row now offers Unmute");
			check(!play(context, VILLAGER, 3.0), "the next play is silent");
			check(rowTexts(context, VILLAGER).stream().anyMatch(t -> t.contains("Muted")), "the row says Muted, was " + rowTexts(context, VILLAGER));
			context.takeScreenshot("sounds-muted-row");
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER) == null),
					"Undo removes the entry");

			// Level…: the pop-up writes the level as it changes; closing it offers one Undo.
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_LEVEL));
			check(context.computeOnClient(mc -> main(mc).hasOverlay() && sounds(mc).levelSlider != null
					&& main(mc).getFocused() == sounds(mc).levelSlider), "Level… opens the pop-up with the slider focused");
			// The list refreshes every 5 ticks; it must not pull focus out of the pop-up.
			play(context, VILLAGER, 3.0);
			context.waitTicks(10);
			check(context.computeOnClient(mc -> main(mc).hasOverlay() && main(mc).getFocused() == sounds(mc).levelSlider),
					"the slider keeps focus while the list would refresh");
			context.takeScreenshot("sounds-level-popup");
			context.runOnClient(mc -> sounds(mc).levelField.setValue("40"));
			check(context.computeOnClient(mc -> {
				SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER);
				return entry != null && Integer.valueOf(40).equals(entry.volume()) && entry.mute() == null;
			}), "the level is saved in Everywhere's Quick changes at once");
			context.runOnClient(mc -> sounds(mc).levelField.setValue("250"));
			check(context.computeOnClient(mc -> Integer.valueOf(40).equals(
					QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER).volume())),
					"a level out of range is refused and changes nothing");
			context.runOnClient(mc -> main(mc).closeOverlay());
			check(toast(context).equals("Changed the level of Villager mumbles"), "closing offers Undo, was " + toast(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER) == null),
					"Undo removes the level entry");
		} finally {
			cleanUp(context);
		}
	}

	/** FR-1 and FR-3 AC: in zone A with Apply to set to A, Mute writes A's Quick changes, not Everywhere's. */
	private static void zoneApplyToWritesZoneQuick(ClientGameTestContext context) {
		String key = context.computeOnClient(mc -> ProfileManager.key());
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		Zone pen = new Zone("pen", "Pen", true, key, "minecraft:overworld", new Point(eye.x, eye.y, eye.z), new Shape.Box(9, 9, 9), PlaceSets.EMPTY);
		context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withZones(List.of(pen))));
		open(context);
		try {
			context.runOnClient(mc -> sounds(mc).setRecentOnly(false));
			search(context, "villager ambient");
			List<String> choices = context.computeOnClient(mc -> sounds(mc).applyChoices().stream()
					.map(c -> c.isEverywhere() ? "Everywhere" : c.zoneName()).toList());
			check(choices.equals(List.of("Everywhere", "Pen")), "Apply to offers Everywhere and Pen, was " + choices);
			context.runOnClient(mc -> sounds(mc).setApplyTarget(sounds(mc).applyChoices().get(1)));
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_MUTE));
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().quickChanges() == null), "Everywhere has no Quick changes");
			check(context.computeOnClient(mc -> {
				SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), PlaceRef.zone("pen"), VILLAGER);
				return entry != null && Boolean.TRUE.equals(entry.mute());
			}), "the mute is in Pen's Quick changes");
			check(toast(context).equals("Muted Villager mumbles in Pen"), "the toast names the zone, was " + toast(context));
			check(button(context, VILLAGER, SoundsPanel.BUTTON_MUTE).equals("Unmute"), "with Pen chosen, the row offers Unmute");
			context.takeScreenshot("sounds-apply-to-zone");

			// The choice stays with the filter on.
			context.runOnClient(mc -> sounds(mc).setRecentOnly(true));
			check(context.computeOnClient(mc -> sounds(mc).applyToDropdown.getMessage().getString()).contains("Zone: Pen"),
					"Apply to keeps Pen with the filter on");
		} finally {
			cleanUp(context);
		}
	}

	/**
	 * Add to set… (FR-1.5, FR-4.8): a library set, then a Like this group with its count; the entry has no settings and
	 * Undo removes it. What the set has is marked "In this set" and cannot be added again. New set makes the set and the
	 * entry in one change. A full set says so and changes nothing.
	 */
	private static void addToSetCreatesEntryInChosenSet(ClientGameTestContext context) {
		List<SetEntry> many = new ArrayList<>();
		for (int i = 0; i < SoundSet.MAX_ENTRIES; i++) {
			many.add(SetEntry.of("e" + i, "minecraft:test.sound_" + i));
		}
		SoundSet full = new SoundSet("sounds-full", "Full", many);
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()),
				ws.library().put(FARM).put(OTHER).put(full))));
		open(context);
		try {
			context.runOnClient(mc -> sounds(mc).setRecentOnly(false));
			search(context, "villager ambient");
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_ADD_TO_SET));
			context.waitForScreen(AddToSetScreen.class);
			check(context.computeOnClient(mc -> add(mc).list.findRow(FARM.id()) != null && add(mc).list.findRow(OTHER.id()) != null),
					"the library sets are offered");
			context.takeScreenshot("sounds-add-to-set-sets");
			context.runOnClient(mc -> add(mc).chooseSet(FARM.id()));
			check(context.computeOnClient(mc -> add(mc).isChoosingTarget()), "choosing Farm asks what to add");
			List<String> group = context.computeOnClient(mc -> add(mc).list.findRow(VILLAGER_GROUP).columnTexts());
			check(group.get(0).equals(VILLAGER_GROUP) && group.stream().anyMatch(t -> t.matches("Matches \\d+ sounds")),
					"the Like this group shows its count, was " + group);
			check(context.computeOnClient(mc -> String.valueOf(add(mc).list.children().get(0).key())).equals(VILLAGER),
					"This sound comes first");
			context.takeScreenshot("sounds-add-to-set-targets");
			context.runOnClient(mc -> add(mc).list.findRow(VILLAGER_GROUP).buttons().get(0).onPress(null));
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> {
				SoundSet farm = ProfileManager.library().get(FARM.id());
				SetEntry added = farm.entries().get(farm.entries().size() - 1);
				return farm.entries().size() == 2 && added.pattern().text().equals(VILLAGER_GROUP) && added.mute() == null
						&& added.volume() == null && added.enabled();
			}), "Farm gains the group entry, with no settings");
			check(context.computeOnClient(mc -> ProfileManager.library().get(OTHER.id()).equals(OTHER)
					&& ProfileManager.profile().everywhere().quickChanges() == null), "nothing else changes");
			check(toast(context).equals("Added Villager mumbles to 'Farm'"), "the toast names the set, was " + toast(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.library().get(FARM.id()).equals(FARM)), "Undo puts Farm back");

			// Added again, it is marked "In this set" and has no Add button; a second add changes nothing.
			addGroupToFarm(context);
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_ADD_TO_SET));
			context.waitForScreen(AddToSetScreen.class);
			context.runOnClient(mc -> add(mc).chooseSet(FARM.id()));
			SculptList.Row marked = context.computeOnClient(mc -> add(mc).list.findRow(VILLAGER_GROUP));
			check(marked.buttons().isEmpty() && marked.columnTexts().contains("✔ In this set"), "the group is marked In this set");
			check(!context.computeOnClient(mc -> add(mc).list.findRow(VILLAGER).buttons().isEmpty()), "This sound can still be added");
			check(!context.computeOnClient(mc -> add(mc).addGroup(VILLAGER_GROUP)), "adding the group again does nothing");
			check(context.computeOnClient(mc -> ProfileManager.library().get(FARM.id()).entries().size()) == 2, "no duplicate entry");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitForScreen(SoundSculptorScreen.class);

			// New set: a name, then This sound; one change makes the set and its entry, and Undo removes both.
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_ADD_TO_SET));
			context.waitForScreen(AddToSetScreen.class);
			context.runOnClient(mc -> add(mc).openNew());
			context.waitForScreen(SetNameScreen.class);
			context.runOnClient(mc -> {
				SetNameScreen name = (SetNameScreen) mc.gui.screen();
				name.setName("Barn");
				name.save();
			});
			context.waitForScreen(AddToSetScreen.class);
			check(context.computeOnClient(mc -> add(mc).isChoosingTarget() && add(mc).list.findRow(VILLAGER).buttons().size() == 1),
					"the new set asks what to add");
			context.runOnClient(mc -> add(mc).addSound());
			context.waitForScreen(SoundSculptorScreen.class);
			check(context.computeOnClient(mc -> ProfileManager.library().sets().stream().anyMatch(s -> s.name().equals("Barn")
					&& s.entries().size() == 1 && s.entries().get(0).pattern().text().equals(VILLAGER) && s.entries().get(0).mute() == null)),
					"the new set Barn holds the sound");
			check(toast(context).equals("Added Villager mumbles to the new set 'Barn'"), "the toast names the new set, was " + toast(context));
			context.runOnClient(mc -> main(mc).pressUndo());
			check(context.computeOnClient(mc -> ProfileManager.library().sets().stream().noneMatch(s -> s.name().equals("Barn"))),
					"Undo removes the new set");

			// A full set says so and changes nothing.
			context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_ADD_TO_SET));
			context.waitForScreen(AddToSetScreen.class);
			context.runOnClient(mc -> add(mc).chooseSet(full.id()));
			check(context.computeOnClient(mc -> !add(mc).isChoosingTarget()
					&& add(mc).toast().message().getString().equals("A Sound Set can have up to 512 entries.")), "a full set says so");
			check(context.computeOnClient(mc -> ProfileManager.library().get(full.id()).equals(full)), "the full set is unchanged");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			context.waitForScreen(SoundSculptorScreen.class);
		} finally {
			context.runOnClient(mc -> ProfileManager.change(ws -> {
				var library = ws.library().remove(FARM.id()).remove(OTHER.id()).remove(full.id());
				for (SoundSet set : ws.library().sets()) {
					if (set.name().equals("Barn")) {
						library = library.remove(set.id());
					}
				}
				return new Workspace(ws.profile(), library);
			}));
			cleanUp(context);
		}
	}

	private static void addGroupToFarm(ClientGameTestContext context) {
		context.runOnClient(mc -> press(mc, VILLAGER, SoundsPanel.BUTTON_ADD_TO_SET));
		context.waitForScreen(AddToSetScreen.class);
		context.runOnClient(mc -> {
			add(mc).chooseSet(FARM.id());
			add(mc).addGroup(VILLAGER_GROUP);
		});
		context.waitForScreen(SoundSculptorScreen.class);
	}

	/** FR-1.0: the filter is on the first time the tab opens in a game run, then stays as the player left it. */
	private static void filterStateRememberedAcrossReopen(ClientGameTestContext context) {
		context.runOnClient(mc -> SoundsPanel.resetRememberedState());
		open(context);
		try {
			check(context.computeOnClient(mc -> sounds(mc).isRecentOnly() && sounds(mc).recentToggle.value()), "the filter starts on");
			context.runOnClient(mc -> sounds(mc).recentToggle.onPress(null));
			reopen(context);
			check(context.computeOnClient(mc -> !sounds(mc).isRecentOnly() && !sounds(mc).recentToggle.value()
					&& !sounds(mc).pauseToggle.visible), "the filter stays off after closing and opening the screen");
			context.runOnClient(mc -> {
				main(mc).selectTab(SoundSculptorScreen.TAB_SETS);
				main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS);
			});
			check(context.computeOnClient(mc -> !sounds(mc).recentToggle.value()), "the filter stays off after switching tabs");
			context.runOnClient(mc -> sounds(mc).recentToggle.onPress(null));
			reopen(context);
			check(context.computeOnClient(mc -> sounds(mc).recentToggle.value() && sounds(mc).pauseToggle.visible), "turned on again, it stays on");
		} finally {
			cleanUp(context);
		}
	}

	// ---- Helpers ----

	/** Opens the main screen on the Sounds tab with the filter on and an empty log and search. */
	private static void open(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			ProfileManager.setBypass(false);
			RecentLog.get().sounds().clear();
			SoundsPanel.resetRememberedState();
		});
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS));
	}

	private static void reopen(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.waitTicks(1);
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> main(mc).currentTab()) == SoundSculptorScreen.TAB_SOUNDS, "the screen opens on the last used tab");
	}

	private static void cleanUp(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.runOnClient(mc -> {
			ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library().remove(NOT_MUTED.id())));
			ProfileManager.flushSaves();
			RecentLog.get().sounds().clear();
			SoundsPanel.resetRememberedState();
		});
	}

	/** Plays a sound east of the listener and waits for the list to refresh; returns whether it started. */
	private static boolean play(ClientGameTestContext context, String id, double blocksAway) {
		boolean started = context.computeOnClient(mc -> {
			Vec3 listener = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + blocksAway, listener.y(), listener.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(SETTLE);
		return started;
	}

	/** Types into the search field and waits past the 150 ms delay. */
	private static void search(ClientGameTestContext context, String text) {
		context.runOnClient(mc -> sounds(mc).searchField.setValue(text));
		context.waitTicks(6);
	}

	private static void press(Minecraft mc, String id, int button) {
		SculptList.Row row = sounds(mc).list.findRow(id);
		check(row != null, "no row for " + id);
		row.buttons().get(button).onPress(null);
	}

	private static String button(ClientGameTestContext context, String id, int button) {
		return context.computeOnClient(mc -> sounds(mc).list.findRow(id).buttons().get(button).getMessage().getString());
	}

	private static List<String> keys(ClientGameTestContext context) {
		return context.computeOnClient(mc -> sounds(mc).list.children().stream().map(row -> String.valueOf(row.key())).toList());
	}

	private static List<List<String>> rows(ClientGameTestContext context) {
		return context.computeOnClient(mc -> sounds(mc).list.children().stream().map(SculptList.Row::columnTexts).toList());
	}

	private static List<String> rowTexts(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> {
			SculptList.Row row = sounds(mc).list.findRow(id);
			return row == null ? null : row.columnTexts();
		});
	}

	private static List<String> labels(ClientGameTestContext context) {
		return context.computeOnClient(mc -> main(mc).body().labels().stream().map(l -> l.text().get().getString()).toList());
	}

	private static String toast(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			SculptScreen.Toast toast = main(mc).toast();
			return toast == null ? null : toast.message().getString();
		});
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static SoundsPanel sounds(Minecraft mc) {
		SoundsPanel panel = main(mc).sounds();
		check(panel != null && main(mc).currentTab() == SoundSculptorScreen.TAB_SOUNDS, "the Sounds tab should be open");
		return panel;
	}

	private static AddToSetScreen add(Minecraft mc) {
		check(mc.gui.screen() instanceof AddToSetScreen, "expected Add to set, found " + mc.gui.screen());
		return (AddToSetScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
