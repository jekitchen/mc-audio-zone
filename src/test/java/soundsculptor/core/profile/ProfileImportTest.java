package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Shape;

import soundsculptor.core.TestEnglish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PRD FR-11.6: an import file is checked before anything changes; errors name the problem and the place. */
class ProfileImportTest {
	private static final TestEnglish EN = new TestEnglish();

	@TempDir
	Path dir;

	private static String problem(String text) {
		return assertThrows(ProfileFormatException.class, () -> ProfileImport.check(text)).problem().text(EN);
	}

	private static String zoneFile(String shape) {
		return "{\"schemaVersion\": 3, \"zones\": [" + fixedZone("z1", "{\"type\": \"sphere\", \"radius\": 5}") + ", "
				+ fixedZone("z2", "{\"type\": \"sphere\", \"radius\": 5}") + ", " + fixedZone("z3", shape) + "]}";
	}

	private static String fixedZone(String id, String shape) {
		return "{\"id\": \"" + id + "\", \"name\": \"N\", \"anchor\": {\"type\": \"fixed\", \"x\": 1, \"y\": 2, \"z\": 3,"
				+ " \"dimension\": \"minecraft:overworld\", \"worldKey\": \"a.net\"}, \"shape\": " + shape + "}";
	}

	@Test
	void aValidFileIsAccepted() throws ProfileFormatException {
		Profile p = Profile.empty("A").withZones(List.of(ProfileZonesTest.fixed("z1", new Shape.Sphere(9))));
		ExportCodec.Read checked = ProfileImport.check(ProfileCodec.toJson(p));
		assertEquals(p, checked.profile());
		assertTrue(checked.warnings().isEmpty(), checked.warnings().toString());
	}

	@Test
	void radiusAboveTheMaximumNamesTheZoneAndField() {
		assertEquals("Zone 3, field radius: 900 is above the maximum 256.",
				problem(zoneFile("{\"type\": \"sphere\", \"radius\": 900}")));
	}

	@Test
	void sizeBelowTheMinimumNamesTheZoneAndField() {
		assertEquals("Zone 3, field height: 0.5 is below the minimum 1.",
				problem(zoneFile("{\"type\": \"cylinder\", \"radius\": 3, \"height\": 0.5}")));
	}

	@Test
	void unknownShapeIsRefused() {
		assertEquals("Zone 3, field shape type: \"cone\" is not one of: sphere, box, cylinder.",
				problem(zoneFile("{\"type\": \"cone\"}")));
	}

	@Test
	void missingFieldIsNamed() {
		assertEquals("Zone 3, field width: missing.", problem(zoneFile("{\"type\": \"box\", \"height\": 3, \"depth\": 3}")));
	}

	@Test
	void wrongTypeIsNamed() {
		assertEquals("Zone 3, field radius: \"big\" is not a number.",
				problem(zoneFile("{\"type\": \"sphere\", \"radius\": \"big\"}")));
	}

	@Test
	void levelAboveTwoHundredNamesThePlaceAndTheEntry() {
		assertEquals("Everywhere, Quick changes, entry 2, level: 250 is above the maximum 200.", problem("{\"schemaVersion\": 3,"
				+ " \"everywhere\": {\"sets\": [{\"set\": \"quick\"}], \"quickChanges\": {\"id\": \"quick\", \"entries\": ["
				+ "{\"id\": \"a\", \"target\": \"x:a\"}, {\"id\": \"b\", \"target\": \"x:b\", \"volume\": 250}]}}}"));
	}

	@Test
	void zoneEntryProblemsNameTheZoneAndTheEntry() {
		String zone = "{\"id\": \"z1\", \"anchor\": {\"type\": \"bubble\"}, \"shape\": {\"type\": \"sphere\", \"radius\": 4},"
				+ " \"sets\": [{\"set\": \"quick\"}], \"quickChanges\": {\"id\": \"quick\", \"entries\": [{\"id\": \"r\", \"target\": \"a:b:c\"}]}}";
		String message = problem("{\"schemaVersion\": 3, \"zones\": [" + zone + "]}");
		assertEquals("Zone 1, Quick changes, entry 1, target: Use at most one :.", message);
	}

	@Test
	void invalidJsonGivesTheLocation() {
		assertEquals("The file is not valid JSON (line 1, column 9).", problem("{ this is not json"));
	}

	@Test
	void emptyFileIsRefused() {
		assertEquals("The file is empty.", problem(""));
		assertEquals("The file is empty.", problem("  \n"));
	}

