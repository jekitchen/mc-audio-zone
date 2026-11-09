package audiozone.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import audiozone.core.TestEnglish;
import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.zones.Point;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.5: "Copy trace" gives the panel as plain text, in the FR-14.2 order. */
class TraceTextTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";

	private static LastPlay playAt(double x, float volume, boolean dropped, boolean cueShown) {
		return new LastPlay(true, WORLD, OVERWORLD, x, 0, 0, volume, dropped, cueShown);
	}

	private static Zone sphere(String id, double x, double radius, Rule... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Sphere(radius), List.of(rules));
	}

	private static String text(Precedence p, LastPlay play, boolean bypass) {
		return TraceText.of(SoundTracer.trace(p, AMBIENT, play, bypass, false), EN);
	}

	@Test
	void globalRulesShowTheWinnerItsReasonAndWhoWasOverridden() {
		Rule wildcard = Rule.of("w", "*villager*").withMute(true);
		Rule exact = Rule.of("e", AMBIENT).withVolume(60);
		String text = text(new Precedence(List.of(wildcard, exact), List.of()), playAt(0, 60, false, false), false);
		assertEquals(String.join("\n",
				"Audio Zone trace for minecraft:entity.villager.ambient",
				"Global rules",
				"  minecraft:entity.villager.ambient: Volume 60%",
				"    Decides loudness (Exact sound ID)",
				"  *:*villager*: Muted",
				"    loudness overridden by minecraft:entity.villager.ambient",
				"Zones",
				"  No zone affects this sound",
				"Result",
				"  Volume 60%",
				"  No repeat limit",
				"  Last play was allowed",
				"  Cue off",
				"  No cue was shown",
				""), text);
	}

	@Test
	void zonesShowModeWeightDistanceTheirRulesAndTheWinners() {
		Zone near = sphere("near", 3, 20, Rule.of("rn", "*villager*").withVolume(20));
		Zone far = sphere("far", 9, 20, Rule.of("rf", "*villager*").withVolume(80));
		String text = text(new Precedence(List.of(), List.of(far, near)), playAt(0, 20, false, false), false);
		assertTrue(text.contains(String.join("\n",
				"Zones",
				"  far (Inside): weight 100%, 9 blocks from its anchor",
				"    *:*villager*: Volume 80%",
				"      Decides loudness (Only matching rule)",
				"  near (Inside): weight 100%, 3 blocks from its anchor",
				"    *:*villager*: Volume 20%",
				"      Decides loudness (Only matching rule)",
				"Deciding zones",
				"  loudness: near (Closest anchor)",
				"Result",
				"  Volume 20%")), text);
		assertTrue(text.startsWith("Audio Zone trace for " + AMBIENT + "\nGlobal rules\n  No rule matches this sound\n"), text);
	}

	@Test
	void noticesComeFirst() {
		Precedence p = new Precedence(List.of(Rule.of("m", AMBIENT).withMute(true)), List.of());
		List<String> lines = text(p, playAt(0, 100, false, false), true).lines().toList();
		assertEquals("  Bypass is on: nothing is changed.", lines.get(1));
		assertEquals("Global rules", lines.get(2));
	}

	@Test
	void limitDroppedPlayAndCueShown() {
		Rule rule = Rule.of("l", AMBIENT).withMute(true).withCue(true).withLimit(new Limit(3, 1000, true));
		String text = text(new Precedence(List.of(rule), List.of()), playAt(0, 0, true, true), false);
		assertTrue(text.endsWith(String.join("\n",
				"Result",
				"  Volume 0%",
				"  At most 3 per 1 s, per source",
				"  Last play was dropped by the repeat limit",
				"  Cue on",
				"  A cue was shown",
				"")), text);
	}

	@Test
	void notHeardYetLeavesOutWhatHappenedAtTheLastPlay() {
		String text = text(new Precedence(List.of(Rule.of("m", AMBIENT).withMute(true)), List.of()), null, false);
		assertTrue(text.contains("Not heard yet; zones depend on where it plays"), text);
		assertFalse(text.contains("Last play"), text);
		assertFalse(text.contains("cue was shown"), text);
	}

	@Test
	void fadeWeightAndUnnamedZones() {
		Zone fade = new Zone("z7", "", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Sphere(20),
				List.of(Rule.of("r", "*").withMute(true))).withEdge(audiozone.core.zones.ZoneEdge.fade(8));
		String text = text(new Precedence(List.of(), List.of(fade)), playAt(16, 50, false, false), false);
		assertTrue(text.contains("  z7 (Inside): weight 50%, 16 blocks from its anchor"), text);
	}
}
