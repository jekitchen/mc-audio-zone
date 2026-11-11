package soundsculptor.core.start;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.rules.Limit;
import soundsculptor.core.rules.Rule;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Precedence;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-6 acceptance: 20 villagers in a pen, {@code minecraft:entity.villager.ambient} limited to 3 per second. Over
 * 30 seconds about 90 play and the rest are Limited, never more than 3 starting in any one second. With Per source on,
 * each villager counts on its own and the total rises.
 */
class VillagerPenLimitTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final int VILLAGERS = 20;
	private static final long DURATION_MS = 30_000;

	/** Start times of the plays allowed through, from a fixed random pen. */
	private static List<Long> run(boolean perSource) {
		SoundStartDecider decider = new SoundStartDecider(new LastPlays());
		Rule rule = Rule.of("g1", VILLAGER).withLimit(new Limit(3, 1000, perSource));
		decider.setProfile(new Precedence(List.of(rule), List.of()));
		SoundStart in = new SoundStart();
		SoundStartResult out = new SoundStartResult();
		Random random = new Random(20);
		List<Long> allowed = new ArrayList<>();
		// Each villager mumbles every 0.5 to 2 seconds, on 50 ms game ticks.
		long[] next = new long[VILLAGERS];
		for (int v = 0; v < VILLAGERS; v++) {
			next[v] = random.nextInt(2000);
		}
		for (long now = 0; now < DURATION_MS; now += 50) {
			for (int v = 0; v < VILLAGERS; v++) {
				if (now < next[v]) {
					continue;
				}
				next[v] = now + 500 + random.nextInt(1500);
				in.reset();
				in.soundId = VILLAGER;
				in.positional = true;
				in.range = 16;
				in.distanceSq = 9;
				in.worldKey = "w";
				in.dimension = "minecraft:overworld";
				in.x = v % 5 * 2;
				in.z = v / 5 * 2;
				in.hasSubtitle = true;
				in.nowMs = now;
				decider.start(in, out);
				if (out.status() != PlayStatus.LIMITED) {
					allowed.add(now);
				}
			}
		}
		return allowed;
	}

	@Test
	void sharedLimitLetsAbout90ThroughAndNeverMoreThan3PerSecond() {
		List<Long> allowed = run(false);
		assertTrue(allowed.size() >= 85 && allowed.size() <= 90, allowed.size() + " plays");
		for (int i = 3; i < allowed.size(); i++) {
			assertTrue(allowed.get(i) - allowed.get(i - 3) >= 1000, "4 plays within one second ending at " + allowed.get(i));
		}
	}

	@Test
	void perSourceLimitLetsEachVillagerBeHeard() {
		List<Long> shared = run(false);
		List<Long> perSource = run(true);
		assertTrue(perSource.size() > shared.size() * 3, perSource.size() + " vs " + shared.size());
	}
}
