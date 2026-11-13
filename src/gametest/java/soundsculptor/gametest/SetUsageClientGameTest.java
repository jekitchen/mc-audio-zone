package soundsculptor.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * loss).
 */
public final class SetUsageClientGameTest implements FabricClientGameTest {
	private static final String OTHER = "other.example.net";
	private static final String SAVE_FAILED = "Sound Sculptor could not save";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			SoundSet farm = new SoundSet("usage-farm", "Usage farm", List.of(SetEntry.of("e1", "minecraft:entity.cow.ambient")));
			Zone secret = new Zone("secret-zone", "Secret zone", true, OTHER, "minecraft:overworld", new Point(0, 64, 0),
					new Shape.Sphere(10), PlaceSets.EMPTY.attach(farm.id()));
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
			context.runOnClient(mc -> ProfileManager.update(p -> p.withName(name)));

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
