package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.start.SoundStart;
import soundsculptor.core.start.SoundStartDecider;
import soundsculptor.core.start.SoundStartResult;
import soundsculptor.core.trace.LastPlays;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-9 with places that attach library Sound Sets (FR-8.6, FR-8.10): Everywhere and zones refer to sets by ID, so
 * one set can apply in many places, and editing it changes all of them (matrix case P16).
 */
class PrecedenceSetsTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final SoundSet FARM = new SoundSet("set-farm", "Farm animals",
			List.of(SetEntry.of("e1", COW).withVolume(30)));

	private static PlaceSets attached(String... setIds) {
		PlaceSets place = PlaceSets.EMPTY;
		for (String id : setIds) {
			place = place.attach(id);
		}
		return place;
	}

	private static Zone cylinder(String id, double x, double radius, PlaceSets sets) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Cylinder(radius, 2 * radius), sets);
	}

	private static SoundSet set(String id, SetEntry... entries) {
		return new SoundSet(id, id, List.of(entries));
	}

	private static SoundDecision decide(Precedence p, String soundId, double x) {
		SoundDecision out = new SoundDecision();
		p.decide(soundId, true, WORLD, OVERWORLD, x, 0, 0, out);
		return out;
	}

	@Test
	void p16LinkedSetChangesBothZones() {
		Zone a = cylinder("a", 0, 10, attached(FARM.id()));
		Zone b = cylinder("b", 100, 10, attached(FARM.id()));
		SetLibrary library = new SetLibrary(List.of(FARM));
		Precedence before = new Precedence(PlaceSets.EMPTY, List.of(a, b), library);
		assertEquals(30, decide(before, COW, 0).volumePercent(), 1e-6);
		assertEquals(30, decide(before, COW, 100).volumePercent(), 1e-6);

		SetLibrary edited = library.put(FARM.putEntry(FARM.entry("e1").withVolume(60)));
		Precedence after = new Precedence(PlaceSets.EMPTY, List.of(a, b), edited);
		assertEquals(60, decide(after, COW, 0).volumePercent(), 1e-6, "a cow in A");
		assertEquals(60, decide(after, COW, 100).volumePercent(), 1e-6, "and in B: the set is linked, not copied");
	}

	@Test
	void p1ThroughP5StillHold() {
		SetEntry villagers = SetEntry.of("e1", "minecraft:entity.villager.*");
		SoundSet everywhereMute = set("set-g", villagers.withMute(true));
		SoundSet quiet = set("set-a", villagers.withVolume(50));
		SoundSet twenty = set("set-20", villagers.withVolume(20));
		SoundSet eighty = set("set-80", villagers.withVolume(80));
		SoundSet mute = set("set-m", villagers.withMute(true));
		SoundSet limit = set("set-l", villagers.withLimit(new Limit(2, 1000, false)));
		SetLibrary library = new SetLibrary(List.of(everywhereMute, quiet, twenty, eighty, mute, limit));

		// P1: a zone's set overrides Everywhere's inside the zone only.
		Zone a = new Zone("a", "A", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(20, 10, 20), attached("set-a"));
		Precedence p1 = new Precedence(attached("set-g"), List.of(a), library);
		assertEquals(50, decide(p1, VILLAGER, 0).volumePercent(), 1e-6);
		assertSame(a, decide(p1, VILLAGER, 0).loudnessZone());
		assertEquals(0, decide(p1, VILLAGER, 100).volumePercent(), 1e-6);
		assertNull(decide(p1, VILLAGER, 100).loudnessZone());

		// P2: Everywhere mutes with no zones.
		assertEquals(0, decide(new Precedence(attached("set-g"), List.of(), library), VILLAGER, 0).volumePercent(), 1e-6);

		// P3: the closest anchor wins, whatever the list order.
		Zone near = cylinder("near", 3, 10, attached("set-20"));
		Zone far = cylinder("far", 9, 20, attached("set-80"));
		assertEquals(20, decide(new Precedence(PlaceSets.EMPTY, List.of(near, far), library), VILLAGER, 0).volumePercent(), 1e-6);
		assertEquals(20, decide(new Precedence(PlaceSets.EMPTY, List.of(far, near), library), VILLAGER, 0).volumePercent(), 1e-6);

		// P4: each property comes from the closest zone that sets it.
		Zone muting = cylinder("m", 2, 10, attached("set-m"));
		Zone limiting = cylinder("l", 6, 10, attached("set-l"));
		SoundDecision d = decide(new Precedence(PlaceSets.EMPTY, List.of(muting, limiting), library), VILLAGER, 0);
		assertEquals(0, d.volumePercent(), 1e-6);
		assertSame(muting, d.loudnessZone());
		assertEquals(new Limit(2, 1000, false), d.limit());
		assertSame(limiting, d.limitZone());
		assertEquals("set-l", d.limitSet());

		// P5: a tie on distance goes to the smaller zone.
		Zone small = cylinder("small", 5, 6, attached("set-20"));
		Zone big = new Zone("big", "big", true, WORLD, OVERWORLD, new Point(0, 5, 0), new Shape.Cylinder(10, 20), attached("set-80"));
		assertEquals(20, decide(new Precedence(PlaceSets.EMPTY, List.of(big, small), library), VILLAGER, 0).volumePercent(), 1e-6);
		assertEquals(20, decide(new Precedence(PlaceSets.EMPTY, List.of(small, big), library), VILLAGER, 0).volumePercent(), 1e-6);
	}

	@Test
	void limiterKeysDifferPerPlaceForSharedSet() {
		SoundSet limited = set("set-limit", SetEntry.of("e1", COW).withLimit(new Limit(1, 1000, false)));
		SetLibrary library = new SetLibrary(List.of(limited));
		Zone a = cylinder("a", 0, 10, attached(limited.id()));
		Zone b = cylinder("b", 100, 10, attached(limited.id()));
		SoundStartDecider decider = new SoundStartDecider(new LastPlays());
		decider.setProfile(new Precedence(attached(limited.id()), List.of(a, b), library));

		String inA = SoundStartDecider.limiterKey("a", limited.id(), "e1");
		String inB = SoundStartDecider.limiterKey("b", limited.id(), "e1");
		String everywhere = SoundStartDecider.limiterKey(null, limited.id(), "e1");
		assertNotEquals(inA, inB);
		assertNotEquals(inA, everywhere);

		// Each place counts on its own: one play is allowed in A, in B and outside both.
		assertEquals(PlayStatus.PLAYING, start(decider, 0, 1_000));
		assertEquals(PlayStatus.LIMITED, start(decider, 0, 1_001));
		assertEquals(PlayStatus.PLAYING, start(decider, 100, 1_002), "zone b has its own counter");
		assertEquals(PlayStatus.LIMITED, start(decider, 100, 1_003));
		assertEquals(PlayStatus.PLAYING, start(decider, 50, 1_004), "Everywhere has its own counter");
		assertEquals(1, decider.limiter().droppedCount(inA));
		assertEquals(1, decider.limiter().droppedCount(inB));
		assertEquals(0, decider.limiter().droppedCount(everywhere));
	}

	/** A cow sound at {@code (x, 0, 0)}, heard from right next to it. */
	private static PlayStatus start(SoundStartDecider decider, double x, long nowMs) {
		SoundStart in = new SoundStart();
		in.soundId = COW;
		in.positional = true;
		in.worldKey = WORLD;
		in.dimension = OVERWORLD;
		in.x = x;
		in.range = 16;
		in.distanceSq = 1;
		in.nowMs = nowMs;
		SoundStartResult out = new SoundStartResult();
		decider.start(in, out);
		return out.status();
	}
}
