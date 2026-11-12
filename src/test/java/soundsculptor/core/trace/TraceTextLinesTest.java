package soundsculptor.core.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.4: each trace line says which rule or zone it is about, so the panel can put edit controls on it. */
class TraceTextLinesTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final LastPlay PLAY = new LastPlay(true, WORLD, OVERWORLD, 0, 0, 0, 60, false, false);

	private static Zone sphere(String id, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(1, 0, 0), new Shape.Sphere(20), List.of(rules));
	}

	private static SoundTrace trace(List<SetEntry> global, List<Zone> zones) {
		return SoundTracer.trace(new Precedence(global, zones, soundsculptor.core.zones.ZoneEdge.hard()), AMBIENT, PLAY, false, false);
	}

	@Test
	void linesJoinToTheCopyTraceText() {
		SoundTrace t = trace(List.of(SetEntry.of("w", "*villager*").withMute(true), SetEntry.of("e", AMBIENT).withVolume(60)),
				List.of(sphere("z", SetEntry.of("zr", "*villager*").withVolume(20)), sphere("empty")));
		StringBuilder joined = new StringBuilder();
		for (TraceText.Line line : TraceText.lines(t, EN)) {
			joined.append(line.text()).append('\n');
		}
		assertEquals(TraceText.of(t, EN), joined.toString());
	}

	@Test
	void ruleZoneAndNoRuleLinesCarryTheirTargets() {
		SetEntry wildcard = SetEntry.of("w", "*villager*").withMute(true);
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		SetEntry zoneRule = SetEntry.of("zr", "*villager*").withVolume(20);
		Zone z = sphere("z", zoneRule);
		Zone empty = sphere("empty");
		List<TraceText.Line> lines = TraceText.lines(trace(List.of(wildcard, exact), List.of(z, empty)), EN);

		List<TraceText.Target> targets = new ArrayList<>();
		for (TraceText.Line line : lines) {
			if (line.target() != null) {
				targets.add(line.target());
			}
		}
		assertEquals(List.of(
				new TraceText.RuleTarget(RuleLocation.global("e"), exact),
				new TraceText.RuleTarget(RuleLocation.global("w"), wildcard),
				new TraceText.ZoneTarget(z),
				new TraceText.RuleTarget(RuleLocation.inZone("z", "zr"), zoneRule),
				new TraceText.ZoneTarget(empty),
				new TraceText.NoRuleTarget("empty")), targets);
		int global = indexOf(lines, "Global rules");
		assertEquals("  minecraft:entity.villager.ambient: Volume 60%", lines.get(global + 1).text());
		assertNull(lines.get(global + 2).target(), "the 'Decides loudness' line has nothing to edit");
	}

	@Test
	void noGlobalRuleGivesAnAddRuleLineForTheGlobalList() {
		List<TraceText.Line> lines = TraceText.lines(trace(List.of(), List.of()), EN);
		int global = indexOf(lines, "Global rules");
		assertEquals("  No rule matches this sound", lines.get(global + 1).text());
		assertEquals(new TraceText.NoRuleTarget(null), lines.get(global + 1).target());
		assertNull(lines.get(indexOf(lines, "  No zone affects this sound")).target(), "'No zone affects this sound' has nothing to edit");
	}

	private static int indexOf(List<TraceText.Line> lines, String text) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).text().equals(text)) {
				return i;
			}
		}
		throw new AssertionError("no line " + text + " in " + lines);
	}
}
