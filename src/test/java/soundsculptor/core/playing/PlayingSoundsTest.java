package soundsculptor.core.playing;

import static soundsculptor.core.Fixtures.quick;
import static soundsculptor.core.Fixtures.precedence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;

import org.junit.jupiter.api.Test;

/** PRD FR-8.8: playing sounds are re-decided every tick, volume only. */
class PlayingSoundsTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String NOTE = "minecraft:block.note_block.harp";

	private final PlayingSounds playing = new PlayingSounds();
	private final Map<Long, Float> reported = new HashMap<>();
	private final PlayingSounds.GainListener listener = reported::put;

	private static Zone sphere(String id, double radius, Point anchor, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, anchor, new Shape.Sphere(radius), quick(List.of(rules)));
	}

	private int update(Precedence p, double px, double py, double pz) {
		return playing.update(p, WORLD, OVERWORLD, px, py, pz, true, listener);
	}

	@Test
	void reportsNothingWhenTheGainIsWhatTheSoundStartedWith() {
		Precedence p = precedence(List.of(SetEntry.of("r", VILLAGER).withVolume(50)), List.of());
		playing.start(1, VILLAGER, 0, 64, 0, 0.8F, 0.4F);
		assertEquals(0, update(p, 0, 65.62, 0));
		assertTrue(reported.isEmpty());
	}

	@Test
	void reportsTheNewGainWhenTheProfileChangedWhileTheSoundPlays() {
		playing.start(1, VILLAGER, 0, 64, 0, 0.8F, 0.8F);
		Precedence p = precedence(List.of(SetEntry.of("r", VILLAGER).withVolume(50)), List.of());
		assertEquals(1, update(p, 0, 65.62, 0));
		assertEquals(0.4F, reported.get(1L), 1e-6);
		assertEquals(0.4F, playing.gain(1), 1e-6);
	}

	@Test
	void muteReEvaluatesToSilenceWithoutStoppingTheSound() {
		// A cow walks into a muting zone: its sound goes to gain 0 but stays tracked (still playing).
		Precedence p = precedence(List.of(), List.of(sphere("z", 10, new Point(0, 64, 0), SetEntry.of("m", COW).withMute(true))));
		playing.start(7, COW, 15, 64, 0, 1F, 1F);
		update(p, 30, 65.62, 0);
		assertTrue(reported.isEmpty());
		playing.move(7, 5, 64, 0);
		update(p, 30, 65.62, 0);
		assertEquals(0F, reported.get(7L));
		assertTrue(playing.isTracked(7));
		playing.move(7, 15, 64, 0);
		update(p, 30, 65.62, 0);
		assertEquals(1F, reported.get(7L), 1e-6);
	}

	@Test
	void bubbleVolumeChangesAsYouWalkAway() {
		// FR-8 acceptance: bubble radius 8, rule * at 30%; a note block loop changes volume as you walk away.
		Zone bubble = sphere("bubble", 8, new Point(0, 0, 0), SetEntry.of("all", "*").withVolume(30)).asBubble();
		Precedence p = precedence(List.of(), List.of(bubble));
		playing.start(3, NOTE, 2, 64, 0, 1F, 0.3F);
		update(p, 0, 65.62, 0);
		assertTrue(reported.isEmpty(), "inside the bubble from the start");
		update(p, 4, 65.62, 0);
		assertTrue(reported.isEmpty(), "still within 8 blocks");
		update(p, 12, 65.62, 0);
		assertEquals(1F, reported.get(3L), 1e-6, "the bubble no longer covers the note block");
		update(p, 3, 65.62, 0);
		assertEquals(0.3F, reported.get(3L), 1e-6, "walk back: quieter again");
	}

	@Test
	void fadeZoneMovingSourceGetsSteadilyQuieter() {
		// FR-8 acceptance: fade 8 on a 20-block sphere muting cows; a cow walking from the edge to the centre gets
		// steadily quieter and is silent from 8 blocks inside.
		Zone zone = sphere("pen", 20, new Point(0, 64, 0), SetEntry.of("m", COW).withMute(true)).withEdge(ZoneEdge.fade(8));
		Precedence p = precedence(List.of(), List.of(zone));
		playing.start(5, COW, 20, 64, 0, 1F, 1F);
		update(p, 40, 65.62, 0);
		float last = 1F;
		for (double x = 19.5; x >= 0; x -= 0.5) {
			playing.move(5, x, 64, 0);
			update(p, 40, 65.62, 0);
			float gain = playing.gain(5);
			if (x > 12) {
				assertTrue(gain < last, "quieter at " + x + ": " + gain + " after " + last);
			} else {
				assertEquals(0F, gain, 1e-6, "silent at " + x);
			}
			last = gain;
		}
	}

	@Test
	void repeatLimitsAreNotReDecided() {
		// A zone that only sets a limit changes nothing while the sound plays.
		Precedence p = precedence(List.of(),
				List.of(sphere("z", 10, new Point(0, 64, 0), SetEntry.of("l", COW).withLimit(new Limit(1, 1000, false)))));
		playing.start(1, COW, 30, 64, 0, 1F, 1F);
		update(p, 0, 65.62, 0);
		playing.move(1, 0, 64, 0);
		assertEquals(0, update(p, 0, 65.62, 0));
		assertTrue(reported.isEmpty());
	}

	@Test
	void anotherWorldOrDimensionDropsTheFixedZone() {
		Precedence p = precedence(List.of(), List.of(sphere("z", 10, new Point(0, 64, 0), SetEntry.of("m", COW).withMute(true))));
		playing.start(1, COW, 0, 64, 0, 1F, 0F);
		update(p, 0, 65.62, 0);
		assertTrue(reported.isEmpty());
		playing.update(p, WORLD, "minecraft:the_nether", 0, 65.62, 0, true, listener);
		assertEquals(1F, reported.get(1L), 1e-6);
	}

	@Test
	void boostIsCappedWhenNotSupported() {
		Precedence p = precedence(List.of(SetEntry.of("b", COW).withVolume(150)), List.of());
		playing.start(1, COW, 0, 64, 0, 0.8F, 0.8F);
		playing.update(p, WORLD, OVERWORLD, 0, 65.62, 0, false, listener);
		assertTrue(reported.isEmpty(), "capped at 100%, which is where it started");
		playing.update(p, WORLD, OVERWORLD, 0, 65.62, 0, true, listener);
		assertEquals(1.2F, reported.get(1L), 1e-6);
	}

	@Test
	void vanillaGainChangeIsFollowed() {
		Precedence p = precedence(List.of(SetEntry.of("r", COW).withVolume(50)), List.of());
		playing.start(1, COW, 0, 64, 0, 1F, 0.5F);
		update(p, 0, 65.62, 0);
		playing.setVanillaGain(1, 0.4F);
		update(p, 0, 65.62, 0);
		assertEquals(0.2F, reported.get(1L), 1e-6);
	}

	@Test
	void bypassRestoresVanillaGainsAndTheNextUpdateReappliesRules() {
		Precedence p = precedence(List.of(SetEntry.of("m", COW).withMute(true)), List.of());
		playing.start(1, COW, 0, 64, 0, 0.7F, 0F);
		update(p, 0, 65.62, 0);
		assertEquals(1, playing.updateBypassed(listener));
		assertEquals(0.7F, reported.get(1L), 1e-6);
		assertEquals(1, update(p, 0, 65.62, 0));
		assertEquals(0F, reported.get(1L));
	}

	@Test
	void soundsWhoseInputsDidNotChangeAreNotReDecided() {
		Precedence noBubbles = precedence(List.of(SetEntry.of("r", COW).withVolume(50)),
				List.of(sphere("z", 10, new Point(0, 64, 0), SetEntry.of("q", COW).withVolume(20))));
		for (int i = 0; i < 10; i++) {
			playing.start(i, COW, i, 64, 0, 1F, 1F);
		}
		update(noBubbles, 0, 65.62, 0);
		long before = playing.decisionCount();
		update(noBubbles, 0, 65.62, 0);
		update(noBubbles, 5, 65.62, 0); // the player moved, but no bubble depends on it
		assertEquals(0, playing.decisionCount() - before);
		playing.move(3, 3.5, 64, 0);
		update(noBubbles, 5, 65.62, 0);
		assertEquals(1, playing.decisionCount() - before, "only the sound that moved");

		Precedence withBubble = precedence(List.of(),
				List.of(sphere("b", 8, new Point(0, 0, 0), SetEntry.of("all", "*").withVolume(30)).asBubble()));
		update(withBubble, 0, 65.62, 0);
		long bubbleBefore = playing.decisionCount();
		update(withBubble, 1, 65.62, 0);
		assertEquals(10, playing.decisionCount() - bubbleBefore, "the player moved and a bubble follows them");
	}


	@Test
	void stoppedSoundsAreRemoved() {
		playing.start(1, COW, 0, 64, 0, 1F, 1F);
		playing.start(2, COW, 0, 64, 0, 1F, 1F);
		playing.stop(1);
		assertFalse(playing.isTracked(1));
		assertTrue(playing.isTracked(2));
		assertEquals(1, playing.size());
		assertTrue(Float.isNaN(playing.gain(1)));
		playing.stop(1); // stopping twice is harmless
		playing.move(1, 5, 5, 5); // so is moving an untracked sound
		assertEquals(1, playing.size());
	}

	@Test
	void startingTheSameHandleTwiceIsRefused() {
		assertTrue(playing.start(1, COW, 0, 64, 0, 1F, 1F));
		assertFalse(playing.start(1, VILLAGER, 0, 64, 0, 1F, 1F));
		assertEquals(1, playing.size());
	}

	@Test
	void aFullTableFailsOpen() {
		for (int i = 0; i < PlayingSounds.CAPACITY; i++) {
			assertTrue(playing.start(i, COW, 0, 64, 0, 1F, 1F));
		}
		assertFalse(playing.start(9_999, COW, 0, 64, 0, 1F, 1F));
		assertEquals(1, playing.rejectedCount());
		Precedence p = precedence(List.of(SetEntry.of("r", COW).withVolume(50)), List.of());
		assertEquals(PlayingSounds.CAPACITY, update(p, 0, 65.62, 0), "every tracked sound is still re-decided");
		assertFalse(reported.containsKey(9_999L));
		playing.stop(0);
		assertTrue(playing.start(9_999, COW, 0, 64, 0, 1F, 1F), "room again after a sound stops");
	}

	@Test
	void manyStartsAndStopsKeepTheTableConsistent() {
		// Random churn against a plain map, so the handle hash and swap-remove stay in step.
		Random random = new Random(88);
		Map<Long, Double> model = new HashMap<>();
		List<Long> live = new ArrayList<>();
		for (int step = 0; step < 50_000; step++) {
			if (!live.isEmpty() && (live.size() >= PlayingSounds.CAPACITY || random.nextInt(3) == 0)) {
				long h = live.remove(random.nextInt(live.size()));
				playing.stop(h);
				model.remove(h);
			} else {
				long h = random.nextLong() >>> random.nextInt(60); // varied handles, some colliding in the hash
				if (model.containsKey(h)) {
					continue;
				}
				assertTrue(playing.start(h, COW, 0, 64, 0, 1F, 1F));
				double x = random.nextInt(1000);
				playing.move(h, x, 64, 0);
				model.put(h, x);
				live.add(h);
			}
		}
		assertEquals(model.size(), playing.size());
		for (long h : live) {
			assertTrue(playing.isTracked(h), "handle " + h);
		}
		playing.clear();
		assertEquals(0, playing.size());
		assertFalse(playing.isTracked(live.get(0)));
	}

	@Test
	void aNewProfileSnapshotIsResolvedAgain() {
		// The player saves a change while the sound plays: the new snapshot's rules apply, not the cached old ones.
		playing.start(1, COW, 0, 64, 0, 1F, 0.5F);
		update(precedence(List.of(SetEntry.of("r", COW).withVolume(50)), List.of()), 0, 65.62, 0);
		assertTrue(reported.isEmpty());
		update(precedence(List.of(SetEntry.of("r", COW).withVolume(25)), List.of()), 0, 65.62, 0);
		assertEquals(0.25F, reported.get(1L), 1e-6);
	}

	@Test
	void stoppingASoundKeepsTheOthersRulesStraight() {
		// Swap-remove moves the last sound into the hole; its cached rules must move with it.
		Precedence p = precedence(List.of(SetEntry.of("c", COW).withVolume(40), SetEntry.of("v", VILLAGER).withVolume(60)), List.of());
		playing.start(1, COW, 0, 64, 0, 1F, 0.4F);
		playing.start(2, VILLAGER, 0, 64, 0, 1F, 0.6F);
		update(p, 0, 65.62, 0);
		playing.stop(1);
		playing.setVanillaGain(2, 0.5F);
		update(p, 0, 65.62, 0);
		assertEquals(0.3F, reported.get(2L), 1e-6, "the villager keeps its own 60% rule");
	}
}
