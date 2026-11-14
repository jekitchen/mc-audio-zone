package soundsculptor.gametest;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.i18n.PseudoLocale;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.LibraryOps;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryLocation;
import soundsculptor.core.trace.TraceText;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.RecentLog;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentSounds;
import soundsculptor.mc.AddToSetScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.AttachSetScreen;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.core.editor.SetDraft;
import soundsculptor.mc.SetDeleteDialog;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SetLibraryPanel;
import soundsculptor.mc.SetNameScreen;
import soundsculptor.mc.SoundPickerScreen;
import soundsculptor.mc.SetRenameOnImportScreen;
import soundsculptor.mc.ProfileTransfer;
import soundsculptor.core.Message;
import soundsculptor.core.sets.SetImportPlan;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.mc.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * The automatable part of the release checklist (PRD 11.5) and FR-12.3:
 * <ul>
 *   <li>Opened outside a world (the title screen, or Mod Menu's config button when Mod Menu is installed:
 *   {@code -Pmodmenu=true}), the screen says to join a world instead of offering settings that would go nowhere.</li>
 *   <li>A-3 layout: every Sound Sculptor screen at 854 × 480 and 1920 × 1080, GUI scales 1 to 4 and Auto: each control
 *   inside the window, no two controls overlapping, no button or label text cut off, no label below the body. Inside
 *   lists too: each row's buttons fit beside its text, no row button or fixed column cuts its text, and a row's long
 *   text (a name, an ID) keeps enough room to tell rows apart. The Sounds tab and the picker are checked with the
 *   Recently played filter on and off, with recently played rows (one in a long-named zone) and "In this set" marks.</li>
 *   <li>A-4 narration: every control on the Sounds tab (the Recently played filter on and off, its search help, its
 *   Level pop-up and Add to set…), the Sounds picker (the filter on and off, and Like this…), the Sound Sets, Zones and
 *   Profiles tabs, a place's Sound Sets and Attach set…,
 *   the sound trace (a shared set's line, which reads its places, and Add to Quick changes with its choice), the set
 *   editor, the delete warning, the linked-save confirmation and the import dialogs, including list row buttons, has a
 *   label to read.</li>
 *   <li>Long place lists (FR-4.11): with a set used in {@value #MANY_ZONES} long-named zones, the delete warning and the
 *   linked-save confirmation keep their buttons on screen and scroll, and every place is still listed and read.</li>
 *   <li>Pseudo-language (PRD 9.3): the same layout check with every Sound Sculptor text about 40% longer, reported, and
 *   held at or under {@value #PSEUDO_CEILING} problems (the Diagnostics timing lines aside) so new ones fail the test.</li>
 * </ul>
 * Grayscale, Sodium, Iris, Sound Physics Remastered and servers stay manual checks.
 */
public final class AccessibilityClientGameTest implements FabricClientGameTest {
	private static final int[][] WINDOWS = {{854, 480}, {1920, 1080}};
	private static final int[] GUI_SCALES = {1, 2, 3, 4, 0};
	/** Room a button needs around its text (vanilla's 4 px each side). */
	private static final int BUTTON_PADDING = 8;
	/** The fixture's second entry in Everywhere's Quick changes, as the trace lists it. */
	private static final EntryLocation QUICK_R2 = new EntryLocation(PlaceRef.EVERYWHERE, SoundSet.QUICK_ID, "r2");
	/** A sound SET matches: a shared set's line in the trace. */
	private static final String COW = "minecraft:entity.cow.ambient";
	/** A sound for the Sounds tab's Level pop-up and Add to set…. */
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	/** A sound nothing on Everywhere matches: the trace's Add to Quick changes line. */
	private static final String PIG = "minecraft:entity.pig.ambient";
	/** A library set for the Sound Sets tab, its name screen and its delete warning. */
	private static final SoundSet SET = new SoundSet("set-a11y", "Animals of the farm and the barn by the river",
			List.of(SetEntry.of("e1", "*cow*").withVolume(40)));
	/** The picker's header for SET, as the set editor names it. */
	private static final Component PICKER_SUBTITLE = Component.literal(SET.name());
	/** Zones using one set, for the long place lists of the delete warning and the linked-save confirmation. */
	private static final int MANY_ZONES = 40;
	/** Another server's profile, seeded so the Profiles tab has the same rows every run. */
	private static final String OTHER_PROFILE = "a11y-other.example.net";
	/**
	 * The most pseudo-language layout problems allowed, not counting the Diagnostics timing lines (their live numbers change
	 * length from run to run): a ratchet at the count this check finds today. Buttons and choices now fit their text and
	 * long hints wrap; what is left are 38 list columns squeezed at the narrowest GUI widths (Sounds, picker, Zones,
	 * Profiles and a place's Sound Sets), due in M16. Lower it when fixes bring the count down.
	 */
	private static final int PSEUDO_CEILING = 38;

	@Override
	public void runTest(ClientGameTestContext context) {
		// Outside a world (title screen): per-world tabs explain instead of showing settings.
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		check(!context.computeOnClient(mc -> ProfileManager.isLoaded()), "no profile is loaded on the title screen");
		// The screen opens on the last used tab; this check is about the Sounds tab.
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS));
		check(context.computeOnClient(mc -> labels(main(mc)).stream().anyMatch(t -> t.startsWith("Join a world or server"))),
				"the Sounds tab should say to join a world");
		context.takeScreenshot("a11y-no-world");
		context.setScreen(() -> null);

		// FR-12.3: Mod Menu's config button opens the same screen.
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			Screen opened = context.computeOnClient(mc -> modMenuScreen(mc.gui.screen()));
			check(opened instanceof SoundSculptorScreen, "Mod Menu's config button should open the Sound Sculptor screen, was " + opened);
			System.out.println("A11Y Mod Menu present: its config button opens the Sound Sculptor screen");
		} else {
			System.out.println("A11Y Mod Menu absent: the game runs without it (run with -Pmodmenu=true to test the button)");
		}

		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			// Another server's profile, so the Profiles tab always has its widest row (Copy, Export, Rename, Reset, Delete),
			// whatever other tests left behind.
			context.runOnClient(mc -> {
				ProfileManager.saveOther(OTHER_PROFILE, Profile.empty("Hypixel Skyblock"));
				ProfileManager.flushSaves();
			});
			try {
				checkInWorld(context);
			} finally {
				context.runOnClient(mc -> {
					SoundsPanel.resetRememberedState();
					RecentLog.get().sounds().clear();
					ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library().remove(SET.id())));
					ProfileManager.delete(OTHER_PROFILE);
					ProfileManager.flushSaves();
				});
			}
		}
	}

	/** Everything that needs the world: the fixture, the narration checks, and A-3 in English and pseudo-language. */
	private static void checkInWorld(ClientGameTestContext context) {
		String key = context.computeOnClient(mc -> ProfileManager.key());
		// SET is attached to Everywhere and the first zone, so the set editor's Attached to panel has lines, Attach to…
		// has a choice, Everywhere's Sound Sets show the pinned Quick changes and a shared set, and Attach set… for the
		// second zone offers SET.
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
				.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true),
						SetEntry.of("r2", "minecraft:entity.villager.ambient").withVolume(30))).attach(SET.id()))
				.withZones(List.of(attached(zone("z1", "Trading hall", key), SET.id()),
						zone("z2", "Very long zone name for the bee farm by the river", key))), ws.library().put(SET))));
		// Recently played rows (FR-1.2), so the Sounds tab and the picker show their two-line rows: in a zone with a short
		// name and one with a long name (cut, and giving way to the ID in a narrow window), heard around the player, and
		// one heard earlier in the session ("Seen as", FR-3.2).
		context.runOnClient(mc -> recordRecentPlays());

		// A-4: every control on the Sounds tab, with the Recently played filter on and off, has something to read out,
		// and so do its search help, its Level pop-up and both steps of Add to set….
		List<String> silentSounds = context.computeOnClient(mc -> {
			List<String> silent = new ArrayList<>();
			for (boolean recentOnly : new boolean[] {true, false}) {
				SoundSculptorScreen screen = new SoundSculptorScreen();
				mc.gui.setScreen(screen);
				screen.selectTab(SoundSculptorScreen.TAB_SOUNDS);
				screen.sounds().setRecentOnly(recentOnly);
				silent.addAll(silentControls(screen));
			}
			SoundSculptorScreen screen = (SoundSculptorScreen) mc.gui.screen();
			check(!screen.sounds().list.children().isEmpty(), "with the filter off, every known sound is listed");
			screen.sounds().openHelp();
			silent.addAll(silentControls(screen));
			check(screen.sounds().helpList.getFocused() != null, "the search help takes focus, so arrows and Esc work in it");
			screen.closeOverlay();
			screen.sounds().openLevel(VILLAGER);
			silent.addAll(silentControls(screen));
			screen.closeOverlay();
			AddToSetScreen add = new AddToSetScreen(screen, VILLAGER, "Villager mumbles", () -> { });
			mc.gui.setScreen(add);
			silent.addAll(silentControls(add));
			add.chooseSet(SET.id());
			silent.addAll(silentControls(add));
			return silent;
		});
		check(silentSounds.isEmpty(), "controls without a label on the Sounds tab: " + silentSounds);
		context.runOnClient(mc -> SoundsPanel.resetRememberedState());

		// A-4 for the Sounds picker (FR-1.7): its checkboxes and Like this… in both filter modes, and the groups pop-up.
		List<String> silentPicker = context.computeOnClient(mc -> {
			List<String> silent = new ArrayList<>();
			for (boolean recentOnly : new boolean[] {true, false}) {
				SoundPickerScreen picker = new SoundPickerScreen(null, pickerDraft(), PICKER_SUBTITLE);
				mc.gui.setScreen(picker);
				picker.sounds().setRecentOnly(recentOnly);
				silent.addAll(silentControls(picker));
			}
			SoundPickerScreen picker = (SoundPickerScreen) mc.gui.screen();
			check(!picker.sounds().list.children().isEmpty(), "with the filter off, the picker lists every known sound");
			picker.sounds().openLikeThis(VILLAGER);
			silent.addAll(silentControls(picker));
			check(picker.sounds().likeThisList.getFocused() != null, "Like this… takes focus, so arrows, Space and Esc work in it");
			return silent;
		});
		check(silentPicker.isEmpty(), "controls without a label in the Sounds picker: " + silentPicker);
		context.runOnClient(mc -> SoundsPanel.resetRememberedState());

		// A-4: every control on the Sound Sets, Zones and Profiles tabs has something to read out.
		for (int tab : new int[] {SoundSculptorScreen.TAB_SETS, SoundSculptorScreen.TAB_ZONES, SoundSculptorScreen.TAB_PROFILES}) {
			List<String> silent = context.computeOnClient(mc -> {
				SoundSculptorScreen screen = new SoundSculptorScreen();
				mc.gui.setScreen(screen);
				screen.selectTab(tab);
				return silentControls(screen);
			});
			check(silent.isEmpty(), "controls without a label on tab " + tab + ": " + silent);
		}
		// A-4 for the sound trace's inline controls (FR-14.4) and its Volume panel.
		List<String> silentTrace = context.computeOnClient(mc -> {
			TraceScreen trace = new TraceScreen(null, "minecraft:entity.villager.ambient");
			mc.gui.setScreen(trace);
			List<String> silent = new ArrayList<>(silentControls(trace));
			trace.openVolume(QUICK_R2, QuickEntries.get(ProfileManager.profile(), "r2"));
			silent.addAll(silentControls(trace));
			return silent;
		});
		check(silentTrace.isEmpty(), "controls without a label in the sound trace: " + silentTrace);
		// FR-14.4: a shared set's line (SET on Everywhere and Trading hall) lists its places in its tooltip, and the
		// narrator reads them with the line's button; a "No entry matches" line's Add to Quick changes and its choice.
		List<String> silentSets = context.computeOnClient(mc -> {
			TraceScreen trace = new TraceScreen(null, COW);
			mc.gui.setScreen(trace);
			SculptList.Row line = trace.setRow(PlaceRef.EVERYWHERE, SET.id());
			check(line != null && line.tooltip() != null && line.tooltip().getString().startsWith("Used in Everywhere and Trading hall."),
					"the shared set's line lists its places, was " + (line == null || line.tooltip() == null ? null : line.tooltip().getString()));
			String read = NarrationCompat.collect(line.buttons().get(0)::updateNarration);
			check(read.contains("Used in Everywhere and Trading hall"), "the narrator reads the places, was " + read);
			List<String> silent = new ArrayList<>(silentControls(trace));
			// SET matches the cow on Everywhere and Quick changes does not: Everywhere's own line offers Add to Quick
			// changes, read with the line; its choice is labelled, and Esc returns focus to that button.
			SculptList.Row placeLine = trace.row(new TraceText.PlaceTarget(PlaceRef.EVERYWHERE));
			check(placeLine != null && placeLine.buttons().size() == 1
					&& placeLine.buttons().get(0).getMessage().getString().equals("Add to Quick changes"),
					"Everywhere's line offers Add to Quick changes when only a library set matches");
			String placeSaid = NarrationCompat.collect(placeLine.buttons().get(0)::updateNarration);
			check(placeSaid.contains(placeLine.narrationText().getString()) && placeSaid.contains("Add to Quick changes"),
					"Add to Quick changes on Everywhere's line is read with the line, was " + placeSaid);
			trace.openAddToQuickChanges(PlaceRef.EVERYWHERE);
			silent.addAll(silentControls(trace));
			trace.closeOverlay();
			check(trace.lines.getFocused() == placeLine && placeLine.getFocused() == placeLine.buttons().get(0),
					"closing the choice returns focus to Everywhere's Add to Quick changes");
			TraceScreen none = new TraceScreen(null, PIG);
			mc.gui.setScreen(none);
			silent.addAll(silentControls(none));
			SculptList.Row add = none.row(new TraceText.NoEntryTarget(PlaceRef.EVERYWHERE));
			check(add != null && add.buttons().get(0).getMessage().getString().equals("Add to Quick changes"),
					"the no-match line has a labelled Add to Quick changes");
			none.openAddToQuickChanges(PlaceRef.EVERYWHERE);
			silent.addAll(silentControls(none));
			check(none.getFocused() == none.addQuickMute, "the choice takes focus, so Tab, arrows and Enter work in it");
			return silent;
		});
		check(silentSets.isEmpty(), "controls without a label in the sound trace's set and Add to Quick changes lines: " + silentSets);
		// A-4 for the set editor, its Attach to… menu, the Quick changes editor and the entry editor.
		List<String> silentEditors = context.computeOnClient(mc -> {
			SetEditorScreen editor = SetEditorScreen.edit(null, SET.id());
			mc.gui.setScreen(editor);
			List<String> silent = new ArrayList<>(silentControls(editor));
			editor.attachedPanel().openAttachMenu();
			silent.addAll(silentControls(editor));
			SetEditorScreen quick = SetEditorScreen.forQuickChanges(null, PlaceRef.EVERYWHERE);
			mc.gui.setScreen(quick);
			silent.addAll(silentControls(quick));
			EntryEditorScreen entry = EntryEditorScreen.edit(null, SetDraft.of(SET), "e1");
			mc.gui.setScreen(entry);
			silent.addAll(silentControls(entry));
			return silent;
		});
		check(silentEditors.isEmpty(), "controls without a label in the set and entry editors: " + silentEditors);
		// FR-4.8: plain words in the entry editor list sounds with This sound and Like this; the Like this groups show
		// their counts. Everything is narrated with the sound or group, keeps to the window, and takes focus.
		List<String> findProblems = context.computeOnClient(mc -> {
			List<String> problems = new ArrayList<>();
			EntryEditorScreen entry = EntryEditorScreen.edit(null, SetDraft.of(SET), "e1");
			mc.gui.setScreen(entry);
			entry.patternField.setValue("cow");
			if (!entry.searching() || entry.matchList.findRow(COW) == null) {
				problems.add("the words should list " + COW);
				return problems;
			}
			SculptList.Row row = entry.matchList.findRow(COW);
			problems.addAll(silentControls(entry));
			problems.addAll(check(mc, entry, "entry editor search results"));
			for (SculptButton button : row.buttons()) {
				String said = NarrationCompat.collect(button::updateNarration);
				if (!said.contains("Cow") || !said.contains(button.getMessage().getString())) {
					problems.add("the " + button.getMessage().getString() + " button is not narrated with the sound: " + said);
				}
			}
			entry.showFamilies(COW, row.buttons().get(1));
			if (entry.familyButtons().isEmpty()) {
				problems.add("Like this should list groups");
			}
			for (AbstractWidget group : entry.familyButtons()) {
				String said = NarrationCompat.collect(group::updateNarration);
				if (!group.getMessage().getString().matches("minecraft:.* · \\d+ sounds?") || !said.contains(group.getMessage().getString())) {
					problems.add("a group is not narrated with its pattern and count: " + said);
				}
				if (group.getX() < 0 || group.getY() < 0 || group.getRight() > entry.width || group.getBottom() > entry.height) {
					problems.add("a Like this group is outside the window: " + describe(group));
				}
			}
			if (!entry.familyButtons().isEmpty() && entry.getFocused() == null) {
				problems.add("the Like this list should take focus, so Tab, arrows and Enter work");
			}
			return problems;
		});
		check(findProblems.isEmpty(), "finding a sound in the entry editor: " + findProblems);
		// A-4 for a place's Sound Sets (FR-8.6) and Attach set….
		List<String> silentPlaces = context.computeOnClient(mc -> {
			List<String> silent = new ArrayList<>();
			for (PlaceRef where : List.of(PlaceRef.EVERYWHERE, PlaceRef.zone("z1"))) {
				PlaceSetsScreen places = new PlaceSetsScreen(null, where);
				mc.gui.setScreen(places);
				silent.addAll(silentControls(places));
			}
			AttachSetScreen attach = new AttachSetScreen(null, PlaceRef.zone("z2"));
			mc.gui.setScreen(attach);
			silent.addAll(silentControls(attach));
			return silent;
		});
		check(silentPlaces.isEmpty(), "controls without a label in the Sound Sets of a place: " + silentPlaces);
		// A-4 for the import's rename screen (FR-11.5a).
		List<String> silentImport = context.computeOnClient(mc -> {
			SetRenameOnImportScreen rename = Screens.renameOnImport();
			mc.gui.setScreen(rename);
			return silentControls(rename);
		});
		check(silentImport.isEmpty(), "controls without a label in the import's rename screen: " + silentImport);
		// A-4 for the delete warning, the linked-save confirmation and the import's exact-match dialog.
		List<String> silentDialogs = context.computeOnClient(mc -> {
			List<String> silent = new ArrayList<>();
			for (Screens dialog : List.of(Screens.SET_DELETE, Screens.SET_SAVE_CONFIRM_MANY, Screens.IMPORT_EXACT_MATCH)) {
				Screen opened = dialog.open(mc);
				mc.gui.setScreen(opened);
				silent.addAll(silentControls(opened));
			}
			return silent;
		});
		check(silentDialogs.isEmpty(), "controls without a label in the dialogs: " + silentDialogs);

		// FR-4.11: a set used in 40 long-named zones. The delete warning lists every place; it scrolls so Delete and
		// Cancel stay on screen; the wheel, Up/Down and Page Up/Down move it; the narrator reads every place.
		context.runOnClient(mc -> {
			SculptDialog dialog = (SculptDialog) Screens.SET_DELETE_MANY.open(mc);
			mc.gui.setScreen(dialog);
			String message = dialog.message().getString();
			for (int i = 1; i <= MANY_ZONES; i++) {
				check(message.contains(manyZoneName(i)), "the warning lists every place, missing " + manyZoneName(i));
			}
			check(dialog.isScrollable(), "40 places do not fit at " + dialog.width + "x" + dialog.height + ", so the warning scrolls");
			for (SculptButton button : dialog.buttons()) {
				check(button.getY() >= 0 && button.getBottom() <= dialog.height, "the buttons stay on screen: " + button.getMessage().getString());
			}
			check(dialog.firstVisibleLine() == 0, "it starts at the top");
		});
		context.getInput().pressKey(InputConstants.KEY_PAGEDOWN);
		context.waitTicks(1);
		context.runOnClient(mc -> {
			SculptDialog dialog = (SculptDialog) mc.gui.screen();
			int last = dialog.lineCount() - dialog.visibleLineCount();
			check(dialog.firstVisibleLine() == Math.min(dialog.visibleLineCount(), last), "Page Down moves a screenful, at " + dialog.firstVisibleLine());
			int at = dialog.firstVisibleLine();
			dialog.mouseScrolled(dialog.width / 2.0, dialog.height / 2.0, 0, -1);
			check(dialog.firstVisibleLine() == Math.min(last, at + 3), "the wheel scrolls down 3 lines, at " + dialog.firstVisibleLine());
			dialog.scrollBy(1000);
			check(dialog.firstVisibleLine() == last, "it scrolls to the last line and stops there");
		});
		context.getInput().pressKey(InputConstants.KEY_UP);
		context.waitTicks(1);
		context.runOnClient(mc -> {
			SculptDialog dialog = (SculptDialog) mc.gui.screen();
			check(dialog.firstVisibleLine() == dialog.lineCount() - dialog.visibleLineCount() - 1, "Up moves one line");
			check(dialog.getNarrationMessage().getString().contains(manyZoneName(MANY_ZONES)), "the narrator reads every place");
			check(dialog.getFocused() == dialog.buttons().get(1), "Cancel still has focus");
		});
		context.takeScreenshot("a11y-delete-many-places");
		context.setScreen(() -> null);

		// A-3: every screen at every window size and GUI scale.
		List<String> english = layoutProblems(context);
		System.out.println("A11Y layout (English): " + english.size() + " problems" + (english.isEmpty() ? "" : "\n  " + String.join("\n  ", english)));
		check(english.isEmpty(), english.size() + " layout problems in English, first: " + (english.isEmpty() ? "" : english.get(0)));

		// Pseudo-language: the same check with every Sound Sculptor text about 40% longer.
		context.runOnClient(mc -> Language.inject(new PseudoLanguage(Language.getInstance())));
		List<String> pseudo;
		try {
			pseudo = layoutProblems(context);
		} finally {
			context.runOnClient(mc -> Language.inject(((PseudoLanguage) Language.getInstance()).english));
		}
		System.out.println("A11Y layout (pseudo-language, text 40% longer): " + pseudo.size() + " problems"
				+ (pseudo.isEmpty() ? "" : "\n  " + String.join("\n  ", pseudo)));
		// The Diagnostics timing lines ("… µs") show live numbers whose length changes from run to run, so whether they are
		// cut does too: they are reported above but not counted against the ceiling.
		long gated = pseudo.stream().filter(p -> !(p.contains("SETTINGS_DIAGNOSTICS: label cut off") && p.contains("µ"))).count();
		System.out.println("A11Y layout (pseudo-language) counted against the ceiling: " + gated + " of at most " + PSEUDO_CEILING);
		check(gated <= PSEUDO_CEILING, "pseudo-language layout problems rose to " + gated + " (ceiling " + PSEUDO_CEILING
				+ "): fix the new ones, or lower the ceiling when the count falls");
	}

	/** Every screen, tab and settings page, at every window size and GUI scale. */
	private static List<String> layoutProblems(ClientGameTestContext context) {
		List<String> problems = new ArrayList<>();
		int originalScale = context.computeOnClient(mc -> mc.options.guiScale().get());
		for (int[] window : WINDOWS) {
			context.getInput().resizeWindow(window[0], window[1]);
			context.waitTicks(2);
			for (int scale : GUI_SCALES) {
				context.runOnClient(mc -> {
					mc.options.guiScale().set(scale);
					mc.resizeGui();
				});
				context.waitTicks(1);
				String where = window[0] + "x" + window[1] + " scale " + (scale == 0 ? "auto" : scale);
				for (Screens screen : Screens.values()) {
					problems.addAll(context.computeOnClient(mc -> {
						Screen opened = screen.open(mc);
						mc.gui.setScreen(opened);
						if (screen.after != null) {
							screen.after.apply((SculptScreen) opened);
						}
						return check(mc, mc.gui.screen(), where + " " + screen.name());
					}));
				}
			}
		}
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			mc.options.guiScale().set(originalScale);
			mc.resizeGui();
		});
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(2);
		return problems;
	}

	/** The screens to check; each opens on its own, as the player would reach it. */
	private enum Screens {
		SOUNDS_RECENT(mc -> tab(SoundSculptorScreen.TAB_SOUNDS), s -> {
			((SoundSculptorScreen) s).sounds().setRecentOnly(true);
			return null;
		}),
		SOUNDS_ALL(mc -> tab(SoundSculptorScreen.TAB_SOUNDS), s -> {
			((SoundSculptorScreen) s).sounds().setRecentOnly(false);
			return null;
		}),
		SOUNDS_HELP(mc -> tab(SoundSculptorScreen.TAB_SOUNDS), s -> {
			((SoundSculptorScreen) s).sounds().openHelp();
			return null;
		}),
		SOUNDS_LEVEL(mc -> tab(SoundSculptorScreen.TAB_SOUNDS), s -> {
			((SoundSculptorScreen) s).sounds().openLevel(VILLAGER);
			return null;
		}),
		ADD_TO_SET(mc -> new AddToSetScreen((SculptScreen) tab(SoundSculptorScreen.TAB_SOUNDS), VILLAGER, "Villager mumbles", () -> { }), null),
		ADD_TO_SET_TARGETS(mc -> new AddToSetScreen((SculptScreen) tab(SoundSculptorScreen.TAB_SOUNDS), VILLAGER, "Villager mumbles", () -> { }), s -> {
			((AddToSetScreen) s).chooseSet(SET.id());
			return null;
		}),
		PICKER_RECENT(mc -> new SoundPickerScreen(null, pickerDraft(), PICKER_SUBTITLE), s -> {
			((SoundPickerScreen) s).sounds().setRecentOnly(true);
			return null;
		}),
		PICKER_ALL(mc -> new SoundPickerScreen(null, pickerDraft(), PICKER_SUBTITLE), s -> {
			((SoundPickerScreen) s).sounds().setRecentOnly(false);
			return null;
		}),
		PICKER_LIKE_THIS(mc -> new SoundPickerScreen(null, pickerDraft(), PICKER_SUBTITLE), s -> {
			((SoundPickerScreen) s).sounds().setRecentOnly(false);
			((SoundPickerScreen) s).sounds().openLikeThis(VILLAGER);
			return null;
		}),
		SETS(mc -> tab(SoundSculptorScreen.TAB_SETS), null),
		SET_NEW(mc -> SetNameScreen.create(null, new SetLibraryPanel(null)), null),
		SET_RENAME(mc -> SetNameScreen.rename(null, new SetLibraryPanel(null), SET.id()), null),
		SET_DELETE(mc -> SetDeleteDialog.create(null, ProfileManager.profile(), new LibraryOps.DeletePlan(SET.id(), SET.name(),
				List.of(PlaceRef.EVERYWHERE, PlaceRef.zone("z1"), PlaceRef.zone("z2")), List.of("Hypixel Skyblock", "Creative test world")),
				true, () -> { }), null),
		SET_DELETE_MANY(mc -> SetDeleteDialog.create(null, manyZonesProfile(), new LibraryOps.DeletePlan(SET.id(), SET.name(),
				manyPlaces(), List.of("Hypixel Skyblock", "Creative test world")), true, () -> { }), null),
		SET_SAVE_CONFIRM_MANY(mc -> {
			List<String> names = new ArrayList<>();
			for (int i = 1; i <= MANY_ZONES; i++) {
				names.add(manyZoneName(i));
			}
			return SetEditorScreen.linkedSaveDialog(null, SET.name(), SetEditorScreen.linkedMessage(names, 3, true), () -> { });
		}, null),
		ZONES(mc -> tab(SoundSculptorScreen.TAB_ZONES), null),
		PROFILES(mc -> tab(SoundSculptorScreen.TAB_PROFILES), null),
		PROFILE_RENAME(mc -> new soundsculptor.mc.ProfileRenameScreen(null, ProfileManager.key(), ProfileManager.profile()), null),
		PROFILE_IMPORT(mc -> new soundsculptor.mc.ProfileImportScreen(null), null),
		IMPORT_RENAME(mc -> renameOnImport(), null),
		IMPORT_RENAME_TAKEN(mc -> renameOnImport(), s -> {
			((SetRenameOnImportScreen) s).setName(SET.name());
			return null;
		}),
		IMPORT_EXACT_MATCH(mc -> ProfileTransfer.exactMatchDialog(null, "Very long export file name-2026-11-13.json",
				Message.of(SetImportPlan.EXACT_MATCH_PROBLEM, SET.name())), null),
		SETTINGS_GENERAL(mc -> settings(SoundSculptorScreen.SettingsPage.GENERAL), null),
		SETTINGS_ZONES(mc -> settings(SoundSculptorScreen.SettingsPage.ZONES), null),
		SETTINGS_DIAGNOSTICS(mc -> settings(SoundSculptorScreen.SettingsPage.DIAGNOSTICS), null),
		QUICK_ENTRY_EDITOR(mc -> EntryEditorScreen.forQuickEntry(null, PlaceRef.EVERYWHERE, "r1"), null),
		SET_EDITOR(mc -> SetEditorScreen.edit(null, SET.id()), null),
		SET_EDITOR_ATTACH(mc -> SetEditorScreen.edit(null, SET.id()), s -> {
			((SetEditorScreen) s).attachedPanel().openAttachMenu();
			return null;
		}),
		SET_EDITOR_QUICK(mc -> SetEditorScreen.forQuickChanges(null, PlaceRef.EVERYWHERE), null),
		ENTRY_EDITOR(mc -> EntryEditorScreen.edit(null, SetDraft.of(SET), "e1"), null),
		ZONE_EDITOR_NEW(mc -> ZoneEditorScreen.create(null), null),
		ZONE_EDITOR(mc -> ZoneEditorScreen.edit(null, ProfileManager.profile().zone("z1")), null),
		ZONE_EDITOR_BOX(mc -> ZoneEditorScreen.edit(null, ProfileManager.profile().zone("z1")), s -> {
			((ZoneEditorScreen) s).setShape(soundsculptor.core.editor.ZoneDraft.ShapeKind.BOX);
			return null;
		}),
		PLACE_SETS_EVERYWHERE(mc -> new PlaceSetsScreen(null, PlaceRef.EVERYWHERE), null),
		PLACE_SETS_ZONE(mc -> new PlaceSetsScreen(null, PlaceRef.zone("z2")), null),
		ATTACH_SET(mc -> new AttachSetScreen(null, PlaceRef.zone("z2")), null),
		SAVE_TO_LIBRARY(mc -> new PlaceSetsScreen(null, PlaceRef.EVERYWHERE), s -> {
			((PlaceSetsScreen) s).panel.openSaveToLibrary();
			return null;
		}),
		TRACE(mc -> new TraceScreen(null, "minecraft:entity.villager.ambient"), null),
		TRACE_VOLUME(mc -> new TraceScreen(null, "minecraft:entity.villager.ambient"), s -> {
			((TraceScreen) s).openVolume(QUICK_R2, QuickEntries.get(ProfileManager.profile(), "r2"));
			return null;
		}),
		TRACE_SHARED_SET(mc -> new TraceScreen(null, COW), null),
		TRACE_ADD_TO_QUICK(mc -> new TraceScreen(null, PIG), s -> {
			((TraceScreen) s).openAddToQuickChanges(PlaceRef.EVERYWHERE);
			return null;
		});

		final Function<Minecraft, Screen> opener;
		final Function<SculptScreen, Void> after;

		Screens(Function<Minecraft, Screen> opener, Function<SculptScreen, Void> after) {
			this.opener = opener;
			this.after = after;
		}

		Screen open(Minecraft mc) {
			return opener.apply(mc);
		}

		/** The import's rename screen for SET, the first of two clashes, refusing SET's own name as the library would. */
		static SetRenameOnImportScreen renameOnImport() {
			return new SetRenameOnImportScreen(null, SET, 1, 2, SET.name() + " (imported)",
					name -> name.strip().equalsIgnoreCase(SET.name()) ? Message.of(SetImportPlan.NAME_TAKEN) : null, name -> { });
		}

		private static Screen tab(int tab) {
			SoundSculptorScreen screen = new SoundSculptorScreen();
			Minecraft.getInstance().gui.setScreen(screen);
			screen.selectTab(tab);
			return screen;
		}

		private static Screen settings(SoundSculptorScreen.SettingsPage page) {
			SoundSculptorScreen screen = (SoundSculptorScreen) tab(SoundSculptorScreen.TAB_SETTINGS);
			screen.setSettingsPage(page);
			return screen;
		}
	}

	/** Controls outside the window, overlapping controls, and text cut off by its button or label. */
	private static List<String> check(Minecraft mc, Screen screen, String where) {
		List<String> problems = new ArrayList<>();
		Font font = mc.font;
		List<AbstractWidget> widgets = new ArrayList<>();
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget.visible) {
				widgets.add(widget);
			}
		}
		for (int i = 0; i < widgets.size(); i++) {
			AbstractWidget w = widgets.get(i);
			String name = describe(w);
			if (w.getX() < 0 || w.getY() < 0 || w.getRight() > screen.width || w.getBottom() > screen.height) {
				problems.add(where + ": " + name + " is outside the window");
			}
			for (int j = i + 1; j < widgets.size(); j++) {
				AbstractWidget o = widgets.get(j);
				if (w.getX() < o.getRight() && o.getX() < w.getRight() && w.getY() < o.getBottom() && o.getY() < w.getBottom()) {
					problems.add(where + ": " + name + " overlaps " + describe(o));
				}
			}
			// Icon buttons and color swatches show no text; their label is for the narrator and the tooltip.
			boolean textControl = (w instanceof AbstractButton && !(w instanceof SculptButton button && button.isIconButton())
					&& !(w instanceof soundsculptor.mc.ui.SculptColorPicker.Swatch)) || w instanceof AbstractSliderButton;
			if (textControl && font.width(w.getMessage()) + BUTTON_PADDING > w.getWidth()) {
				problems.add(where + ": text cut off in " + name);
			}
			if (w instanceof SculptList list) {
				problems.addAll(rowProblems(font, list, where));
			}
		}
		if (screen instanceof SculptScreen az && az.body() != null) {
			for (SculptScreen.Body.Label label : az.body().labels()) {
				String text = label.text().get().getString();
				if (font.width(text) > label.width()) {
					problems.add(where + ": label cut off \"" + text + "\"");
				}
				// A label that wraps or follows a wrapped one must still end above the footer.
				if (label.y() + font.lineHeight > az.body().bottom()) {
					problems.add(where + ": label below the body \"" + text + "\"");
				}
			}
		}
		return problems;
	}

	/**
	 * The controls and text inside a list's rows, placed as {@link SculptList.Row#layout} draws them: buttons that do not
	 * fit in the row or leave no room for text, button text cut off, a fixed-width column squeezed narrower than asked or
	 * too narrow for its text, and the rest column (a name, an ID, a pattern) cut while narrower than
	 * {@link SculptList.Row#REST_MIN_WIDTH}, too little to tell rows apart. Each problem is reported once per list and
	 * column, with the first row that shows it.
	 */
	private static List<String> rowProblems(Font font, SculptList list, String where) {
		java.util.Map<String, String> found = new java.util.LinkedHashMap<>();
		String in = where + ": list \"" + list.getMessage().getString() + "\"";
		for (SculptList.Row row : list.children()) {
			SculptList.Row.Layout layout = list.layout(row);
			String rowName = String.valueOf(row.key());
			List<SculptButton> buttons = row.buttons();
			int contentLeft = list.getRowLeft() + 2;
			if (!buttons.isEmpty() && layout.buttonX().get(0) < contentLeft) {
				found.putIfAbsent("buttons", in + ": row buttons do not fit (row " + rowName + ")");
			} else if (layout.textRight() < layout.textLeft()) {
				found.putIfAbsent("buttons", in + ": row buttons leave no room for text (row " + rowName + ")");
			}
			for (int i = 0; i < buttons.size(); i++) {
				SculptButton button = buttons.get(i);
				boolean text = !button.isIconButton();
				if (text && font.width(button.getMessage()) + BUTTON_PADDING > button.getWidth()) {
					found.putIfAbsent("button " + i, in + ": text cut off in row button \"" + button.getMessage().getString() + "\"");
				}
			}
			for (int i = 0; i < layout.columns().size(); i++) {
				SculptList.Row.Placed placed = layout.columns().get(i);
				String text = placed.column().text().get();
				int asked = placed.column().width();
				// A shrinking column (a zone's name) may be cut, and may give way down to its minimum.
				boolean shrinks = placed.column().shrinks();
				int least = shrinks ? Math.min(asked, SculptList.Row.SHRINK_MIN_WIDTH) : asked;
				boolean cut = font.width(text) > placed.width();
				if (asked > 0 && placed.width() < least) {
					found.putIfAbsent("squeezed " + i, in + ": column " + i + " gets " + placed.width() + " of its " + asked + " px (row " + rowName + ")");
				} else if (asked > 0 && cut && !shrinks) {
					found.putIfAbsent("cut " + i, in + ": column " + i + " cut off \"" + text + "\"");
				} else if (asked == 0 && cut && placed.width() < SculptList.Row.REST_MIN_WIDTH) {
					found.putIfAbsent("narrow " + i, in + ": column " + i + " only " + placed.width() + " px for \"" + text + "\"");
				}
			}
		}
		return new ArrayList<>(found.values());
	}

	private static String describe(AbstractWidget widget) {
		String text = widget.getMessage().getString();
		return widget.getClass().getSimpleName() + " \"" + (text.length() > 40 ? text.substring(0, 40) + "…" : text) + "\"";
	}

	/** Controls with nothing to read: an empty label, including list row buttons. */
	private static List<String> silentControls(Screen screen) {
		List<String> silent = new ArrayList<>();
		for (GuiEventListener child : screen.children()) {
			if (child instanceof SculptList list) {
				for (SculptList.Row row : list.children()) {
					for (SculptButton button : row.buttons()) {
						if (button.getMessage().getString().isBlank()) {
							silent.add("a button in row " + row.key());
						}
					}
				}
			} else if (child instanceof AbstractWidget widget && widget.getMessage().getString().isBlank()) {
				silent.add(widget.getClass().getSimpleName());
			}
		}
		return silent;
	}

	/** Mod Menu's config screen for Sound Sculptor, through its entrypoint (no compile-time Mod Menu classes here). */
	private static Screen modMenuScreen(Screen parent) {
		for (EntrypointContainer<Object> container : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
			if (!container.getProvider().getMetadata().getId().equals("soundsculptor")) {
				continue;
			}
			try {
				Object factory = container.getEntrypoint().getClass().getMethod("getModConfigScreenFactory").invoke(container.getEntrypoint());
				java.lang.reflect.Method create = factory.getClass().getMethod("create", Screen.class);
				create.setAccessible(true);
				Screen screen = (Screen) create.invoke(factory, parent);
				Minecraft.getInstance().gui.setScreen(screen);
				return screen;
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("Mod Menu entrypoint failed", e);
			}
		}
		throw new AssertionError("Sound Sculptor has no Mod Menu entrypoint");
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static List<String> labels(SculptScreen screen) {
		List<String> texts = new ArrayList<>();
		for (SculptScreen.Body.Label label : screen.body().labels()) {
			texts.add(label.text().get().getString());
		}
		return texts;
	}

	/** The villager in Trading hall, the cow in the long-named zone, the pig around the player; the stone step earlier. */
	private static void recordRecentPlays() {
		RecentSounds log = RecentLog.get().sounds();
		log.clear();
		long now = Util.getMillis();
		log.record("minecraft:block.stone.step", "block", true, 2.0, true, PlayStatus.PLAYING, null, now - RecentSounds.WINDOW_MS - 1000);
		log.record(VILLAGER, "neutral", true, 3.0, true, PlayStatus.PLAYING, "Trading hall", now);
		log.record(COW, "neutral", true, 10.0, true, PlayStatus.MUTED, "Very long zone name for the bee farm by the river", now);
		log.record(PIG, "neutral", false, 0.0, true, PlayStatus.BOOSTED, null, now);
	}

	/** SET with the villager and its group too, so the picker marks a row and a Like this group "In this set". */
	private static SetDraft pickerDraft() {
		List<SetEntry> entries = new ArrayList<>(SET.entries());
		entries.add(SetEntry.of("e2", VILLAGER));
		entries.add(SetEntry.of("e3", "minecraft:entity.villager.*"));
		return SetDraft.of(new SoundSet(SET.id(), SET.name(), entries));
	}

	private static Zone zone(String id, String name, String key) {
		return new Zone(id, name, true, key, "minecraft:overworld", new Point(0, -60, 0), new Shape.Cylinder(8, 16),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "*cow*").withVolume(40))));
	}

	private static Zone attached(Zone zone, String setId) {
		return zone.withSets(zone.sets().attach(setId));
	}

	/** A long zone name, like a player's own ("Automatic sugar cane farm by the north river, row 12"). */
	private static String manyZoneName(int i) {
		return "Automatic sugar cane farm by the north river, row " + i;
	}

	/** Everywhere and {@value #MANY_ZONES} long-named zones, all using SET. */
	private static List<PlaceRef> manyPlaces() {
		List<PlaceRef> places = new ArrayList<>(List.of(PlaceRef.EVERYWHERE));
		for (int i = 1; i <= MANY_ZONES; i++) {
			places.add(PlaceRef.zone("many-" + i));
		}
		return places;
	}

	/** A profile whose zones carry the names {@link #manyPlaces} are shown with. */
	private static Profile manyZonesProfile() {
		List<Zone> zones = new ArrayList<>();
		for (int i = 1; i <= MANY_ZONES; i++) {
			zones.add(attached(zone("many-" + i, manyZoneName(i), ProfileManager.key()), SET.id()));
		}
		return Profile.empty("Many zones").withZones(zones);
	}

	/** Every Sound Sculptor text through {@link PseudoLocale#pseudo}; everything else unchanged. */
	private static final class PseudoLanguage extends Language {
		final Language english;

		PseudoLanguage(Language english) {
			this.english = english;
		}

		@Override
		public String getOrDefault(String key, String fallback) {
			String text = english.getOrDefault(key, fallback);
			return key.startsWith("soundsculptor.") && english.has(key) ? PseudoLocale.pseudo(text) : text;
		}

		@Override
		public boolean has(String key) {
			return english.has(key);
		}

		@Override
		public boolean isDefaultRightToLeft() {
			return english.isDefaultRightToLeft();
		}

		@Override
		public FormattedCharSequence getVisualOrder(FormattedText text) {
			return english.getVisualOrder(text);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
