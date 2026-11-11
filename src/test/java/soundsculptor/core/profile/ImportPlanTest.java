package soundsculptor.core.profile;

import static soundsculptor.core.profile.ProfileZonesTest.SERVER;
import static soundsculptor.core.profile.ProfileZonesTest.bubble;
import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.rules.Rule;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import soundsculptor.core.TestEnglish;

import org.junit.jupiter.api.Test;

/** PRD FR-11.5 and FR-11.7: what an import changes on the current server's profile. */
class ImportPlanTest {
	private static final TestEnglish EN = new TestEnglish();

	private static Zone fixedOn(String id, String worldKey) {
		return new Zone(id, "Z", true, worldKey, "minecraft:overworld", new Point(1, 2, 3), new Shape.Sphere(5), List.of());
	}

	private static final Profile CURRENT = new Profile("This server", new ProfileSettings(true),
			List.of(Rule.of("old", "x:old").withMute(true)), List.of(fixed("mine", new Shape.Sphere(4)), bubble("oldBubble")),
			java.util.Map.of("mine", 0x111111, "oldBubble", 0x222222));

	@Test
	void rulesBubblesAndSettingsReplaceThisServers() {
		Profile file = new Profile("Friend", new ProfileSettings(false), List.of(Rule.of("new", "x:new").withVolume(20)),
				List.of(bubble("b1"), fixedOn("theirs", "other.net")), java.util.Map.of("b1", 0x333333));
		ImportPlan plan = ImportPlan.of(CURRENT, file, SERVER);
		Profile result = plan.result();
		assertEquals("This server", result.name(), "the display name stays");
		assertEquals(file.globalRules(), result.globalRules());
		assertEquals(file.settings(), result.settings());
		assertEquals(List.of("mine", "b1"), result.zones().stream().map(Zone::id).toList());
		assertEquals(0x111111, result.colorOf("mine"));
		assertEquals(0x333333, result.colorOf("b1"));
		assertEquals(1, plan.ruleCount());
		assertEquals(1, plan.bubbleCount());
		assertEquals(0, plan.fixedImported());
		assertEquals(1, plan.fixedSkipped());
	}

	@Test
	void importingABoostAsksForTheBoostConfirmFirst() {
		// FR-5.4: the first time a profile goes above 100%, a dialog needs a confirm, however the boost arrives.
		Zone loudBubble = bubble("b1").withRules(List.of(Rule.of("z", "x:z").withVolume(150)));
		Profile global = new Profile("F", ProfileSettings.DEFAULTS, List.of(Rule.of("n", "x:n").withVolume(120)));
		Profile inZone = new Profile("F", ProfileSettings.DEFAULTS, List.of()).withZones(List.of(loudBubble));
		Profile quiet = new Profile("F", ProfileSettings.DEFAULTS, List.of(Rule.of("n", "x:n").withVolume(100)));
		assertTrue(ImportPlan.of(CURRENT, global, SERVER).needsBoostConfirm());
		assertTrue(ImportPlan.of(CURRENT, inZone, SERVER).needsBoostConfirm());
		assertFalse(ImportPlan.of(CURRENT, quiet, SERVER).needsBoostConfirm());
		assertFalse(ImportPlan.of(CURRENT.withBoostConfirmed(true), global, SERVER).needsBoostConfirm(),
				"this profile already confirmed");
	}

	@Test
	void fixedZonesForThisServerAreImportedWithExactCoordinates() {
		Zone backup = fixedOn("restored", SERVER);
		Profile file = new Profile("Backup", ProfileSettings.DEFAULTS, List.of(), List.of(backup, fixedOn("x", "other.net")),
				java.util.Map.of());
		ImportPlan plan = ImportPlan.of(CURRENT, file, SERVER);
		assertTrue(plan.result().zones().contains(backup));
		assertEquals(1, plan.fixedImported());
		assertEquals(1, plan.fixedSkipped());
	}

	@Test
	void worldKeysCompareWithoutCase() {
		Profile file = new Profile("B", ProfileSettings.DEFAULTS, List.of(), List.of(fixedOn("r", "Play.Example.NET")),
				java.util.Map.of());
		assertEquals(1, ImportPlan.of(CURRENT, file, SERVER).fixedImported());
	}

	@Test
	void anImportedFixedZoneWithTheSameIdReplacesTheExistingOne() {
		Zone newer = fixedOn("mine", SERVER);
		Profile file = new Profile("Backup", ProfileSettings.DEFAULTS, List.of(), List.of(newer), java.util.Map.of());
		Profile result = ImportPlan.of(CURRENT, file, SERVER).result();
		assertEquals(List.of(newer), result.zones());
		assertEquals(null, result.colorOf("mine"), "the imported zone brings its own (missing) color");
	}

	@Test
	void anImportedBubbleWhoseIdClashesWithAKeptZoneGetsANewId() {
		Profile file = new Profile("F", ProfileSettings.DEFAULTS, List.of(), List.of(bubble("mine")), java.util.Map.of("mine", 7));
		Profile result = ImportPlan.of(CURRENT, file, SERVER).result();
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
		Profile file = new Profile("F", ProfileSettings.DEFAULTS, List.of(), many, java.util.Map.of());
		ImportPlan plan = ImportPlan.of(CURRENT, file, SERVER);
		assertEquals(64, plan.result().zones().size());
		assertEquals(null, plan.problem());
		many.add(fixedOn("f63", SERVER));
		ImportPlan over = ImportPlan.of(CURRENT, new Profile("F", ProfileSettings.DEFAULTS, List.of(), many, java.util.Map.of()), SERVER);
		assertEquals("After the import this profile would have 65 zones; the maximum is 64.", over.problem().text(EN));
	}

	@Test
	void summaryListsWhatHappens() {
		Profile file = new Profile("F", ProfileSettings.DEFAULTS, List.of(Rule.of("a", "x:a"), Rule.of("b", "x:b")),
				List.of(bubble("b1"), fixedOn("t1", "other.net"), fixedOn("t2", "other.net"), fixedOn("t3", "other.net")),
				java.util.Map.of());
		ImportPlan plan = ImportPlan.of(CURRENT, file, SERVER);
		assertEquals(2, plan.ruleCount());
		assertEquals(3, plan.fixedSkipped());
	}
}
