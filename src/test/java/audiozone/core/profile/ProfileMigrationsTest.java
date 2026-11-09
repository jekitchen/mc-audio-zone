package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PRD 7.6: each schema change adds one migration step; the original is backed up before migrating. */
class ProfileMigrationsTest {
	@TempDir
	Path dir;

	/** v1 → v2: renames "showDistance" to "distance" (a made-up change, to test the framework). */
	private static final ProfileMigrations.Step V1_TO_V2 = new ProfileMigrations.Step(1, root -> {
		JsonObject s = root.getAsJsonObject("settings");
		s.add("distance", s.remove("showDistance"));
	});
	/** v2 → v3: adds a field. */
	private static final ProfileMigrations.Step V2_TO_V3 = new ProfileMigrations.Step(2, root -> root.addProperty("added", true));

	private static JsonObject v1() {
		return JsonParser.parseString("{\"schemaVersion\": 1, \"settings\": {\"showDistance\": false}}").getAsJsonObject();
	}

	@Test
	void currentSchemaHasNoStepsYet() {
		assertEquals(Profile.SCHEMA_VERSION, ProfileMigrations.CURRENT.target());
	}

	@Test
	void stepsRunInOrderAndSetTheVersion() throws ProfileFormatException {
		ProfileMigrations m = new ProfileMigrations(List.of(V1_TO_V2, V2_TO_V3));
		List<Integer> backedUp = new ArrayList<>();
		JsonObject out = m.migrate(v1(), backedUp::add);
		assertEquals(3, out.get("schemaVersion").getAsInt());
		assertFalse(out.getAsJsonObject("settings").getAsJsonObject().get("distance").getAsBoolean());
		assertTrue(out.get("added").getAsBoolean());
		assertEquals(List.of(1), backedUp, "one backup, of the original version, before any step runs");
	}

	@Test
	void aFileAtTheTargetIsNotTouchedOrBackedUp() throws ProfileFormatException {
		ProfileMigrations m = new ProfileMigrations(List.of(V1_TO_V2));
		JsonObject v2 = JsonParser.parseString("{\"schemaVersion\": 2}").getAsJsonObject();
		List<Integer> backedUp = new ArrayList<>();
		assertEquals(v2, m.migrate(v2, backedUp::add));
		assertTrue(backedUp.isEmpty());
	}

	@Test
	void startsFromTheFilesVersion() throws ProfileFormatException {
		ProfileMigrations m = new ProfileMigrations(List.of(V1_TO_V2, V2_TO_V3));
		JsonObject v2 = JsonParser.parseString("{\"schemaVersion\": 2}").getAsJsonObject();
		JsonObject out = m.migrate(v2, v -> { });
		assertEquals(3, out.get("schemaVersion").getAsInt());
		assertTrue(out.has("added"));
	}

	@Test
	void stepsMustBeContiguousFromOne() {
		assertThrows(IllegalArgumentException.class, () -> new ProfileMigrations(List.of(V2_TO_V3)));
		assertThrows(IllegalArgumentException.class, () -> new ProfileMigrations(List.of(V2_TO_V3, V1_TO_V2)));
	}

	@Test
	void newerAndInvalidVersionsAreRefused() {
		ProfileMigrations m = new ProfileMigrations(List.of(V1_TO_V2));
		assertThrows(ProfileFormatException.class,
				() -> m.migrate(JsonParser.parseString("{\"schemaVersion\": 3}").getAsJsonObject(), v -> { }));
		assertThrows(ProfileFormatException.class,
				() -> m.migrate(JsonParser.parseString("{\"schemaVersion\": 0}").getAsJsonObject(), v -> { }));
	}

	@Test
	void aFailingBackupStopsTheMigration() {
		ProfileMigrations m = new ProfileMigrations(List.of(V1_TO_V2));
		JsonObject root = v1();
		assertThrows(ProfileFormatException.class, () -> m.migrate(root, v -> {
			throw new IOException("disk full");
		}));
		assertEquals(1, root.get("schemaVersion").getAsInt(), "nothing changed");
	}

	@Test
	void backupFileIsNamedAfterTheOldVersion() throws IOException {
		Path file = dir.resolve("play.example.net.json");
		Files.writeString(file, "{\"schemaVersion\": 1}");
		Path backup = ProfileMigrations.backup(file, 1);
		assertEquals("play.example.net.v1.bak", backup.getFileName().toString());
		assertEquals("{\"schemaVersion\": 1}", Files.readString(backup));
	}
}
