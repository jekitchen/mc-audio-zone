package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import audiozone.core.TestEnglish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PRD FR-11.8 storage and section 7.5 file format. */
class ProfileStoreTest {
	private static final TestEnglish EN = new TestEnglish();

	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	@TempDir
	Path dir;

	private ProfileStore store() {
		return new ProfileStore(dir);
	}

	@Test
	void missingFileGivesAnEmptyProfileWithoutAProblem() {
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertFalse(result.failed());
		assertTrue(result.profile().globalRules().isEmpty());
		assertEquals("Default", result.profile().name());
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException {
		Rule full = new Rule("r2", false, audiozone.core.rules.SoundPattern.of("*villager*"), null, 50,
				new Limit(3, 1000, true), true);
		Profile profile = new Profile("My server", new ProfileSettings(false),
				List.of(Rule.of("r1", VILLAGER).withMute(true), full));
		store().save("default", profile);
		ProfileStore.LoadResult result = store().load("default", "ignored");
		assertFalse(result.failed(), String.valueOf(result.problem()));
		assertEquals(profile, result.profile());
	}

	@Test
	void fileHasSchemaVersionAndOmitsUnsetProperties() throws IOException {
		store().save("default", Profile.empty("Default").muteExact(VILLAGER));
		String json = Files.readString(store().fileFor("default"));
		assertTrue(json.contains("\"schemaVersion\": 2"), json);
		assertTrue(json.contains("\"mute\": true"), json);
		assertFalse(json.contains("\"volume\""), "unset properties must be omitted: " + json);
	}

	@Test
	void saveKeepsThePreviousFileAsBakAndLeavesNoTempFile() throws IOException {
		store().save("default", Profile.empty("First"));
		store().save("default", Profile.empty("Second"));
		Path file = store().fileFor("default");
		Path bak = file.resolveSibling(file.getFileName() + ".bak");
		assertTrue(Files.exists(bak));
		assertTrue(Files.readString(bak).contains("First"));
		assertTrue(Files.readString(file).contains("Second"));
		assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
	}

	@Test
	void corruptFileIsRenamedBrokenAndAnEmptyProfileIsUsed() throws IOException {
		Files.createDirectories(dir);
		Path file = store().fileFor("default");
		Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertEquals("The file is not valid JSON (line 1, column 9).", result.problem().text(EN));
		assertTrue(result.profile().globalRules().isEmpty());
		assertNotNull(result.brokenCopy());
		assertTrue(Files.exists(result.brokenCopy()));
		assertFalse(Files.exists(file), "the broken file must be moved aside so it is not overwritten");
	}

	@Test
	void newerSchemaIsRefusedWithAPlainMessage() throws IOException {
		Files.createDirectories(dir);
		Files.writeString(store().fileFor("default"), "{\"schemaVersion\": 99, \"globalRules\": []}");
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertTrue(result.problem().text(EN).contains("newer Audio Zone"), result.problem().text(EN));
	}

	@Test
	void badRuleNamesTheRuleAndField() throws IOException {
		Files.createDirectories(dir);
		Files.writeString(store().fileFor("default"),
				"{\"schemaVersion\": 1, \"globalRules\": [{\"id\": \"r1\", \"pattern\": \"minecraft:a\", \"volume\": 900}]}");
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertTrue(result.problem().text(EN).startsWith("Rule 1"), result.problem().text(EN));
	}

	@Test
	void unknownFieldsAreIgnored() throws ProfileFormatException {
		Profile p = ProfileCodec.fromJson("{\"schemaVersion\": 1, \"fromTheFuture\": 3, \"globalRules\": []}");
		assertTrue(p.globalRules().isEmpty());
	}

	@Test
	void notAnObjectIsRejected() {
		assertThrows(ProfileFormatException.class, () -> ProfileCodec.fromJson("[]"));
		assertThrows(ProfileFormatException.class, () -> ProfileCodec.fromJson(""));
	}

	@Test
	void keysBecomeSafeFileNames() {
		assertEquals("play.example.net", ProfileStore.safeName("play.example.net"));
		assertEquals("world_1a2b", ProfileStore.safeName("World:1A2B"));
		assertEquals("realms_123", ProfileStore.safeName("realms:123"));
		assertEquals("_..", ProfileStore.safeName(".."));
		assertEquals("_", ProfileStore.safeName(""));
	}
}
