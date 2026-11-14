package soundsculptor.gametest;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ui.SculptDialog;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * The Sound Set cases of the zone precedence matrix (PRD 11.4) in game, built as the matrix says: a cow in a boat is
 * the source, and each case checks the start level, the recently played row and the trace (FR-14).
 * <ul>
 *   <li>P13: Everywhere has set S1 (cows 20%) above S2 (cows 80%): 20%; Move S2 up in Everywhere's Sound Sets: 80%.</li>
 *   <li>P14: one set has a group muting cows and the single sound cow ambient at 60%: the ambient plays at 60%, other
 *   cow sounds are muted.</li>
 *   <li>P16: zones A and B both attach "Farm animals" (cows 30%) and the cow is in A; edit the set to 60% from B's Sound
 *   Sets: the cow in A plays at 60%.</li>
 * </ul>
 * The sounds are the cow's own: entity-bound, like the ones the game plays for it, started from the test so the case
 * does not wait for a random ambient sound.
 */
public final class PrecedenceSetsClientGameTest implements FabricClientGameTest {
	private static final String AMBIENT = "minecraft:entity.cow.ambient";
	private static final String HURT = "minecraft:entity.cow.hurt";
	private static final String COWS = "minecraft:entity.cow.*";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int SETTLE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			String key = context.computeOnClient(mc -> ProfileManager.key());
			Vec3 feet = context.computeOnClient(mc -> mc.player.position());
			// The source: a cow in a boat, 6 blocks from the player. NoAI keeps it from trying to leave.
			world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:oak_boat %.1f %.1f %.1f {Passengers:[{id:\"minecraft:cow\",NoAI:1b}]}",
					feet.x + 6, feet.y, feet.z));
			context.waitFor(mc -> cowInBoat(mc) != null, 200);
			Snapshot before = context.computeOnClient(mc -> new Snapshot(ProfileManager.profile(), ProfileManager.library()));
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				RecentLog.get().sounds().clear();
			});
			try {
				p13(context);
				p14(context);
				p16(context, key);
			} finally {
				context.runOnClient(mc -> {
					mc.gui.setScreen(null);
					ProfileManager.change(ws -> new Workspace(before.profile(), before.library()));
					ProfileManager.flushSaves();
				});
				world.getServer().runCommand("kill @e[type=minecraft:cow]");
				world.getServer().runCommand("kill @e[type=minecraft:oak_boat]");
			}
		}
	}

	/** P13: the higher set on Everywhere wins; after Move up in Everywhere's Sound Sets, the other one does. */
	private static void p13(ClientGameTestContext context) {
		SoundSet s1 = new SoundSet("p13-s1", "S1", List.of(SetEntry.of("e1", COWS).withVolume(20)));
		SoundSet s2 = new SoundSet("p13-s2", "S2", List.of(SetEntry.of("e1", COWS).withVolume(80)));
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
				.withEverywhere(PlaceSets.EMPTY.attach(s1.id()).attach(s2.id())), ws.library().put(s1).put(s2))));
		check(play(context, AMBIENT) == 0.2F, "P13: S1 above S2 gives 20%");
		check(lastRow(context, AMBIENT).status() == PlayStatus.PLAYING, "P13: the row says Playing");
		String trace = traceText(context, AMBIENT);
		check(trace.contains("  S1\n    minecraft:entity.cow.*: Level 20%\n      Decides loudness (Higher in the list)\n")
				&& trace.contains("  S2\n    minecraft:entity.cow.*: Level 80%\n      loudness overridden by S1: minecraft:entity.cow.*\n"),
				"P13: the trace names both sets and the higher one deciding:\n" + trace);

		// Swap them as the player would: Move S2 up in Everywhere's Sound Sets.
		context.runOnClient(mc -> mc.gui.setScreen(new PlaceSetsScreen(null, PlaceRef.EVERYWHERE)));
		context.waitForScreen(PlaceSetsScreen.class);
		context.runOnClient(mc -> ((PlaceSetsScreen) mc.gui.screen()).panel.move(s2.id(), -1));
		context.setScreen(() -> null);
		check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().get(0).setId()).equals(s2.id()), "P13: S2 is first now");
		check(play(context, AMBIENT) == 0.8F, "P13: after the swap, 80%");
		trace = traceText(context, AMBIENT);
		check(trace.contains("  S2\n    minecraft:entity.cow.*: Level 80%\n      Decides loudness (Higher in the list)\n"),
				"P13: after the swap the trace has S2 deciding:\n" + trace);
	}

	/** P14: in one set the single-sound entry beats the group for its sound; the group still mutes the rest. */
	private static void p14(ClientGameTestContext context) {
		SoundSet cows = new SoundSet("p14-cows", "Cows", List.of(SetEntry.of("g", COWS).withMute(true),
				SetEntry.of("a", AMBIENT).withVolume(60)));
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
				.withEverywhere(PlaceSets.EMPTY.attach(cows.id())), ws.library().put(cows))));
		check(play(context, AMBIENT) == 0.6F, "P14: the cow ambient plays at 60%");
		String trace = traceText(context, AMBIENT);
		check(trace.contains("    minecraft:entity.cow.ambient: Level 60%\n      Decides loudness (Exact sound ID)\n"),
				"P14: the trace has the single sound deciding loudness:\n" + trace);
		check(play(context, HURT) == 0.0F, "P14: other cow sounds are muted");
		check(lastRow(context, HURT).status() == PlayStatus.MUTED, "P14: the hurt row says Muted");
		trace = traceText(context, HURT);
		check(trace.contains("    minecraft:entity.cow.*: Muted\n      Decides loudness (Only matching entry)\n"),
				"P14: the trace has the group muting the hurt sound:\n" + trace);
	}

	/**
	 * P16: a set linked into A and B, edited from B (B's Sound Sets, the set editor, the entry editor, Save and the
	 * linked-save confirmation): the cow, which is in A only, plays at the new level, and the row names A.
	 */
	private static void p16(ClientGameTestContext context, String key) {
		SoundSet farm = new SoundSet("p16-farm", "Farm animals", List.of(SetEntry.of("e1", COWS).withVolume(30)));
		Vec3 cow = context.computeOnClient(mc -> cowInBoat(mc).position());
		Zone a = new Zone("p16-a", "A", true, key, OVERWORLD, new Point(cow.x, cow.y, cow.z), new Shape.Cylinder(4, 8), PlaceSets.EMPTY.attach(farm.id()));
		Zone b = new Zone("p16-b", "B", true, key, OVERWORLD, new Point(cow.x + 40, cow.y, cow.z), new Shape.Cylinder(4, 8), PlaceSets.EMPTY.attach(farm.id()));
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withZones(List.of(a, b)),
				ws.library().put(farm))));
		check(play(context, AMBIENT) == 0.3F, "P16: the cow in A plays at the set's 30%");
		check("A".equals(lastRow(context, AMBIENT).zoneName()), "P16: the row names zone A");

		context.runOnClient(mc -> mc.gui.setScreen(new PlaceSetsScreen(null, PlaceRef.zone(b.id()))));
		context.waitForScreen(PlaceSetsScreen.class);
		context.runOnClient(mc -> ((PlaceSetsScreen) mc.gui.screen()).panel.edit(farm.id()));
		context.waitForScreen(SetEditorScreen.class);
		context.runOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).editEntry("e1"));
		context.waitForScreen(EntryEditorScreen.class);
		context.runOnClient(mc -> {
			EntryEditorScreen entry = (EntryEditorScreen) mc.gui.screen();
			entry.volumeField.setValue("60");
			entry.save();
		});
		context.waitForScreen(SetEditorScreen.class);
		context.runOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).save());
		context.waitForScreen(SculptDialog.class);
		String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
		check(message.startsWith("This changes A and B"), "P16: Save from B says it changes A too, was " + message);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(PlaceSetsScreen.class);
		context.setScreen(() -> null);

		check(context.computeOnClient(mc -> ProfileManager.library().get(farm.id()).entry("e1").volume()) == 60, "P16: the library set is 60%");
		check(play(context, AMBIENT) == 0.6F, "P16: the cow in A plays at 60%");
		check("A".equals(lastRow(context, AMBIENT).zoneName()), "P16: the row still names zone A");
		String trace = traceText(context, AMBIENT);
		check(trace.contains("  A (Inside): weight 100%, ") && trace.contains("    Farm animals · used in 2 places\n      minecraft:entity.cow.*: Level 60%\n"),
				"P16: the trace shows A's Farm animals at 60%:\n" + trace);
	}

	/** The test's cow, riding its boat, as the client sees it; {@code null} until it has arrived. */
	private static Entity cowInBoat(Minecraft mc) {
		if (mc.level == null) {
			return null;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (EntityType.getKey(entity.getType()).toString().equals("minecraft:cow") && entity.getVehicle() != null
					&& EntityType.getKey(entity.getVehicle().getType()).toString().equals("minecraft:oak_boat")) {
				return entity;
			}
		}
		return null;
	}

	/** Plays one of the cow's sounds from the cow itself; returns the start factor (0 when it did not start). */
	private static float play(ClientGameTestContext context, String id) {
		float factor = context.computeOnClient(mc -> {
			Entity cow = cowInBoat(mc);
			check(cow != null, "the cow in its boat should be there");
			SoundInstance sound = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(Identifier.parse(id)), SoundSource.NEUTRAL,
					1.0F, 1.0F, cow, 0L);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound) ? SoundHook.lastStartFactor() : 0.0F;
		});
		context.waitTicks(SETTLE);
		return factor;
	}

	/** The newest recently played row of {@code id}. */
	private static RecentRow lastRow(ClientGameTestContext context, String id) {
		RecentRow row = context.computeOnClient(mc -> RecentLog.get().sounds().snapshot(Util.getMillis(), SortOrder.MOST_RECENT, s -> null)
				.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null));
		check(row != null, "the recently played list should have " + id);
		return row;
	}

	/** The trace of the sound's last play, as "Copy trace" gives it. */
	private static String traceText(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> new TraceScreen(null, id).text());
	}

	private record Snapshot(Profile profile, SetLibrary library) {
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
