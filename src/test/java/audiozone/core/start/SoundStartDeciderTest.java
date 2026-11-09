package audiozone.core.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import audiozone.core.cue.CueSettings;
import audiozone.core.recent.PlayStatus;
import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.trace.LastPlay;
import audiozone.core.trace.LastPlays;
import audiozone.core.zones.Point;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.core.zones.ZoneEdge;

import org.junit.jupiter.api.Test;

/**
 * One sound start, end to end (PRD section 7 flowchart): range gate, decide (global rules, zones, precedence, repeat
 * limit), then drop, mute (with cue) or play. The source is at the origin and the listener 5 blocks away unless a test
 * says otherwise.
 */
class SoundStartDeciderTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final Limit TWO_PER_SECOND = new Limit(2, 1000, false);

	private final LastPlays lastPlays = new LastPlays();
	private final SoundStartDecider decider = new SoundStartDecider(lastPlays);
	private final SoundStart in = new SoundStart();
	private final SoundStartResult out = new SoundStartResult();

	private static Rule villagers(String id) {
		return Rule.of(id, "minecraft:entity.villager.*");
	}

	private static Zone sphere(String id, double x, double radius, Rule... rules) {
		return new Zone(id, "Zone " + id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), List.of(rules));
	}

	private void profile(List<Rule> global, Zone... zones) {
		decider.setProfile(new Precedence(global, List.of(zones)));
	}

	/** A villager sound at {@code (x, 0, 0)}, heard from 5 blocks away with vanilla's 16-block range. */
	private SoundStart villagerAt(double x) {
		in.reset();
		in.soundId = VILLAGER;
		in.positional = true;
		in.range = 16;
		in.distanceSq = 25;
		in.worldKey = WORLD;
		in.dimension = OVERWORLD;
		in.x = x;
		in.hasSubtitle = true;
		return in;
	}

	private SoundStartResult start(SoundStart sound, long nowMs) {
		sound.nowMs = nowMs;
		decider.start(sound, out);
		return out;
	}

	private SoundStartResult start(SoundStart sound) {
		return start(sound, 1_000);
	}

	// --- Pass-through cases ---

	@Test
	void noRulesPlaysAsVanilla() {
		profile(List.of());
		SoundStartResult r = start(villagerAt(0));
		assertTrue(r.handled());
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(100, r.volumePercent(), 1e-6);
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
		assertFalse(r.cue());
		assertNull(r.zone());
	}

	@Test
	void bypassLeavesEverythingAlone() {
		profile(List.of(villagers("g1").withMute(true)));
		SoundStart s = villagerAt(0);
		s.bypass = true;
		SoundStartResult r = start(s);
		assertFalse(r.handled());
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
		// The trace still knows the sound was heard, so the panel can say Bypass was on.
		assertEquals(100, lastPlays.get(VILLAGER).volumePercent(), 1e-6);
	}

	@Test
	void p11VanillaCoveredSoundsAreNeverChanged() {
		profile(List.of(Rule.of("all", "*").withMute(true)));
		SoundStart s = villagerAt(0);
		s.soundId = "minecraft:music.game";
		s.vanillaCovered = true;
		SoundStartResult r = start(s);
		assertFalse(r.handled());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
		assertNull(lastPlays.get("minecraft:music.game"), "vanilla-covered sounds have no trace");
	}

	@Test
	void noProfileYetFailsOpen() {
		SoundStartResult r = start(villagerAt(0));
		assertFalse(r.handled());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
	}

	// --- Mute and volume ---

	@Test
	void p2GlobalMuteCancelsTheStart() {
		profile(List.of(villagers("g1").withMute(true)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.MUTED, r.status());
		assertEquals(0, r.volumePercent(), 1e-6);
		assertEquals(0F, r.factor(), 1e-6);
		assertTrue(r.cancel(), "nothing can unmute it while it plays, so no channel is needed");
	}

	@Test
	void zeroVolumeCountsAsMuted() {
		profile(List.of(villagers("g1").withVolume(0)));
		assertEquals(PlayStatus.MUTED, start(villagerAt(0)).status());
	}

	@Test
	void turnedDownPlaysQuieter() {
		profile(List.of(villagers("g1").withVolume(25)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(0.25F, r.factor(), 1e-6);
	}

	@Test
	void p1ZoneOverridesGlobalInsideAndNamesTheZone() {
		Zone a = sphere("a", 0, 10, villagers("z1").withVolume(50));
		profile(List.of(villagers("g1").withMute(true)), a);

		SoundStartResult inside = start(villagerAt(0));
		assertEquals(0.5F, inside.factor(), 1e-6);
		assertSame(a, inside.zone());
		assertEquals("Zone a", inside.zoneName());

		SoundStartResult outside = start(villagerAt(100));
		assertEquals(PlayStatus.MUTED, outside.status());
		assertNull(outside.zone());
		assertFalse(outside.cancel(), "a moving source could still enter zone a, so it starts silently (P12)");
	}

	@Test
	void p3ClosestAnchorWins() {
		profile(List.of(), sphere("a", 3, 10, villagers("ra").withVolume(20)), sphere("b", 9, 20, villagers("rb").withVolume(80)));
		assertEquals(0.2F, start(villagerAt(0)).factor(), 1e-6);
	}

	@Test
	void p6FadeEdgeBlendsTheVolume() {
		Zone a = new Zone("a", "A", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(10),
				List.of(Rule.of("ra", "*").withMute(true))).withEdge(ZoneEdge.fade(4));
		profile(List.of(), a);
		// 2 blocks inside a 4-block fade: half way.
		assertEquals(0.5F, start(villagerAt(8)).factor(), 1e-3);
	}

	@Test
	void p10FixedZonesStayInTheirDimension() {
		profile(List.of(), sphere("a", 0, 10, villagers("ra").withMute(true)));
		SoundStart s = villagerAt(0);
		s.dimension = NETHER;
		SoundStartResult r = start(s);
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(1.0F, r.factor(), 1e-6);
	}

	@Test
	void p12ZoneMuteStartsSilentlySoThePerTickLoopCanBringItBack() {
		profile(List.of(), sphere("a", 0, 10, villagers("ra").withMute(true)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.MUTED, r.status());
		assertFalse(r.cancel());
		assertEquals(0F, r.factor(), 1e-6);
	}

	@Test
	void globalMuteStartsSilentlyWhenAZoneInThisWorldCouldOverrideIt() {
		Zone far = sphere("far", 500, 10, villagers("z1").withVolume(50));
		profile(List.of(villagers("g1").withMute(true)), far);
		assertFalse(start(villagerAt(0)).cancel());

		SoundStart nether = villagerAt(0);
		nether.dimension = NETHER;
		assertTrue(start(nether).cancel(), "a zone in another dimension can never apply");
	}

	@Test
	void globalMuteIsCancelledWhenZonesOnlySetOtherProperties() {
		profile(List.of(villagers("g1").withMute(true)), sphere("a", 0, 10, villagers("z1").withCue(true)));
		assertTrue(start(villagerAt(0)).cancel());
	}

	@Test
	void globalMuteOfANonPositionalSoundIsCancelled() {
		profile(List.of(villagers("g1").withMute(true)), sphere("a", 0, 10, villagers("z1").withVolume(50)));
		SoundStart s = villagerAt(0);
		s.positional = false;
		s.range = Float.POSITIVE_INFINITY;
		assertTrue(start(s).cancel(), "zones never apply to non-positional sounds");
	}

	@Test
	void bubblesCanOverrideAGlobalMuteAnywhere() {
		Zone bubble = sphere("b", 0, 10, villagers("z1").withVolume(50)).asBubble();
		profile(List.of(villagers("g1").withMute(true)), bubble);
		SoundStart s = villagerAt(100);
		s.dimension = NETHER;
		assertFalse(start(s).cancel());
	}

	@Test
	void disabledZonesNeverOverride() {
		Zone a = new Zone("a", "A", false, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(10),
				List.of(villagers("z1").withVolume(50)));
		profile(List.of(villagers("g1").withMute(true)), a);
		assertTrue(start(villagerAt(0)).cancel());
	}

	// --- Boost ---

	@Test
	void boostAbove100() {
		profile(List.of(villagers("g1").withVolume(150)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.BOOSTED, r.status());
		assertEquals(1.5F, r.factor(), 1e-6);
	}

	@Test
	void boostStopsAt100WhenTheDeviceCannotBoost() {
		profile(List.of(villagers("g1").withVolume(150)));
		decider.setBoostSupported(false);
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertEquals(150, r.volumePercent(), 1e-6, "the decided volume is kept for the trace");
	}

	@Test
	void p8CueOnlyZoneKeepsTheGlobalBoost() {
		profile(List.of(villagers("g1").withVolume(150)), sphere("a", 0, 10, villagers("z1").withCue(true)));
		assertEquals(1.5F, start(villagerAt(0)).factor(), 1e-6);
	}

	// --- Range gate ---

	@Test
	void outOfRangeSoundsAreDecidedButNotHeard() {
		profile(List.of(villagers("g1").withVolume(50).withLimit(new Limit(1, 1000, false)).withCue(true)));
		SoundStart s = villagerAt(0);
		s.distanceSq = 20 * 20;
		for (int i = 0; i < 5; i++) {
			SoundStartResult r = start(s, 1_000 + i);
			assertFalse(r.audible());
			assertEquals(PlayStatus.PLAYING, r.status(), "never limited: unheard plays do not use up the limit");
			assertEquals(0.5F, r.factor(), 1e-6);
		}
		assertNull(lastPlays.get(VILLAGER), "not heard, not logged");
		// The limit is still free for the first sound the player can hear.
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_010).status());
	}

	@Test
	void rangeIsStrictlyCloser() {
		profile(List.of());
		SoundStart s = villagerAt(0);
		s.distanceSq = 16 * 16;
		assertFalse(start(s).audible());
		s.distanceSq = 15.99 * 15.99;
		assertTrue(start(s).audible());
	}

	// --- Repeat limit ---

	@Test
	void limitDropsPlaysOverTheCap() {
		profile(List.of(villagers("g1").withLimit(TWO_PER_SECOND)));
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_100).status());
		SoundStartResult dropped = start(villagerAt(0), 1_200);
		assertEquals(PlayStatus.LIMITED, dropped.status());
		assertTrue(dropped.cancel());
		assertTrue(lastPlays.get(VILLAGER).dropped());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 2_000).status(), "the window slides");
		assertEquals(1, decider.limiter().droppedCount("g1"));
	}

	@Test
	void p4MutedWithLimitFromAnotherZone() {
		Zone a = sphere("a", 2, 10, villagers("ra").withMute(true).withCue(true));
		Zone b = sphere("b", 6, 10, villagers("rb").withLimit(TWO_PER_SECOND));
		profile(List.of(), a, b);
		assertEquals(PlayStatus.MUTED, start(villagerAt(0), 1_000).status());
		assertTrue(out.cue());
		assertEquals(PlayStatus.MUTED, start(villagerAt(0), 1_001).status());
		SoundStartResult third = start(villagerAt(0), 1_002);
		assertEquals(PlayStatus.LIMITED, third.status());
		assertSame(b, third.zone(), "the zone that dropped it is the one shown");
		assertFalse(third.cue(), "dropped plays never produce a cue (FR-6.8)");
	}

	@Test
	void zoneLimitWinsInsideTheZone() {
		// FR-6 AC: global limit 5/s, zone limit 1/s on the same sound: inside the zone, at most 1 per second plays.
		profile(List.of(villagers("g1").withLimit(new Limit(5, 1000, false))),
				sphere("a", 0, 10, villagers("z1").withLimit(new Limit(1, 1000, false))));
		int played = 0;
		for (int i = 0; i < 10; i++) {
			if (start(villagerAt(0), 1_000 + i * 50).status() != PlayStatus.LIMITED) {
				played++;
			}
		}
		assertEquals(1, played);
	}

	@Test
	void globalAndZoneRulesWithTheSameIdHaveTheirOwnCounters() {
		// Rule IDs are unique within one list only, so a global "r1" and zone a's "r1" are different rules (FR-6.7).
		Rule global = Rule.of("r1", "minecraft:entity.cow.*").withLimit(new Limit(1, 1000, false));
		Zone a = sphere("a", 0, 10, villagers("r1").withLimit(new Limit(1, 1000, false)));
		profile(List.of(global), a);

		SoundStart cow = villagerAt(100); // outside the zone: the global rule decides
		cow.soundId = "minecraft:entity.cow.ambient";
		assertEquals(PlayStatus.PLAYING, start(cow, 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_001).status(), "zone a's r1 has not played yet");
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0), 1_002).status());
		assertEquals(1, decider.limiter().droppedCount(SoundStartDecider.limiterKey(a, "r1")));
		assertEquals(0, decider.limiter().droppedCount("r1"));
	}

	@Test
	void twoZonesWithTheSameRuleIdHaveTheirOwnCounters() {
		Zone a = sphere("a", 0, 10, villagers("r1").withLimit(new Limit(1, 1000, false)));
		Zone b = sphere("b", 100, 10, villagers("r1").withLimit(new Limit(1, 1000, false)));
		profile(List.of(), a, b);
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(100), 1_001).status());
	}

	@Test
	void countersSurviveASaveThatKeepsTheRule() {
		Rule g1 = villagers("g1").withLimit(new Limit(1, 1000, false));
		profile(List.of(g1));
		start(villagerAt(0), 1_000);
		profile(List.of(g1.withCue(true))); // the player saves an unrelated change
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0), 1_001).status());
	}

	@Test
	void countersOfDeletedRulesAreForgotten() {
		profile(List.of(villagers("g1").withLimit(TWO_PER_SECOND), Rule.of("g2", "*cow*").withLimit(TWO_PER_SECOND)));
		start(villagerAt(0), 1_000);
		SoundStart cow = villagerAt(0);
		cow.soundId = "minecraft:entity.cow.ambient";
		start(cow, 1_000);
		assertEquals(2, decider.limiter().ruleIds().size());
		profile(List.of(villagers("g1").withLimit(TWO_PER_SECOND)));
		assertEquals(java.util.Set.of("g1"), decider.limiter().ruleIds());
	}

	@Test
	void perSourceLimitCountsEachBlock() {
		profile(List.of(villagers("g1").withLimit(new Limit(1, 1000, true))));
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(3), 1_001).status());
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0.5), 1_002).status());
	}

	// --- Cues ---

	@Test
	void mutedSoundWithCueOnShowsItsSubtitle() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		SoundStartResult r = start(villagerAt(0));
		assertTrue(r.cue());
		assertFalse(r.cueUsesSoundId());
		assertTrue(lastPlays.get(VILLAGER).cueShown());
	}

	@Test
	void noCueWhenTheRuleHasCueOff() {
		profile(List.of(villagers("g1").withMute(true)));
		assertFalse(start(villagerAt(0)).cue());
	}

	@Test
	void noCueForAudibleSounds() {
		profile(List.of(villagers("g1").withVolume(10).withCue(true)));
		assertFalse(start(villagerAt(0)).cue());
	}

	@Test
	void noCueOutOfRange() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		SoundStart s = villagerAt(0);
		s.distanceSq = 25 * 25;
		assertFalse(start(s).cue());
	}

	@Test
	void masterSwitchOffHidesAllCues() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		decider.setCueSettings(new CueSettings(false, true, true));
		assertFalse(start(villagerAt(0)).cue());
	}

	@Test
	void soundsWithoutSubtitlesShowTheirIdOnlyWhenAllowed() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		SoundStart s = villagerAt(0);
		s.hasSubtitle = false;
		assertFalse(start(s).cue());

		decider.setCueSettings(new CueSettings(true, true, true));
		SoundStartResult r = start(s);
		assertTrue(r.cue());
		assertTrue(r.cueUsesSoundId());
	}

	@Test
	void fullyFadedZoneMuteShowsACueButAPartlyFadedOneDoesNot() {
		Zone a = new Zone("a", "A", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(10),
				List.of(villagers("z1").withMute(true).withCue(true))).withEdge(ZoneEdge.fade(4));
		profile(List.of(), a);
		assertTrue(start(villagerAt(0)).cue(), "full weight at the centre: volume 0");
		assertFalse(start(villagerAt(8)).cue(), "half way through the fade the sound is still audible");
	}

	// --- Trace ---

	@Test
	void theLastPlayIsRecordedForTheTrace() {
		profile(List.of(villagers("g1").withVolume(60)));
		SoundStart s = villagerAt(1);
		s.px = 2;
		s.py = 3;
		s.pz = 4;
		start(s);
		LastPlay play = lastPlays.get(VILLAGER);
		assertNotNull(play);
		assertEquals(60, play.volumePercent(), 1e-6);
		assertEquals(1, play.x(), 1e-9);
		assertEquals(2, play.px(), 1e-9);
		assertEquals(WORLD, play.worldKey());
		assertFalse(play.dropped());
		assertFalse(play.cueShown());
	}
}
