package audiozone.core.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.zones.Point;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;

/**
 * The "stress" profile for the noisy-farm test (PRD 11.2 step 2): 50 global rules mixing exact IDs and wildcards,
 * and 64 zones (the maximum) overlapping a 9 x 9 pen centred on (0, {@value #PEN_Y}, 0). It also lists the sound IDs
 * such a farm plays. Generated from a fixed seed, so every run measures the same thing.
 *
 * <p>Used by the budget tests now. Once the profile file can hold zones, a dev command can load it for the in-game
 * test. Fade, Outside and cylinder zones join it with v1 (M13).
 */
public final class StressProfile {
	public static final double PEN_Y = 64;
	private static final long SEED = 11_2L;

	private static final String[] PROFESSIONS = {"armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
			"fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith"};

	private static final String[] ZONE_PATTERNS = {"*villager*", "minecraft:entity.chicken.*", "minecraft:entity.cow.*",
			"*", "minecraft:entity.*.ambient", "minecraft:block.*", "minecraft:entity.iron_golem.*",
			"minecraft:entity.villager.work_*", "*:block.*.step", "othermod:*"};

	private StressProfile() {
	}

	/** Sound IDs a villager, chicken, cow and iron golem farm with a redstone clock plays, plus some modded ones. */
	public static List<String> soundIds() {
		List<String> ids = new ArrayList<>();
		for (String s : new String[] {"ambient", "hurt", "death", "yes", "no", "trade", "celebrate", "work_masterwork"}) {
			ids.add("minecraft:entity.villager." + s);
		}
		for (String p : PROFESSIONS) {
			ids.add("minecraft:entity.villager.work_" + p);
		}
		for (String mob : new String[] {"chicken", "cow", "sheep", "pig", "zombie", "wandering_trader"}) {
			for (String s : new String[] {"ambient", "hurt", "death", "step"}) {
				ids.add("minecraft:entity." + mob + "." + s);
			}
		}
		ids.add("minecraft:entity.chicken.egg");
		ids.add("minecraft:entity.cow.milk");
		for (String s : new String[] {"attack", "damage", "death", "hurt", "repair", "step"}) {
			ids.add("minecraft:entity.iron_golem." + s);
		}
		for (String block : new String[] {"stone", "wood", "grass", "gravel", "sand", "metal", "glass", "wool"}) {
			for (String s : new String[] {"step", "break", "place", "hit", "fall"}) {
				ids.add("minecraft:block." + block + "." + s);
			}
		}
		for (String s : new String[] {"comparator.click", "piston.extend", "piston.contract", "dispenser.dispense",
				"lever.click", "stone_button.click_on", "note_block.harp", "note_block.basedrum", "chest.open", "chest.close"}) {
			ids.add("minecraft:block." + s);
		}
		for (int i = 0; i < 20; i++) {
			ids.add("othermod:machine.sound_" + i);
		}
		return List.copyOf(ids);
	}

	/** 50 global rules: every fourth farm sound as an exact rule, plus wildcard families. */
	public static List<Rule> globalRules() {
		Random random = new Random(SEED);
		List<String> ids = soundIds();
		List<Rule> rules = new ArrayList<>();
		for (int i = 0; i < ids.size() && rules.size() < 25; i += 4) {
			rules.add(withSettings(Rule.of("g" + rules.size(), ids.get(i)), random));
		}
		String[] wildcards = {"minecraft:entity.villager.*", "*villager*", "minecraft:entity.chicken.*",
				"minecraft:entity.cow.*", "minecraft:entity.*.ambient", "minecraft:entity.*.hurt", "minecraft:entity.*.death",
				"*:block.*.step", "minecraft:block.*.break", "minecraft:block.piston.*", "minecraft:block.note_block.*",
				"minecraft:entity.iron_golem.*", "minecraft:entity.villager.work_*", "othermod:*", "othermod:machine.*",
				"*:*.step", "minecraft:block.*", "minecraft:entity.*", "*golem*", "*.click*", "minecraft:block.chest.*",
				"minecraft:entity.zombie.*", "*sheep*", "minecraft:entity.pig.*", "*"};
		for (String w : wildcards) {
			rules.add(withSettings(Rule.of("g" + rules.size(), w), random));
		}
		return List.copyOf(rules);
	}

	/** 64 zones overlapping the pen: half spheres, half boxes, anchors scattered up to 20 blocks away. */
	public static List<Zone> zones(String worldKey, String dimension) {
		Random random = new Random(SEED + 1);
		List<Zone> zones = new ArrayList<>(Precedence.MAX_ZONES);
		for (int i = 0; i < Precedence.MAX_ZONES; i++) {
			double ax = random.nextInt(41) - 20;
			double ay = PEN_Y + random.nextInt(11) - 5;
			double az = random.nextInt(41) - 20;
			Shape shape;
			if (i % 2 == 0) {
				double reach = Math.sqrt(ax * ax + (PEN_Y - ay) * (PEN_Y - ay) + az * az);
				shape = new Shape.Sphere(Math.min(Shape.Sphere.MAX_RADIUS, Math.ceil(reach) + 2 + random.nextInt(18)));
			} else {
				shape = new Shape.Box(
						side(Math.abs(ax), random), side(Math.abs(PEN_Y - ay), random), side(Math.abs(az), random));
			}
			List<Rule> rules = new ArrayList<>();
			int count = 1 + random.nextInt(3);
			for (int r = 0; r < count; r++) {
				String pattern = ZONE_PATTERNS[random.nextInt(ZONE_PATTERNS.length)];
				rules.add(withSettings(Rule.of("z" + i + "r" + r, pattern), random));
			}
			zones.add(new Zone("z" + i, "Stress " + i, true, worldKey, dimension, new Point(ax, ay, az), shape, rules));
		}
		return List.copyOf(zones);
	}

	/** A box side long enough to reach the pen centre from {@code offset} away, plus some slack. */
	private static double side(double offset, Random random) {
		return Math.min(Shape.Box.MAX_SIDE, 2 * offset + 2 + random.nextInt(30));
	}

	/** A mix of mute, volume, limit and cue settings, like a real profile. */
	private static Rule withSettings(Rule rule, Random random) {
		Rule r = switch (random.nextInt(4)) {
			case 0 -> rule.withMute(true);
			case 1 -> rule.withVolume(random.nextInt(101));
			case 2 -> rule.withVolume(random.nextInt(101)).withLimit(new Limit(1 + random.nextInt(10), 1000, random.nextBoolean()));
			default -> rule.withCue(true);
		};
		return random.nextInt(3) == 0 ? r.withCue(random.nextBoolean()) : r;
	}
}
