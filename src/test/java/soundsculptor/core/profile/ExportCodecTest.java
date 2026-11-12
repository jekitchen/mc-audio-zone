package soundsculptor.core.profile;

import static soundsculptor.core.profile.ProfileZonesTest.bubble;
import static soundsculptor.core.profile.ProfileZonesTest.fixed;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetCodec;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** PRD FR-11.4: an export carries every library set the profile attaches; FR-11.6: reading it checks every limit. */
class ExportCodecTest {
	private static final TestEnglish EN = new TestEnglish();

	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals",
			List.of(SetEntry.of("e1", "*cow*").withVolume(40), SetEntry.of("e2", "*pig*").withMute(true)));
	private static final SoundSet RAIN = new SoundSet("set-rain", "Rain", List.of(SetEntry.of("e1", "*rain*").withVolume(30)));
	private static final SoundSet UNUSED = new SoundSet("set-unused", "Unused", List.of());
	private static final SetLibrary LIB = new SetLibrary(List.of(FARM, RAIN, UNUSED));

	/** Everywhere: Quick changes and farm; a fixed zone: rain and farm; a bubble: rain. */
	private static final Profile PROFILE = Profile.empty("Export")
			.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("q", "*bee*"))).attach("set-farm"))
			.withZones(List.of(fixed("z1", new Shape.Sphere(5)).withSets(PlaceSets.EMPTY.attach("set-rain").attach("set-farm")),
					bubble("b1").withSets(PlaceSets.EMPTY.attach("set-rain"))));

	private static String problem(String text) {
		return assertThrows(ProfileFormatException.class, () -> ExportCodec.read(text)).problem().text(EN);
	}

	private static String withLibrarySets(JsonArray sets) {
		JsonObject root = JsonParser.parseString(ProfileCodec.toJson(Profile.empty("F"))).getAsJsonObject();
		root.add("librarySets", sets);
		return root.toString();
	}

	@Test
	void exportCarriesEveryAttachedSet() throws ProfileFormatException {
		String json = ExportCodec.toJson(PROFILE, "play.example.net", LIB);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonArray sets = root.getAsJsonArray("librarySets");
		assertEquals(List.of("set-farm", "set-rain"),
				sets.asList().stream().map(s -> s.getAsJsonObject().get("id").getAsString()).toList(),
				"each attached set once, in the order the places first attach it; Quick changes and unattached sets are not listed");
		assertEquals(SetCodec.toJsonObject(FARM), sets.get(0), "in the Sound Set file format");
		assertEquals("play.example.net", root.get("worldKey").getAsString());

		ExportCodec.Read read = ExportCodec.read(json);
		assertEquals(PROFILE, read.profile());
		assertEquals(List.of(FARM, RAIN), read.sets());
		assertTrue(read.warnings().isEmpty(), "librarySets is not an unknown field: " + read.warnings());
	}

	@Test
	void anAttachedSetTheLibraryLacksIsNotExported() throws ProfileFormatException {
		Profile p = PROFILE.withEverywhere(PROFILE.everywhere().attach("set-gone"));
		assertEquals(List.of(FARM, RAIN), ExportCodec.read(ExportCodec.toJson(p, null, LIB)).sets());
	}

	@Test
	void aPlainProfileFileHasNoSets() throws ProfileFormatException {
		ExportCodec.Read read = ExportCodec.read(ProfileCodec.toJson(PROFILE));
		assertEquals(PROFILE, read.profile());
		assertEquals(List.of(), read.sets());
	}

	@Test
	void exportReadRejects257Sets() {
		JsonArray sets = new JsonArray();
		for (int i = 0; i < SetLibrary.MAX_SETS + 1; i++) {
			sets.add(SetCodec.toJsonObject(new SoundSet("set-" + i, "Set " + i, List.of())));
		}
		assertEquals("The file has 257 Sound Sets; the maximum is 256.", problem(withLibrarySets(sets)));
		sets.remove(0);
		assertEquals(SetLibrary.MAX_SETS, assertDoesNotThrow(() -> ExportCodec.read(withLibrarySets(sets))).sets().size(),
				"256 is allowed");
	}

	@Test
	void exportReadRejectsLevel250WithLocation() {
		JsonObject farm = SetCodec.toJsonObject(FARM.withEntries(List.of(SetEntry.of("e1", "*a*"), SetEntry.of("e2", "*b*"),
				SetEntry.of("e3", "*c*").withVolume(200))));
		farm.getAsJsonArray("entries").get(2).getAsJsonObject().addProperty("volume", 250);
		JsonArray sets = new JsonArray();
		sets.add(farm);
		assertEquals("Sound Set 'Farm animals', entry 3, level: 250 is above the maximum 200.", problem(withLibrarySets(sets)));
	}

	@Test
	void aSetWithAReservedIdIsRefused() {
		for (String id : new String[] {"deleted", "con"}) {
			JsonArray sets = new JsonArray();
			sets.add(SetCodec.toJsonObject(new SoundSet(id, "Reserved", List.of())));
			assertEquals("Sound Set 'Reserved', id: \"" + id + "\" is a reserved name and can't be used as a Sound Set ID.",
					problem(withLibrarySets(sets)));
		}
	}

	@Test
	void aSetListedTwiceIsRefused() {
		JsonArray sets = new JsonArray();
		sets.add(SetCodec.toJsonObject(FARM));
		sets.add(SetCodec.toJsonObject(FARM.withName("Other")));
		assertEquals("Sound Set 2, field id: \"set-farm\" is already used by Sound Set 1.", problem(withLibrarySets(sets)));
	}

	@Test
	void librarySetsMustBeAListOfObjects() {
		JsonObject root = JsonParser.parseString(ProfileCodec.toJson(Profile.empty("F"))).getAsJsonObject();
		root.addProperty("librarySets", 5);
		assertEquals("Field librarySets: 5 is not a list.", problem(root.toString()));
		JsonArray sets = new JsonArray();
		sets.add(3);
		assertEquals("Sound Set 1: 3 is not an object.", problem(withLibrarySets(sets)));
	}

	@Test
	void theFileIsCheckedAsAWhole() {
		assertEquals("The file is empty.", problem(" "));
		assertEquals("This file was made with a newer Sound Sculptor. Update the mod to import it.", problem("{\"schemaVersion\": 4}"));
		assertEquals("The file is larger than 1 MB.", problem("{\"name\": \"" + "x".repeat(1024 * 1024) + "\"}"));
	}
}
