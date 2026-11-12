package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.Message;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PRD 7.6: each schema change adds one migration step; the original is backed up before migrating. Schema 3 is the
 * oldest read: earlier files came from test builds and are refused (PRD 7.5).
 */
class ProfileMigrationsTest {
	@TempDir
	Path dir;

	/** v3 → v4: renames "showDistance" to "distance" (a made-up change, to test the framework). */
	private static final ProfileMigrations.Step V3_TO_V4 = new ProfileMigrations.Step(3, root -> {
		JsonObject s = root.getAsJsonObject("settings");
		s.add("distance", s.remove("showDistance"));
	});
	/** v4 → v5: adds a field. */
	private static final ProfileMigrations.Step V4_TO_V5 = new ProfileMigrations.Step(4, root -> root.addProperty("added", true));

	private static JsonObject v3() {
		return JsonParser.parseString("{\"schemaVersion\": 3, \"settings\": {\"showDistance\": false}}").getAsJsonObject();
	}

	@Test
	void currentStepsReachTheCurrentSchema() {
		assertEquals(Profile.SCHEMA_VERSION, ProfileMigrations.CURRENT.target());
	}

	@Test
	void olderSchemaIsRefusedAsEarlierBuild() {
		for (int version : new int[] {1, 2}) {
			String text = "{\"schemaVersion\": " + version + ", \"name\": \"Old\", \"globalRules\": []}";
			ProfileFormatException e = assertThrows(ProfileFormatException.class, () -> ProfileCodec.fromJson(text));
			assertEquals(Message.of(ProfileMigrations.OLD_BUILD), e.problem(), "schema " + version);
		}
	}

	@Test
	void stepsRunInOrderAndSetTheVersion() throws ProfileFormatException {
		ProfileMigrations m = new ProfileMigrations(3, List.of(V3_TO_V4, V4_TO_V5));
		List<Integer> backedUp = new ArrayList<>();
		JsonObject out = m.migrate(v3(), backedUp::add);
		assertEquals(5, out.get("schemaVersion").getAsInt());
		assertFalse(out.getAsJsonObject("settings").getAsJsonObject().get("distance").getAsBoolean());
		assertTrue(out.get("added").getAsBoolean());
		assertEquals(List.of(3), backedUp, "one backup, of the original version, before any step runs");
	}

	@Test
	void aFileAtTheTargetIsNotTouchedOrBackedUp() throws ProfileFormatException {
		ProfileMigrations m = new ProfileMigrations(3, List.of(V3_TO_V4));
		JsonObject v4 = JsonParser.parseString("{\"schemaVersion\": 4}").getAsJsonObject();
		List<Integer> backedUp = new ArrayList<>();
		assertEquals(v4, m.migrate(v4, backedUp::add));
		assertTrue(backedUp.isEmpty());
	}

	@Test
	void startsFromTheFilesVersion() throws ProfileFormatException {
		ProfileMigrations m = new ProfileMigrations(3, List.of(V3_TO_V4, V4_TO_V5));
		JsonObject v4 = JsonParser.parseString("{\"schemaVersion\": 4}").getAsJsonObject();
		JsonObject out = m.migrate(v4, v -> { });
		assertEquals(5, out.get("schemaVersion").getAsInt());
		assertTrue(out.has("added"));
	}

	@Test
	void stepsMustBeContiguousFromTheOldest() {
		assertThrows(IllegalArgumentException.class, () -> new ProfileMigrations(3, List.of(V4_TO_V5)));
		assertThrows(IllegalArgumentException.class, () -> new ProfileMigrations(3, List.of(V4_TO_V5, V3_TO_V4)));
	}

	@Test
	void newerAndInvalidVersionsAreRefused() {
		ProfileMigrations m = new ProfileMigrations(3, List.of(V3_TO_V4));
		assertThrows(ProfileFormatException.class,
				() -> m.migrate(JsonParser.parseString("{\"schemaVersion\": 5}").getAsJsonObject(), v -> { }));
		assertThrows(ProfileFormatException.class,
				() -> m.migrate(JsonParser.parseString("{\"schemaVersion\": 0}").getAsJsonObject(), v -> { }));
	}

	@Test
	void aFailingBackupStopsTheMigration() {
		ProfileMigrations m = new ProfileMigrations(3, List.of(V3_TO_V4));
		JsonObject root = v3();
		assertThrows(ProfileFormatException.class, () -> m.migrate(root, v -> {
			throw new IOException("disk full");
		}));
		assertEquals(3, root.get("schemaVersion").getAsInt(), "nothing changed");
	}

	@Test
	void backupFileIsNamedAfterTheOldVersion() throws IOException {
		Path file = dir.resolve("play.example.net.json");
		Files.writeString(file, "{\"schemaVersion\": 3}");
		Path backup = ProfileMigrations.backup(file, 3);
		assertEquals("play.example.net.v3.bak", backup.getFileName().toString());
		assertEquals("{\"schemaVersion\": 3}", Files.readString(backup));
	}
}
