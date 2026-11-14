package soundsculptor.core.start;

import static soundsculptor.core.Fixtures.quick;
import static soundsculptor.core.Fixtures.precedence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD 9.1: one Diagnostics entry covers the repeat-limit counters and every per-ID cache of the current profile. */
class SoundStartDeciderMemoryTest {
	@Test
	void estimateIsTheLimiterPlusTheProfilesCaches() {
		SoundStartDecider decider = new SoundStartDecider(new LastPlays());
		assertEquals(decider.limiter().estimatedBytes(), decider.estimatedBytes(), "before any profile");

		SetEntry limited = SetEntry.of("r", "minecraft:*").withLimit(new Limit(3, 1000, true));
		Zone zone = new Zone("z", "Z", true, "world", "minecraft:overworld", new Point(0, 0, 0), new Shape.Cylinder(10, 20),
				quick(List.of(SetEntry.of("zr", "minecraft:entity.*").withMute(true))));
		Precedence precedence = precedence(List.of(limited), List.of(zone));
		decider.setProfile(precedence);
		decider.limiter().tryStart("r", limited.limit(), 0, 64, 0, 0);
		precedence.everywhere().resolve("minecraft:entity.cow.ambient");
		precedence.zoneResolver(0).resolve("minecraft:entity.cow.ambient");

		assertEquals(decider.limiter().estimatedBytes() + precedence.estimatedBytes(), decider.estimatedBytes());
	}
}
