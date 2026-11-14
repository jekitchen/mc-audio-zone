package soundsculptor.core.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

/**
 * The "stress" profile for the noisy-farm test (PRD 11.2 step 2), and the "typical" profile measured beside it (PRD 9.1).
 * Generated from fixed seeds, so every run measures the same thing.
 *
 * <ul>
 *   <li><b>Stress:</b> Everywhere attaches 2 library Sound Sets of 25 entries each, mixing single sounds and groups;
 *       64 zones (the maximum, every kind) that all affect a 9 x 9 pen centred on (0, {@value #PEN_Y}, 0) each attach 1
 *       to 3 sets from a pool of 8, so most pool sets are shared by several zones. The pool is drawn so the zones weigh
 *       what the 64 zones of 1 to 3 rules weighed before Sound Sets (see {@link #POOL_SEED}), keeping the worst case
 *       comparable with PRD 9.1's.</li>
 *   <li><b>Typical:</b> the shape it had before Sound Sets, so its timings compare with earlier runs: Everywhere
 *       attaches one set of 20 single-sound entries, and each zone attaches one set of its own with the 1 to 3 entries it
 *       used to hold as rules. The zones are the stress zones (same seed, same geometry).</li>
 * </ul>
 * It also lists the sound IDs such a farm plays. Both use {@link #library()} as the library.
 */
public final class StressProfile {
	public static final double PEN_Y = 64;
	/** The player's eye position, standing in the middle of the pen: the anchor of the zones around the player. */
	public static final Point PLAYER = new Point(0, PEN_Y + 1.62, 0);
	/** The two sets attached to Everywhere. */
	public static final List<String> EVERYWHERE_SET_IDS = List.of("stress-everywhere-1", "stress-everywhere-2");
	/** The set attached to Everywhere in the typical profile. */
	public static final String TYPICAL_SET_ID = "stress-typical";
	/** Single-sound entries in the typical Everywhere set. */
	public static final int TYPICAL_ENTRIES = 20;
	/** Zones attach sets from this many. */
	public static final int POOL_SIZE = 8;
	private static final long SEED = 11_2L;
	/**
	 * Seed of the zone pool. Pool entries are drawn exactly as the zones' own rules were before Sound Sets (same
	 * patterns, same settings), but each set holds 1 or 2 of them, because a zone now attaches 1 to 3 sets: on average
	 * that matches about as many zones per sound as the old 1 to 3 rules did. The seed was picked from a scan: its pool
	 * has no match-all {@code *} entry (shared by a dozen zones, it would make all of them match every sound), and over
	 * the budget tests' 255 sounds its zones match 18.58 zones and 7.33 zone limits per sound, against 19.5 and 6.7 before
	 * Sound Sets. {@code StressProfileTest} keeps both within 15%.
	 */
	private static final long POOL_SEED = 323;

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

	/**
	 * The 50 entries of Everywhere's two sets, first set first: every fourth farm sound as a single-sound entry, plus
	 * group families, so each set mixes both.
	 */
	public static List<SetEntry> everywhereEntries() {
		Random random = new Random(SEED);
		List<String> ids = soundIds();
		List<SetEntry> singles = new ArrayList<>();
		for (int i = 0; i < ids.size() && singles.size() < 25; i += 4) {
			singles.add(withSettings(SetEntry.of("g" + singles.size(), ids.get(i)), random));
		}
		String[] wildcards = {"minecraft:entity.villager.*", "*villager*", "minecraft:entity.chicken.*",
				"minecraft:entity.cow.*", "minecraft:entity.*.ambient", "minecraft:entity.*.hurt", "minecraft:entity.*.death",
				"*:block.*.step", "minecraft:block.*.break", "minecraft:block.piston.*", "minecraft:block.note_block.*",
				"minecraft:entity.iron_golem.*", "minecraft:entity.villager.work_*", "othermod:*", "othermod:machine.*",
				"*:*.step", "minecraft:block.*", "minecraft:entity.*", "*golem*", "*.click*", "minecraft:block.chest.*",
				"minecraft:entity.zombie.*", "*sheep*", "minecraft:entity.pig.*", "*"};
		List<SetEntry> groups = new ArrayList<>();
		for (String w : wildcards) {
			groups.add(withSettings(SetEntry.of("g" + (singles.size() + groups.size()), w), random));
		}
		List<SetEntry> entries = new ArrayList<>(singles.subList(0, 13));
		entries.addAll(groups.subList(0, 12));
		entries.addAll(singles.subList(13, singles.size()));
		entries.addAll(groups.subList(12, groups.size()));
		return List.copyOf(entries);
	}

