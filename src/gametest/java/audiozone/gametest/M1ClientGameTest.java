package audiozone.gametest;

import audiozone.mc.SoundHook;
import audiozone.mc.SoundObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M1 milestone test (PRD 11.1): every sound start is observed with its ID, category,
 * position, vanilla volume and vanilla range; the range gate matches vanilla; sounds still play.
 *
 * <p>Needs a working sound engine. On machines without an audio device, run the game with the environment variable
 * {@code ALSOFT_DRIVERS=null} (OpenAL Soft's silent output), as CI does.
 */
public final class M1ClientGameTest implements FabricClientGameTest {
	private static final Identifier TEST_SOUND = Identifier.withDefaultNamespace("block.note_block.harp");
	private static final int SETTLE_TICKS = 5;

	private record Seen(Identifier id, SoundSource category, float volume, float range, double distance, boolean audible) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		List<Seen> seen = new CopyOnWriteArrayList<>();
		SoundObserver observer = o -> seen.add(new Seen(o.id(), o.category(), o.volume(), o.range(), o.distance(), o.audible()));

		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			SoundHook.addObserver(observer);
			try {
				// Close (4 blocks), normal volume: heard, range 16.
				Seen near = playAt(context, seen, 4.0, 1.0F);
				check(near.category() == SoundSource.RECORDS, "category: expected records, got " + near.category());
				check(near.range() == 16.0F, "range: expected 16, got " + near.range());
				check(near.audible(), "a sound 4 blocks away should be audible");
				check(Math.abs(near.distance() - 4.0) < 0.01, "distance: expected 4, got " + near.distance());

				// Far (40 blocks), normal volume: out of vanilla range.
				Seen far = playAt(context, seen, 40.0, 1.0F);
				check(!far.audible(), "a sound 40 blocks away with range 16 should not be audible");

				// Far but loud (volume 3 gives range 48): heard, as in vanilla.
				Seen loud = playAt(context, seen, 40.0, 3.0F);
				check(loud.range() == 48.0F, "range for volume 3: expected 48, got " + loud.range());
				check(loud.audible(), "a volume-3 sound 40 blocks away (range 48) should be audible");

				// Relative UI sound: infinite range.
				seen.clear();
				context.runOnClient(mc -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)));
				context.waitTicks(SETTLE_TICKS);
				Seen ui = single(seen, SoundEvents.UI_BUTTON_CLICK.value().location());
				check(Float.isInfinite(ui.range()) && ui.audible(), "a UI click should have infinite range");
			} finally {
				SoundHook.removeObserver(observer);
			}
		}
	}

	/** Plays the test sound {@code blocksAway} east of the listener and returns what the hook saw. */
	private static Seen playAt(ClientGameTestContext context, List<Seen> seen, double blocksAway, float volume) {
		seen.clear();
		boolean playing = context.computeOnClient(mc -> {
			Vec3 listener = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(TEST_SOUND, SoundSource.RECORDS, volume, 1.0F,
					RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR,
					listener.x() + blocksAway, listener.y(), listener.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		check(!seen.isEmpty(), "the sound hook saw no sound. If this machine has no audio device, run with ALSOFT_DRIVERS=null");
		check(playing, "the sound did not start: observing must not stop sounds from playing");
		context.waitTicks(SETTLE_TICKS);
		Seen result = single(seen, TEST_SOUND);
		check(result.volume() == volume, "volume: expected " + volume + ", got " + result.volume());
		return result;
	}

	private static Seen single(List<Seen> seen, Identifier id) {
		List<Seen> matches = seen.stream().filter(s -> s.id().equals(id)).toList();
		check(matches.size() == 1, "expected exactly one observation of " + id + ", got " + matches.size() + ": " + seen);
		return matches.get(0);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
