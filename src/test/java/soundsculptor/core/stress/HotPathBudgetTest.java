package soundsculptor.core.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentSounds;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;

import org.junit.jupiter.api.Test;

/**
 * PRD section 9.1: the sound hot path must cost under 5 µs per sound on average with 50 global rules and 64 zones,
 * and allocate nothing in steady state. These run on every build, so a regression shows up in CI, not in a farm.
 *
 * <p>The time limit is the PRD budget, far above the expected cost, so a slow CI machine does not make it flaky. The
 * allocation check uses the JVM's own per-thread counter.
 */
class HotPathBudgetTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final double BUDGET_NANOS = 5_000;
	private static final Point PLAYER = StressProfile.PLAYER;

	private final Precedence precedence = new Precedence(StressProfile.globalRules(), StressProfile.zones(WORLD, OVERWORLD));
	private final String[] ids = StressProfile.soundIds().toArray(new String[0]);
	private final double[] xs = new double[ids.length];
	private final double[] zs = new double[ids.length];

	HotPathBudgetTest() {
		// Sources spread over the 9 x 9 pen, deterministically.
		for (int i = 0; i < ids.length; i++) {
			xs[i] = (i * 37 % 90) / 10.0 - 4.5;
			zs[i] = (i * 53 % 90) / 10.0 - 4.5;
		}
	}

	private void decideAll(SoundDecision out, int rounds) {
		for (int r = 0; r < rounds; r++) {
			for (int i = 0; i < ids.length; i++) {
				precedence.decide(ids[i], true, WORLD, OVERWORLD, xs[i], 64, zs[i], PLAYER.x(), PLAYER.y(), PLAYER.z(), out);
			}
		}
	}

	@Test
	void averageDecisionIsUnderFiveMicroseconds() {
		SoundDecision out = new SoundDecision();
		decideAll(out, 2_000); // warm up the JIT and the per-ID caches
		int rounds = 1_000;
		long start = System.nanoTime();
		decideAll(out, rounds);
		double perSound = (System.nanoTime() - start) / (double) (rounds * ids.length);
		System.out.printf("Stress profile: %.0f ns per sound decision (budget %.0f ns)%n", perSound, BUDGET_NANOS);
		assertTrue(perSound < BUDGET_NANOS, "average " + perSound + " ns per sound");
	}

	@Test
	void stressProfileReallyExercisesTheZones() {
		// Guard against a profile that never reaches the zone code: most sounds in the pen should be decided by a zone.
		SoundDecision out = new SoundDecision();
		int byZone = 0;
		for (int i = 0; i < ids.length; i++) {
			precedence.decide(ids[i], true, WORLD, OVERWORLD, xs[i], 64, zs[i], PLAYER.x(), PLAYER.y(), PLAYER.z(), out);
			if (out.loudnessZone() != null) {
				byZone++;
			}
		}
		assertTrue(byZone > ids.length / 2, byZone + " of " + ids.length);
	}

	@Test
	void hotPathAllocatesNothingInSteadyState() {
		SoundDecision out = new SoundDecision();
		RecentSounds recent = new RecentSounds();
		LastPlays lastPlays = new LastPlays();
		TimingWindow timing = new TimingWindow();

		// Warm up: every ID seen once, so caches and rows exist, and the JIT has compiled the loop.
		for (int round = 0; round < 2_000; round++) {
			soundStarts(out, recent, lastPlays, timing, round);
		}
		long allocated = soundsculptor.core.Allocations.fewestBytes(pass -> {
			for (int round = 0; round < 200; round++) {
				soundStarts(out, recent, lastPlays, timing, 2_000 + pass * 200 + round);
			}
		});
		assertEquals(0, allocated, "bytes allocated on the hot path for " + 200 * ids.length + " sound starts");
	}

	/** What the sound hook does per sound start: decide, log, remember the play, time it. */
	private void soundStarts(SoundDecision out, RecentSounds recent, LastPlays lastPlays, TimingWindow timing, int round) {
		long now = 1_000L + round * 10L; // stays inside the 3-minute window, so no row expires
		for (int i = 0; i < ids.length; i++) {
			long t0 = System.nanoTime();
			precedence.decide(ids[i], true, WORLD, OVERWORLD, xs[i], 64, zs[i], PLAYER.x(), PLAYER.y(), PLAYER.z(), out);
			PlayStatus status = out.volumePercent() == 0 ? PlayStatus.MUTED : PlayStatus.PLAYING;
			String zoneName = out.loudnessZone() == null ? null : out.loudnessZone().name();
			recent.record(ids[i], "neutral", true, 6.0, true, status, zoneName, now);
			lastPlays.record(ids[i], true, WORLD, OVERWORLD, xs[i], 64, zs[i], PLAYER.x(), PLAYER.y(), PLAYER.z(), out.volumePercent(), false, false);
			timing.add(System.nanoTime() - t0, now);
		}
	}

	@Test
	void soundMixFitsTheRecentListAndLastPlayTable() {
		// Otherwise the allocation test would measure evictions, not steady state.
		List<String> ids = StressProfile.soundIds();
		assertTrue(ids.size() <= RecentSounds.MAX_IDS && ids.size() <= LastPlays.CAPACITY, "ids: " + ids.size());
	}
}
