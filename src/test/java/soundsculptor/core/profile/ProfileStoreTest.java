package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import soundsculptor.core.TestEnglish;

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
		assertTrue(result.profile().everywhere().quickEntries().isEmpty());
		assertEquals("Default", result.profile().name());
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException {
		SetEntry full = new SetEntry("r2", false, soundsculptor.core.sets.SoundPattern.of("*villager*"), null, 50,
				new Limit(3, 1000, true), true);
		Profile profile = new Profile("My server", new ProfileSettings(false),
				quick(List.of(SetEntry.of("r1", VILLAGER).withMute(true), full)));
		store().save("default", profile);
		ProfileStore.LoadResult result = store().load("default", "ignored");
		assertFalse(result.failed(), String.valueOf(result.problem()));
		assertEquals(profile, result.profile());
	}

	@Test
	void fileHasSchemaVersionAndOmitsUnsetProperties() throws IOException {
		store().save("default", Profile.empty("Default").withEverywhere(quick(SetEntry.of("exact:" + VILLAGER, VILLAGER).withMute(true))));
		String json = Files.readString(store().fileFor("default"));
		assertTrue(json.contains("\"schemaVersion\": 3"), json);
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
		assertTrue(result.profile().everywhere().quickEntries().isEmpty());
		assertNotNull(result.brokenCopy());
		assertTrue(Files.exists(result.brokenCopy()));
		assertFalse(Files.exists(file), "the broken file must be moved aside so it is not overwritten");
	}

	@Test
	void fileLargerThan1MbIsRefusedAndSetAside() throws IOException {
		Files.createDirectories(dir);
		Path file = store().fileFor("default");
		Files.writeString(file, "{\"schemaVersion\": 3" + " ".repeat((int) ProfileStore.MAX_FILE_BYTES) + "}");
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertEquals("The file is larger than 1 MB.", result.problem().text(EN));
		assertEquals(Profile.empty("Default"), result.profile());
		assertTrue(Files.exists(result.brokenCopy()));
		assertFalse(Files.exists(file));
	}

	@Test
	void fileOfExactly1MbStillLoads() throws IOException {
		Files.createDirectories(dir);
		String start = "{\"schemaVersion\": 3, \"name\": \"Big\"";
		Files.writeString(store().fileFor("default"), start + " ".repeat((int) ProfileStore.MAX_FILE_BYTES - start.length() - 1) + "}");
		assertEquals(ProfileStore.MAX_FILE_BYTES, Files.size(store().fileFor("default")));
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertFalse(result.failed(), String.valueOf(result.problem()));
		assertEquals("Big", result.profile().name());
	}

	@Test
	void newerSchemaIsRefusedWithAPlainMessage() throws IOException {
		Files.createDirectories(dir);
		Files.writeString(store().fileFor("default"), "{\"schemaVersion\": 99, \"globalRules\": []}");
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertTrue(result.problem().text(EN).contains("newer Sound Sculptor"), result.problem().text(EN));
	}

	@Test
	void badEntryNamesThePlaceAndField() throws IOException {
		Files.createDirectories(dir);
		Files.writeString(store().fileFor("default"), "{\"schemaVersion\": 3, \"everywhere\": {\"sets\": [{\"set\": \"quick\"}],"
				+ " \"quickChanges\": {\"id\": \"quick\", \"entries\": [{\"id\": \"r1\", \"target\": \"minecraft:a\", \"volume\": 900}]}}}");
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertTrue(result.problem().text(EN).startsWith("Everywhere, Quick changes, entry 1, level: 900"), result.problem().text(EN));
	}

	@Test
	void olderSchemaIsSetAsideAsBrokenAsAnEarlierBuild() throws IOException {
		// PRD 7.5: profiles from the schema 1 and 2 test builds are not migrated.
		Files.createDirectories(dir);
		Path file = store().fileFor("default");
		Files.writeString(file, "{\"schemaVersion\": 2, \"globalRules\": [{\"id\": \"r1\", \"pattern\": \"minecraft:a\"}]}");
		ProfileStore.LoadResult result = store().load("default", "Default");
		assertTrue(result.failed());
		assertEquals("This profile was made by an earlier test build of Sound Sculptor and can't be read.", result.problem().text(EN));
		assertEquals(Profile.empty("Default"), result.profile());
		assertTrue(Files.exists(result.brokenCopy()));
		assertFalse(Files.exists(file));
	}

	@Test
	void unknownFieldsAreIgnored() throws ProfileFormatException {
		Profile p = ProfileCodec.fromJson("{\"schemaVersion\": 3, \"fromTheFuture\": 3, \"everywhere\": {\"sets\": []}}");
		assertTrue(p.everywhere().quickEntries().isEmpty());
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
