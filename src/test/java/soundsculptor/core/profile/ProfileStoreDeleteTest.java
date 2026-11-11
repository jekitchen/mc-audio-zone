package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PRD FR-11.3 Delete: the profile is removed, and a new empty one is created on the next join. */
class ProfileStoreDeleteTest {
	@TempDir
	Path dir;

	@Test
	void deleteRemovesTheProfileAndKeepsItAsBak() throws IOException {
		ProfileStore store = new ProfileStore(dir);
		store.save("other.net", Profile.empty("Other").muteExact("x:a"));
		assertTrue(store.delete("other.net"));
		Path file = store.fileFor("other.net");
		assertFalse(Files.exists(file));
		assertTrue(Files.readString(file.resolveSibling(file.getFileName() + ".bak")).contains("x:a"),
				"the deleted profile can still be recovered by hand");
		ProfileStore.LoadResult next = store.load("other.net", "Other");
		assertFalse(next.failed());
		assertTrue(next.profile().globalRules().isEmpty());
	}

	@Test
	void deletingAMissingProfileDoesNothing() throws IOException {
		assertFalse(new ProfileStore(dir).delete("never.net"));
	}
}
