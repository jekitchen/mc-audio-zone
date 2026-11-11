package audiozone.core.profile;

import audiozone.core.Message;
import audiozone.core.outline.OutlineMode;
import audiozone.core.outline.ZoneColors;
import audiozone.core.rules.InvalidPatternException;
import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.rules.SoundPattern;
import audiozone.core.zones.AnchorType;
import audiozone.core.zones.Point;
import audiozone.core.zones.Precedence;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.core.zones.ZoneEdge;
import audiozone.core.zones.ZoneMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes the profile file (PRD section 7.5). Human-readable JSON; unset rule properties are omitted
 * ("this rule does not set it"); unknown fields are ignored, with a warning, so newer optional fields do not break
 * older readers.
 *
 * <p>Reading checks every value (PRD FR-11.6) and names the problem and its place in plain words, for example
 * "Zone 3, field radius: 900 is above the maximum 256." Older schema versions are migrated first (PRD 7.6).
 */
public final class ProfileCodec {
	/** At most this many rules in a profile, counting global and zone rules (PRD FR-11.6). */
	public static final int MAX_RULES = 1000;
	/** Coordinates beyond the world border are refused. */
	static final double MAX_COORDINATE = 30_000_000;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "id", "name", "worldKey", "settings", "globalRules",
			"zones", "lastJoined", "boostConfirmed");
	private static final Set<String> SETTINGS_FIELDS = Set.of("showDistance", "cueDefault", "outlines", "edge");
	private static final Set<String> RULE_FIELDS = Set.of("id", "enabled", "pattern", "mute", "volume", "cue", "limit");
	private static final Set<String> LIMIT_FIELDS = Set.of("count", "windowMs", "perSource");
	private static final Set<String> ZONE_FIELDS = Set.of("id", "name", "enabled", "color", "anchor", "shape", "mode", "edge",
			"rules");
	private static final Set<String> ANCHOR_FIELDS = Set.of("type", "x", "y", "z", "dimension", "worldKey");
	private static final Set<String> EDGE_FIELDS = Set.of("mode", "fade");
	private static final Map<String, Set<String>> SHAPE_FIELDS = Map.of(
			"sphere", Set.of("type", "radius"),
			"box", Set.of("type", "width", "height", "depth"),
			"cylinder", Set.of("type", "radius", "height"));

	private ProfileCodec() {
	}

	/** A profile read from a file, and the unknown fields that were ignored. */
	public record Read(Profile profile, List<Message> warnings) {
		public Read {
			warnings = List.copyOf(warnings);
		}
	}

	public static String toJson(Profile profile) {
		return toJson(profile, null);
	}

	/**
	 * The profile file's text, recording its world key (FR-11.3): file names are made safe for the file system, so the
	 * key cannot be read back from the name. The key is not part of {@link Profile}; it is ignored when reading.
	 *
	 * @param worldKey the key to record, or null for none (exports and older callers)
	 */
	public static String toJson(Profile profile, String worldKey) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", Profile.SCHEMA_VERSION);
		root.addProperty("name", profile.name());
		if (worldKey != null) {
			root.addProperty("worldKey", worldKey);
		}
		if (profile.lastJoined() != null) {
			root.addProperty("lastJoined", profile.lastJoined().toString());
		}
		root.addProperty("boostConfirmed", profile.boostConfirmed());
		root.add("settings", settingsToJson(profile.settings()));
		root.add("globalRules", rulesToJson(profile.globalRules()));
		JsonArray zones = new JsonArray();
		for (Zone zone : profile.zones()) {
			zones.add(zoneToJson(zone, profile.colorOf(zone.id())));
		}
		root.add("zones", zones);
		return GSON.toJson(root) + "\n";
	}

	private static JsonObject settingsToJson(ProfileSettings s) {
		JsonObject json = new JsonObject();
		json.addProperty("showDistance", s.showDistance());
		json.addProperty("cueDefault", s.cueDefault());
		json.addProperty("outlines", s.outlines().name().toLowerCase(Locale.ROOT));
		JsonObject edge = new JsonObject();
		boolean fade = s.defaultEdge().mode() == ZoneEdge.Mode.FADE;
		edge.addProperty("mode", fade ? "fade" : "hard");
		if (fade) {
			edge.addProperty("fade", s.defaultEdge().fadeDistance());
		}
		json.add("edge", edge);
		return json;
	}

	private static JsonArray rulesToJson(List<Rule> rules) {
		JsonArray array = new JsonArray();
		for (Rule rule : rules) {
			array.add(ruleToJson(rule));
		}
		return array;
	}

	private static JsonObject ruleToJson(Rule rule) {
		JsonObject json = new JsonObject();
		json.addProperty("id", rule.id());
		json.addProperty("enabled", rule.enabled());
		json.addProperty("pattern", rule.pattern().text());
		if (rule.mute() != null) {
			json.addProperty("mute", rule.mute());
		}
		if (rule.volume() != null) {
			json.addProperty("volume", rule.volume());
		}
		if (rule.cue() != null) {
			json.addProperty("cue", rule.cue());
		}
		if (rule.limit() != null) {
			JsonObject limit = new JsonObject();
			limit.addProperty("count", rule.limit().count());
			limit.addProperty("windowMs", rule.limit().windowMs());
			limit.addProperty("perSource", rule.limit().perSource());
			json.add("limit", limit);
		}
		return json;
	}

	private static JsonObject zoneToJson(Zone zone, Integer color) {
		JsonObject json = new JsonObject();
		json.addProperty("id", zone.id());
		if (zone.name() != null) {
			json.addProperty("name", zone.name());
		}
		json.addProperty("enabled", zone.enabled());
		if (color != null) {
			json.addProperty("color", ZoneColors.format(color));
		}
		JsonObject anchor = new JsonObject();
		if (zone.isBubble()) {
			// A bubble follows the player: it carries no position or world data (PRD FR-8.2).
			anchor.addProperty("type", "bubble");
		} else {
			anchor.addProperty("type", "fixed");
			anchor.addProperty("x", zone.anchor().x());
			anchor.addProperty("y", zone.anchor().y());
			anchor.addProperty("z", zone.anchor().z());
			anchor.addProperty("dimension", zone.dimension());
			anchor.addProperty("worldKey", zone.worldKey());
		}
		json.add("anchor", anchor);
		JsonObject shape = new JsonObject();
		switch (zone.shape()) {
			case Shape.Sphere s -> {
				shape.addProperty("type", "sphere");
				shape.addProperty("radius", s.radius());
			}
			case Shape.Box b -> {
				shape.addProperty("type", "box");
				shape.addProperty("width", b.width());
				shape.addProperty("height", b.height());
				shape.addProperty("depth", b.depth());
			}
			case Shape.Cylinder c -> {
				shape.addProperty("type", "cylinder");
				shape.addProperty("radius", c.radius());
				shape.addProperty("height", c.height());
			}
		}
		json.add("shape", shape);
		json.addProperty("mode", zone.mode() == ZoneMode.INSIDE ? "inside" : "outside");
		JsonObject edge = new JsonObject();
		edge.addProperty("mode", switch (zone.edge().mode()) {
			case DEFAULT -> "default";
			case HARD -> "hard";
			case FADE -> "fade";
		});
		if (zone.edge().mode() == ZoneEdge.Mode.FADE) {
			edge.addProperty("fade", zone.edge().fadeDistance());
		}
		json.add("edge", edge);
		json.add("rules", rulesToJson(zone.rules()));
		return json;
	}

	/**
	 * The world key recorded in a profile file's text, without reading the rest. Empty if the file does not record one
	 * (files saved before keys were recorded) or cannot be parsed.
	 */
	public static Optional<String> worldKeyOf(String text) {
		try {
			JsonElement key = parse(text).get("worldKey");
			if (key != null && key.isJsonPrimitive() && key.getAsJsonPrimitive().isString() && !key.getAsString().isEmpty()) {
				return Optional.of(key.getAsString());
			}
		} catch (ProfileFormatException e) {
			// Unreadable: the Profiles tab shows the file without a key.
		}
		return Optional.empty();
	}

	/** @throws ProfileFormatException if the text is not a valid profile of a known schema version */
	public static Profile fromJson(String text) throws ProfileFormatException {
		return read(text, version -> { }).profile();
	}

	/**
	 * Reads a profile file, migrating an older schema first.
	 *
	 * @param backup called once with the file's version before an older file is migrated; if it throws, nothing is read
	 */
	public static Read read(String text, ProfileMigrations.Backup backup) throws ProfileFormatException {
		return read(parse(text), backup);
	}

	/** Parses the text into a JSON object, with plain-language errors. */
	static JsonObject parse(String text) throws ProfileFormatException {
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) {
				throw ProfileText.problem(ProfileText.NOT_PROFILE);
			}
			return parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			throw new ProfileFormatException(jsonProblem(e), e);
		}
	}

	static Read read(JsonObject original, ProfileMigrations.Backup backup) throws ProfileFormatException {
		try {
			return readChecked(original, backup);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// Every value is checked above; this is a last guard so a bad file can never crash the game.
			throw new ProfileFormatException(Message.of(ProfileText.NOT_ALLOWED), e);
		}
	}

	private static Read readChecked(JsonObject original, ProfileMigrations.Backup backup) throws ProfileFormatException {
		JsonObject root = ProfileMigrations.CURRENT.migrate(original, backup);
		List<Message> warnings = new ArrayList<>();
		Fields top = new Fields(root, null, warnings, ROOT_FIELDS);
		String name = top.has("name") ? top.string("name") : "Default";
		Instant lastJoined = top.has("lastJoined") ? top.instant("lastJoined") : null;
		boolean boostConfirmed = top.has("boostConfirmed") && top.bool("boostConfirmed");
		ProfileSettings settings = top.has("settings")
				? readSettings(top.object("settings", Message.of(ProfileText.PLACE_SETTINGS), SETTINGS_FIELDS))
				: ProfileSettings.DEFAULTS;
		JsonArray ruleArray = top.has("globalRules") ? top.array("globalRules") : new JsonArray();
		JsonArray zoneArray = top.has("zones") ? top.array("zones") : new JsonArray();
		if (zoneArray.size() > Precedence.MAX_ZONES) {
			throw ProfileText.problem(ProfileText.TOO_MANY_ZONES, zoneArray.size(), Precedence.MAX_ZONES);
		}
		List<Rule> rules = new ArrayList<>();
		for (int i = 0; i < ruleArray.size(); i++) {
			rules.add(readRule(top.element(ruleArray, i, Message.of(ProfileText.PLACE_RULE, i + 1), RULE_FIELDS)));
		}
		int ruleCount = rules.size();
		List<Zone> zones = new ArrayList<>();
		Map<String, Integer> colors = new LinkedHashMap<>();
		Map<String, Integer> firstUse = new HashMap<>();
		for (int i = 0; i < zoneArray.size(); i++) {
			Fields z = top.element(zoneArray, i, Message.of(ProfileText.PLACE_ZONE, i + 1), ZONE_FIELDS);
			Zone zone = readZone(z);
			Integer earlier = firstUse.putIfAbsent(zone.id(), i + 1);
			if (earlier != null) {
				throw z.problem("id", Message.of(ProfileText.ID_USED, quote(zone.id()), earlier));
			}
			if (z.has("color")) {
				colors.put(zone.id(), z.color("color"));
			}
			ruleCount += zone.rules().size();
			zones.add(zone);
		}
		if (ruleCount > MAX_RULES) {
			throw ProfileText.problem(ProfileText.TOO_MANY_RULES, ruleCount, MAX_RULES);
		}
		return new Read(new Profile(name, settings, rules, zones, colors, lastJoined, boostConfirmed), warnings);
	}

	/** Profile settings; a missing value takes its default. */
	private static ProfileSettings readSettings(Fields s) throws ProfileFormatException {
		ProfileSettings settings = ProfileSettings.DEFAULTS;
		if (s.has("showDistance")) {
			settings = settings.withShowDistance(s.bool("showDistance"));
		}
		if (s.has("cueDefault")) {
			settings = settings.withCueDefault(s.bool("cueDefault"));
		}
		if (s.has("outlines")) {
			String mode = s.choice("outlines", "outlines", "always", "editing", "never");
			settings = settings.withOutlines(OutlineMode.valueOf(mode.toUpperCase(Locale.ROOT)));
		}
		if (s.has("edge")) {
			Fields e = s.object("edge", s.where(), EDGE_FIELDS);
			String mode = e.has("mode") ? e.choice("mode", "edge mode", "hard", "fade") : "hard";
			double fade = e.has("fade") ? e.number("fade", ZoneEdge.MIN_FADE, ZoneEdge.MAX_FADE) : ZoneEdge.DEFAULT_FADE;
			settings = settings.withDefaultEdge(mode.equals("fade") ? ZoneEdge.fade(fade) : ZoneEdge.hard());
		}
		return settings;
	}

	private static Rule readRule(Fields r) throws ProfileFormatException {
		String id = r.string("id");
		String patternText = r.string("pattern");
		SoundPattern pattern;
		try {
			pattern = SoundPattern.of(patternText);
		} catch (InvalidPatternException e) {
			throw new ProfileFormatException(r.problem("pattern", Message.of(e.error().translationKey())).problem(), e);
		}
		boolean enabled = !r.has("enabled") || r.bool("enabled");
		Boolean mute = r.has("mute") ? r.bool("mute") : null;
		Integer volume = r.has("volume") ? r.integer("volume", Rule.MIN_VOLUME, Rule.MAX_VOLUME) : null;
		Boolean cue = r.has("cue") ? r.bool("cue") : null;
		Limit limit = null;
		if (r.has("limit")) {
			Fields l = r.object("limit", r.where(), LIMIT_FIELDS);
			limit = new Limit(l.integer("count", Limit.MIN_COUNT, Limit.MAX_COUNT),
					l.integer("windowMs", Limit.MIN_WINDOW_MS, Limit.MAX_WINDOW_MS),
					l.has("perSource") && l.bool("perSource"));
		}
		return new Rule(id, enabled, pattern, mute, volume, limit, cue);
	}

	private static Zone readZone(Fields z) throws ProfileFormatException {
		String id = z.string("id");
		String name = z.has("name") ? z.string("name") : null;
		boolean enabled = !z.has("enabled") || z.bool("enabled");
		Fields a = z.object("anchor", z.where(), ANCHOR_FIELDS);
		String anchorType = a.choice("type", "anchor type", "fixed", "bubble");
		String worldKey = "";
		String dimension = "";
		Point anchor = new Point(0, 0, 0);
		if (anchorType.equals("fixed")) {
			anchor = new Point(a.number("x", -MAX_COORDINATE, MAX_COORDINATE), a.number("y", -MAX_COORDINATE, MAX_COORDINATE),
					a.number("z", -MAX_COORDINATE, MAX_COORDINATE));
			dimension = a.string("dimension");
			worldKey = a.string("worldKey");
		}
		Shape shape = readShape(z);
		ZoneMode mode = ZoneMode.INSIDE;
		if (z.has("mode")) {
			mode = z.choice("mode", "mode", "inside", "outside").equals("inside") ? ZoneMode.INSIDE : ZoneMode.OUTSIDE;
		}
		ZoneEdge edge = ZoneEdge.DEFAULT;
		if (z.has("edge")) {
			Fields e = z.object("edge", z.where(), EDGE_FIELDS);
			String edgeMode = e.has("mode") ? e.choice("mode", "edge mode", "default", "hard", "fade") : "default";
			double fade = e.has("fade") ? e.number("fade", ZoneEdge.MIN_FADE, ZoneEdge.MAX_FADE) : ZoneEdge.DEFAULT_FADE;
			edge = switch (edgeMode) {
				case "hard" -> ZoneEdge.hard();
				case "fade" -> ZoneEdge.fade(fade);
				default -> ZoneEdge.DEFAULT;
			};
		}
		List<Rule> rules = new ArrayList<>();
		if (z.has("rules")) {
			JsonArray array = z.array("rules");
			for (int i = 0; i < array.size(); i++) {
				rules.add(readRule(z.element(array, i, Message.of(ProfileText.PLACE_ZONE_RULE, z.where(), i + 1),
						RULE_FIELDS)));
			}
		}
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, rules, mode, edge,
				anchorType.equals("fixed") ? AnchorType.FIXED : AnchorType.BUBBLE);
	}

	private static Shape readShape(Fields z) throws ProfileFormatException {
		JsonElement raw = z.require("shape");
		if (!raw.isJsonObject()) {
			throw z.problem("shape", Message.of(ProfileText.NOT_OBJECT, describe(raw)));
		}
		JsonObject json = raw.getAsJsonObject();
		Fields probe = new Fields(json, z.where(), null, null);
		String type = probe.choice("type", "shape type", "sphere", "box", "cylinder");
		Fields s = z.object("shape", z.where(), SHAPE_FIELDS.get(type));
		return switch (type) {
			case "sphere" -> new Shape.Sphere(s.number("radius", Shape.Sphere.MIN_RADIUS, Shape.Sphere.MAX_RADIUS));
			case "box" -> new Shape.Box(s.number("width", Shape.Box.MIN_SIDE, Shape.Box.MAX_SIDE),
					s.number("height", Shape.Box.MIN_SIDE, Shape.Box.MAX_SIDE),
					s.number("depth", Shape.Box.MIN_SIDE, Shape.Box.MAX_SIDE));
			default -> new Shape.Cylinder(s.number("radius", Shape.Cylinder.MIN_RADIUS, Shape.Cylinder.MAX_RADIUS),
					s.number("height", Shape.Cylinder.MIN_HEIGHT, Shape.Cylinder.MAX_HEIGHT));
		};
	}

	/** Plain words with a location, e.g. "The file is not valid JSON (line 1, column 44).", instead of parser jargon. */
	static Message jsonProblem(JsonParseException e) {
		String detail = e.getMessage() == null ? "" : e.getMessage();
		Matcher where = LOCATION.matcher(detail);
		return where.find()
				? Message.of(ProfileText.NOT_JSON_AT, where.group(1), where.group(2))
				: Message.of(ProfileText.NOT_JSON);
	}

	private static final Pattern LOCATION = Pattern.compile("line ([0-9]+) column ([0-9]+)");

	/** A number as a person writes it: 900, 0.5, 12.25. */
	static String format(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}

	private static String quote(String text) {
		return "\"" + text + "\"";
	}

	private static String describe(JsonElement value) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			return format(value.getAsDouble());
		}
		String text = value.toString();
		return text.length() > 40 ? text.substring(0, 37) + "..." : text;
	}

	/** One JSON object being read, with its place in the file for messages ("Zone 3", "Rule 2"; null at the top). */
	private static final class Fields {
		private final JsonObject json;
		private final Message where;
		private final List<Message> warnings;

		Fields(JsonObject json, Message where, List<Message> warnings, Set<String> known) {
			this.json = json;
			this.where = where;
			this.warnings = warnings;
			if (warnings != null && known != null) {
				for (String key : json.keySet()) {
					if (!known.contains(key)) {
						warnings.add(where == null ? Message.of(ProfileText.UNKNOWN_FIELD, quote(key))
								: Message.of(ProfileText.UNKNOWN_FIELD_AT, where, quote(key)));
					}
				}
			}
		}

		Message where() {
			return where;
		}

		boolean has(String field) {
			return json.has(field) && !json.get(field).isJsonNull();
		}

		/** {@code field}'s problem at this place; the field is named as in the file (or a short label). */
		ProfileFormatException problem(String field, Message what) {
			return new ProfileFormatException(where == null ? Message.of(ProfileText.TOP_FIELD, field, what)
					: Message.of(ProfileText.FIELD, where, field, what));
		}

		private ProfileFormatException problem(String field, String key, Object... args) {
			return problem(field, Message.of(key, args));
		}

		JsonElement require(String field) throws ProfileFormatException {
			if (!has(field)) {
				throw problem(field, ProfileText.MISSING);
			}
			return json.get(field);
		}

		private JsonPrimitive primitive(String field) throws ProfileFormatException {
			JsonElement value = require(field);
			return value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
		}

		String string(String field) throws ProfileFormatException {
			JsonPrimitive p = primitive(field);
			if (p == null || !p.isString()) {
				throw problem(field, ProfileText.NOT_TEXT, describe(json.get(field)));
			}
			return p.getAsString();
		}

		/** A moment in ISO-8601 form, e.g. {@code 2026-11-09T13:37:46Z}. */
		Instant instant(String field) throws ProfileFormatException {
			String text = string(field);
			try {
				return Instant.parse(text);
			} catch (DateTimeParseException e) {
				throw problem(field, ProfileText.NOT_DATE, quote(text));
			}
		}

		boolean bool(String field) throws ProfileFormatException {
			JsonPrimitive p = primitive(field);
			if (p == null || !p.isBoolean()) {
				throw problem(field, ProfileText.NOT_BOOLEAN, describe(json.get(field)));
			}
			return p.getAsBoolean();
		}

		double number(String field, double min, double max) throws ProfileFormatException {
			JsonPrimitive p = primitive(field);
			if (p == null || !p.isNumber()) {
				throw problem(field, ProfileText.NOT_NUMBER, describe(json.get(field)));
			}
			double value = p.getAsDouble();
			if (!Double.isFinite(value)) {
				throw problem(field, ProfileText.NOT_NUMBER, describe(p));
			}
			if (value > max) {
				throw problem(field, ProfileText.ABOVE_MAX, format(value), format(max));
			}
			if (value < min) {
				throw problem(field, ProfileText.BELOW_MIN, format(value), format(min));
			}
			return value;
		}

		int integer(String field, int min, int max) throws ProfileFormatException {
			double value = number(field, min, max);
			if (value != Math.rint(value)) {
				throw problem(field, ProfileText.NOT_WHOLE, format(value));
			}
			return (int) value;
		}

		/** One of the allowed words, e.g. "sphere"; {@code label} names the field in the message ("shape type"). */
		String choice(String field, String label, String... allowed) throws ProfileFormatException {
			if (!has(field)) {
				throw problem(label, ProfileText.MISSING);
			}
			JsonPrimitive p = primitive(field);
			if (p != null && p.isString()) {
				for (String a : allowed) {
					if (a.equals(p.getAsString())) {
						return a;
					}
				}
			}
			// The allowed values are words in the file, so they are listed as written.
			throw problem(label, ProfileText.NOT_CHOICE, describe(json.get(field)), String.join(", ", allowed));
		}

		int color(String field) throws ProfileFormatException {
			String text = string(field);
			ZoneColors.Parsed parsed = ZoneColors.parseHex(text);
			if (parsed.error() != null) {
				throw problem(field, ProfileText.NOT_COLOR, quote(text));
			}
			return parsed.rgb();
		}

		Fields object(String field, Message childWhere, Set<String> known) throws ProfileFormatException {
			JsonElement value = require(field);
			if (!value.isJsonObject()) {
				throw problem(field, ProfileText.NOT_OBJECT, describe(value));
			}
			return new Fields(value.getAsJsonObject(), childWhere, warnings, known);
		}

		JsonArray array(String field) throws ProfileFormatException {
			JsonElement value = require(field);
			if (!value.isJsonArray()) {
				throw problem(field, ProfileText.NOT_LIST, describe(value));
			}
			return value.getAsJsonArray();
		}

		/** Item {@code i} of a list, which must be an object; {@code itemWhere} is its place ("Rule 3"). */
		Fields element(JsonArray array, int i, Message itemWhere, Set<String> known) throws ProfileFormatException {
			JsonElement value = array.get(i);
			if (!value.isJsonObject()) {
				throw ProfileText.problem(ProfileText.ITEM_NOT_OBJECT, itemWhere, describe(value));
			}
			return new Fields(value.getAsJsonObject(), itemWhere, warnings, known);
		}
	}
}
