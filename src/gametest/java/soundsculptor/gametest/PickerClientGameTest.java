package soundsculptor.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SoundPickerScreen;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptCheckbox;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;

/**
 * The Sounds picker (PRD FR-1.7, FR-4.3, FR-4.8, M8d): the set editor's Add sounds… opens the Sounds browser with a
 * checkbox on each row and Like this…; Add puts one entry per pick into the editor's draft, and Save writes it.
 * <ul>
 *   <li>{@code tickThreeCowsAddsThreeEntries}: three ticks (one with the keyboard) make three settings-free entries in
 *   pick order; the footer counts them; Add is off while nothing is picked; Cancel changes nothing; the entries make the
 *   draft changed, so closing the set editor asks to Save or Discard.</li>
 *   <li>{@code reopenMarksThemInThisSet}: the FR-1 AC: after Save, the picker marks them "In this set" with no
 *   checkbox.</li>
 *   <li>{@code likeThisAddsGroupEntry}: Like this… lists the sound's groups with counts; a ticked group becomes a group
 *   entry, and a group the set has is marked "In this set".</li>
 *   <li>{@code overLimitShowsMessage}: picks that would pass 512 entries show the limit and the picker stays.</li>
 * </ul>
 */
public final class PickerClientGameTest implements FabricClientGameTest {
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String COW_HURT = "minecraft:entity.cow.hurt";
	private static final String COW_DEATH = "minecraft:entity.cow.death";
	private static final String COW_STEP = "minecraft:entity.cow.step";
	private static final String COW_GROUP = "minecraft:entity.cow.*";
	private static final SoundSet FARM = new SoundSet("picker-farm", "Farm", List.of());

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			tickThreeCowsAddsThreeEntries(context);
			reopenMarksThemInThisSet(context);
			likeThisAddsGroupEntry(context);
			overLimitShowsMessage(context);
		}
	}

	private static void tickThreeCowsAddsThreeEntries(ClientGameTestContext context) {
		Snapshot before = Snapshot.take(context);
		try {
			putSet(context, FARM);
			openPicker(context, FARM.id());
			check(context.computeOnClient(mc -> picker(mc).footerText()).equals("0 picked"), "the footer starts at 0 picked");
			check(!context.computeOnClient(mc -> picker(mc).addButton.active), "Add is off while nothing is picked");
			check(context.computeOnClient(mc -> sounds(mc).applyToDropdown == null && sounds(mc).pauseToggle == null
					&& sounds(mc).sortDropdown == null), "the picker has no Apply to, Pause or Sort");
			showCows(context);
			check(context.computeOnClient(mc -> {
				SculptList.Row row = sounds(mc).list.findRow(COW);
				return row.buttons().size() == 2 && row.buttons().get(0) instanceof SculptCheckbox
						&& row.buttons().get(1).getMessage().getString().equals("Like this…");
			}), "a row has a checkbox and Like this…, and no quick actions");

			// The first tick with the keyboard: Space on the focused checkbox.
			context.runOnClient(mc -> sounds(mc).list.focusRow(sounds(mc).list.findRow(COW)));
			check(context.computeOnClient(mc -> mc.gui.screen().getFocused() == sounds(mc).list), "the cow row has focus");
			String unchecked = narration(context, COW);
			check(unchecked.contains(rowName(context, COW)) && unchecked.contains("not checked"),
					"the checkbox reads the sound's name and its state, was " + unchecked);
			context.getInput().pressKey(InputConstants.KEY_SPACE);
			context.waitTicks(1);
			check(context.computeOnClient(mc -> picker(mc).picker().picked(COW)), "Space ticks the cow");
			String checked = narration(context, COW);
			check(checked.contains(rowName(context, COW)) && checked.contains("checked") && !checked.contains("not checked"),
					"the ticked checkbox reads checked, was " + checked);
			check(context.computeOnClient(mc -> checkbox(mc, COW).isFocused()), "the new checkbox keeps keyboard focus");
			check(context.computeOnClient(mc -> picker(mc).footerText()).equals("1 picked"), "the footer says 1 picked");
			check(context.computeOnClient(mc -> picker(mc).addButton.active), "Add is on once something is picked");

			// A tick rebuilds only its own row.
			int rebuilt = context.computeOnClient(mc -> {
				int built = sounds(mc).rowsBuilt;
				checkbox(mc, COW_HURT).onPress(null);
				return sounds(mc).rowsBuilt - built;
			});
			check(rebuilt == 1, "a tick rebuilds only its row, rebuilt " + rebuilt);
			context.runOnClient(mc -> checkbox(mc, COW_DEATH).onPress(null));
			// Ticked and unticked again: not added.
			context.runOnClient(mc -> {
				checkbox(mc, COW_STEP).onPress(null);
				checkbox(mc, COW_STEP).onPress(null);
			});
			check(context.computeOnClient(mc -> picker(mc).footerText()).equals("3 picked"), "the footer says 3 picked");
			context.takeScreenshot("picker-three-cows");

			context.runOnClient(mc -> picker(mc).addButton.onPress(null));
			context.waitForScreen(SetEditorScreen.class);
			List<SetEntry> drafted = context.computeOnClient(mc -> editor(mc).draft().entries());
			check(drafted.stream().map(e -> e.pattern().text()).toList().equals(List.of(COW, COW_HURT, COW_DEATH)),
					"Add makes one entry per pick, in pick order, was " + drafted);
			check(drafted.stream().allMatch(e -> e.enabled() && e.mute() == null && e.volume() == null && e.limit() == null && e.cue() == null),
					"the entries have no settings yet");
			check(context.computeOnClient(mc -> editor(mc).entryList.children().size()) == 3, "the set editor lists them");
			check(context.computeOnClient(mc -> ProfileManager.library().get(FARM.id()).entries().isEmpty()), "nothing is saved before Save");

			// Cancel in the picker changes nothing.
			context.runOnClient(mc -> editor(mc).addSounds());
			context.waitForScreen(SoundPickerScreen.class);
			showCows(context);
			context.runOnClient(mc -> {
				checkbox(mc, COW_STEP).onPress(null);
				picker(mc).cancelButton.onPress(null);
			});
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> editor(mc).draft().entries().size()) == 3, "Cancel adds nothing");

			// The picked entries are unsaved changes: closing asks, and Discard drops them.
			context.runOnClient(mc -> editor(mc).onClose());
			context.waitForScreen(SculptDialog.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("Unsaved changes"), "closing asks to save");
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1)); // Discard
			check(context.computeOnClient(mc -> ProfileManager.library().get(FARM.id()).entries().isEmpty()), "Discard drops the picks");

			// Type a pattern… still opens the entry editor for a typed pattern (FR-4.8).
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(null, FARM.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.runOnClient(mc -> editor(mc).typeButton.onPress(null));
			context.waitForScreen(EntryEditorScreen.class);
			check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals("New entry"), "Type a pattern… opens a new entry");
		} finally {
			before.restore(context);
		}
	}

	/** FR-1 AC: sounds added through the picker and saved are marked "In this set" when the picker opens again. */
	private static void reopenMarksThemInThisSet(ClientGameTestContext context) {
		Snapshot before = Snapshot.take(context);
		try {
			putSet(context, FARM);
			openPicker(context, FARM.id());
			showCows(context);
			context.runOnClient(mc -> {
				checkbox(mc, COW).onPress(null);
				checkbox(mc, COW_HURT).onPress(null);
				picker(mc).addButton.onPress(null);
			});
			context.waitForScreen(SetEditorScreen.class);
			saveEditor(context);
			check(context.computeOnClient(mc -> ProfileManager.library().get(FARM.id()).entries().stream().map(e -> e.pattern().text()).toList())
					.equals(List.of(COW, COW_HURT)), "Save writes the picked entries");

			openPicker(context, FARM.id());
			showCows(context);
			for (String id : List.of(COW, COW_HURT)) {
				SculptList.Row row = context.computeOnClient(mc -> sounds(mc).list.findRow(id));
				check(row.columnTexts().contains("✔ In this set"), id + " is marked In this set, was " + row.columnTexts());
				check(row.buttons().size() == 1 && !(row.buttons().get(0) instanceof SculptCheckbox), id + " has no checkbox");
			}
			check(context.computeOnClient(mc -> checkbox(mc, COW_DEATH) != null && !sounds(mc).list.findRow(COW_DEATH).columnTexts()
					.contains("✔ In this set")), "a sound not in the set can still be picked");
			context.runOnClient(mc -> sounds(mc).togglePick(COW));
			check(context.computeOnClient(mc -> picker(mc).picker().pickedCount()) == 0, "a sound in the set cannot be picked again");
			context.takeScreenshot("picker-in-this-set");
		} finally {
			before.restore(context);
		}
	}

	/** FR-4.8: Like this… lists the sound's groups with their counts; a ticked group becomes one group entry. */
	private static void likeThisAddsGroupEntry(ClientGameTestContext context) {
		Snapshot before = Snapshot.take(context);
		try {
			putSet(context, FARM);
			openPicker(context, FARM.id());
			showCows(context);
			context.runOnClient(mc -> likeThisButton(mc, COW).onPress(null));
			check(context.computeOnClient(mc -> picker(mc).hasOverlay() && sounds(mc).likeThisList != null), "Like this… opens the groups");
			List<String> group = context.computeOnClient(mc -> sounds(mc).likeThisList.findRow(COW_GROUP).columnTexts());
			check(group.get(0).equals(COW_GROUP) && group.stream().anyMatch(t -> t.matches("Matches \\d+ sounds")),
					"the group shows its count, was " + group);
			check(context.computeOnClient(mc -> String.valueOf(sounds(mc).likeThisList.children().get(0).key())).equals(COW_GROUP),
					"the closest group comes first");
			check(context.computeOnClient(mc -> mc.gui.screen().getFocused() == sounds(mc).likeThisList), "the groups take focus");
			context.takeScreenshot("picker-like-this");
			context.runOnClient(mc -> groupCheckbox(mc, COW_GROUP).onPress(null));
			check(context.computeOnClient(mc -> picker(mc).picker().groupPicked(COW_GROUP)), "ticking the group picks it");
			check(context.computeOnClient(mc -> groupCheckbox(mc, COW_GROUP).checked()), "its checkbox shows ticked");
			check(context.computeOnClient(mc -> picker(mc).footerText()).equals("1 picked"), "the footer counts the group");
			context.runOnClient(mc -> picker(mc).closeOverlay());
			check(context.computeOnClient(mc -> likeThisButton(mc, COW).isFocused()), "closing returns to Like this…");
			context.runOnClient(mc -> picker(mc).addButton.onPress(null));
			context.waitForScreen(SetEditorScreen.class);
			List<SetEntry> drafted = context.computeOnClient(mc -> editor(mc).draft().entries());
			check(drafted.size() == 1 && drafted.get(0).pattern().text().equals(COW_GROUP) && drafted.get(0).mute() == null,
					"the group becomes one entry with no settings, was " + drafted);

			// The group is in the set now (the draft): Like this… marks it and offers no checkbox.
			context.runOnClient(mc -> editor(mc).addSounds());
			context.waitForScreen(SoundPickerScreen.class);
			showCows(context);
			context.runOnClient(mc -> likeThisButton(mc, COW).onPress(null));
			SculptList.Row marked = context.computeOnClient(mc -> sounds(mc).likeThisList.findRow(COW_GROUP));
			check(marked.buttons().isEmpty() && marked.columnTexts().contains("✔ In this set"), "the group is marked In this set");
		} finally {
			before.restore(context);
		}
	}

	/** Picks that would pass 512 entries show the limit; the picker stays open and the draft is unchanged. */
	private static void overLimitShowsMessage(ClientGameTestContext context) {
		List<SetEntry> many = new ArrayList<>();
		for (int i = 1; i <= SoundSet.MAX_ENTRIES - 2; i++) {
			many.add(SetEntry.of("e" + i, "minecraft:test.sound_" + i));
		}
		SoundSet big = new SoundSet("picker-big", "Big", many);
		Snapshot before = Snapshot.take(context);
		try {
			putSet(context, big);
			openPicker(context, big.id());
			showCows(context);
			context.runOnClient(mc -> {
				checkbox(mc, COW).onPress(null);
				checkbox(mc, COW_HURT).onPress(null);
				checkbox(mc, COW_DEATH).onPress(null);
				picker(mc).addButton.onPress(null);
			});
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof SoundPickerScreen), "the picker stays open");
			check(context.computeOnClient(mc -> picker(mc).toast() != null
					&& picker(mc).toast().message().getString().equals("A Sound Set can have up to 512 entries.")), "the limit is shown");
			context.takeScreenshot("picker-over-limit");
			context.runOnClient(mc -> checkbox(mc, COW_DEATH).onPress(null));
			context.runOnClient(mc -> picker(mc).addButton.onPress(null));
			context.waitForScreen(SetEditorScreen.class);
			check(context.computeOnClient(mc -> editor(mc).draft().entries().size()) == SoundSet.MAX_ENTRIES, "two fit");
		} finally {
			before.restore(context);
		}
	}

	// ---- Helpers ----

	private static void putSet(ClientGameTestContext context, SoundSet set) {
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(set))));
	}

	/** Opens the set editor on the set, then Add sounds…. */
	private static void openPicker(ClientGameTestContext context, String setId) {
		context.runOnClient(mc -> {
			RecentLog.get().sounds().clear();
			mc.gui.setScreen(SetEditorScreen.edit(null, setId));
		});
		context.waitForScreen(SetEditorScreen.class);
		context.runOnClient(mc -> editor(mc).addSounds());
		context.waitForScreen(SoundPickerScreen.class);
	}

	/** Every known sound, narrowed to cows, waiting past the search delay. */
	private static void showCows(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			sounds(mc).setRecentOnly(false);
			sounds(mc).searchField.setValue("cow");
		});
		context.waitTicks(6);
		for (String id : List.of(COW, COW_HURT, COW_DEATH, COW_STEP)) {
			check(context.computeOnClient(mc -> sounds(mc).list.findRow(id) != null), "the cow search should list " + id);
		}
	}

	/** Saves the set editor, through the linked-save confirmation if it asks. */
	private static void saveEditor(ClientGameTestContext context) {
		context.runOnClient(mc -> editor(mc).save());
		context.waitTicks(1);
		if (context.computeOnClient(mc -> mc.gui.screen() instanceof SculptDialog)) {
			context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		}
		context.waitTicks(1);
		check(context.computeOnClient(mc -> !(mc.gui.screen() instanceof SetEditorScreen)), "Save closes the set editor");
	}

	private static String narration(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> NarrationCompat.collect(sounds(mc).list.findRow(id).buttons().get(0)::updateNarration));
	}

	private static String rowName(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> sounds(mc).list.findRow(id).columnTexts().get(0));
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
				SoundsPanel.resetRememberedState();
			});
		}
	}

	private static SoundPickerScreen picker(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundPickerScreen, "expected the Sounds picker, found " + mc.gui.screen());
		return (SoundPickerScreen) mc.gui.screen();
	}

	private static SoundsPanel sounds(Minecraft mc) {
		return picker(mc).sounds();
	}

	private static SetEditorScreen editor(Minecraft mc) {
		check(mc.gui.screen() instanceof SetEditorScreen, "expected the set editor, found " + mc.gui.screen());
		return (SetEditorScreen) mc.gui.screen();
	}

	/** The row's checkbox, or {@code null} when it has none (in the set). */
	private static SculptCheckbox checkbox(Minecraft mc, String id) {
		SculptList.Row row = sounds(mc).list.findRow(id);
		check(row != null, "no row for " + id);
		return row.buttons().get(0) instanceof SculptCheckbox box ? box : null;
	}

	private static SculptCheckbox groupCheckbox(Minecraft mc, String pattern) {
		SculptList.Row row = sounds(mc).likeThisList.findRow(pattern);
		check(row != null && row.buttons().get(0) instanceof SculptCheckbox, "no checkbox for " + pattern);
		return (SculptCheckbox) row.buttons().get(0);
	}

	private static SculptButton likeThisButton(Minecraft mc, String id) {
		List<SculptButton> buttons = sounds(mc).list.findRow(id).buttons();
		return buttons.get(buttons.size() - 1);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
