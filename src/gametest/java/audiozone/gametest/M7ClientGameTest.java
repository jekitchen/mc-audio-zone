package audiozone.gametest;

import audiozone.core.profile.Profile;
import audiozone.core.profile.WorldIds;
import audiozone.core.recent.PlayStatus;
import audiozone.core.recent.RecentRow;
import audiozone.core.recent.SortOrder;
import audiozone.core.rules.Rule;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.mc.PlayingChannels;
import audiozone.mc.ProfileManager;
import audiozone.mc.RecentLog;
import audiozone.mc.SoundHook;
import audiozone.mc.TraceScreen;
import audiozone.mc.WorldSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M7 engine tests (PRD 11.1): a profile per world (FR-11.1), fixed sphere and box zones with
 * Inside mode and a hard edge (FR-8 MVP AC), precedence cases P1 to P5 (FR-9, section 11.4), the per-tick re-check
 * of playing sounds (FR-8.8), "Zone: name" in the recently played list and zones in the sound trace.
 */
public final class M7ClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String TRADER = "minecraft:entity.wandering_trader.ambient";
	private static final String LOOP = "minecraft:block.beacon.ambient";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final float SILENT = 1.0E-6F;
	private static final int SETTLE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();

			// FR-11.1: a singleplayer world gets world:<id>, kept in audiozone_id.txt in its save folder.
			String key = context.computeOnClient(mc -> ProfileManager.key());
			check(key != null && key.startsWith("world:"), "a singleplayer world should have a world:<id> key, was " + key);
			Path root = context.computeOnClient(mc -> mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
			check(Files.exists(root.resolve(WorldIds.FILE_NAME)), "audiozone_id.txt should be written into the save folder");
			check(WorldSession.singleplayerKey(root).key().equals(key), "joining again should read the same ID");
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				RecentLog.get().sounds().clear();
			});
			Vec3 origin = context.computeOnClient(mc -> mc.getSoundManager().getListenerTransform().position());

			// FR-8 AC 1: a 10-block sphere muting *villager*: a villager inside is silent; one 15 blocks away is not.
			Point pen = new Point(origin.x + 6, origin.y, origin.z);
			setZones(context, List.of(), zone("pen", "Pen", key, OVERWORLD, pen, new Shape.Sphere(10), Rule.of("z1", "*villager*").withMute(true)));
			check(play(context, VILLAGER, pen.x() + 2, pen.y(), pen.z()) == SILENT, "a villager inside the zone should be muted");
			check(lastStatus(context, VILLAGER) == PlayStatus.MUTED, "the row should say Muted");
			check("Pen".equals(lastZone(context, VILLAGER)), "the row should say which zone decided, was " + lastZone(context, VILLAGER));
			// Zones are part of the trace of that play (FR-14.2).
			String trace = context.computeOnClient(mc -> new TraceScreen(null, VILLAGER).text());
			check(trace.contains("  Pen (Inside): weight 100%, ") && trace.contains("Deciding zones\n"), "the trace should list the zone:\n" + trace);
			check(play(context, VILLAGER, pen.x() - 15, pen.y(), pen.z()) == 1.0F, "a villager 15 blocks from the anchor plays");
			check(play(context, TRADER, pen.x(), pen.y(), pen.z()) == 1.0F, "the trader does not match *villager*");

			// FR-8 AC 2 and 3: a zone made for another dimension or another world has no effect here.
			setZones(context, List.of(), zone("nether", "Nether", key, "minecraft:the_nether", pen, new Shape.Sphere(10), Rule.of("z1", "*villager*").withMute(true)));
			check(play(context, VILLAGER, pen.x(), pen.y(), pen.z()) == 1.0F, "a Nether zone must not apply in the Overworld");
			setZones(context, List.of(), zone("other", "Other", "play.example.net", OVERWORLD, pen, new Shape.Sphere(10), Rule.of("z1", "*villager*").withMute(true)));
			check(play(context, VILLAGER, pen.x(), pen.y(), pen.z()) == 1.0F, "another server's zone must not apply here");

			// Box zones: half the size in each direction from the anchor (FR-8.3).
			setZones(context, List.of(), zone("box", "Box", key, OVERWORLD, pen, new Shape.Box(4, 4, 20), Rule.of("z1", "*villager*").withVolume(30)));
			check(play(context, VILLAGER, pen.x(), pen.y(), pen.z() + 9) == 0.3F, "inside the box's long side the zone applies");
			check(play(context, VILLAGER, pen.x() + 3, pen.y(), pen.z()) == 1.0F, "outside the box's short side it does not");

			// P1: global villagers muted, zone A villagers 50%: 50% inside A; muted (not started) outside.
			setZones(context, List.of(Rule.of("g1", "*villager*").withMute(true)),
					zone("a", "A", key, OVERWORLD, pen, new Shape.Sphere(10), Rule.of("z1", "*villager*").withVolume(50)));
			check(play(context, VILLAGER, pen.x(), pen.y(), pen.z()) == 0.5F, "P1 inside A: 50%");
			// Outside A the global mute applies; the sound starts silently because a zone could unmute it while it plays.
			check(play(context, VILLAGER, pen.x() + 30, pen.y(), pen.z()) <= SILENT, "P1 outside A: muted");
			// P2: global mute, no zone.
			setZones(context, List.of(Rule.of("g1", "*villager*").withMute(true)));
			check(play(context, VILLAGER, pen.x(), pen.y(), pen.z()) == 0.0F, "P2: muted");

			// P3: A 20% with its anchor 3 blocks away beats B 80% at 9 blocks.
			Point sound = pen;
			setZones(context, List.of(),
					zone("a", "A", key, OVERWORLD, new Point(sound.x() + 3, sound.y(), sound.z()), new Shape.Sphere(20), Rule.of("z1", "*villager*").withVolume(20)),
					zone("b", "B", key, OVERWORLD, new Point(sound.x() - 9, sound.y(), sound.z()), new Shape.Sphere(20), Rule.of("z1", "*villager*").withVolume(80)));
			check(play(context, VILLAGER, sound.x(), sound.y(), sound.z()) == 0.2F, "P3: the closer zone A wins");
			check("A".equals(lastZone(context, VILLAGER)), "P3: the row should name zone A");
			// P5: both anchors 5.00 blocks away; the smaller zone wins the tie, whatever the list order.
			setZones(context, List.of(),
					zone("b", "B", key, OVERWORLD, new Point(sound.x() - 5, sound.y(), sound.z()), new Shape.Sphere(12), Rule.of("z1", "*villager*").withVolume(80)),
					zone("a", "A", key, OVERWORLD, new Point(sound.x() + 5, sound.y(), sound.z()), new Shape.Sphere(8), Rule.of("z1", "*villager*").withVolume(20)));
			check(play(context, VILLAGER, sound.x(), sound.y(), sound.z()) == 0.2F, "P5: the smaller zone A wins the tie");

			// FR-8.8: a playing sound is re-decided every tick. Add a muting zone around a looping sound: it goes silent
			// without stopping; remove the zone: it comes back.
			setZones(context, List.of());
			SoundInstance loop = context.computeOnClient(mc -> {
				SoundInstance instance = new SimpleSoundInstance(Identifier.parse(LOOP), SoundSource.BLOCKS, 1.0F, 1.0F, RandomSource.create(),
						true, 0, SoundInstance.Attenuation.LINEAR, pen.x(), pen.y(), pen.z(), false);
				mc.getSoundManager().play(instance);
				return instance;
			});
			context.waitTicks(SETTLE);
			float before = context.computeOnClient(mc -> PlayingChannels.get().gain(loop));
			check(before > 0.01F, "the loop should be tracked at its vanilla gain, was " + before);
			setZones(context, List.of(), zone("quiet", "Quiet", key, OVERWORLD, pen, new Shape.Sphere(5), Rule.of("z1", "*beacon*").withMute(true)));
			context.waitTicks(SETTLE);
			check(context.computeOnClient(mc -> PlayingChannels.get().gain(loop)) == 0.0F, "a zone added while it plays should silence it");
			check(context.computeOnClient(mc -> mc.getSoundManager().isActive(loop)), "muting by a zone must not stop the sound");
			setZones(context, List.of());
			context.waitTicks(SETTLE);
			check(Math.abs(context.computeOnClient(mc -> PlayingChannels.get().gain(loop)) - before) < 1e-4, "removing the zone should restore it");
			context.runOnClient(mc -> mc.getSoundManager().stop(loop));
			// Vanilla releases a stopped channel some ticks later; tracking ends together with vanilla's.
			for (int i = 0; i < 60 && context.computeOnClient(mc -> mc.getSoundManager().isActive(loop)); i++) {
				context.waitTicks(1);
			}
			check(!context.computeOnClient(mc -> PlayingChannels.get().isTracked(loop)), "a stopped sound should no longer be tracked");

			// Bypass returns playing sounds to vanilla too.
			setZones(context, List.of(), zone("quiet", "Quiet", key, OVERWORLD, pen, new Shape.Sphere(5), Rule.of("z1", "*beacon*").withVolume(10)));
			SoundInstance loop2 = context.computeOnClient(mc -> {
				SoundInstance instance = new SimpleSoundInstance(Identifier.parse(LOOP), SoundSource.BLOCKS, 1.0F, 1.0F, RandomSource.create(),
						true, 0, SoundInstance.Attenuation.LINEAR, pen.x(), pen.y(), pen.z(), false);
				mc.getSoundManager().play(instance);
				return instance;
			});
			context.waitTicks(SETTLE);
			float quiet = context.computeOnClient(mc -> PlayingChannels.get().gain(loop2));
			context.runOnClient(mc -> ProfileManager.setBypass(true));
			context.waitTicks(SETTLE);
			float bypassed = context.computeOnClient(mc -> PlayingChannels.get().gain(loop2));
			check(bypassed > quiet * 5, "Bypass should bring the playing sound back to vanilla: " + quiet + " -> " + bypassed);
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				mc.getSoundManager().stop(loop2);
			});

			// The profile is saved under this world's key (FR-11.8).
			context.runOnClient(mc -> ProfileManager.flushSaves());
			Path file = context.computeOnClient(mc -> ProfileManager.store().fileFor(key));
			check(Files.exists(file), "the profile should be saved as " + file);

			// Clean up for later tests.
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
		check(context.computeOnClient(mc -> !ProfileManager.isLoaded()), "leaving the world should unload its profile");
	}

	private static Zone zone(String id, String name, String key, String dimension, Point anchor, Shape shape, Rule rule) {
		return new Zone(id, name, true, key, dimension, anchor, shape, List.of(rule));
	}

	private static void setZones(ClientGameTestContext context, List<Rule> globalRules, Zone... zones) {
		context.runOnClient(mc -> ProfileManager.update(p -> p.withGlobalRules(globalRules).withZones(List.of(zones))));
	}

	/** Plays a villager-style sound at a position; returns the start factor (0 when it did not start). */
	private static float play(ClientGameTestContext context, String id, double x, double y, double z) {
		float factor = context.computeOnClient(mc -> {
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound) ? SoundHook.lastStartFactor() : 0.0F;
		});
		context.waitTicks(SETTLE);
		return factor;
	}

	private static RecentRow row(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> RecentLog.get().sounds().snapshot(Util.getMillis(), SortOrder.MOST_RECENT, s -> null)
				.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null));
	}

	private static PlayStatus lastStatus(ClientGameTestContext context, String id) {
		RecentRow row = row(context, id);
		return row == null ? null : row.status();
	}

	private static String lastZone(ClientGameTestContext context, String id) {
		RecentRow row = row(context, id);
		return row == null ? null : row.zoneName();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
