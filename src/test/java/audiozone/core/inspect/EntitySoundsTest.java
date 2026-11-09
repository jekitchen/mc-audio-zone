package audiozone.core.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-2.2 to FR-2.4: which entity a sound belongs to, and what the inspector remembers. */
class EntitySoundsTest {
	private static final String VILLAGER = "minecraft:villager";
	private static final String CHICKEN = "minecraft:chicken";
	private static final String MUMBLE = "minecraft:entity.villager.ambient";
	private static final String CLUCK = "minecraft:entity.chicken.ambient";

	private final EntitySounds sounds = new EntitySounds();
	private final Candidates near = new Candidates();

	// --- Linked (FR-2.3, certain) ---

	@Test
	void soundAttachedToAnEntityIsLinked() {
		sounds.recordLinked(7, MUMBLE, true, 1_000);
		List<EntitySound> list = sounds.soundsOf(7, 1_000);
		assertEquals(1, list.size());
		assertEquals(MUMBLE, list.get(0).soundId());
		assertEquals(LinkKind.LINKED, list.get(0).kind());
	}

	@Test
	void entityOutsideHearingRangeGetsNothing() {
		// FR-2.2: only entities within vanilla hearing range.
		sounds.recordLinked(7, MUMBLE, false, 1_000);
		assertTrue(sounds.soundsOf(7, 1_000).isEmpty());
		assertEquals(0, sounds.entityCount());
	}

	// --- Likely (FR-2.3, guessed) ---

	@Test
	void positionalSoundNextToOneEntityIsLikely() {
		near.add(3, VILLAGER, 10.5, 64, 10.5, true);
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
		assertEquals(3, sounds.lastEntityId());
		assertEquals(LinkKind.LIKELY, sounds.soundsOf(3, 1_000).get(0).kind());
	}

