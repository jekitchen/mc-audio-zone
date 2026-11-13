package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static soundsculptor.core.profile.ProfileZonesTest.SERVER;
import static soundsculptor.core.profile.ProfileZonesTest.bubble;
import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetImportPlan;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import soundsculptor.core.TestEnglish;

import org.junit.jupiter.api.Test;

/** PRD FR-11.5 and FR-11.7: what an import changes on the current server's profile. */
class ImportPlanTest {
	private static final TestEnglish EN = new TestEnglish();

	private static Zone fixedOn(String id, String worldKey) {
		return new Zone(id, "Z", true, worldKey, "minecraft:overworld", new Point(1, 2, 3), new Shape.Sphere(5), PlaceSets.EMPTY);
	}

	private static final Profile CURRENT = new Profile("This server", new ProfileSettings(true),
			quick(List.of(SetEntry.of("old", "x:old").withMute(true))), List.of(fixed("mine", new Shape.Sphere(4)), bubble("oldBubble")),
			java.util.Map.of("mine", 0x111111, "oldBubble", 0x222222));

	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)));
	private static final SoundSet RAIN = new SoundSet("set-rain", "Rain", List.of(SetEntry.of("e1", "*rain*").withVolume(30)));

	/** Imports a plain profile file (no library sets) into an empty library. */
	private static ImportPlan plan(Profile current, Profile file) {
		return ImportPlan.of(current, new ExportCodec.Read(file, List.of(), List.of()), SetLibrary.EMPTY, SERVER, Map.of());
	}

	private static List<String> ids(PlaceSets place) {
		return place.attached().stream().map(Attachment::setId).toList();
	}

	@Test
	void fixedZonesOtherServerSkippedWithSets() {
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-farm"))
				.withZones(List.of(fixedOn("theirs", "other.net").withSets(PlaceSets.EMPTY.attach("set-rain").attach("set-gone")),
						bubble("b1").withSets(PlaceSets.EMPTY.attach("set-rain"))));
		ImportPlan plan = ImportPlan.of(CURRENT, new ExportCodec.Read(file, List.of(FARM, RAIN), List.of()), SetLibrary.EMPTY,
				SERVER, Map.of());
		assertNull(plan.problem());
		assertEquals(1, plan.fixedSkipped());
		assertEquals(List.of("mine", "b1"), plan.result().zones().stream().map(Zone::id).toList());
		assertEquals(List.of("set-farm"), ids(plan.result().everywhere()));
		assertEquals(List.of("set-rain"), ids(plan.result().zone("b1").sets()));
		assertEquals(2, plan.setsAdded(), "every set in the file is added to the library (FR-11.5)");
		assertEquals(FARM, plan.library().get("set-farm"));
		assertEquals(RAIN, plan.library().get("set-rain"));
		assertEquals(0, plan.setsMissing(), "a set missing only in a skipped zone is not counted");
		assertNull(plan.missingNote());
	}

	@Test
	void missingSetsAreSkippedAndCounted() {
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-gone").attach("set-farm"));
		ImportPlan plan = ImportPlan.of(CURRENT, new ExportCodec.Read(file, List.of(), List.of()), new SetLibrary(List.of(FARM)),
				SERVER, Map.of());
		assertEquals(List.of("set-farm"), ids(plan.result().everywhere()), "set-farm is the library's; set-gone is dropped");
		assertEquals(1, plan.setsMissing());
		assertEquals(0, plan.setsAdded());
		assertEquals("1 Sound Set not found, skipped", plan.missingNote().text(EN));
		assertEquals(1, plan.setCount());
		Profile two = file.withEverywhere(file.everywhere().attach("set-lost"));
		assertEquals("2 Sound Sets not found, skipped", ImportPlan.of(CURRENT, new ExportCodec.Read(two, List.of(), List.of()),
				new SetLibrary(List.of(FARM)), SERVER, Map.of()).missingNote().text(EN));
	}

	@Test
	void aNameClashWaitsForANameThenAddsTheImportedSet() {
		SoundSet mine = FARM.withEntries(List.of());
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-farm"));
		ExportCodec.Read read = new ExportCodec.Read(file, List.of(FARM), List.of());
		SetLibrary lib = new SetLibrary(List.of(mine));
		ImportPlan waiting = ImportPlan.of(CURRENT, read, lib, SERVER, Map.of());
		assertEquals(SetImportPlan.Status.NAME_CLASH, waiting.sets().statusById().get("set-farm"));
		assertEquals("Choose a new name for the Sound Set \"Farm animals\" to import it.", waiting.problem().text(EN));
		assertEquals(CURRENT, waiting.result(), "nothing changes until the name is chosen");
		assertEquals(lib, waiting.library());

		ImportPlan plan = ImportPlan.of(CURRENT, read, lib, SERVER, waiting.sets().suggestedNames(lib));
		assertNull(plan.problem());
		String importedId = ids(plan.result().everywhere()).get(0);
		assertEquals("Farm animals (imported)", plan.library().get(importedId).name());
		assertEquals(mine, plan.library().get("set-farm"), "the library set is unchanged");
		assertEquals(1, plan.setsAdded());
	}

	@Test
	void anExactMatchUnderAnotherIdCancelsAndNothingChanges() {
		SoundSet same = new SoundSet("set-mine", "My farm", FARM.entries());
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-farm"))
				.withZones(List.of(bubble("b1")));
		SetLibrary lib = new SetLibrary(List.of(same));
		ImportPlan plan = ImportPlan.of(CURRENT, new ExportCodec.Read(file, List.of(FARM, RAIN), List.of()), lib, SERVER, Map.of());
		assertEquals("The import failed because \"My farm\" already has exactly these entries.", plan.problem().text(EN));
		assertEquals(CURRENT, plan.result());
		assertEquals(lib, plan.library());
		assertEquals(0, plan.setsAdded());
	}

	@Test
	void ownExportIntoAnotherWorldLinksTheSets() {
		// The export of another world on this computer: its sets are the library's own, so they are attached, not copied.
		SetLibrary lib = new SetLibrary(List.of(FARM, RAIN));
		Profile other = Profile.empty("Other world").withEverywhere(PlaceSets.EMPTY.attach("set-farm"))
				.withZones(List.of(bubble("b1").withSets(PlaceSets.EMPTY.attach("set-rain"))));
		ExportCodec.Read read = parse(ExportCodec.toJson(other, "world:other", lib));
		ImportPlan plan = ImportPlan.of(CURRENT, read, lib, SERVER, Map.of());
		assertNull(plan.problem());
		assertEquals(Map.of("set-farm", SetImportPlan.Status.SAME, "set-rain", SetImportPlan.Status.SAME), plan.sets().statusById());
		assertEquals(lib, plan.library(), "nothing is duplicated");
		assertEquals(0, plan.setsAdded());
		assertEquals(List.of("set-farm"), ids(plan.result().everywhere()));
		assertEquals(List.of("set-rain"), ids(plan.result().zone("b1").sets()));
	}

	private static ExportCodec.Read parse(String json) {
		try {
			return ExportCodec.read(json);
		} catch (ProfileFormatException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void aLibraryThatWouldOverflowIsAProblemAndNothingChanges() {
		List<SoundSet> many = new java.util.ArrayList<>();
		for (int i = 0; i < SetLibrary.MAX_SETS; i++) {
			many.add(new SoundSet("set-" + i, "Set " + i, List.of()));
		}
		SetLibrary full = new SetLibrary(many);
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-farm"));
		ImportPlan plan = ImportPlan.of(CURRENT, new ExportCodec.Read(file, List.of(FARM), List.of()), full, SERVER, Map.of());
		assertEquals("The library can hold up to 256 Sound Sets. This import adds 1, and there is room for 0.",
				plan.problem().text(EN));
		assertEquals(CURRENT, plan.result());
		assertEquals(full, plan.library());
	}

	@Test
	void boostInAnImportedLibrarySetAsksForTheBoostConfirm() {
		SoundSet loud = new SoundSet("set-loud", "Loud", List.of(SetEntry.of("e1", "*bell*").withVolume(150)));
		Profile file = Profile.empty("Friend").withEverywhere(PlaceSets.EMPTY.attach("set-loud"));
		assertTrue(ImportPlan.of(CURRENT, new ExportCodec.Read(file, List.of(loud), List.of()), SetLibrary.EMPTY, SERVER, Map.of())
				.needsBoostConfirm(), "the boost is in the set the import adds to the library");
	}

	@Test
	void rulesBubblesAndSettingsReplaceThisServers() {
		Profile file = new Profile("Friend", new ProfileSettings(false), quick(List.of(SetEntry.of("new", "x:new").withVolume(20))),
				List.of(bubble("b1"), fixedOn("theirs", "other.net")), java.util.Map.of("b1", 0x333333));
		ImportPlan plan = plan(CURRENT, file);
		Profile result = plan.result();
		assertEquals("This server", result.name(), "the display name stays");
		assertEquals(file.everywhere().quickEntries(), result.everywhere().quickEntries());
		assertEquals(file.settings(), result.settings());
		assertEquals(List.of("mine", "b1"), result.zones().stream().map(Zone::id).toList());
		assertEquals(0x111111, result.colorOf("mine"));
		assertEquals(0x333333, result.colorOf("b1"));
		assertEquals(1, plan.setCount(), "Everywhere's Quick changes");
		assertEquals(1, plan.bubbleCount());
		assertEquals(0, plan.fixedImported());
		assertEquals(1, plan.fixedSkipped());
	}

	@Test
	void importingABoostAsksForTheBoostConfirmFirst() {
		// FR-5.4: the first time a profile goes above 100%, a dialog needs a confirm, however the boost arrives.
		Zone loudBubble = bubble("b1").withSets(quick(List.of(SetEntry.of("z", "x:z").withVolume(150))));
		Profile global = new Profile("F", ProfileSettings.DEFAULTS, quick(List.of(SetEntry.of("n", "x:n").withVolume(120))));
		Profile inZone = new Profile("F", ProfileSettings.DEFAULTS, PlaceSets.EMPTY).withZones(List.of(loudBubble));
		Profile quiet = new Profile("F", ProfileSettings.DEFAULTS, quick(List.of(SetEntry.of("n", "x:n").withVolume(100))));
		assertTrue(plan(CURRENT, global).needsBoostConfirm());
		assertTrue(plan(CURRENT, inZone).needsBoostConfirm());
		assertFalse(plan(CURRENT, quiet).needsBoostConfirm());
		assertFalse(plan(CURRENT.withBoostConfirmed(true), global).needsBoostConfirm(),
				"this profile already confirmed");
	}

	@Test
	void fixedZonesForThisServerAreImportedWithExactCoordinates() {
		Zone backup = fixedOn("restored", SERVER);
		Profile file = new Profile("Backup", ProfileSettings.DEFAULTS, PlaceSets.EMPTY, List.of(backup, fixedOn("x", "other.net")),
				java.util.Map.of());
		ImportPlan plan = plan(CURRENT, file);
		assertTrue(plan.result().zones().contains(backup));
		assertEquals(1, plan.fixedImported());
		assertEquals(1, plan.fixedSkipped());
	}

	@Test
	void worldKeysCompareWithoutCase() {
		Profile file = new Profile("B", ProfileSettings.DEFAULTS, PlaceSets.EMPTY, List.of(fixedOn("r", "Play.Example.NET")),
				java.util.Map.of());
		assertEquals(1, plan(CURRENT, file).fixedImported());
	}

	@Test
	void anImportedFixedZoneWithTheSameIdReplacesTheExistingOne() {
		Zone newer = fixedOn("mine", SERVER);
		Profile file = new Profile("Backup", ProfileSettings.DEFAULTS, PlaceSets.EMPTY, List.of(newer), java.util.Map.of());
		Profile result = plan(CURRENT, file).result();
		assertEquals(List.of(newer), result.zones());
		assertEquals(null, result.colorOf("mine"), "the imported zone brings its own (missing) color");
	}

	@Test
	void anImportedBubbleWhoseIdClashesWithAKeptZoneGetsANewId() {
		Profile file = new Profile("F", ProfileSettings.DEFAULTS, PlaceSets.EMPTY, List.of(bubble("mine")), java.util.Map.of("mine", 7));
		Profile result = plan(CURRENT, file).result();
		assertEquals(List.of("mine", "mine-2"), result.zones().stream().map(Zone::id).toList());
		assertEquals(0x111111, result.colorOf("mine"));
		assertEquals(7, result.colorOf("mine-2"));
	}

	@Test
	void tooManyZonesAfterImportIsAProblem() {
		List<Zone> many = new java.util.ArrayList<>();
		for (int i = 0; i < 63; i++) {
			many.add(fixedOn("f" + i, SERVER));
		}
		Profile file = new Profile("F", ProfileSettings.DEFAULTS, PlaceSets.EMPTY, many, java.util.Map.of());
		ImportPlan plan = plan(CURRENT, file);
		assertEquals(64, plan.result().zones().size());
		assertEquals(null, plan.problem());
		many.add(fixedOn("f63", SERVER));
		ImportPlan over = plan(CURRENT, new Profile("F", ProfileSettings.DEFAULTS, PlaceSets.EMPTY, many, java.util.Map.of()));
		assertEquals("After the import this profile would have 65 zones; the maximum is 64.", over.problem().text(EN));
	}

	@Test
	void summaryListsWhatHappens() {
		Profile file = new Profile("F", ProfileSettings.DEFAULTS, quick(List.of(SetEntry.of("a", "x:a"), SetEntry.of("b", "x:b"))),
				List.of(bubble("b1"), fixedOn("t1", "other.net"), fixedOn("t2", "other.net"), fixedOn("t3", "other.net")),
				java.util.Map.of());
		ImportPlan plan = plan(CURRENT, file);
		assertEquals(1, plan.setCount());
		assertEquals(3, plan.fixedSkipped());
	}
}
