package soundsculptor.core.playing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.Allocations;
import soundsculptor.core.stress.StressProfile;
import soundsculptor.core.zones.Precedence;

import org.junit.jupiter.api.Test;

/**
 * PRD section 9.1: re-deciding the playing sounds must cost under 0.2 ms per tick, and allocate nothing. Measured at
 * the worst case: the stress profile (50 global rules, 64 zones including fades), as many sounds as vanilla can play at
 * once, and every source moving every tick, so nothing can be skipped.
 */
class PlayingSoundsBudgetTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";
	/** Vanilla's channel limit: 247 normal plus 8 streaming. */
	private static final int SOUNDS = 255;
	private static final double BUDGET_NANOS = 200_000;

	private final Precedence precedence = new Precedence(StressProfile.everywhere(), StressProfile.zones(WORLD, OVERWORLD),
			StressProfile.library());
	private final String[] ids = StressProfile.soundIds().toArray(new String[0]);
	private final PlayingSounds playing = new PlayingSounds();
	private final PlayingSounds.GainListener listener = (handle, gain) -> { };

	PlayingSoundsBudgetTest() {
		for (int i = 0; i < SOUNDS; i++) {
			playing.start(i, ids[i % ids.length], x(i, 0), StressProfile.PEN_Y, z(i, 0), 1F, 1F);
		}
	}

	private static double x(int i, int tick) {
		return ((i * 37 + tick) % 90) / 10.0 - 4.5;
	}

	private static double z(int i, int tick) {
		return ((i * 53 + tick * 3) % 90) / 10.0 - 4.5;
	}

	/** One tick in which every source moved a little. */
	private void tick(int tick) {
		for (int i = 0; i < SOUNDS; i++) {
			playing.move(i, x(i, tick), StressProfile.PEN_Y, z(i, tick));
		}
		playing.update(precedence, WORLD, OVERWORLD, true, listener);
	}

	@Test
	void worstCaseTickIsUnderTheBudget() {
		for (int t = 0; t < 2_000; t++) {
			tick(t); // warm up the JIT and the per-ID caches
		}
		// Best of five batches: a shared CI machine can pause the test (another job, GC), which would inflate a single
		// average. A slow implementation is slow in every batch, so this still catches one.
		int ticks = 200;
		double perTick = Double.MAX_VALUE;
		for (int batch = 0; batch < 5; batch++) {
			long start = System.nanoTime();
			for (int t = 0; t < ticks; t++) {
				tick(2_000 + batch * ticks + t);
			}
			perTick = Math.min(perTick, (System.nanoTime() - start) / (double) ticks);
		}
		System.out.printf("Playing sounds: %.0f ns per tick for %d sounds (budget %.0f ns)%n", perTick, SOUNDS, BUDGET_NANOS);
		assertTrue(perTick < BUDGET_NANOS, "average " + perTick + " ns per tick");
	}

	@Test
	void tickAllocatesNothingInSteadyState() {
		for (int t = 0; t < 2_000; t++) {
			tick(t);
		}
		long allocated = Allocations.fewestBytes(round -> {
			for (int t = 0; t < 100; t++) {
				tick(2_000 + round * 100 + t);
			}
		});
		assertEquals(0, allocated, "bytes allocated re-deciding " + SOUNDS + " playing sounds");
	}
}
