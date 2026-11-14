package soundsculptor.gametest;

import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.Notices;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SetUsageIndex;

/**
 * Where Sound Sets are used (FR-4.9, FR-4.11): the background index sees other worlds' profiles, the loaded profile
 * always comes from the live snapshot, and a set whose save keeps failing shows its toast once until a save works. A
 * profile whose save failed is kept and written by the next flush once the file is free, as at exit (PRD 9.2: no data
 * loss); rejoining the world meanwhile keeps it instead of reading the older file; and on Windows, 40 changes to a held
 * file cost about one wait, so the flush stays within its bound.
 */
public final class SetUsageClientGameTest implements FabricClientGameTest {
	private static final String OTHER = "other.example.net";
	private static final String GONE = "gone.example.net";
	private static final String SAVE_FAILED = "Sound Sculptor could not save";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			SoundSet farm = new SoundSet("usage-farm", "Usage farm", List.of(SetEntry.of("e1", "minecraft:entity.cow.ambient")));
			Zone secret = new Zone("secret-zone", "Secret zone", true, OTHER, "minecraft:overworld", new Point(0, 64, 0),
					new Shape.Cylinder(10, 20), PlaceSets.EMPTY.attach(farm.id()));
			Profile other = Profile.empty("Other server").withZones(List.of(secret));
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(ws.profile().withEverywhere(PlaceSets.EMPTY.attach(farm.id())),
						ws.library().put(farm)));
				ProfileManager.saveOther(OTHER, other);
				ProfileManager.flushSaves();
				SetUsageIndex.rebuild();
			});

			// The index finishes in the background and never blocks the caller.
			boolean seen = false;
			for (int i = 0; i < 100 && !seen; i++) {
				context.waitTicks(2);
				seen = context.computeOnClient(mc -> SetUsageIndex.current() != null && SetUsageIndex.current().otherCount(farm.id(), key) == 1);
			}
			check(seen, "the index should see the other world's profile");
			check(context.computeOnClient(mc -> SetUsageIndex.current().otherProfileNames(farm.id(), key)).equals(List.of("Other server")),
					"other worlds are listed by display name");
			check(context.computeOnClient(mc -> SetUsageIndex.current().placesHere(farm.id(), key)).equals(List.of(PlaceRef.EVERYWHERE)),
					"places here come from the loaded profile");

			// A change here shows at once, without waiting for a rebuild.
			context.runOnClient(mc -> ProfileManager.update(p -> p.withEverywhere(PlaceSets.EMPTY)));
			check(context.computeOnClient(mc -> SetUsageIndex.current().placesHere(farm.id(), key)).isEmpty(),
					"places here are never stale");

			// A set whose save keeps failing toasts once, and again only after a save worked in between.
			Path file = context.computeOnClient(mc -> ProfileManager.setStore().fileFor("usage-fail"));
			blockFile(context, file);
			SoundSet failing = new SoundSet("usage-fail", "Usage fail", List.of());
			int before = toasts(context);
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(failing))));
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(),
					ws.library().put(failing.withName("Usage fail 2")))));
			context.runOnClient(mc -> ProfileManager.flushSaves());
			context.runOnClient(mc -> ProfileManager.flushSaves());
			context.waitTicks(5);
			check(toasts(context) == before + 1, "a failing set toasts once, not on every change and flush; was "
					+ (toasts(context) - before));

			unblockFile(context, file);
			context.runOnClient(mc -> ProfileManager.flushSaves());
			check(Files.isRegularFile(file), "the set is saved once the file can be written");
			blockFile(context, file);
			context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(),
					ws.library().put(failing.withName("Usage fail 3")))));
			context.runOnClient(mc -> ProfileManager.flushSaves());
			context.waitTicks(5);
			check(toasts(context) == before + 2, "it toasts again after a save worked");

			unblockFile(context, file);

			// A profile whose save fails is not lost: the latest one is written by the next flush (as when the game exits)
			// once the file is free. It toasts once, like a set.
			Path profileFile = context.computeOnClient(mc -> ProfileManager.store().fileFor(key));
			String name = context.computeOnClient(mc -> ProfileManager.profile().name());
			blockFile(context, profileFile);
			int beforeProfile = toasts(context);
			context.runOnClient(mc -> {
				ProfileManager.update(p -> p.withName("Usage renamed"));
				ProfileManager.update(p -> p.withName("Usage renamed again"));
				ProfileManager.flushSaves();
			});
			context.waitTicks(5);
			check(toasts(context) == beforeProfile + 1, "a failing profile toasts once; was " + (toasts(context) - beforeProfile));
			unblockFile(context, profileFile);
			context.runOnClient(mc -> ProfileManager.flushSaves());
			String saved = context.computeOnClient(mc -> read(profileFile));
			check(saved.contains("\"Usage renamed again\""), "the flush writes the latest profile once the file is free:\n" + saved);

			// Leaving and joining the world again while its save still fails keeps the newer profile: join uses it instead
			// of the older file, and it is written once the file is free.
			blockFile(context, profileFile);
			String rejoined = context.computeOnClient(mc -> {
				ProfileManager.update(p -> p.withName("Usage pending"));
				ProfileManager.flushSaves();
				ProfileManager.leave();
				ProfileManager.join(key, "Usage fallback");
				return ProfileManager.profile().name();
			});
			check(rejoined.equals("Usage pending"), "rejoining keeps the profile whose save failed, was " + rejoined);
			check(context.computeOnClient(mc -> ProfileManager.storedProfile(key).name()).equals("Usage pending"),
					"the Profiles tab shows it too");
			unblockFile(context, profileFile);
			context.runOnClient(mc -> ProfileManager.flushSaves());
			String afterRejoin = context.computeOnClient(mc -> read(profileFile));
			check(afterRejoin.contains("\"Usage pending\""), "the kept profile is written once the file is free:\n" + afterRejoin);

			// Windows: a file held open (as a virus scanner does) while 40 changes queue up, e.g. a dragged level slider.
			// Only the first save waits out the lock, so the flush finishes in time and the newest profile is written.
			if (System.getProperty("os.name", "").startsWith("Windows")) {
				long millis = context.computeOnClient(mc -> {
					try (SeekableByteChannel held = Files.newByteChannel(profileFile, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
						long start = System.nanoTime();
						for (int step = 1; step <= 40; step++) {
							int n = step;
							ProfileManager.update(p -> p.withName("Usage step " + n));
						}
						check(ProfileManager.flushSaves(), "the flush finishes within its bound");
						return (System.nanoTime() - start) / 1_000_000;
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
				check(millis < 3000, "40 changes to a held file and a flush should take about one wait (0.3 s), took " + millis + " ms");
				context.runOnClient(mc -> ProfileManager.flushSaves());
				String stepped = context.computeOnClient(mc -> read(profileFile));
				check(stepped.contains("\"Usage step 40\""), "the newest profile is written once the file is free:\n" + stepped);
			}
			context.runOnClient(mc -> ProfileManager.update(p -> p.withName(name)));

			// A set deleted just before joining a profile that attaches it (FR-4.11): the note names the set although
			// deleted.json has not been written. A folder in its place makes that certain, instead of racing the saver.
			Path deletedRecord = context.computeOnClient(mc -> ProfileManager.setStore().directory().resolve("deleted.json"));
			String goneName = "Usage gone " + System.nanoTime(); // not in any deleted.json from an earlier run
			SoundSet gone = new SoundSet("usage-gone", goneName, List.of(SetEntry.of("e1", "minecraft:entity.pig.ambient")));
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().put(gone)));
				ProfileManager.saveOther(GONE, Profile.empty("Gone server").withEverywhere(PlaceSets.EMPTY.attach(gone.id())));
				ProfileManager.flushSaves();
			});
			blockFile(context, deletedRecord);
			int notesBefore = context.computeOnClient(mc -> Notices.shownMessages().size());
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().remove(gone.id())));
				ProfileManager.flushSaves(); // the delete fails: deleted.json cannot be written
				check(!ProfileManager.setStore().deletedNames().containsKey(gone.id()), "the record on disk does not have the name");
				ProfileManager.leave();
				ProfileManager.join(GONE, "Gone server");
			});
			context.waitTicks(3); // notices are shown on the next client task
			List<String> notes = context.computeOnClient(mc -> Notices.shownMessages());
			List<String> newNotes = notes.subList(notesBefore, notes.size());
			check(newNotes.stream().anyMatch(n -> n.equals("'" + goneName + "' was deleted, so it was removed from Everywhere")),
					"the note names the deleted set, was " + newNotes);
			context.runOnClient(mc -> {
				ProfileManager.leave();
				ProfileManager.join(key, name);
			});
			unblockFile(context, deletedRecord);
			context.runOnClient(mc -> {
				ProfileManager.flushSaves(); // the delete is retried and now records the name
				check(ProfileManager.setStore().deletedNames().get(gone.id()).equals(goneName), "the retried delete records the name");
				ProfileManager.delete(GONE);
				ProfileManager.flushSaves();
			});
			check(context.computeOnClient(mc -> ProfileManager.key()).equals(key), "back in this world's profile");

			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(ws.profile(), ws.library().remove("usage-fail").remove(farm.id())));
				ProfileManager.flushSaves();
				ProfileManager.delete(OTHER);
				ProfileManager.flushSaves();
			});
		}
	}

	private static int toasts(ClientGameTestContext context) {
		return (int) context.computeOnClient(mc -> Notices.shownTitles()).stream().filter(SAVE_FAILED::equals).count();
	}

	/** Puts a folder with a file in it where the set file goes, so writing the set fails. */
	private static void blockFile(ClientGameTestContext context, Path file) {
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file);
				Files.createDirectories(file);
				Files.writeString(file.resolve("blocker"), "x");
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void unblockFile(ClientGameTestContext context, Path file) {
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file.resolve("blocker"));
				Files.deleteIfExists(file);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
