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
import soundsculptor.core.sets.SetImportPlan.Choice;
import soundsculptor.core.sets.SetImportPlan.Status;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-11.5a: each Sound Set in an import file is matched to the library by ID; FR-11.6: missing sets are skipped. */
class SetImportPlanTest {
	private static final TestEnglish EN = new TestEnglish();

	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals",
			List.of(SetEntry.of("e1", "*cow*").withVolume(40), SetEntry.of("e2", "*pig*").withMute(true)));
	private static final SoundSet FARM_MINE = FARM.withEntries(List.of(SetEntry.of("e1", "*cow*").withVolume(10)));
	private static final SoundSet RAIN = new SoundSet("set-rain", "Rain", List.of(SetEntry.of("e1", "*rain*").withVolume(30)));

	/** A library whose new IDs are predictable: set-000000000001, set-000000000002, ... */
	private static SetLibrary library(SoundSet... sets) {
		AtomicLong next = new AtomicLong();
		return new SetLibrary(List.of(sets), next::incrementAndGet);
	}

	private static Zone bubble(String id, PlaceSets sets) {
		return new Zone(id, "Bubble", true, "a.net", "minecraft:overworld", new Point(0, 0, 0), new Shape.Sphere(4), sets)
				.asBubble();
	}

	/** The file's profile: Everywhere has Quick changes and the farm set; a bubble attaches farm and rain. */
	private static final Profile FILE = Profile.empty("Friend")
			.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("q", "*bee*"))).attach("set-farm"))
			.withZones(List.of(bubble("b1", PlaceSets.EMPTY.attach("set-rain").attach("set-farm"))));

	private static List<String> ids(PlaceSets place) {
		return place.attached().stream().map(Attachment::setId).toList();
	}

	@Test
	void newSetIsAdded() {
		SetLibrary lib = library();
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Map.of("set-farm", Status.NEW, "set-rain", Status.NEW), plan.statusById());
		assertEquals(0, plan.missingRefs());
		SetImportPlan.Applied applied = plan.apply(FILE, List.of(FARM, RAIN), lib, Map.of());
		assertNull(applied.problem());
		assertEquals(FARM, applied.library().get("set-farm"), "added with its ID, name and entries");
		assertEquals(RAIN, applied.library().get("set-rain"));
		assertEquals(FILE, applied.profile(), "the file's references already point at the added sets");
	}

	@Test
	void newSetWhoseNameIsTakenGetsANumber() {
		// Library names are unique (FR-4.7): a new set whose name another library set has is numbered.
		SoundSet other = new SoundSet("set-other", "Farm animals", List.of());
		SetLibrary lib = library(other);
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(FARM), FILE, lib).apply(FILE, List.of(FARM), lib, Map.of());
		assertEquals("Farm animals (2)", applied.library().get("set-farm").name());
		assertEquals(other, applied.library().get("set-other"));
	}

	@Test
	void sameContentsIsNotDuplicated() {
		SetLibrary lib = library(FARM, RAIN);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Map.of("set-farm", Status.SAME, "set-rain", Status.SAME), plan.statusById());
		SetImportPlan.Applied applied = plan.apply(FILE, List.of(FARM, RAIN), lib, Map.of());
		assertSame(lib, applied.library(), "nothing is added or rewritten");
		assertEquals(FILE, applied.profile());
	}

	@Test
	void differentKeepBothAddsImportedCopyAndRewritesRefs() {
		SetLibrary lib = library(FARM_MINE, RAIN);
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib);
		assertEquals(Status.DIFFERENT, plan.statusById().get("set-farm"));
		assertEquals(Status.SAME, plan.statusById().get("set-rain"));

		// Keep both is the default: no choice given.
		SetImportPlan.Applied applied = plan.apply(FILE, List.of(FARM, RAIN), lib, Map.of());
		assertEquals(FARM_MINE, applied.library().get("set-farm"), "the library version is unchanged");
		SoundSet imported = applied.library().get("set-000000000001");
		assertNotNull(imported, "the file's version is added with a new ID");
		assertEquals("Farm animals (imported)", imported.name());
		assertEquals(FARM.entries(), imported.entries());
		assertEquals(3, applied.library().size());
		assertEquals(List.of(SoundSet.QUICK_ID, "set-000000000001"), ids(applied.profile().everywhere()));
		assertEquals(List.of("set-rain", "set-000000000001"), ids(applied.profile().zone("b1").sets()),
				"every reference is rewritten, in place, keeping the order");
		assertEquals(FILE.everywhere().quickChanges(), applied.profile().everywhere().quickChanges());
	}

	@Test
	void keepBothKeepsTheAttachmentsOnOrOff() {
		Profile off = FILE.withEverywhere(FILE.everywhere().setEnabled("set-farm", false));
		SetLibrary lib = library(FARM_MINE);
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(FARM), off, lib).apply(off, List.of(FARM), lib,
				Map.of("set-farm", Choice.KEEP_BOTH));
		assertFalse(applied.profile().everywhere().attached().get(1).enabled());
	}

	@Test
	void keepBothNameClashGetsNumberSuffix() {
		SoundSet earlier = new SoundSet("set-earlier", "Farm animals (imported)", List.of());
		SetLibrary lib = library(FARM_MINE, earlier);
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(FARM), FILE, lib).apply(FILE, List.of(FARM), lib, Map.of());
		assertEquals("Farm animals (imported) (2)", applied.library().get("set-000000000001").name());
		assertEquals(earlier, applied.library().get("set-earlier"));
	}

	@Test
	void keepBothOfALongNameStillFits() {
		SoundSet longFile = new SoundSet("set-long", "x".repeat(SoundSet.MAX_NAME_LENGTH), List.of(SetEntry.of("e1", "*a*")));
		SetLibrary lib = library(longFile.withEntries(List.of()));
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(longFile), FILE, lib).apply(FILE, List.of(longFile), lib, Map.of());
		String name = applied.library().get("set-000000000001").name();
		assertEquals(SoundSet.MAX_NAME_LENGTH, name.length());
		assertTrue(name.endsWith(" (imported)"), name);
	}

	@Test
	void useMineAttachesLibraryVersion() {
		SetLibrary lib = library(FARM_MINE, RAIN);
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib)
				.apply(FILE, List.of(FARM, RAIN), lib, Map.of("set-farm", Choice.USE_MINE));
		assertSame(lib, applied.library(), "the library is kept");
		assertEquals(FILE, applied.profile(), "the places attach the library's set-farm");
	}

	@Test
	void replaceOverwritesLibrary() {
		SetLibrary lib = library(FARM_MINE, RAIN);
		SetImportPlan.Applied applied = SetImportPlan.of(List.of(FARM, RAIN), FILE, lib)
				.apply(FILE, List.of(FARM, RAIN), lib, Map.of("set-farm", Choice.REPLACE));
		assertEquals(FARM, applied.library().get("set-farm"));
		assertEquals(2, applied.library().size());
		assertEquals(FILE, applied.profile());
	}

	@Test
	void missingRefIsDroppedAndCounted() {
		Profile file = FILE.withEverywhere(FILE.everywhere().attach("set-gone"));
		SetLibrary lib = library(FARM);
		// Neither set-gone nor set-rain is in the file's sets or in this library.
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), file, lib);
		assertEquals(2, plan.missingRefs(), "set-gone on Everywhere and set-rain on the bubble");
		SetImportPlan.Applied applied = plan.apply(file, List.of(FARM), lib, Map.of());
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
			many.add(new SoundSet("set-" + i, "Set " + i, List.of()));
		}
		many.set(0, FARM_MINE);
		SetLibrary full = library(many.toArray(SoundSet[]::new));
		SetImportPlan plan = SetImportPlan.of(List.of(FARM), FILE.withZones(List.of()), full);

		assertEquals("The library can hold up to 256 Sound Sets. This import adds 1, and there is room for 0.",
				plan.canApply(full, Map.of()).text(EN));
		SetImportPlan.Applied refused = plan.apply(FILE, List.of(FARM), full, Map.of());
		assertEquals(plan.canApply(full, Map.of()), refused.problem());
		assertSame(full, refused.library(), "nothing is added");
		assertSame(FILE, refused.profile());

		// Use mine or Replace add nothing, so they fit.
		assertNull(plan.canApply(full, Map.of("set-farm", Choice.USE_MINE)));
		assertNull(plan.canApply(full, Map.of("set-farm", Choice.REPLACE)));
	}

	@Test
	void aLibraryThatFilledUpSinceThePlanIsRefusedCleanly() {
		SetImportPlan plan = SetImportPlan.of(List.of(FARM, RAIN), FILE, library());
		List<SoundSet> many = new ArrayList<>();
		for (int i = 0; i < SetLibrary.MAX_SETS - 1; i++) {
			many.add(new SoundSet("set-" + i, "Set " + i, List.of()));
		}
		SetLibrary nearlyFull = library(many.toArray(SoundSet[]::new));
		SetImportPlan.Applied refused = plan.apply(FILE, List.of(FARM, RAIN), nearlyFull, Map.of());
		assertEquals("The library can hold up to 256 Sound Sets. This import adds 2, and there is room for 1.",
				refused.problem().text(EN));
		assertSame(nearlyFull, refused.library(), "no set is added, so nothing refers to a set that is not there");
	}
}
