package audiozone.core.start;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.trace.LastPlays;
import audiozone.core.zones.Point;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD 9.1: one Diagnostics entry covers the repeat-limit counters and every per-ID cache of the current profile. */
class SoundStartDeciderMemoryTest {
	@Test
	void estimateIsTheLimiterPlusTheProfilesCaches() {
		SoundStartDecider decider = new SoundStartDecider(new LastPlays());
		assertEquals(decider.limiter().estimatedBytes(), decider.estimatedBytes(), "before any profile");

		Rule limited = Rule.of("r", "minecraft:*").withLimit(new Limit(3, 1000, true));
		Zone zone = new Zone("z", "Z", true, "world", "minecraft:overworld", new Point(0, 0, 0), new Shape.Sphere(10),
				List.of(Rule.of("zr", "minecraft:entity.*").withMute(true)));
		Precedence precedence = new Precedence(List.of(limited), List.of(zone));
		decider.setProfile(precedence);
		decider.limiter().tryStart("r", limited.limit(), 0, 64, 0, 0);
		precedence.globalRules().resolve("minecraft:entity.cow.ambient");
		precedence.zoneRules(0).resolve("minecraft:entity.cow.ambient");

		assertEquals(decider.limiter().estimatedBytes() + precedence.estimatedBytes(), decider.estimatedBytes());
	}
}
