package soundsculptor.core.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.Allocations;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.stress.StressProfile;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Precedence;

import org.junit.jupiter.api.Test;

/**
 * PRD section 9.1 for the whole sound start (range gate, precedence, repeat limit, cue, trace record), with the
 * noisy-farm stress profile: 50 global rules and 64 zones. Under 5 µs per sound on average, and no allocation in
 * steady state.
 *
 * <p>The time is the best of several batches, against the PRD budget, so a busy CI machine does not make it flaky.
 */
class SoundStartBudgetTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final double BUDGET_NANOS = 5_000;

	private final SoundStartDecider decider = new SoundStartDecider(new LastPlays());
	private final String[] ids = StressProfile.soundIds().toArray(new String[0]);
	private final double[] xs = new double[ids.length];
	private final double[] zs = new double[ids.length];
	private final SoundStart in = new SoundStart();
	private final SoundStartResult out = new SoundStartResult();

	SoundStartBudgetTest() {
		decider.setProfile(new Precedence(StressProfile.everywhere(), StressProfile.zones(WORLD, OVERWORLD),
				StressProfile.library()));
		for (int i = 0; i < ids.length; i++) {
			xs[i] = (i * 37 % 90) / 10.0 - 4.5;
			zs[i] = (i * 53 % 90) / 10.0 - 4.5;
		}
	}

	/** One pass over every farm sound; the clock moves 10 ms per pass, so limits both allow and drop. */
	private int startAll(long pass) {
		int limited = 0;
		for (int i = 0; i < ids.length; i++) {
			in.reset();
			in.soundId = ids[i];
			in.positional = true;
			in.range = 16;
			in.distanceSq = xs[i] * xs[i] + zs[i] * zs[i];
			in.worldKey = WORLD;
			in.dimension = OVERWORLD;
			in.x = xs[i];
			in.y = StressProfile.PEN_Y;
			in.z = zs[i];
			in.hasSubtitle = true;
			in.nowMs = 1_000 + pass * 10;
			decider.start(in, out);
			if (out.status() == PlayStatus.LIMITED) {
				limited++;
			}
		}
		return limited;
	}

	@Test
	void averageSoundStartIsUnderFiveMicroseconds() {
		for (int pass = 0; pass < 2_000; pass++) {
			startAll(pass);
		}
		double best = Double.MAX_VALUE;
		long pass = 2_000;
		for (int batch = 0; batch < 5; batch++) {
			long t0 = System.nanoTime();
			for (int k = 0; k < 400; k++) {
				startAll(pass++);
			}
			best = Math.min(best, (System.nanoTime() - t0) / (400.0 * ids.length));
		}
		System.out.printf("Stress profile: %.0f ns per sound start (budget %.0f ns)%n", best, BUDGET_NANOS);
		assertTrue(best < BUDGET_NANOS, "average " + best + " ns per sound start");
	}

	@Test
	void stressRunReallyLimitsSomeSounds() {
		int limited = 0;
		for (int pass = 0; pass < 200; pass++) {
			limited += startAll(pass);
		}
		assertTrue(limited > 0, "the stress profile's limits should drop some plays");
	}

	@Test
	void soundStartAllocatesNothingInSteadyState() {
		for (int pass = 0; pass < 2_000; pass++) {
			startAll(pass);
		}
		long allocated = Allocations.fewestBytes(round -> {
			for (int k = 0; k < 200; k++) {
				startAll(2_000 + round * 200L + k);
			}
		});
		assertEquals(0, allocated, "bytes allocated for " + 200 * ids.length + " sound starts");
	}
}
