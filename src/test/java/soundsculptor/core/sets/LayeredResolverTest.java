package soundsculptor.core.sets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import soundsculptor.core.Allocations;

/** PRD FR-4.5: a place's attached Sound Sets resolve as ordered layers, top set first. */
class LayeredResolverTest {
	private static final String VILLAGERS = "minecraft:entity.villager.*";
	private static final String VILLAGER_AMBIENT = "minecraft:entity.villager.ambient";
	private static final String VILLAGER_HURT = "minecraft:entity.villager.hurt";
	private static final String COW_AMBIENT = "minecraft:entity.cow.ambient";

	private static SoundSet set(String id, SetEntry... entries) {
		return new SoundSet(id, id, List.of(entries));
	}

	/** Every set attached and on, in the order given. */
	private static PlaceSets place(String... setIds) {
		List<Attachment> attached = new ArrayList<>();
		for (String id : setIds) {
			attached.add(new Attachment(id, true));
		}
		return new PlaceSets(attached, null);
	}

	private static EntryResolver resolver(PlaceSets place, SoundSet... library) {
		return EntryResolver.forPlace(place, new SetLibrary(List.of(library)), null);
	}

	@Test
	void p13HigherSetWinsAndSwapFlips() {
		SoundSet s1 = set("S1", SetEntry.of("e1", VILLAGERS).withVolume(20));
		SoundSet s2 = set("S2", SetEntry.of("e1", VILLAGERS).withVolume(80));
		PlaceSets place = place("S1", "S2");

		assertEquals(20, resolver(place, s1, s2).resolve(VILLAGER_AMBIENT).volumePercent());
		assertEquals(80, resolver(place.move("S2", -1), s1, s2).resolve(VILLAGER_AMBIENT).volumePercent());
	}

	@Test
	void p14SingleSoundBeatsGroupInOneSet() {
		SoundSet s = set("S", SetEntry.of("group", VILLAGERS).withMute(true), SetEntry.of("one", VILLAGER_AMBIENT).withVolume(60));
		EntryResolver r = resolver(place("S"), s);

		EntryResolution ambient = r.resolve(VILLAGER_AMBIENT);
		assertEquals(60, ambient.volumePercent());
		assertFalse(ambient.muted());
		assertTrue(r.resolve(VILLAGER_HURT).muted());
	}

	@Test
	void higherSetGroupBeatsLowerSetSingleSound() {
		// Across sets, set order comes before specificity.
		SoundSet top = set("top", SetEntry.of("group", VILLAGERS).withMute(true));
		SoundSet below = set("below", SetEntry.of("one", VILLAGER_AMBIENT).withVolume(60).withMute(false));
		EntryResolution res = resolver(place("top", "below"), top, below).resolve(VILLAGER_AMBIENT);
		assertTrue(res.muted());
		assertEquals("top", res.loudnessSet());
	}

	@Test
	void p15SettingsLayer() {
		SoundSet s1 = set("S1", SetEntry.of("mute", VILLAGERS).withMute(true));
		SoundSet s2 = set("S2", SetEntry.of("limit", VILLAGERS).withLimit(new Limit(2, 1000, false)));
		EntryResolution res = resolver(place("S1", "S2"), s1, s2).resolve(VILLAGER_AMBIENT);

		assertTrue(res.muted());
		assertEquals(new Limit(2, 1000, false), res.limit().limit());
		assertEquals("S1", res.loudnessSet());
		assertEquals("S2", res.limitSet());
		assertNull(res.cue());
		assertNull(res.cueSet());
	}

	@Test
	void notMutedQuickBeatsMutingLibrarySet() {
		SoundSet quick = SoundSet.quick(List.of(SetEntry.of("q1", COW_AMBIENT).withMute(false)));
		SoundSet a = set("A", SetEntry.of("cows", "minecraft:entity.cow.*").withMute(true));
		EntryResolver r = resolver(place("A").withQuickChanges(quick), a);

		EntryResolution res = r.resolve(COW_AMBIENT);
		assertFalse(res.muted());
		assertEquals(SoundSet.QUICK_ID, res.loudnessSet());
		assertTrue(r.resolve("minecraft:entity.cow.hurt").muted(), "the rest of set A still applies");
		assertEquals(List.of(SoundSet.QUICK_ID, "A"), r.layers().stream().map(Layer::setId).toList());
	}

	@Test
	void disabledAttachmentIsSkipped() {
		SoundSet s1 = set("S1", SetEntry.of("mute", VILLAGERS).withMute(true));
		SoundSet s2 = set("S2", SetEntry.of("vol", VILLAGERS).withVolume(80));
		EntryResolver r = resolver(place("S1", "S2").setEnabled("S1", false), s1, s2);

		EntryResolution res = r.resolve(VILLAGER_AMBIENT);
		assertFalse(res.muted());
		assertEquals(80, res.volumePercent());
		assertEquals("S2", res.loudnessSet());
		assertEquals(List.of("S2"), r.layers().stream().map(Layer::setId).toList());
	}

	@Test
	void missingLibrarySetIsSkipped() {
		SoundSet s2 = set("S2", SetEntry.of("vol", VILLAGERS).withVolume(80));
		EntryResolver r = resolver(place("gone", "S2"), s2);

		assertEquals(80, r.resolve(VILLAGER_AMBIENT).volumePercent());
		assertEquals(List.of("S2"), r.layers().stream().map(Layer::setId).toList());
	}

	@Test
	void duplicateAttachedBelowOriginalIsItsOwnLayer() {
		SoundSet a = set("A", SetEntry.of("e1", VILLAGERS).withVolume(30));
		SoundSet copy = new SoundSet("A-copy", "A (2)", a.entries()); // Duplicate copies the entries by reference
		assertSame(a.entries().get(0), copy.entries().get(0));

		EntryResolution bothOn = resolver(place("A", "A-copy"), a, copy).resolve(VILLAGER_AMBIENT);
		assertEquals("A", bothOn.loudnessSet());

		EntryResolution aOff = resolver(place("A", "A-copy").setEnabled("A", false), a, copy).resolve(VILLAGER_AMBIENT);
		assertSame(a.entries().get(0), aOff.loudness());
		assertEquals("A-copy", aOff.loudnessSet());
	}

	@Test
	void cacheHitAllocatesNothing() {
		SoundSet quick = SoundSet.quick(List.of(SetEntry.of("q1", COW_AMBIENT).withMute(false)));
		SoundSet s1 = set("S1", SetEntry.of("mute", VILLAGERS).withMute(true), SetEntry.of("cue", VILLAGER_AMBIENT).withCue(true));
		SoundSet s2 = set("S2", SetEntry.of("limit", "minecraft:*").withLimit(new Limit(2, 1000, false)).withVolume(50));
		EntryResolver r = resolver(place("S1", "S2").withQuickChanges(quick), s1, s2);
		// An array, not a list: a list's iterator would be the test's own allocation.
		String[] ids = {VILLAGER_AMBIENT, VILLAGER_HURT, COW_AMBIENT, "minecraft:block.stone.step", "other:thing"};
		for (int i = 0; i < 1000; i++) {
			for (String id : ids) {
				r.resolve(id);
			}
		}

		long allocated = Allocations.fewestBytes(round -> {
			for (int i = 0; i < 100; i++) {
				for (String id : ids) {
					r.resolve(id);
				}
			}
		});
		assertEquals(0, allocated);
		assertEquals("S1", r.resolve(VILLAGER_AMBIENT).cueSet());
		assertEquals("S2", r.resolve(VILLAGER_AMBIENT).limitSet());
	}
}
