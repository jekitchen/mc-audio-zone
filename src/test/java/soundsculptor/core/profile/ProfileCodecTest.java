package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The profile file in schema 3 (PRD 7.5): each place lists its attached sets by ID, {@code "quick"} first when the place
 * has Quick changes, which are held inline with entries written as in a Sound Set file.
 */
class ProfileCodecTest {
	private static final TestEnglish EN = new TestEnglish();

	private static Profile sample() {
		SetEntry quickMute = SetEntry.of("q1", "minecraft:block.glass.break").withMute(true).withCue(true);
		SetEntry zoneEntry = SetEntry.of("q1", "minecraft:entity.*").withVolume(150)
				.withLimit(new Limit(3, 1000, true)).withEnabled(false);
		PlaceSets everywhere = quick(quickMute).attach("set-4b2e").setEnabled("quick", false);
		Zone hall = new Zone("z1", "Trading hall", true, "play.example.net", "minecraft:overworld", new Point(120.5, 64, -33.5),
				new Shape.Box(20, 8, 30), PlaceSets.EMPTY.attach("set-4b2e").attach("set-9aa0").setEnabled("set-9aa0", false));
		Zone tower = new Zone("z2", "Tower", true, "play.example.net", "minecraft:the_nether", new Point(0, 70, 0),
				new Shape.Cylinder(8, 16), quick(zoneEntry));
		return new Profile("play.example.net", ProfileSettings.DEFAULTS, everywhere, List.of(hall, tower),
				Map.of("z1", 0xE69F00));
	}

	private static String sets(String place, String list) {
		return "{\"schemaVersion\": 3, \"everywhere\": " + place.replace("SETS", list) + "}";
	}

	private static String problem(String text) {
		ProfileFormatException e = assertThrows(ProfileFormatException.class, () -> ProfileCodec.fromJson(text));
		return e.problem().text(EN);
	}

	@Test
	void schema3RoundTrip() throws ProfileFormatException {
		Profile p = sample();
		String json = ProfileCodec.toJson(p, "play.example.net");
		assertEquals(p, ProfileCodec.fromJson(json));

		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		assertEquals(3, root.get("schemaVersion").getAsInt());
		assertFalse(root.has("globalRules"));
		JsonObject everywhere = root.getAsJsonObject("everywhere");
		assertEquals("quick", everywhere.getAsJsonArray("sets").get(0).getAsJsonObject().get("set").getAsString());
		assertFalse(everywhere.getAsJsonArray("sets").get(0).getAsJsonObject().get("enabled").getAsBoolean());
		assertEquals("set-4b2e", everywhere.getAsJsonArray("sets").get(1).getAsJsonObject().get("set").getAsString());
		JsonObject quick = everywhere.getAsJsonObject("quickChanges");
		assertEquals("quick", quick.get("id").getAsString());
		JsonObject entry = quick.getAsJsonArray("entries").get(0).getAsJsonObject();
		assertEquals("minecraft:block.glass.break", entry.get("target").getAsString(), "entries are written as in set files");
		assertFalse(entry.has("pattern"));
		JsonObject zone = root.getAsJsonArray("zones").get(0).getAsJsonObject();
		assertEquals(2, zone.getAsJsonArray("sets").size());
		assertFalse(zone.has("quickChanges"));
		assertFalse(zone.has("rules"));
	}

	@Test
	void aPlaceWithoutSetsReadsAsEmpty() throws ProfileFormatException {
		Profile p = ProfileCodec.fromJson("{\"schemaVersion\": 3, \"name\": \"A\"}");
		assertEquals(PlaceSets.EMPTY, p.everywhere());
		Profile q = ProfileCodec.fromJson("{\"schemaVersion\": 3, \"everywhere\": {\"sets\": []}}");
		assertEquals(PlaceSets.EMPTY, q.everywhere());
	}

	@Test
	void quickNotFirstIsRejected() {
		String text = sets("{\"sets\": SETS, \"quickChanges\": {\"id\": \"quick\", \"entries\": []}}",
				"[{\"set\": \"set-a\"}, {\"set\": \"quick\"}]");
		assertEquals("Everywhere, field sets: \"quick\" can only be first.", problem(text));
	}

	@Test
	void quickAndQuickChangesGoTogether() {
		assertEquals("Everywhere, field quickChanges: missing.",
				problem(sets("{\"sets\": SETS}", "[{\"set\": \"quick\"}]")));
		assertEquals("Everywhere, field quickChanges: there is no \"quick\" first in sets.",
				problem(sets("{\"sets\": SETS, \"quickChanges\": {\"id\": \"quick\", \"entries\": []}}", "[]")));
	}

	@Test
	void aSetListedTwiceIsRejected() {
		assertEquals("Everywhere, field sets: \"set-a\" is listed twice.",
				problem(sets("{\"sets\": SETS}", "[{\"set\": \"set-a\"}, {\"set\": \"set-a\"}]")));
	}