	@Test
	void newerSchemaUsesTheImportWording() {
		assertEquals("This file was made with a newer Sound Sculptor. Update the mod to import it.",
				problem("{\"schemaVersion\": 4}"));
	}

	@Test
	void moreThanSixtyFourZonesIsRefused() {
		StringBuilder zones = new StringBuilder();
		for (int i = 0; i < 65; i++) {
			zones.append(i == 0 ? "" : ",").append(fixedZone("z" + i, "{\"type\": \"sphere\", \"radius\": 5}"));
		}
		assertEquals("The file has 65 zones; the maximum is 64.", problem("{\"schemaVersion\": 3, \"zones\": [" + zones + "]}"));
	}

	@Test
	void duplicateZoneIdsAreRefused() {
		String z = fixedZone("same", "{\"type\": \"sphere\", \"radius\": 5}");
		assertEquals("Zone 2, field id: \"same\" is already used by zone 1.",
				problem("{\"schemaVersion\": 3, \"zones\": [" + z + ", " + z + "]}"));
	}

	@Test
	void badColorIsNamed() {
		String zone = fixedZone("z1", "{\"type\": \"sphere\", \"radius\": 5}").replace("\"name\"", "\"color\": \"#12\", \"name\"");
		assertEquals("Zone 1, field color: \"#12\" is not a color like #E69F00.",
				problem("{\"schemaVersion\": 3, \"zones\": [" + zone + "]}"));
	}

	@Test
	void fadeOutOfRangeIsNamed() {
		String zone = fixedZone("z1", "{\"type\": \"sphere\", \"radius\": 5}").replace("\"name\"",
				"\"edge\": {\"mode\": \"fade\", \"fade\": 40}, \"name\"");
		assertEquals("Zone 1, field fade: 40 is above the maximum 32.",
				problem("{\"schemaVersion\": 3, \"zones\": [" + zone + "]}"));
	}

	@Test
	void unknownFieldsAreWarningsNotErrors() throws ProfileFormatException {
		String zone = fixedZone("z1", "{\"type\": \"sphere\", \"radius\": 5, \"spin\": 1}");
		ExportCodec.Read checked = ProfileImport.check("{\"schemaVersion\": 3, \"extra\": true,"
				+ " \"everywhere\": {\"sets\": [{\"set\": \"quick\"}], \"quickChanges\": {\"id\": \"quick\", \"entries\": ["
				+ "{\"id\": \"a\", \"target\": \"x:a\", \"loud\": 1}]}}, \"zones\": [" + zone + "]}");
		assertEquals(List.of("Unknown field \"extra\" was ignored.",
				"Everywhere, Quick changes, entry 1: unknown field \"loud\" was ignored.",
				"Zone 1: unknown field \"spin\" was ignored."), checked.warnings().stream().map(w -> w.text(EN)).toList());
	}

	@Test
	void fileLargerThanOneMegabyteIsRefusedBeforeReading() throws IOException {
		Path big = dir.resolve("big.json");
		Files.writeString(big, "{\"schemaVersion\": 3, \"name\": \"" + "x".repeat(1024 * 1024) + "\"}", StandardCharsets.UTF_8);
		assertEquals("The file is larger than 1 MB.",
				assertThrows(ProfileFormatException.class, () -> ProfileImport.check(big)).problem().text(EN));
	}

	@Test
	void textLargerThanOneMegabyteIsRefused() {
		assertEquals("The file is larger than 1 MB.", problem("{\"name\": \"" + "é".repeat(600 * 1024) + "\"}"));
	}

	@Test
	void fileOnDiskIsReadAsUtf8() throws IOException, ProfileFormatException {
		Path file = dir.resolve("p.json");
		Files.writeString(file, ProfileCodec.toJson(Profile.empty("Café")), StandardCharsets.UTF_8);
		assertEquals("Café", ProfileImport.check(file).profile().name());
	}

	@Test
	void anExportOnDiskBringsItsSets() throws IOException, ProfileFormatException {
		SoundSet farm = new SoundSet("set-farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)));
		Profile p = Profile.empty("A").withEverywhere(PlaceSets.EMPTY.attach("set-farm"));
		Path file = dir.resolve("export.json");
		Files.writeString(file, ExportCodec.toJson(p, "a.net", new SetLibrary(List.of(farm))), StandardCharsets.UTF_8);
		ExportCodec.Read read = ProfileImport.check(file);
		assertEquals(p, read.profile());
		assertEquals(List.of(farm), read.sets());
	}
}