	/** Everywhere's sets: the two {@link #EVERYWHERE_SET_IDS}, both on. */
	public static PlaceSets everywhere() {
		return new PlaceSets(EVERYWHERE_SET_IDS.stream().map(id -> new Attachment(id, true)).toList(), null);
	}

	/** The typical profile's Everywhere: the {@link #TYPICAL_SET_ID} set of {@value #TYPICAL_ENTRIES} single sounds. */
	public static PlaceSets typicalEverywhere() {
		return PlaceSets.EMPTY.attach(TYPICAL_SET_ID);
	}

	/**
	 * Every set either profile attaches: Everywhere's two sets of 25, the pool of {@value #POOL_SIZE}, the typical
	 * Everywhere set, and each zone's own set for the typical profile.
	 */
	public static SetLibrary library() {
		List<SoundSet> sets = new ArrayList<>();
		List<SetEntry> everywhere = everywhereEntries();
		sets.add(new SoundSet(EVERYWHERE_SET_IDS.get(0), "Stress everywhere 1", everywhere.subList(0, 25)));
		sets.add(new SoundSet(EVERYWHERE_SET_IDS.get(1), "Stress everywhere 2", everywhere.subList(25, 50)));
		sets.addAll(pool());
		// The first 20 single sounds, in order: the 20 global rules the typical profile had before Sound Sets.
		sets.add(new SoundSet(TYPICAL_SET_ID, "Stress typical",
				everywhere.stream().filter(e -> e.pattern().isExact()).limit(TYPICAL_ENTRIES).toList()));
		sets.addAll(generate("", "").zoneSets());
		return new SetLibrary(sets);
	}

	/** {@value #POOL_SIZE} sets of 1 or 2 group entries each, which zones attach; see {@link #POOL_SEED}. */
	private static List<SoundSet> pool() {
		Random random = new Random(POOL_SEED);
		List<SoundSet> pool = new ArrayList<>(POOL_SIZE);
		for (int s = 0; s < POOL_SIZE; s++) {
			List<SetEntry> entries = new ArrayList<>();
			int count = 1 + random.nextInt(2);
			for (int e = 0; e < count; e++) {
				String pattern = ZONE_PATTERNS[random.nextInt(ZONE_PATTERNS.length)];
				entries.add(withSettings(SetEntry.of("p" + s + "e" + e, pattern), random));
			}
			pool.add(new SoundSet(poolId(s), "Stress pool " + (s + 1), entries));
		}
		return pool;
	}

	private static String poolId(int s) {
		return "stress-pool-" + (s + 1);
	}

	private static String zoneSetId(int i) {
		return "stress-zone-" + i;
	}

	/**
	 * The stress profile's 64 zones that all affect the pen: Inside boxes and cylinders scattered up to 20 blocks away,
	 * Outside cylinders 40 to 60 blocks away, and boxes centred on the player. Every third zone fades. Each attaches 1 to
	 * 3 different sets from the pool in {@link #library()}.
	 */
	public static List<Zone> zones(String worldKey, String dimension) {
		return generate(worldKey, dimension).stress();
	}

	/** The same 64 zones for the typical profile: each attaches only its own set of 1 to 3 entries. */
	public static List<Zone> typicalZones(String worldKey, String dimension) {
		return generate(worldKey, dimension).typical();
	}

	private record Generated(List<Zone> stress, List<Zone> typical, List<SoundSet> zoneSets) {
	}

