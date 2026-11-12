package soundsculptor.core.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

/**
 * The "stress" profile for the noisy-farm test (PRD 11.2 step 2): 50 global rules mixing exact IDs and wildcards,
 * and 64 zones (the maximum, every kind) that all affect a 9 x 9 pen centred on (0, {@value #PEN_Y}, 0). It also lists the sound IDs
 * such a farm plays. Generated from a fixed seed, so every run measures the same thing.
 *
 * <p>Used by the budget tests now. Once the profile file can hold zones, a dev command can load it for the in-game
 * test.
 */
public final class StressProfile {
	public static final double PEN_Y = 64;
	/** The player's eye position, standing in the middle of the pen: the anchor of the profile's personal bubbles. */
	public static final Point PLAYER = new Point(0, PEN_Y + 1.62, 0);
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
	public static List<SetEntry> globalRules() {
		Random random = new Random(SEED);
		List<String> ids = soundIds();
		List<SetEntry> rules = new ArrayList<>();
		for (int i = 0; i < ids.size() && rules.size() < 25; i += 4) {
			rules.add(withSettings(SetEntry.of("g" + rules.size(), ids.get(i)), random));
		}
		String[] wildcards = {"minecraft:entity.villager.*", "*villager*", "minecraft:entity.chicken.*",
				"minecraft:entity.cow.*", "minecraft:entity.*.ambient", "minecraft:entity.*.hurt", "minecraft:entity.*.death",
				"*:block.*.step", "minecraft:block.*.break", "minecraft:block.piston.*", "minecraft:block.note_block.*",
				"minecraft:entity.iron_golem.*", "minecraft:entity.villager.work_*", "othermod:*", "othermod:machine.*",
				"*:*.step", "minecraft:block.*", "minecraft:entity.*", "*golem*", "*.click*", "minecraft:block.chest.*",
				"minecraft:entity.zombie.*", "*sheep*", "minecraft:entity.pig.*", "*"};
		for (String w : wildcards) {
			rules.add(withSettings(SetEntry.of("g" + rules.size(), w), random));
		}
		return List.copyOf(rules);
	}

	/**
	 * 64 zones that all affect the pen: Inside spheres, boxes and cylinders scattered up to 20 blocks away, Outside
	 * spheres 40 to 60 blocks away, and personal bubbles around the player. Every third zone fades.
	 */
	public static List<Zone> zones(String worldKey, String dimension) {
		Random random = new Random(SEED + 1);
		List<Zone> zones = new ArrayList<>(Precedence.MAX_ZONES);
		for (int i = 0; i < Precedence.MAX_ZONES; i++) {
			double ax = random.nextInt(41) - 20;
			double ay = PEN_Y + random.nextInt(11) - 5;
			double az = random.nextInt(41) - 20;
			double dy = Math.abs(PEN_Y - ay);
			Shape shape;
			ZoneMode mode = ZoneMode.INSIDE;
			boolean bubble = false;
			switch (i % 8) {
				case 0, 1 -> {
					double reach = Math.sqrt(ax * ax + dy * dy + az * az);
					shape = new Shape.Sphere(Math.min(Shape.Sphere.MAX_RADIUS, Math.ceil(reach) + 2 + random.nextInt(18)));
				}
				case 2, 3 -> shape = new Shape.Box(side(Math.abs(ax), random), side(dy, random), side(Math.abs(az), random));
				case 4, 5 -> shape = new Shape.Cylinder(
						Math.min(Shape.Cylinder.MAX_RADIUS, Math.ceil(Math.hypot(ax, az)) + 2 + random.nextInt(18)), side(dy, random));
				case 6 -> {
					// Outside zone around somewhere else: the pen is outside it, so it is affected.
					double angle = random.nextDouble() * 2 * Math.PI;
					double distance = 40 + random.nextInt(21);
					ax = Math.round(Math.cos(angle) * distance);
					az = Math.round(Math.sin(angle) * distance);
					shape = new Shape.Sphere(5 + random.nextInt(16));
					mode = ZoneMode.OUTSIDE;
				}
				default -> {
					shape = new Shape.Sphere(10 + random.nextInt(31));
					bubble = true;
				}
			}
			List<SetEntry> rules = new ArrayList<>();
			int count = 1 + random.nextInt(3);
			for (int r = 0; r < count; r++) {
				String pattern = ZONE_PATTERNS[random.nextInt(ZONE_PATTERNS.length)];
				rules.add(withSettings(SetEntry.of("z" + i + "r" + r, pattern), random));
			}
			Zone zone = new Zone("z" + i, "Stress " + i, true, worldKey, dimension, new Point(ax, ay, az), shape, rules)
					.withMode(mode);
			if (i % 3 == 0) {
				zone = zone.withEdge(ZoneEdge.fade(1 + random.nextInt(8)));
			}
			zones.add(bubble ? zone.asBubble() : zone);
		}
		return List.copyOf(zones);
	}

	/** A box side long enough to reach the pen centre from {@code offset} away, plus some slack. */
	private static double side(double offset, Random random) {
		return Math.min(Shape.Box.MAX_SIDE, 2 * offset + 2 + random.nextInt(30));
	}

	/** A mix of mute, volume, limit and cue settings, like a real profile. */
	private static SetEntry withSettings(SetEntry rule, Random random) {
		SetEntry r = switch (random.nextInt(4)) {
			case 0 -> rule.withMute(true);
			case 1 -> rule.withVolume(random.nextInt(101));
			case 2 -> rule.withVolume(random.nextInt(101)).withLimit(new Limit(1 + random.nextInt(10), 1000, random.nextBoolean()));
			default -> rule.withCue(true);
		};
		return random.nextInt(3) == 0 ? r.withCue(random.nextBoolean()) : r;
	}
}