	@Test
	void entityMoreThanOneBlockAwayIsNotACandidate() {
		near.add(3, VILLAGER, 11.6, 64, 10.5, true); // 1.1 blocks from the sound
		assertNull(sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
		assertEquals(-1, sounds.lastEntityId());
		assertTrue(sounds.soundsOf(3, 1_000).isEmpty());
	}

	@Test
	void exactlyOneBlockAwayStillCounts() {
		near.add(3, VILLAGER, 11.5, 64, 10.5, true);
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
	}

	@Test
	void distanceIsMeasuredInAllThreeAxes() {
		near.add(3, VILLAGER, 10.5, 65.2, 10.5, true); // 1.2 blocks above
		assertNull(sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
	}

	@Test
	void entityOfTheMatchingTypeIsPreferredOverACloserOne() {
		near.add(1, CHICKEN, 10.5, 64, 10.5, true); // right on the sound
		near.add(2, VILLAGER, 11.2, 64, 10.5, true); // 0.7 away, but it is a villager sound
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
		assertEquals(2, sounds.lastEntityId());
	}

	@Test
	void withoutATypeMatchTheNearestEntityIsChosen() {
		near.add(1, CHICKEN, 10.9, 64, 10.5, true);
		near.add(2, VILLAGER, 10.6, 64, 10.5, true);
		assertEquals(LinkKind.LIKELY, sounds.recordPositional("minecraft:block.stone.step", 10.5, 64, 10.5, near, 1_000));
		assertEquals(2, sounds.lastEntityId());
	}

	@Test
	void twoCandidatesWithinAQuarterBlockOfEachOtherAreUncertain() {
		near.add(1, VILLAGER, 10.5, 64, 10.5, true);
		near.add(2, VILLAGER, 10.7, 64, 10.5, true); // 0.2 from villager 1
		assertEquals(LinkKind.UNCERTAIN, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
		assertEquals(1, sounds.lastEntityId());
		assertEquals(LinkKind.UNCERTAIN, sounds.soundsOf(1, 1_000).get(0).kind());
	}

	@Test
	void candidatesMoreThanAQuarterBlockApartAreLikely() {
		near.add(1, VILLAGER, 10.5, 64, 10.5, true);
		near.add(2, VILLAGER, 10.8, 64, 10.5, true); // 0.3 away from villager 1
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
		assertEquals(1, sounds.lastEntityId());
	}

	@Test
	void aNonMatchingEntityCloseByDoesNotMakeATypeMatchUncertain() {
		// The chicken is not a candidate for a villager sound once a villager matches.
		near.add(1, VILLAGER, 10.5, 64, 10.5, true);
		near.add(2, CHICKEN, 10.6, 64, 10.5, true);
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
	}

	@Test
	void entitiesOutsideHearingRangeAreNeverLinked() {
		near.add(1, VILLAGER, 10.5, 64, 10.5, false);
		near.add(2, VILLAGER, 11.3, 64, 10.5, true);
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
		assertEquals(2, sounds.lastEntityId());
		assertTrue(sounds.soundsOf(1, 1_000).isEmpty());
	}

	@Test
	void outOfRangeEntitiesDoNotMakeALinkUncertain() {
		near.add(1, VILLAGER, 10.5, 64, 10.5, true);
		near.add(2, VILLAGER, 10.6, 64, 10.5, false);
		assertEquals(LinkKind.LIKELY, sounds.recordPositional(MUMBLE, 10.5, 64, 10.5, near, 1_000));
	}

	// --- Type matching (FR-2.3) ---

	@Test
	void typeMatchingUsesTheEntitySegmentOfTheSoundId() {
		assertTrue(EntitySounds.typeMatches(MUMBLE, VILLAGER));
		assertTrue(EntitySounds.typeMatches("minecraft:entity.villager.work_farmer", VILLAGER));
		assertTrue(EntitySounds.typeMatches("othermod:entity.dragon.roar", "othermod:dragon"));
		assertTrue(EntitySounds.typeMatches("minecraft:entity.iron_golem.step", "minecraft:iron_golem"));
	}

	@Test
	void typeMatchingRejectsNearMisses() {
		assertFalse(EntitySounds.typeMatches("minecraft:entity.villager.ambient", "minecraft:wandering_trader"));
		assertFalse(EntitySounds.typeMatches("minecraft:entity.villagerx.ambient", VILLAGER), "whole segment only");
		assertFalse(EntitySounds.typeMatches("minecraft:entity.villager", VILLAGER), "needs a sound after the type");
		assertFalse(EntitySounds.typeMatches("othermod:entity.villager.ambient", VILLAGER), "other namespace");
		assertFalse(EntitySounds.typeMatches("minecraft:block.villager.place", VILLAGER), "not an entity sound");
		assertFalse(EntitySounds.typeMatches("minecraft:entity.golem.step", "minecraft:iron_golem"));
	}

	@Test
	void idsWithoutANamespaceMeanMinecraft() {
		assertTrue(EntitySounds.typeMatches("entity.villager.ambient", VILLAGER));
		assertTrue(EntitySounds.typeMatches(MUMBLE, "villager"));
	}

	// --- Memory (FR-2.2) ---

	@Test
	void keepsTheLastTwentySoundsNewestFirst() {
		for (int i = 0; i < 25; i++) {
			sounds.recordLinked(7, "minecraft:entity.villager.s" + i, true, 1_000 + i);
		}
		List<EntitySound> list = sounds.soundsOf(7, 1_100);
		assertEquals(EntitySounds.SOUNDS_PER_ENTITY, list.size());
		assertEquals("minecraft:entity.villager.s24", list.get(0).soundId());
		assertEquals("minecraft:entity.villager.s5", list.get(19).soundId());
	}

	@Test
	void forgetsSoundsOlderThanThirtySeconds() {
		sounds.recordLinked(7, "old", true, 1_000);
		sounds.recordLinked(7, "new", true, 20_000);
		List<EntitySound> list = sounds.soundsOf(7, 31_001);
		assertEquals(1, list.size());
		assertEquals("new", list.get(0).soundId());
	}

	@Test
	void aSoundExactlyThirtySecondsOldIsStillShown() {
		sounds.recordLinked(7, "edge", true, 1_000);
		assertEquals(1, sounds.soundsOf(7, 1_000 + EntitySounds.WINDOW_MS).size());
		assertTrue(sounds.soundsOf(7, 1_001 + EntitySounds.WINDOW_MS).isEmpty());
	}

	@Test
	void unknownEntityHasNoSounds() {
		// FR-2.4: the panel then says "No sounds linked yet".
		assertTrue(sounds.soundsOf(99, 1_000).isEmpty());
	}

	@Test
	void tracksAtMost256EntitiesAndEvictsTheLeastRecentlyHeard() {
		for (int id = 0; id < EntitySounds.MAX_ENTITIES; id++) {
			sounds.recordLinked(id, "s", true, 1_000 + id);
		}
		sounds.recordLinked(0, "s", true, 5_000); // entity 0 heard again, so entity 1 is now the oldest
		sounds.recordLinked(1_000, "s", true, 6_000);
		assertEquals(EntitySounds.MAX_ENTITIES, sounds.entityCount());
		assertTrue(sounds.soundsOf(1, 6_000).isEmpty(), "entity 1 evicted");
		assertEquals(2, sounds.soundsOf(0, 6_000).size());
		assertEquals(1, sounds.soundsOf(1_000, 6_000).size());
	}

	@Test
	void anEvictedSlotStartsEmpty() {
		for (int id = 0; id < EntitySounds.MAX_ENTITIES; id++) {
			for (int s = 0; s < 3; s++) {
				sounds.recordLinked(id, "s" + s, true, 1_000 + id);
			}
		}
		sounds.recordLinked(5_000, "fresh", true, 9_000);
		List<EntitySound> list = sounds.soundsOf(5_000, 9_000);
		assertEquals(1, list.size());
		assertEquals("fresh", list.get(0).soundId());
	}

	@Test
	void forgetAndClear() {
		sounds.recordLinked(7, "a", true, 1_000);
		sounds.recordLinked(8, "b", true, 1_000);
		sounds.forget(7);
		assertTrue(sounds.soundsOf(7, 1_000).isEmpty());
		assertEquals(1, sounds.entityCount());
		sounds.clear();
		assertEquals(0, sounds.entityCount());
		assertTrue(sounds.soundsOf(8, 1_000).isEmpty());
	}

	@Test
	void candidatesAreReusedAcrossSounds() {
		near.add(1, VILLAGER, 0, 64, 0, true);
		near.clear();
		assertEquals(0, near.size());
		assertNull(sounds.recordPositional(MUMBLE, 0, 64, 0, near, 1_000));
	}

	@Test
	void candidateBufferHasAFixedCapacity() {
		for (int i = 0; i < Candidates.CAPACITY + 5; i++) {
			near.add(i, VILLAGER, 50, 64, 50, true);
		}
		assertEquals(Candidates.CAPACITY, near.size());
	}
}
