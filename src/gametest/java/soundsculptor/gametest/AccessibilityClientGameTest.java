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
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.AttachSetScreen;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.core.editor.SetDraft;
import soundsculptor.mc.SetDeleteDialog;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SetLibraryPanel;
import soundsculptor.mc.SetNameScreen;
import soundsculptor.mc.SetRenameOnImportScreen;
import soundsculptor.mc.ProfileTransfer;
import soundsculptor.core.Message;
import soundsculptor.core.sets.SetImportPlan;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.mc.compat.NarrationCompat;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
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
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * The automatable part of the release checklist (PRD 11.5) and FR-12.3:
 * <ul>
 *   <li>Opened outside a world (the title screen, or Mod Menu's config button when Mod Menu is installed:
 *   {@code -Pmodmenu=true}), the screen says to join a world instead of offering settings that would go nowhere.</li>
 *   <li>A-3 layout: every Sound Sculptor screen at 854 × 480 and 1920 × 1080, GUI scales 1 to 4 and Auto: each control
 *   inside the window, no two controls overlapping, no button or label text cut off.</li>
 *   <li>A-4 narration: every control on the Sound Sets, Zones and Profiles tabs, a place's Sound Sets and Attach set…,
 *   and the sound trace (a shared set's line, which reads its places, and Add to Quick changes with its choice),
 *   including list row buttons, has a label to read.</li>
 *   <li>Pseudo-language (PRD 9.3): the same layout check with every Sound Sculptor text about 40% longer, reported.</li>
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
	/** A sound nothing on Everywhere matches: the trace's Add to Quick changes line. */
	private static final String PIG = "minecraft:entity.pig.ambient";
	/** A library set for the Sound Sets tab, its name screen and its delete warning. */
	private static final SoundSet SET = new SoundSet("set-a11y", "Animals of the farm and the barn by the river",
			List.of(SetEntry.of("e1", "*cow*").withVolume(40)));

	@Override
	public void runTest(ClientGameTestContext context) {
		// Outside a world (title screen): per-world tabs explain instead of showing settings.
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		check(!context.computeOnClient(mc -> ProfileManager.isLoaded()), "no profile is loaded on the title screen");
		// The screen opens on the last used tab; this check is about Recently Played.
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_RECENT));
		check(context.computeOnClient(mc -> labels(main(mc)).stream().anyMatch(t -> t.startsWith("Join a world or server"))),
				"Recently Played should say to join a world");
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
			String key = context.computeOnClient(mc -> ProfileManager.key());
			// SET is attached to Everywhere and the first zone, so the set editor's Attached to panel has lines, Attach to…
			// has a choice, Everywhere's Sound Sets show the pinned Quick changes and a shared set, and Attach set… for the
			// second zone offers SET.
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "minecraft:entity.villager.*").withMute(true),
							SetEntry.of("r2", "minecraft:entity.villager.ambient").withVolume(30))).attach(SET.id()))
					.withZones(List.of(attached(zone("z1", "Trading hall", key), SET.id()),
							zone("z2", "Very long zone name for the bee farm by the river", key))), ws.library().put(SET))));

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

			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library().remove(SET.id())));
				ProfileManager.flushSaves();
			});
		}
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
		RECENT(mc -> tab(SoundSculptorScreen.TAB_RECENT), null),
		SEARCH(mc -> tab(SoundSculptorScreen.TAB_SEARCH), null),
		SEARCH_HELP(mc -> tab(SoundSculptorScreen.TAB_SEARCH), s -> {
			((SoundSculptorScreen) s).toggleSearchHelp();
			return null;
		}),
		SETS(mc -> tab(SoundSculptorScreen.TAB_SETS), null),
		SET_NEW(mc -> SetNameScreen.create(null, new SetLibraryPanel(null)), null),
		SET_RENAME(mc -> SetNameScreen.rename(null, new SetLibraryPanel(null), SET.id()), null),
		SET_DELETE(mc -> SetDeleteDialog.create(null, ProfileManager.profile(), new LibraryOps.DeletePlan(SET.id(), SET.name(),
				List.of(PlaceRef.EVERYWHERE, PlaceRef.zone("z1"), PlaceRef.zone("z2")), List.of("Hypixel Skyblock", "Creative test world")),
				true, () -> { }), null),
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
		}
		if (screen instanceof SculptScreen az && az.body() != null) {
			for (SculptScreen.Body.Label label : az.body().labels()) {
				String text = label.text().get().getString();
				if (font.width(text) > label.width()) {
					problems.add(where + ": label cut off \"" + text + "\"");
				}
			}
		}
		return problems;
	}

	private static String describe(AbstractWidget widget) {
		String text = widget.getMessage().getString();
		return widget.getClass().getSimpleName() + " \"" + (text.length() > 40 ? text.substring(0, 40) + "…" : text) + "\"";
	}

	/** Controls with nothing to read: an empty label, including list row buttons. */
	private static List<String> silentControls(SculptScreen screen) {
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

	private static Zone zone(String id, String name, String key) {
		return new Zone(id, name, true, key, "minecraft:overworld", new Point(0, -60, 0), new Shape.Sphere(8),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("r1", "*cow*").withVolume(40))));
	}

	private static Zone attached(Zone zone, String setId) {
		return zone.withSets(zone.sets().attach(setId));
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
