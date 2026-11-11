package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import audiozone.core.outline.OutlineMode;
import audiozone.core.zones.ZoneEdge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import audiozone.core.TestEnglish;

import org.junit.jupiter.api.Test;

/** PRD 7.5 file format for the profile settings, last joined and the boost confirm; 7.6 the v1 → v2 migration. */
class ProfileSettingsCodecTest {
	private static final TestEnglish EN = new TestEnglish();

	private static final Instant JOINED = Instant.parse("2026-11-09T13:37:46.250Z");

	private static Profile read(String json) throws ProfileFormatException {
		return ProfileCodec.fromJson(json);
	}

	private static String problem(String json) {
		return assertThrows(ProfileFormatException.class, () -> read(json)).problem().text(EN);
	}

	private static JsonObject written(Profile p) {
		return JsonParser.parseString(ProfileCodec.toJson(p)).getAsJsonObject();
	}

	@Test
	void everySettingRoundTrips() throws ProfileFormatException {
		for (ProfileSettings s : List.of(ProfileSettings.DEFAULTS,
				new ProfileSettings(false, false, OutlineMode.ALWAYS, ZoneEdge.fade(0.5)),
				new ProfileSettings(true, false, OutlineMode.NEVER, ZoneEdge.fade(32)))) {
			Profile p = Profile.empty("A").withSettings(s);
			assertEquals(p, read(ProfileCodec.toJson(p)));
		}
	}

	@Test
	void settingsUseTheNamesFromTheFileSketch() {
		JsonObject s = written(Profile.empty("A").withSettings(
				new ProfileSettings(false, false, OutlineMode.ALWAYS, ZoneEdge.fade(8)))).getAsJsonObject("settings");
		assertFalse(s.get("showDistance").getAsBoolean());
		assertFalse(s.get("cueDefault").getAsBoolean());
		assertEquals("always", s.get("outlines").getAsString());
		assertEquals("fade", s.getAsJsonObject("edge").get("mode").getAsString());
		assertEquals(8.0, s.getAsJsonObject("edge").get("fade").getAsDouble());
	}

	@Test
	void aHardEdgeIsWrittenWithoutAFadeDistance() {
		JsonObject edge = written(Profile.empty("A")).getAsJsonObject("settings").getAsJsonObject("edge");
		assertEquals("hard", edge.get("mode").getAsString());
		assertFalse(edge.has("fade"));
	}

	@Test
	void missingSettingsUseTheDefaults() throws ProfileFormatException {
		assertEquals(ProfileSettings.DEFAULTS, read("{\"schemaVersion\": 2, \"settings\": {}}").settings());
		assertEquals(ProfileSettings.DEFAULTS.withOutlines(OutlineMode.NEVER),
				read("{\"schemaVersion\": 2, \"settings\": {\"outlines\": \"never\"}}").settings());
		assertEquals(ProfileSettings.DEFAULTS.withDefaultEdge(ZoneEdge.fade(ZoneEdge.DEFAULT_FADE)),
				read("{\"schemaVersion\": 2, \"settings\": {\"edge\": {\"mode\": \"fade\"}}}").settings(),
				"a fade edge without a distance fades over the default 4 blocks");
	}

	@Test
	void badSettingsNameTheFieldInPlainWords() {
		assertEquals("Settings, field outlines: \"sometimes\" is not one of: always, editing, never.",
				problem("{\"schemaVersion\": 2, \"settings\": {\"outlines\": \"sometimes\"}}"));
		assertEquals("Settings, field edge mode: \"default\" is not one of: hard, fade.",
				problem("{\"schemaVersion\": 2, \"settings\": {\"edge\": {\"mode\": \"default\"}}}"));
		assertEquals("Settings, field fade: 40 is above the maximum 32.",
				problem("{\"schemaVersion\": 2, \"settings\": {\"edge\": {\"mode\": \"fade\", \"fade\": 40}}}"));
		assertEquals("Settings, field cueDefault: \"yes\" is not true or false.",
				problem("{\"schemaVersion\": 2, \"settings\": {\"cueDefault\": \"yes\"}}"));
	}

	@Test
	void lastJoinedAndBoostConfirmRoundTrip() throws ProfileFormatException {
		Profile p = Profile.empty("A").withLastJoined(JOINED).withBoostConfirmed(true);
		JsonObject json = written(p);
		assertEquals("2026-11-09T13:37:46.250Z", json.get("lastJoined").getAsString());
		assertTrue(json.get("boostConfirmed").getAsBoolean());
		assertEquals(p, read(ProfileCodec.toJson(p)));
	}

	@Test
	void neverJoinedIsOmittedAndReadsAsNull() throws ProfileFormatException {
		JsonObject json = written(Profile.empty("A"));
		assertFalse(json.has("lastJoined"));
		assertFalse(json.get("boostConfirmed").getAsBoolean());
		Profile p = read("{\"schemaVersion\": 2}");
		assertNull(p.lastJoined());
		assertFalse(p.boostConfirmed());
	}

	@Test
	void aBadDateIsRefused() {
		assertEquals("Field lastJoined: \"yesterday\" is not a date and time like 2026-11-09T13:37:46Z.",
				problem("{\"schemaVersion\": 2, \"lastJoined\": \"yesterday\"}"));
		assertEquals("Field boostConfirmed: 1 is not true or false.", problem("{\"schemaVersion\": 2, \"boostConfirmed\": 1}"));
	}

	@Test
	void filesAreWrittenAtSchemaVersion2() {
		assertEquals(2, Profile.SCHEMA_VERSION);
		assertEquals(2, written(Profile.empty("A")).get("schemaVersion").getAsInt());
		assertEquals(Profile.SCHEMA_VERSION, ProfileMigrations.CURRENT.target());
	}

	@Test
	void aVersion1FileIsBackedUpThenLoadsWithTheNewDefaults() throws ProfileFormatException {
		List<Integer> backedUp = new ArrayList<>();
		ProfileCodec.Read read = ProfileCodec.read(
				"{\"schemaVersion\": 1, \"name\": \"Old\", \"settings\": {\"showDistance\": false}, \"globalRules\": []}",
				backedUp::add);
		assertEquals(List.of(1), backedUp);
		assertEquals(ProfileSettings.DEFAULTS.withShowDistance(false), read.profile().settings());
		assertNull(read.profile().lastJoined());
		assertFalse(read.profile().boostConfirmed());
		assertTrue(read.warnings().isEmpty(), read.warnings().toString());
	}

	@Test
	void aVersion1FileThatAlreadyUsesAVersion2NameIsNotMisread() throws ProfileFormatException {
		// Version 1 readers ignored unknown fields, so a hand-edited v1 file could hold "boostConfirmed". The
		// migration drops it: the player confirmed nothing in the version that wrote the file.
		Profile p = read("{\"schemaVersion\": 1, \"boostConfirmed\": true}");
		assertFalse(p.boostConfirmed());
	}
}
