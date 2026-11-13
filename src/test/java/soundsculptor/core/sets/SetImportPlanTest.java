package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetImportPlan.Status;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-11.5a: each Sound Set in an import file is compared with the library by its entries (identical, in the same
 * order; names ignored): the same set is used, an exact match under another ID cancels the import, a name clash needs a
 * new unique name, and anything else is added. FR-11.6: missing sets are skipped and counted.
 */
class SetImportPlanTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String TAKEN = "That name is already in use. Choose a unique name to import this set.";

	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals",
			List.of(SetEntry.of("e1", "*cow*").withVolume(40), SetEntry.of("e2", "*pig*").withMute(true)));
	/** The library's set-farm, changed since the export: same ID and name, other entries. */
	private static final SoundSet FARM_MINE = FARM.withEntries(List.of(SetEntry.of("e1", "*cow*").withVolume(10)));
	private static final SoundSet RAIN = new SoundSet("set-rain", "Rain", List.of(SetEntry.of("e1", "*rain*").withVolume(30)));
	private static final SoundSet RAIN_MINE = RAIN.withEntries(List.of());

	/** A library whose new IDs are predictable: set-000000000001, set-000000000002, ... */
	private static SetLibrary library(SoundSet... sets) {
		AtomicLong next = new AtomicLong();
		return new SetLibrary(List.of(sets), next::incrementAndGet);
	}

	private static Zone bubble(String id, PlaceSets sets) {
		return new Zone(id, "Bubble", true, "a.net", "minecraft:overworld", new Point(0, 0, 0), new Shape.Sphere(4), sets)
				.asBubble();
	}

	/** The file's profile: Everywhere has Quick changes and the farm set; a bubble attaches rain and farm. */
	private static final Profile FILE = Profile.empty("Friend")
			.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("q", "*bee*"))).attach("set-farm"))
			.withZones(List.of(bubble("b1", PlaceSets.EMPTY.attach("set-rain").attach("set-farm"))));

	private static List<String> ids(PlaceSets place) {
		return place.attached().stream().map(Attachment::setId).toList();
	}

	// ---- NEW ----

	@Test
	void newSetIsAdded() {
		SetLibrary lib = library();
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Map.of("set-farm", Status.NEW, "set-rain", Status.NEW), plan.statusById());
		assertEquals(0, plan.missingRefs());
		assertEquals(List.of(), plan.nameClashes());
		assertNull(plan.canApply(lib, Map.of()));
		SetImportPlan.Applied applied = plan.apply(FILE, lib, Map.of());
		assertNull(applied.problem());
		assertEquals(FARM, applied.library().get("set-farm"), "added with its ID, name and entries");
		assertEquals(RAIN, applied.library().get("set-rain"));
		assertEquals(FILE, applied.profile(), "the file's references already point at the added sets");
	}

	@Test
	void newSetWhoseIdIsTakenGetsANewIdAndTheReferencesFollow() {
		// The library's set-farm has another name and other entries: no clash, so the file's set is simply added.
		SoundSet barn = new SoundSet("set-farm", "Barn", List.of(SetEntry.of("e1", "*horse*")));
		SetLibrary lib = library(barn, RAIN);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Status.NEW, plan.statusById().get("set-farm"));
		SetImportPlan.Applied applied = plan.apply(FILE, lib, Map.of());
		assertNull(applied.problem());
		assertEquals(barn, applied.library().get("set-farm"), "the library set is unchanged");
		SoundSet added = applied.library().get("set-000000000001");
		assertEquals(new SoundSet("set-000000000001", "Farm animals", FARM.entries()), added);
		assertEquals(List.of(SoundSet.QUICK_ID, "set-000000000001"), ids(applied.profile().everywhere()));
		assertEquals(List.of("set-rain", "set-000000000001"), ids(applied.profile().zone("b1").sets()),
				"every reference is rewritten, in place, keeping the order");
		assertEquals(FILE.everywhere().quickChanges(), applied.profile().everywhere().quickChanges());
	}

	// ---- SAME ----

	@Test
	void sameIdAndEntriesIsTheSameSetWhateverItsName() {
		// Your own export imported into another world: the sets are already in the library (one renamed since).
		SoundSet renamed = FARM.withName("My farm");
		SetLibrary lib = library(renamed, RAIN);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Map.of("set-farm", Status.SAME, "set-rain", Status.SAME), plan.statusById());
		assertNull(plan.canApply(lib, Map.of()));
		SetImportPlan.Applied applied = plan.apply(FILE, lib, Map.of());
		assertSame(lib, applied.library(), "nothing is added or duplicated");
		assertEquals(FILE, applied.profile(), "the places attach the library sets");
	}

	// ---- EXACT_MATCH ----

	@Test
	void exactMatchUnderAnotherIdCancelsTheImport() {
		SoundSet mine = new SoundSet("set-mine", "Barnyard", FARM.entries());
		SetLibrary lib = library(mine);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Status.EXACT_MATCH, plan.statusById().get("set-farm"));
		assertEquals("set-mine", plan.matchById().get("set-farm"));
		assertEquals("The import failed because \"Barnyard\" already has exactly these entries.",
				plan.exactMatchProblem(lib).text(EN));
		assertEquals(plan.exactMatchProblem(lib), plan.canApply(lib, Map.of()));
		SetImportPlan.Applied refused = plan.apply(FILE, lib, Map.of());
		assertEquals(plan.exactMatchProblem(lib), refused.problem());
		assertSame(lib, refused.library(), "nothing is added, not even the new rain set");
		assertSame(FILE, refused.profile());
	}

	@Test
	void entriesInAnotherOrderAreNotAnExactMatch() {
		SoundSet reversed = new SoundSet("set-mine", "Barnyard", List.of(FARM.entries().get(1), FARM.entries().get(0)));
		assertEquals(Status.NEW, SetImportPlan.of(List.of(FARM), FILE, library(reversed)).statusById().get("set-farm"));
	}

	@Test
	void entryIdsAreNotCompared() {
		// The same entries in the same order, under other entry IDs (e.g. a set edited down and back up).
		List<SetEntry> reIded = List.of(FARM.entries().get(0).withId("e7"), FARM.entries().get(1).withId("e9"));
		SetLibrary other = library(new SoundSet("set-mine", "Barnyard", reIded));
		assertEquals(Status.EXACT_MATCH, SetImportPlan.of(List.of(FARM), FILE, other).statusById().get("set-farm"));
		SetLibrary same = library(new SoundSet("set-farm", "Farm animals", reIded));
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, same);
		assertEquals(Status.SAME, plan.statusById().get("set-farm"));
		assertSame(same, plan.apply(FILE, same, Map.of()).library(), "the library's own set is used");
	}

	@Test
	void entriesInAnotherOrderAreNotTheSameSet() {
		SoundSet reversed = new SoundSet("set-farm", "Farm animals", List.of(FARM.entries().get(1), FARM.entries().get(0)));
		assertEquals(Status.NAME_CLASH, SetImportPlan.of(List.of(FARM), FILE, library(reversed)).statusById().get("set-farm"),
				"same ID and name, but the entries are in another order");
	}

	@Test
	void anEntryThatDiffersInAnyPropertyIsNotTheSame() {
		SetEntry base = FARM.entries().get(0);
		for (SetEntry changed : List.of(base.withEnabled(false), base.withVolume(41), base.withMute(true),
				base.withLimit(new Limit(2, 1000, false)), base.withCue(true), new SetEntry("e1", true, SoundPattern.of("*calf*"),
						null, 40, null, null))) {
			SoundSet mine = new SoundSet("set-mine", "Barnyard", List.of(changed, FARM.entries().get(1)));
			assertEquals(Status.NEW, SetImportPlan.of(List.of(FARM), FILE, library(mine)).statusById().get("set-farm"), changed.toString());
		}
	}

	@Test
	void emptySetsAreNeverAnExactMatch() {
		SoundSet todo = new SoundSet("set-todo", "To do", List.of());
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-todo"));
		// Another empty set under another ID and name: added, not a reason to cancel.
		SetLibrary otherName = library(new SoundSet("set-other", "Later", List.of()));
		SetImportPlan plan = SetImportPlan.of(List.of(todo), file, otherName);
		assertEquals(Status.NEW, plan.statusById().get("set-todo"));
		assertNull(plan.canApply(otherName, Map.of()));
		assertEquals(todo, plan.apply(file, otherName, Map.of()).library().get("set-todo"));
		// With the same name: a name clash, not an exact match.
		SetLibrary sameName = library(new SoundSet("set-other", "To do", List.of()));
		assertEquals(Status.NAME_CLASH, SetImportPlan.of(List.of(todo), file, sameName).statusById().get("set-todo"));
		// The same ID: still the same set.
		SetLibrary sameId = library(todo.withName("Renamed"));
		assertEquals(Status.SAME, SetImportPlan.of(List.of(todo), file, sameId).statusById().get("set-todo"));
	}

	@Test
	void anExactMatchWinsOverANameClash() {
		// The file's set clashes by name with set-farm (other entries) and has exactly the entries of set-copy.
		SoundSet copy = new SoundSet("set-copy", "Farm copy", FARM.entries());
		SetLibrary lib = library(FARM_MINE, copy);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, lib);
		assertEquals(Status.EXACT_MATCH, plan.statusById().get("set-farm"));
		assertEquals(List.of(), plan.nameClashes(), "no name is asked for");
		assertEquals("The import failed because \"Farm copy\" already has exactly these entries.",
				plan.canApply(lib, Map.of("set-farm", "Farm animals (imported)")).text(EN), "naming it does not help");
		assertSame(lib, plan.apply(FILE, lib, Map.of("set-farm", "Farm animals (imported)")).library());
	}

	// ---- NAME_CLASH ----

	@Test
	void nameClashIsAddedUnderTheChosenNameWithANewId() {
		SetLibrary lib = library(FARM_MINE, RAIN);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Status.NAME_CLASH, plan.statusById().get("set-farm"));
		assertEquals("set-farm", plan.matchById().get("set-farm"));
		assertEquals(Status.SAME, plan.statusById().get("set-rain"));
		assertEquals(List.of(FARM), plan.nameClashes());
		assertEquals("Farm animals (imported)", plan.suggestedName("set-farm", lib, Map.of()));
		assertEquals(Map.of("set-farm", "Farm animals (imported)"), plan.suggestedNames(lib));
		assertEquals("Choose a new name for the Sound Set \"Farm animals\" to import it.", plan.canApply(lib, Map.of()).text(EN),
				"the import waits for a name");

		SetImportPlan.Applied applied = plan.apply(FILE, lib, plan.suggestedNames(lib));
		assertNull(applied.problem());
		assertEquals(FARM_MINE, applied.library().get("set-farm"), "the library set is unchanged");
		assertEquals(new SoundSet("set-000000000001", "Farm animals (imported)", FARM.entries()),
				applied.library().get("set-000000000001"));
		assertEquals(3, applied.library().size());
		assertEquals(List.of(SoundSet.QUICK_ID, "set-000000000001"), ids(applied.profile().everywhere()));
		assertEquals(List.of("set-rain", "set-000000000001"), ids(applied.profile().zone("b1").sets()));
	}

	@Test
	void nameClashIgnoresCaseAndAnotherNameCanBeTyped() {
		SoundSet shouting = new SoundSet("set-loud", "FARM ANIMALS", List.of());
		SetLibrary lib = library(shouting);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, lib);
		assertEquals(Status.NAME_CLASH, plan.statusById().get("set-farm"));
		SetImportPlan.Applied applied = plan.apply(FILE, lib, Map.of("set-farm", "  Pasture  "));
		assertEquals(new SoundSet("set-000000000001", "Pasture", FARM.entries()), applied.library().get("set-000000000001"),
				"a typed name is used, without the spaces at either end, and a name clash always gets a new ID");
		assertEquals(shouting, applied.library().get("set-loud"));
		assertFalse(applied.library().contains("set-farm"));
	}

	@Test
	void theSuggestedNameGetsANumberWhenImportedIsInUse() {
		// Review focus 4: "Farm animals (imported)" is already a library set, so the dialog fills in "(imported) (2)".
		SoundSet earlier = new SoundSet("set-earlier", "Farm animals (imported)", List.of());
		SetLibrary lib = library(FARM_MINE, earlier);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, lib);
		String suggested = plan.suggestedName("set-farm", lib, Map.of());
		assertEquals("Farm animals (imported) (2)", suggested);
		assertNull(plan.nameProblem("set-farm", suggested, lib, Map.of()));
		SetImportPlan.Applied applied = plan.apply(FILE, lib, Map.of("set-farm", suggested));
		assertEquals("Farm animals (imported) (2)", applied.library().get("set-000000000001").name());
		assertEquals(earlier, applied.library().get("set-earlier"));
	}

	@Test
	void theSuggestedNameAvoidsNamesChosenEarlierInThisImport() {
		SoundSet farm2 = new SoundSet("set-farm2", "farm animals", List.of(SetEntry.of("e1", "*goat*")));
		SetLibrary lib = library(FARM_MINE);
		Profile file = FILE.withEverywhere(FILE.everywhere().attach("set-farm2"));
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, farm2), file, lib);
		assertEquals(List.of(FARM, farm2), plan.nameClashes());
		assertEquals(Map.of("set-farm", "Farm animals (imported)", "set-farm2", "farm animals (imported) (2)"),
				plan.suggestedNames(lib));
		assertEquals("farm animals (imported) (2)",
				plan.suggestedName("set-farm2", lib, Map.of("set-farm", "Farm animals (imported)")));
	}

	@Test
	void theSuggestedNameOfALongNameStillFits() {
		SoundSet longFile = new SoundSet("set-long", "x".repeat(SoundSet.MAX_NAME_LENGTH), List.of(SetEntry.of("e1", "*a*")));
		SetLibrary lib = library(longFile.withEntries(List.of()));
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-long"));
		String name = SetImportPlan.of(List.of(longFile), file, lib).suggestedName("set-long", lib, Map.of());
		assertEquals(SoundSet.MAX_NAME_LENGTH, name.length());
		assertTrue(name.endsWith(" (imported)"), name);
	}

	@Test
	void aNameInTheLibraryIsRefused() {
		SetLibrary lib = library(FARM_MINE, RAIN);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, lib);
		assertEquals(TAKEN, plan.nameProblem("set-farm", "Farm animals", lib, Map.of()).text(EN));
		assertEquals(TAKEN, plan.nameProblem("set-farm", " rain ", lib, Map.of()).text(EN), "ignoring case and spaces");
		assertEquals(TAKEN, plan.canApply(lib, Map.of("set-farm", "Farm animals")).text(EN));
		SetImportPlan.Applied refused = plan.apply(FILE, lib, Map.of("set-farm", "Farm animals"));
		assertEquals(TAKEN, refused.problem().text(EN));
		assertSame(lib, refused.library(), "the import does not go ahead until the name is unique");
		assertNull(plan.nameProblem("set-farm", "Farm animals 2", lib, Map.of()));
	}

	@Test
	void aNameChosenForAnotherSetInThisImportIsRefused() {
		SetLibrary lib = library(FARM_MINE, RAIN_MINE);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(List.of(FARM, RAIN), plan.nameClashes());
		assertEquals(TAKEN, plan.nameProblem("set-rain", "SHARED", lib, Map.of("set-farm", "Shared")).text(EN));
		assertNull(plan.nameProblem("set-farm", "Shared", lib, Map.of("set-farm", "Shared")), "its own choice does not count");
		Map<String, String> both = Map.of("set-farm", "Shared", "set-rain", "shared");
		assertEquals(TAKEN, plan.canApply(lib, both).text(EN));
		assertSame(lib, plan.apply(FILE, lib, both).library());
		assertNull(plan.canApply(lib, Map.of("set-farm", "Shared", "set-rain", "Wet")));
	}

	@Test
	void aNameASetInTheFileBringsIsRefused() {
		// The file also brings a new set called "Bees": the renamed farm set cannot take that name.
		SoundSet bees = new SoundSet("set-bees", "Bees", List.of(SetEntry.of("e1", "*bee*")));
		SetLibrary lib = library(FARM_MINE);
		Profile file = FILE.withEverywhere(FILE.everywhere().attach("set-bees"));
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, bees), file, lib);
		assertEquals(Status.NEW, plan.statusById().get("set-bees"));
		assertEquals(TAKEN, plan.nameProblem("set-farm", "bees", lib, Map.of()).text(EN));
	}

	@Test
	void anEmptyOrTooLongNameIsRefused() {
		SetLibrary lib = library(FARM_MINE);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, lib);
		assertEquals("Enter a name.", plan.nameProblem("set-farm", "   ", lib, Map.of()).text(EN));
		assertEquals("The name can be at most 64 characters.",
				plan.nameProblem("set-farm", "x".repeat(SoundSet.MAX_NAME_LENGTH + 1), lib, Map.of()).text(EN));
		assertEquals("Enter a name.", plan.canApply(lib, Map.of("set-farm", "")).text(EN));
	}

	@Test
	void aRenamedSetKeepsItsAttachmentsOnOrOff() {
		Profile off = FILE.withEverywhere(FILE.everywhere().setEnabled("set-farm", false));
		SetLibrary lib = library(FARM_MINE);
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(FARM), off, lib).apply(off, lib, Map.of("set-farm", "Pasture"));
		Attachment farm = applied.profile().everywhere().attached().get(1);
		assertEquals("set-000000000001", farm.setId());
		assertFalse(farm.enabled());
	}

	@Test
	void twoIdenticalFileSetsNotInTheLibraryAreBothAdded() {
		// The file itself may hold two sets with the same entries; only the library is compared, so both are new.
		SoundSet twin = new SoundSet("set-twin", "Farm twin", FARM.entries());
		Profile file = FILE.withEverywhere(FILE.everywhere().attach("set-twin"));
		SetLibrary lib = library();
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, twin), file, lib);
		assertEquals(Map.of("set-farm", Status.NEW, "set-twin", Status.NEW), plan.statusById());
		SetImportPlan.Applied applied = plan.apply(file, lib, Map.of());
		assertNull(applied.problem());
		assertEquals(FARM, applied.library().get("set-farm"));
		assertEquals(twin, applied.library().get("set-twin"));
	}

	// ---- Only the sets the imported places use ----

	@Test
	void aSetNoImportedPlaceUsesIsIgnored() {
		// RAIN would cancel the import (an exact match) and BEES would ask for a name, but no imported place attaches them.
		SoundSet bees = new SoundSet("set-bees", "Bees", List.of(SetEntry.of("e1", "*bee*")));
		SetLibrary lib = library(new SoundSet("set-wet", "Wet", RAIN.entries()), new SoundSet("set-hive", "bees", List.of()));
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-farm"));
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN, bees), file, lib);
		assertEquals(Map.of("set-farm", Status.NEW), plan.statusById(), "only the attached set is compared");
		assertEquals(List.of(FARM), plan.fileSets());
		assertEquals(List.of(), plan.nameClashes());
		assertNull(plan.exactMatchProblem(lib));
		assertNull(plan.canApply(lib, Map.of()));
		SetImportPlan.Applied applied = plan.apply(file, lib, Map.of());
		assertEquals(3, applied.library().size(), "only the attached set is added");
		assertFalse(applied.library().contains("set-rain"));
		assertFalse(applied.library().contains("set-bees"));
	}

	// ---- Missing sets and limits ----

	@Test
	void missingRefIsDroppedAndCounted() {
		Profile file = FILE.withEverywhere(FILE.everywhere().attach("set-gone"));
		SetLibrary lib = library(FARM);
		// Neither set-gone nor set-rain is in the file's sets or in this library.
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), file, lib);
		assertEquals(2, plan.missingRefs(), "set-gone on Everywhere and set-rain on the bubble");
		SetImportPlan.Applied applied = plan.apply(file, lib, Map.of());
		assertEquals(List.of(SoundSet.QUICK_ID, "set-farm"), ids(applied.profile().everywhere()));
		assertEquals(List.of("set-farm"), ids(applied.profile().zone("b1").sets()));
	}

	@Test
	void aSetOnlyInTheLibraryIsNotMissing() {
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, library(RAIN));
		assertEquals(0, plan.missingRefs());
		assertEquals(Map.of("set-farm", Status.NEW), plan.statusById());
	}

	@Test
	void aFullLibraryIsAProblemNotAnException() {
		List<SoundSet> many = new ArrayList<>();
		for (int i = 0; i < SetLibrary.MAX_SETS; i++) {
			many.add(new SoundSet("set-" + i, "Set " + i, List.of(SetEntry.of("e1", "*s" + i + "*"))));
		}
		many.set(0, FARM_MINE);
		SetLibrary full = library(many.toArray(SoundSet[]::new));
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE.withZones(List.of()), full);
		Map<String, String> names = Map.of("set-farm", "Pasture");

		assertEquals("The library can hold up to 256 Sound Sets. This import adds 1, and there is room for 0.",
				plan.canApply(full, names).text(EN));
		assertEquals(plan.canApply(full, names), plan.canApply(full, Map.of()), "a full library is reported before any name is asked");
		SetImportPlan.Applied refused = plan.apply(FILE, full, names);
		assertEquals(plan.canApply(full, names), refused.problem());
		assertSame(full, refused.library(), "nothing is added");
		assertSame(FILE, refused.profile());

		// The same set adds nothing, so it fits.
		SetLibrary fullOfMine = library(many.subList(1, many.size()).toArray(SoundSet[]::new)).put(FARM);
		assertNull(SetImportPlan.of(List.of(FARM), FILE.withZones(List.of()), fullOfMine).canApply(fullOfMine, Map.of()));
	}

	@Test
	void aLibraryThatFilledUpSinceThePlanIsRefusedCleanly() {
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, library());
		List<SoundSet> many = new ArrayList<>();
		for (int i = 0; i < SetLibrary.MAX_SETS - 1; i++) {
			many.add(new SoundSet("set-" + i, "Set " + i, List.of(SetEntry.of("e1", "*s" + i + "*"))));
		}
		SetLibrary nearlyFull = library(many.toArray(SoundSet[]::new));
		SetImportPlan.Applied refused = plan.apply(FILE, nearlyFull, Map.of());
		assertEquals("The library can hold up to 256 Sound Sets. This import adds 2, and there is room for 1.",
				refused.problem().text(EN));
		assertSame(nearlyFull, refused.library(), "no set is added, so nothing refers to a set that is not there");
	}

	@Test
	void aLibraryThatChangedSinceThePlanIsComparedAgain() {
		// Planned against an empty library (NEW), applied after the player made a set with the same entries.
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE, library());
		SoundSet made = new SoundSet("set-made", "Made by hand", FARM.entries());
		SetLibrary later = library(made);
		SetImportPlan.Applied refused = plan.apply(FILE, later, Map.of());
		assertEquals("The import failed because \"Made by hand\" already has exactly these entries.", refused.problem().text(EN));
		assertSame(later, refused.library());
		assertNotNull(plan.exactMatchProblem(later));
	}
}