	@Test
	void thirtyThreeSetsInAPlaceIsRejected() throws ProfileFormatException {
		List<String> items = new ArrayList<>();
		for (int i = 0; i < PlaceSets.MAX_SETS; i++) {
			items.add("{\"set\": \"set-" + i + "\"}");
		}
		String quickFirst = "[{\"set\": \"quick\"}, " + String.join(", ", items) + "]";
		String place = "{\"sets\": SETS, \"quickChanges\": {\"id\": \"quick\", \"entries\": []}}";
		assertEquals(PlaceSets.MAX_SETS, ProfileCodec.fromJson(sets(place, quickFirst)).everywhere().librarySetCount(),
				"32 library sets plus Quick changes is allowed");

		items.add("{\"set\": \"set-32\"}");
		String zone = "{\"schemaVersion\": 3, \"zones\": [{\"id\": \"z1\", \"anchor\": {\"type\": \"fixed\", \"x\": 0, \"y\": 0,"
				+ " \"z\": 0, \"dimension\": \"minecraft:overworld\", \"worldKey\": \"a.net\"}, \"shape\": {\"type\": \"cylinder\","
				+ " \"radius\": 8, \"height\": 16}, \"sets\": [" + String.join(", ", items) + "]}]}";
		assertEquals("Zone 1, field sets: 33 Sound Sets is more than the maximum 32.", problem(zone));
	}

	/** A zone in the file, fixed in a.net, with the given anchor type and shape. */
	private static String zoneFile(String anchorType, String shape) {
		return "{\"schemaVersion\": 3, \"zones\": [{\"id\": \"z1\", \"anchor\": {\"type\": \"fixed\", \"x\": 1, \"y\": 2,"
				+ " \"z\": 3, \"dimension\": \"minecraft:overworld\", \"worldKey\": \"a.net\"}, \"shape\": {\"type\": \"box\","
				+ " \"width\": 4, \"height\": 4, \"depth\": 4}}, {\"id\": \"z2\", \"anchor\": {\"type\": \"" + anchorType + "\","
				+ " \"x\": 1, \"y\": 2, \"z\": 3, \"dimension\": \"minecraft:overworld\", \"worldKey\": \"a.net\"}, \"shape\": "
				+ shape + "}]}";
	}

	@Test
	void aSphereShapeIsRefusedWithItsPlace() {
		// Sphere shapes were dropped (owner decision 2026-11-13): a file that still has one is invalid, like any value
		// out of spec, and the message says where.
		assertEquals("Zone 2, field shape type: \"sphere\" is not a supported shape.",
				problem(zoneFile("fixed", "{\"type\": \"sphere\", \"radius\": 8}")));
	}

	@Test
	void aBubbleAnchorIsRefusedWithItsPlace() {
		// Personal bubble zones were dropped (owner decision 2026-11-13): every zone has fixed coordinates.
		assertEquals("Zone 2, field anchor type: \"bubble\" is not a supported anchor.",
				problem(zoneFile("bubble", "{\"type\": \"box\", \"width\": 4, \"height\": 4, \"depth\": 4}")));
	}

	@Test
	void otherUnknownShapesAndAnchorsStillListTheChoices() {
		assertEquals("Zone 2, field shape type: \"cone\" is not one of: box, cylinder.",
				problem(zoneFile("fixed", "{\"type\": \"cone\"}")));
		assertEquals("Zone 2, field anchor type: \"moving\" is not one of: fixed.",
				problem(zoneFile("moving", "{\"type\": \"box\", \"width\": 4, \"height\": 4, \"depth\": 4}")));
		assertEquals("Zone 2, field shape type: 3 is not one of: box, cylinder.",
				problem(zoneFile("fixed", "{\"type\": 3}")));
	}

	@Test
	void quickChangesEntriesAreCheckedLikeASetFile() {
		String bad = sets("{\"sets\": SETS, \"quickChanges\": {\"id\": \"quick\", \"entries\": [{\"id\": \"q1\","
				+ " \"target\": \"x:a\", \"volume\": 250}]}}", "[{\"set\": \"quick\"}]");
		assertEquals("Everywhere, Quick changes, entry 1, level: 250 is above the maximum 200.", problem(bad));

		StringBuilder many = new StringBuilder();
		for (int i = 0; i <= SoundSet.MAX_ENTRIES; i++) {
			many.append(i == 0 ? "" : ", ").append("{\"id\": \"q").append(i).append("\", \"target\": \"x:a\"}");
		}
		String full = sets("{\"sets\": SETS, \"quickChanges\": {\"id\": \"quick\", \"entries\": [" + many + "]}}",
				"[{\"set\": \"quick\"}]");
		assertEquals("Everywhere, Quick changes has 513 entries; the maximum is 512.", problem(full));
	}

	@Test
	void theQuickChangesIdMustBeQuick() {
		String text = sets("{\"sets\": SETS, \"quickChanges\": {\"id\": \"set-a\", \"entries\": []}}", "[{\"set\": \"quick\"}]");
		assertTrue(problem(text).contains("\"set-a\" is not \"quick\""), problem(text));
	}
}
