package soundsculptor.core.sets;

import soundsculptor.core.Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The file format of a library Sound Set (PRD FR-11.8, section 7.5): one JSON object with the schema version, the ID,
 * the name and the entries in order. A property an entry does not set is left out.
 *
 * <p>Reading checks every value (PRD FR-11.6) and names the problem and its place in plain words, for example "Sound
 * Set 'Farm animals', entry 3, level: 250 is above the maximum 200." Unknown fields are ignored with a warning, so
 * newer optional fields do not break older readers. Every failure is a {@link SetFormatException}.
 */
public final class SetCodec {
	public static final int SCHEMA_VERSION = 3;
	/** Library set IDs: lowercase letters, digits and hyphens. */
	public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9-]+");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "id", "name", "entries");
	private static final Set<String> QUICK_FIELDS = Set.of("id", "entries");
	private static final Set<String> ENTRY_FIELDS = Set.of("id", "enabled", "target", "mute", "volume", "limit", "cue");
	private static final Set<String> LIMIT_FIELDS = Set.of("count", "windowMs", "perSource");
	private static final Pattern LOCATION = Pattern.compile("line ([0-9]+) column ([0-9]+)");

	private SetCodec() {
	}

	/** A set read from a file, and the unknown fields that were ignored. */
	public record Read(SoundSet set, List<Message> warnings) {
		public Read {
			warnings = List.copyOf(warnings);
		}
	}

	public static String toJson(SoundSet set) {
		return GSON.toJson(toJsonObject(set)) + "\n";
	}

	/** The set as a JSON object, also used by exports. */
	public static JsonObject toJsonObject(SoundSet set) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("id", set.id());
		root.addProperty("name", set.name());
		root.add("entries", entriesToJson(set.entries()));
		return root;
	}

	/**
	 * A place's Quick changes as a profile file holds it inline (PRD 7.5): {@code {"id": "quick", "entries": [...]}},
	 * each entry as in a set file.
	 */
	public static JsonObject quickToJson(SoundSet quick) {
		JsonObject root = new JsonObject();
		root.addProperty("id", SoundSet.QUICK_ID);
		root.add("entries", entriesToJson(quick.entries()));
		return root;
	}

	private static JsonArray entriesToJson(List<SetEntry> list) {
		JsonArray entries = new JsonArray();
		for (SetEntry entry : list) {
			entries.add(entryToJson(entry));
		}
		return entries;
	}

	private static JsonObject entryToJson(SetEntry entry) {
		JsonObject json = new JsonObject();
		json.addProperty("id", entry.id());
		json.addProperty("enabled", entry.enabled());
		json.addProperty("target", entry.pattern().text());
		if (entry.mute() != null) {
			json.addProperty("mute", entry.mute());
		}
		if (entry.volume() != null) {
			json.addProperty("volume", entry.volume());
		}
		if (entry.limit() != null) {
			JsonObject limit = new JsonObject();
			limit.addProperty("count", entry.limit().count());
			limit.addProperty("windowMs", entry.limit().windowMs());
			limit.addProperty("perSource", entry.limit().perSource());
			json.add("limit", limit);
		}
		if (entry.cue() != null) {
			json.addProperty("cue", entry.cue());
		}
		return json;
	}

	/** @throws SetFormatException if the text is not a valid Sound Set of a known schema version */
	public static SoundSet read(String text) throws SetFormatException {
		return readWithWarnings(text).set();
	}

	/**
	 * @param where how messages name the set, e.g. {@code "Sound Set 'Farm animals'"}
	 * @throws SetFormatException if the object is not a valid Sound Set of a known schema version
	 */
	public static SoundSet read(JsonObject o, String where) throws SetFormatException {
		return readWithWarnings(o, where).set();
	}

	/** Like {@link #read(String)}, also returning a warning for each unknown field. */
	public static Read readWithWarnings(String text) throws SetFormatException {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) {
				throw new SetFormatException(Message.of(parsed.isJsonNull() ? SetText.EMPTY : SetText.NOT_SET));
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			throw new SetFormatException(jsonProblem(e), e);
		}
		return readWithWarnings(root, null);
	}

	/** Like {@link #read(JsonObject, String)}, also returning a warning for each unknown field. */
	public static Read readWithWarnings(JsonObject o, String where) throws SetFormatException {
		try {
			return readChecked(o, where);
		} catch (IllegalArgumentException | IllegalStateException | ClassCastException e) {
			// Every value is checked above; this is a last guard so a bad file can never crash the game.
			throw new SetFormatException(Message.of(SetText.NOT_ALLOWED), e);
		}
	}

	private static Read readChecked(JsonObject root, String whereText) throws SetFormatException {
		List<Message> warnings = new ArrayList<>();
		Fields top = new Fields(root, placeOf(root, whereText), warnings, ROOT_FIELDS);
		checkSchema(root);
		String id = top.string("id", SetText.F_ID);
		if (!ID_PATTERN.matcher(id).matches()) {
			throw top.problem(SetText.F_ID, Message.of(SetText.BAD_ID, quote(id)));
		}
		if (SoundSet.QUICK_ID.equals(id)) {
			throw top.problem(SetText.F_ID, Message.of(SetText.RESERVED_ID, quote(id)));
		}
		String name = top.string("name", SetText.F_NAME);
		if (name.length() > SoundSet.MAX_NAME_LENGTH) {
			throw new SetFormatException(Message.of(SetText.NAME_TOO_LONG, top.where(), SoundSet.MAX_NAME_LENGTH));
		}
		return new Read(new SoundSet(id, name, readEntries(top)), warnings);
	}

	/**
	 * Reads a place's Quick changes from a profile file ({@link #quickToJson}), checked like a set file's entries.
	 *
	 * @param where    how messages name it, e.g. "Everywhere, Quick changes"
	 * @param warnings receives a warning for each unknown field
	 * @throws SetFormatException if the ID is not {@code "quick"} or an entry is not valid
	 */
	public static SoundSet readQuick(JsonObject o, Object where, List<Message> warnings) throws SetFormatException {
		try {
			Fields top = new Fields(o, where, warnings, QUICK_FIELDS);
			String id = top.string("id", SetText.F_ID);
			if (!SoundSet.QUICK_ID.equals(id)) {
				throw top.problem(SetText.F_ID, Message.of(SetText.NOT_QUICK, quote(id)));
			}
			return SoundSet.quick(readEntries(top));
		} catch (IllegalArgumentException | IllegalStateException | ClassCastException e) {
			// Every value is checked; this is a last guard so a bad file can never crash the game.
			throw new SetFormatException(Message.of(SetText.NOT_ALLOWED), e);
		}
	}

	/** The {@code entries} list: at most {@link SoundSet#MAX_ENTRIES}, entry IDs unique. */
	private static List<SetEntry> readEntries(Fields top) throws SetFormatException {
		JsonArray array = top.array("entries", SetText.F_ENTRIES);
		if (array.size() > SoundSet.MAX_ENTRIES) {
			throw new SetFormatException(Message.of(SetText.TOO_MANY_ENTRIES, top.where(), array.size(), SoundSet.MAX_ENTRIES));
		}
		List<SetEntry> entries = new ArrayList<>(array.size());
		Map<String, Integer> firstUse = new HashMap<>();
		for (int i = 0; i < array.size(); i++) {
			Fields f = top.element(array, i, ENTRY_FIELDS);
			SetEntry entry = readEntry(f);
			Integer earlier = firstUse.putIfAbsent(entry.id(), i + 1);
			if (earlier != null) {
				throw f.problem(SetText.F_ID, Message.of(SetText.ENTRY_ID_USED, quote(entry.id()), earlier));
			}
			entries.add(entry);
		}
		return entries;
	}

	/** How messages name the set: the caller's text, else "Sound Set 'name'" from the file. */
	private static Object placeOf(JsonObject root, String whereText) {
		if (whereText != null) {
			return whereText;
		}
		JsonElement name = root.get("name");
		boolean text = name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString();
		return Message.of(SetText.PLACE_SET, text ? name.getAsString() : "?");
	}

	private static void checkSchema(JsonObject root) throws SetFormatException {
		JsonElement version = root.get("schemaVersion");
		if (version == null || version.isJsonNull()) {
			throw new SetFormatException(Message.of(SetText.NO_SCHEMA));
		}
		if (version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()) {
			double v = version.getAsDouble();
			if (v == Math.rint(v) && v > SCHEMA_VERSION) {
				throw new SetFormatException(Message.of(SetText.NEWER, Fields.format(v)));
			}
			if (v == SCHEMA_VERSION) {
				return;
			}
		}
		throw new SetFormatException(Message.of(SetText.UNKNOWN_SCHEMA, Fields.describe(version)));
	}

	private static SetEntry readEntry(Fields e) throws SetFormatException {
		String id = e.string("id", SetText.F_ID);
		String target = e.string("target", SetText.F_TARGET);
		SoundPattern pattern;
		try {
			pattern = SoundPattern.of(target);
		} catch (InvalidPatternException ex) {
			throw e.problem(SetText.F_TARGET, Message.of(ex.error().translationKey()));
		}
		boolean enabled = !e.has("enabled") || e.bool("enabled", SetText.F_ENABLED);
		Boolean mute = e.has("mute") ? e.bool("mute", SetText.F_MUTE) : null;
		Integer volume = e.has("volume") ? e.integer("volume", SetText.F_LEVEL, SetEntry.MIN_VOLUME, SetEntry.MAX_VOLUME) : null;
		Boolean cue = e.has("cue") ? e.bool("cue", SetText.F_CUE) : null;
		Limit limit = null;
		if (e.has("limit")) {
			Fields l = e.object("limit", SetText.F_LIMIT, LIMIT_FIELDS);
			limit = new Limit(l.integer("count", SetText.F_LIMIT_COUNT, Limit.MIN_COUNT, Limit.MAX_COUNT),
					l.integer("windowMs", SetText.F_LIMIT_WINDOW, Limit.MIN_WINDOW_MS, Limit.MAX_WINDOW_MS),
					l.has("perSource") && l.bool("perSource", SetText.F_LIMIT_PER_SOURCE));
		}
		return new SetEntry(id, enabled, pattern, mute, volume, limit, cue);
	}

	private static Message jsonProblem(JsonParseException e) {
		String detail = e.getMessage() == null ? "" : e.getMessage();
		Matcher where = LOCATION.matcher(detail);
		return where.find() ? Message.of(SetText.NOT_JSON_AT, where.group(1), where.group(2)) : Message.of(SetText.NOT_JSON);
	}

	private static String quote(String text) {
		return "\"" + text + "\"";
	}

	/** One JSON object being read, with its place in the file for messages ("Sound Set 'X'", "... entry 3"). */
	private static final class Fields {
		private final JsonObject json;
		private final Object where;
		private final List<Message> warnings;

		Fields(JsonObject json, Object where, List<Message> warnings, Set<String> known) {
			this.json = json;
			this.where = where;
			this.warnings = warnings;
			for (String key : json.keySet()) {
				if (!known.contains(key)) {
					warnings.add(Message.of(SetText.UNKNOWN_FIELD_AT, where, quote(key)));
				}
			}
		}

		Object where() {
			return where;
		}

		boolean has(String field) {
			return json.has(field) && !json.get(field).isJsonNull();
		}

		/** The problem with the field named {@code label} (a lang key) at this place. */
		SetFormatException problem(String label, Message what) {
			return new SetFormatException(Message.of(SetText.FIELD, where, Message.of(label), what));
		}

		private JsonElement require(String field, String label) throws SetFormatException {
			if (!has(field)) {
				throw problem(label, Message.of(SetText.MISSING));
			}
			return json.get(field);
		}

		private JsonPrimitive primitive(String field, String label) throws SetFormatException {
			JsonElement value = require(field, label);
			return value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
		}

		String string(String field, String label) throws SetFormatException {
			JsonPrimitive p = primitive(field, label);
			if (p == null || !p.isString()) {
				throw problem(label, Message.of(SetText.NOT_TEXT, describe(json.get(field))));
			}
			return p.getAsString();
		}

		boolean bool(String field, String label) throws SetFormatException {
			JsonPrimitive p = primitive(field, label);
			if (p == null || !p.isBoolean()) {
				throw problem(label, Message.of(SetText.NOT_BOOLEAN, describe(json.get(field))));
			}
			return p.getAsBoolean();
		}

		int integer(String field, String label, int min, int max) throws SetFormatException {
			JsonPrimitive p = primitive(field, label);
			if (p == null || !p.isNumber()) {
				throw problem(label, Message.of(SetText.NOT_NUMBER, describe(json.get(field))));
			}
			double value = p.getAsDouble();
			if (!Double.isFinite(value)) {
				throw problem(label, Message.of(SetText.NOT_NUMBER, describe(p)));
			}
			if (value > max) {
				throw problem(label, Message.of(SetText.ABOVE_MAX, format(value), format(max)));
			}
			if (value < min) {
				throw problem(label, Message.of(SetText.BELOW_MIN, format(value), format(min)));
			}
			if (value != Math.rint(value)) {
				throw problem(label, Message.of(SetText.NOT_WHOLE, format(value)));
			}
			return (int) value;
		}

		Fields object(String field, String label, Set<String> known) throws SetFormatException {
			JsonElement value = require(field, label);
			if (!value.isJsonObject()) {
				throw problem(label, Message.of(SetText.NOT_OBJECT, describe(value)));
			}
			return new Fields(value.getAsJsonObject(), where, warnings, known);
		}

		JsonArray array(String field, String label) throws SetFormatException {
			JsonElement value = require(field, label);
			if (!value.isJsonArray()) {
				throw problem(label, Message.of(SetText.NOT_LIST, describe(value)));
			}
			return value.getAsJsonArray();
		}

		/** Item {@code i} of the entry list, which must be an object. */
		Fields element(JsonArray array, int i, Set<String> known) throws SetFormatException {
			Message itemWhere = Message.of(SetText.PLACE_ENTRY, where, i + 1);
			JsonElement value = array.get(i);
			if (!value.isJsonObject()) {
				throw new SetFormatException(Message.of(SetText.ITEM_NOT_OBJECT, itemWhere, describe(value)));
			}
			return new Fields(value.getAsJsonObject(), itemWhere, warnings, known);
		}

		/** A number as a person writes it: 900, 0.5. */
		static String format(double value) {
			return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
		}

		static String describe(JsonElement value) {
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				return format(value.getAsDouble());
			}
			String text = value.toString();
			return text.length() > 40 ? text.substring(0, 37) + "..." : text;
		}
	}
}
