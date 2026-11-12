package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.sets.SetEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Profile files record their world key (PRD FR-11.3, FR-11.8): file names are made safe for the file system, so
 * "world:6f1c…" and "play.example.net:25570" cannot be read back from the name.
 */
class ProfileWorldKeyTest {
	private static final Profile PROFILE = Profile.empty("Example").withEverywhere(quick(List.of(SetEntry.of("r1", "x:one"))));

	@TempDir
	Path dir;

	@Test
	void toJsonWithAKeyWritesItAfterTheName() {
		String json = ProfileCodec.toJson(PROFILE, "play.example.net:25570");
		assertTrue(json.contains("\"worldKey\": \"play.example.net:25570\""), json);
		assertTrue(json.indexOf("\"name\"") < json.indexOf("\"worldKey\"")
				&& json.indexOf("\"worldKey\"") < json.indexOf("\"settings\""), json);
	}

	@Test
	void toJsonWithoutAKeyIsUnchanged() {
		assertFalse(ProfileCodec.toJson(PROFILE).contains("worldKey"));
		assertEquals(ProfileCodec.toJson(PROFILE), ProfileCodec.toJson(PROFILE, null));
	}

	@Test
	void theKeyIsAKnownFieldSoReadingGivesNoWarning() throws ProfileFormatException {
		ProfileCodec.Read read = ProfileCodec.read(ProfileCodec.toJson(PROFILE, "realms:12"), version -> { });
		assertEquals(List.of(), read.warnings());
		assertEquals(PROFILE, read.profile());
	}

	@Test
	void worldKeyOfReadsTheStoredKey() {
		assertEquals(Optional.of("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30"),
				ProfileCodec.worldKeyOf(ProfileCodec.toJson(PROFILE, "world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30")));
	}

	@Test
	void worldKeyOfIsEmptyWhenTheFileDoesNotSayOrIsUnreadable() {
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf(ProfileCodec.toJson(PROFILE)));
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf("{\"worldKey\": 5}"));
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf("{\"worldKey\": \"\"}"));
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf("{\"worldKey\": null}"));
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf("{oops"));
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf("[]"));
		assertEquals(Optional.empty(), ProfileCodec.worldKeyOf(""));
	}

	@Test
	void storeSavesTheKeyInTheFile() throws IOException {
		ProfileStore store = new ProfileStore(dir);
		store.save("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30", PROFILE);
		String text = Files.readString(store.fileFor("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30"), StandardCharsets.UTF_8);
		assertEquals(Optional.of("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30"), ProfileCodec.worldKeyOf(text));
		assertEquals(PROFILE, store.load("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30", "unused").profile());
	}

	@Test
	void listGivesEveryProfileFileWithItsKey() throws IOException {
		ProfileStore store = new ProfileStore(dir);
		store.save("play.example.net:25570", PROFILE);
		store.save("realms:12", PROFILE);
		store.save("realms:12", PROFILE); // leaves a .bak, which is not a profile
		Files.writeString(dir.resolve("old.json"), ProfileCodec.toJson(PROFILE), StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("bad.json"), "{oops", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("x.json.broken"), "{}", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("y.json.tmp"), "{}", StandardCharsets.UTF_8);
		Files.createDirectory(dir.resolve("folder.json"));

		assertEquals(List.of(
				new ProfileStore.Stored(dir.resolve("bad.json"), null),
				new ProfileStore.Stored(dir.resolve("old.json"), null),
				new ProfileStore.Stored(store.fileFor("play.example.net:25570"), "play.example.net:25570"),
				new ProfileStore.Stored(store.fileFor("realms:12"), "realms:12")), store.list());
	}

	@Test
	void listOfAMissingFolderIsEmpty() throws IOException {
		assertEquals(List.of(), new ProfileStore(dir.resolve("nope")).list());
	}

	@Test
	void anOversizedFileIsListedWithoutReadingIt() throws IOException {
		Files.writeString(dir.resolve("big.json"), "{\"worldKey\": \"a\", \"pad\": \"" + "x".repeat(1024 * 1024) + "\"}",
				StandardCharsets.UTF_8);
		assertEquals(List.of(new ProfileStore.Stored(dir.resolve("big.json"), null)), new ProfileStore(dir).list());
	}
}
