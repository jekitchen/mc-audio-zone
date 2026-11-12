package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.sets.SetEntry;
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
		store.save("other.net", Profile.empty("Other").withEverywhere(quick(SetEntry.of("exact:" + "x:a", "x:a").withMute(true))));
		assertTrue(store.delete("other.net"));
		Path file = store.fileFor("other.net");
		assertFalse(Files.exists(file));
		assertTrue(Files.readString(file.resolveSibling(file.getFileName() + ".bak")).contains("x:a"),
				"the deleted profile can still be recovered by hand");
		ProfileStore.LoadResult next = store.load("other.net", "Other");
		assertFalse(next.failed());
		assertTrue(next.profile().everywhere().quickEntries().isEmpty());
	}

	@Test
	void deletingAMissingProfileDoesNothing() throws IOException {
		assertFalse(new ProfileStore(dir).delete("never.net"));
	}
}