	/**
	 * Builds the zones from one random stream in the same order as before Sound Sets, so each zone's own entries are what
	 * they were then. The stress zones' pool sets come from a second stream that does not disturb it.
	 */
	private static Generated generate(String worldKey, String dimension) {
		Random random = new Random(SEED + 1);
		Random pick = new Random(SEED + 3);
		List<Zone> stress = new ArrayList<>(Precedence.MAX_ZONES);
		List<Zone> typical = new ArrayList<>(Precedence.MAX_ZONES);
		List<SoundSet> zoneSets = new ArrayList<>(Precedence.MAX_ZONES);
		for (int i = 0; i < Precedence.MAX_ZONES; i++) {
			double ax = random.nextInt(41) - 20;
			double ay = PEN_Y + random.nextInt(11) - 5;
			double az = random.nextInt(41) - 20;
			double dy = Math.abs(PEN_Y - ay);
			Shape shape;
			ZoneMode mode = ZoneMode.INSIDE;
			switch (i % 8) {
				case 0, 1 -> {
					// A cube or an upright cylinder whose half-size reaches past the pen centre.
					double reach = Math.sqrt(ax * ax + dy * dy + az * az);
					double r = Math.min(Shape.Cylinder.MAX_RADIUS, Math.ceil(reach) + 2 + random.nextInt(18));
					shape = i % 8 == 0 ? cube(r) : new Shape.Cylinder(r, Math.min(Shape.Cylinder.MAX_HEIGHT, 2 * r));
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
					double r = 5 + random.nextInt(16);
					shape = new Shape.Cylinder(r, 2 * r);
					mode = ZoneMode.OUTSIDE;
				}
				default -> {
					// Centred on the player: the zones nearest to most sounds in the pen.
					shape = cube(10 + random.nextInt(31));
					ax = PLAYER.x();
					ay = PLAYER.y();
					az = PLAYER.z();
				}
			}
			List<SetEntry> own = new ArrayList<>();
			int count = 1 + random.nextInt(3);
			for (int r = 0; r < count; r++) {
				String pattern = ZONE_PATTERNS[random.nextInt(ZONE_PATTERNS.length)];
				own.add(withSettings(SetEntry.of("z" + i + "r" + r, pattern), random));
			}
			zoneSets.add(new SoundSet(zoneSetId(i), "Stress zone " + i, own));
			PlaceSets pooled = PlaceSets.EMPTY;
			int pooledCount = 1 + pick.nextInt(3);
			while (pooled.librarySetCount() < pooledCount) {
				pooled = pooled.attach(poolId(pick.nextInt(POOL_SIZE)));
			}
			Zone zone = new Zone("z" + i, "Stress " + i, true, worldKey, dimension, new Point(ax, ay, az), shape, pooled)
					.withMode(mode);
			if (i % 3 == 0) {
				zone = zone.withEdge(ZoneEdge.fade(1 + random.nextInt(8)));
			}
			stress.add(zone);
			typical.add(zone.withSets(PlaceSets.EMPTY.attach(zoneSetId(i))));
		}
		return new Generated(List.copyOf(stress), List.copyOf(typical), List.copyOf(zoneSets));
	}

	/** A cube with half-side {@code half}. */
	private static Shape.Box cube(double half) {
		double side = Math.min(Shape.Box.MAX_SIDE, 2 * half);
		return new Shape.Box(side, side, side);
	}

	/** A box side long enough to reach the pen centre from {@code offset} away, plus some slack. */
	private static double side(double offset, Random random) {
		return Math.min(Shape.Box.MAX_SIDE, 2 * offset + 2 + random.nextInt(30));
	}

	/** A mix of mute, volume, limit and cue settings, like a real profile. */
	private static SetEntry withSettings(SetEntry entry, Random random) {
		SetEntry r = switch (random.nextInt(4)) {
			case 0 -> entry.withMute(true);
			case 1 -> entry.withVolume(random.nextInt(101));
			case 2 -> entry.withVolume(random.nextInt(101)).withLimit(new Limit(1 + random.nextInt(10), 1000, random.nextBoolean()));
			default -> entry.withCue(true);
		};
		return random.nextInt(3) == 0 ? r.withCue(random.nextBoolean()) : r;
	}
}
