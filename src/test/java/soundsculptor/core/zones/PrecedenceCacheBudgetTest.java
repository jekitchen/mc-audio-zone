package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.Allocations;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.EntryResolution;
import soundsculptor.core.sets.EntryResolver;

import org.junit.jupiter.api.Test;

/**
 * PRD 9.1 memory budget: each zone's rule list caches its answers per sound ID, but all of a profile's zones share
 * one cap, {@link Precedence#ZONE_CACHE_IDS}, so 64 zones cannot hold 64 full caches. Past the cap a zone still
 * answers correctly; it just matches its patterns again.
 */
class PrecedenceCacheBudgetTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";

	private static String id(int i) {
		return "minecraft:entity.test_sound.number_" + i;
	}

	private static List<Zone> fullZones() {
		List<Zone> zones = new ArrayList<>();
		for (int z = 0; z < Precedence.MAX_ZONES; z++) {
			SetEntry rule = SetEntry.of("r", "minecraft:entity.test_sound.number_1*").withMute(true);
			zones.add(new Zone("z" + z, "Z" + z, true, WORLD, OVERWORLD, new Point(z * 100, 0, 0), new Shape.Sphere(10),
					List.of(rule)));
		}
		return zones;
	}

	private static int zoneCachedIds(Precedence p) {
		int n = 0;
		for (int z = 0; z < p.zones().size(); z++) {
			n += p.zoneRules(z).cachedIdCount();
		}
		return n;
	}

	@Test
	void allZonesTogetherCacheAtMostTheSharedCap() {
		Precedence p = new Precedence(List.of(), fullZones());
		EntryResolution[] out = new EntryResolution[p.zoneCount() + 1];
		for (int i = 0; i < 2000; i++) {
			p.resolve(id(i), out);
		}
		assertTrue(zoneCachedIds(p) <= Precedence.ZONE_CACHE_IDS, "cached " + zoneCachedIds(p));
		assertEquals(Precedence.ZONE_CACHE_IDS, zoneCachedIds(p), "the cap is used, not wasted");
	}

	@Test
	void pastTheCapZonesStillAnswerCorrectly() {
		Precedence p = new Precedence(List.of(), fullZones());
		EntryResolution[] out = new EntryResolution[p.zoneCount() + 1];
		for (int i = 0; i < 2000; i++) {
			p.resolve(id(i), out);
		}
		p.resolve(id(1999), out); // matches number_1* and is past the cap for the last zones
		for (int z = 1; z <= p.zoneCount(); z++) {
			assertTrue(out[z].loudness() != null, "zone " + z + " mutes number_1999");
		}
		p.resolve(id(2999), out);
		assertSame(EntryResolution.NONE, out[p.zoneCount()], "number_2999 does not match");
	}

	@Test
	void pastTheCapLookupsAllocateNothing() {
		Precedence p = new Precedence(List.of(), fullZones());
		EntryResolution[] out = new EntryResolution[p.zoneCount() + 1];
		for (int i = 0; i < 2000; i++) {
			p.resolve(id(i), out);
		}
		// IDs 1000..1999 match the zones' rule and are past the cap for most zones: answered without caching.
		String[] ids = new String[1000];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = id(1000 + i);
		}
		long allocated = Allocations.fewestBytes(round -> {
			for (String id : ids) {
				p.resolve(id, out);
			}
		});
		assertEquals(0, allocated);
	}

	@Test
	void sameWinnersShareOneResult() {
		EntryResolver resolver = EntryResolver.of(List.of(SetEntry.of("r", "minecraft:*").withMute(true)));
		assertSame(resolver.resolve(id(1)), resolver.resolve(id(2)), "both IDs are decided by the same rule");
	}

	@Test
	void eachPrecedenceHasItsOwnCap() {
		Precedence first = new Precedence(List.of(), fullZones());
		EntryResolution[] out = new EntryResolution[first.zoneCount() + 1];
		for (int i = 0; i < 2000; i++) {
			first.resolve(id(i), out);
		}
		Precedence second = new Precedence(List.of(), fullZones());
		second.resolve(id(0), out);
		assertEquals(second.zoneCount(), zoneCachedIds(second), "a new snapshot starts with the whole cap");
	}

	@Test
	void aListWithNoEnabledRulesCachesNothing() {
		EntryResolver empty = EntryResolver.of(List.of(SetEntry.of("r", "minecraft:*").withMute(true).withEnabled(false)));
		for (int i = 0; i < 100; i++) {
			assertSame(EntryResolution.NONE, empty.resolve(id(i)));
		}
		assertEquals(0, empty.cachedIdCount());
	}

	@Test
	void estimateCountsTheGlobalAndEveryZoneCache() {
		Precedence p = new Precedence(List.of(SetEntry.of("g", "minecraft:*").withMute(true)), fullZones());
		long before = p.estimatedBytes();
		EntryResolution[] out = new EntryResolution[p.zoneCount() + 1];
		for (int i = 0; i < 500; i++) {
			p.resolve(id(i), out);
		}
		long cachesOnly = p.globalRules().estimatedBytes() - EntryResolver.of(List.of()).estimatedBytes();
		for (int z = 0; z < p.zoneCount(); z++) {
			cachesOnly += p.zoneRules(z).estimatedBytes() - EntryResolver.of(List.of()).estimatedBytes();
		}
		assertEquals(before + cachesOnly, p.estimatedBytes());
	}
}
