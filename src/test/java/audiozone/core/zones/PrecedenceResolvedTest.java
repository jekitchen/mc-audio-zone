package audiozone.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import audiozone.core.rules.RuleResolution;
import audiozone.core.stress.StressProfile;

import org.junit.jupiter.api.Test;

/** {@link Precedence#decideResolved} must decide exactly like {@link Precedence#decide}, from pre-resolved rules. */
class PrecedenceResolvedTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";

	@Test
	void decideResolvedMatchesDecideEverywhereWithEveryZoneKind() {
		Precedence p = new Precedence(StressProfile.globalRules(), StressProfile.zones(WORLD, OVERWORLD), ZoneEdge.fade(4));
		RuleResolution[] resolved = new RuleResolution[Precedence.MAX_ZONES + 1];
		SoundDecision a = new SoundDecision();
		SoundDecision b = new SoundDecision();
		List<String> ids = StressProfile.soundIds();
		int compared = 0;
		for (String id : ids) {
			p.resolve(id, resolved);
			for (int x = -60; x <= 60; x += 12) {
				for (int z = -60; z <= 60; z += 15) {
					double px = x / 3.0;
					p.decide(id, true, WORLD, OVERWORLD, x, 64, z, px, 65.62, 0, a);
					p.decideResolved(resolved, true, WORLD, OVERWORLD, x, 64, z, px, 65.62, 0, b);
					String where = id + " at " + x + ", " + z;
					assertEquals(a.volumePercent(), b.volumePercent(), where);
					assertSame(a.loudnessZone(), b.loudnessZone(), where);
					assertSame(a.loudnessRule(), b.loudnessRule(), where);
					assertSame(a.limitRule(), b.limitRule(), where);
					assertSame(a.cueRule(), b.cueRule(), where);
					assertEquals(a.cueOn(), b.cueOn(), where);
					compared++;
				}
			}
		}
		assertEquals(ids.size() * 11 * 9, compared);
	}
}
