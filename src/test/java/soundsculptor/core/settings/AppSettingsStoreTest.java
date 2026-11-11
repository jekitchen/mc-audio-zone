package soundsculptor.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The app-wide settings file on disk: atomic saves, a .bak of the previous version, and .broken for unreadable files. */
class AppSettingsStoreTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("soundsculptor").resolve("settings.json");
	}

	private AppSettingsStore store() {
		return new AppSettingsStore(file());
	}

	private Path sibling(String suffix) {
		return file().resolveSibling("settings.json" + suffix);
	}

	@Test
	void noFileGivesDefaultsWithoutProblems() {
		AppSettingsStore.LoadResult result = store().load();
		assertEquals(AppSettings.DEFAULTS, result.settings());
		assertEquals(List.of(), result.problems());
		assertNull(result.brokenCopy());
		assertFalse(result.hasProblems());
	}

	@Test
	void saveCreatesTheFolderAndLoadReadsItBack() throws IOException {
		AppSettings s = AppSettings.DEFAULTS.withReducedMotion(true).withCueTextScale(140).withLastTab("rules");
		store().save(s);
		assertEquals(AppSettingsCodec.toJson(s), Files.readString(file(), StandardCharsets.UTF_8));
		AppSettingsStore.LoadResult result = store().load();
		assertEquals(s, result.settings());
		assertFalse(result.hasProblems());
		assertFalse(Files.exists(sibling(".tmp")));
	}

	@Test
	void savingKeepsThePreviousFileAsBak() throws IOException {
		store().save(AppSettings.DEFAULTS.withCueTextScale(120));
		assertFalse(Files.exists(sibling(".bak")), "nothing to back up the first time");
		store().save(AppSettings.DEFAULTS.withCueTextScale(130));
		assertEquals(120, AppSettingsCodec.read(Files.readString(sibling(".bak"))).settings().cueTextScale());
		assertEquals(130, store().load().settings().cueTextScale());
	}

	@Test
	void anUnreadableFileIsMovedToBrokenAndDefaultsAreUsed() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "this is not json {");
		AppSettingsStore.LoadResult result = store().load();
		assertEquals(AppSettings.DEFAULTS, result.settings());
		assertEquals(1, result.problems().size());
		assertEquals(sibling(".broken"), result.brokenCopy());
		assertFalse(Files.exists(file()));
		assertEquals("this is not json {", Files.readString(sibling(".broken")));
	}

	@Test
	void aFileWithOneBadValueKeepsTheGoodValuesAndStaysInPlace() throws IOException {
		Files.createDirectories(file().getParent());
		String text = "{\"schemaVersion\": 1, \"reducedMotion\": true, \"cueTextScale\": \"big\"}";
		Files.writeString(file(), text);
		AppSettingsStore.LoadResult result = store().load();
		assertEquals(AppSettings.DEFAULTS.withReducedMotion(true), result.settings());
		assertEquals(1, result.problems().size());
		assertTrue(result.hasProblems());
		assertNull(result.brokenCopy());
		assertEquals(text, Files.readString(file()));
	}

	@Test
	void anOversizedFileIsNotRead() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{\"schemaVersion\": 1" + " ".repeat((int) AppSettingsStore.MAX_FILE_BYTES) + "}");
		AppSettingsStore.LoadResult result = store().load();
		assertEquals(AppSettings.DEFAULTS, result.settings());
		assertEquals(List.of("The file is larger than 64 KB."), result.problems());
		assertEquals(sibling(".broken"), result.brokenCopy());
	}

	@Test
	void anOlderBrokenCopyIsReplaced() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(sibling(".broken"), "old");
		Files.writeString(file(), "[]");
		assertEquals(sibling(".broken"), store().load().brokenCopy());
		assertEquals("[]", Files.readString(sibling(".broken")));
	}
}
