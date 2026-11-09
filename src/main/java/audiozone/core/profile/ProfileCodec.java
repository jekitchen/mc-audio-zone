package audiozone.core.profile;

import audiozone.core.rules.InvalidPatternException;
import audiozone.core.rules.Limit;
import audiozone.core.rules.Rule;
import audiozone.core.rules.SoundPattern;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes the profile file (PRD section 7.5). Human-readable JSON; unset rule properties are omitted
 * ("this rule does not set it"); unknown fields are ignored so newer optional fields do not break older readers.
 */
public final class ProfileCodec {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ProfileCodec() {
	}

	public static String toJson(Profile profile) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", Profile.SCHEMA_VERSION);
		root.addProperty("name", profile.name());
		JsonObject settings = new JsonObject();
		settings.addProperty("showDistance", profile.settings().showDistance());
		root.add("settings", settings);
		JsonArray rules = new JsonArray();
		for (Rule rule : profile.globalRules()) {
			rules.add(ruleToJson(rule));
		}
		root.add("globalRules", rules);
		root.add("zones", new JsonArray());
		return GSON.toJson(root) + "\n";
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

	/** @throws ProfileFormatException if the text is not a valid profile of a known schema version */
	public static Profile fromJson(String text) throws ProfileFormatException {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject()) {
				throw new ProfileFormatException("The file is not a profile (expected a JSON object).");
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			throw new ProfileFormatException(jsonProblem(e), e);
		}
		try {
			if (!root.has("schemaVersion")) {
				throw new ProfileFormatException("The file has no schemaVersion.");
			}
			int version = root.get("schemaVersion").getAsInt();
			if (version > Profile.SCHEMA_VERSION) {
				throw new ProfileFormatException("This file was made with a newer Audio Zone (schema " + version
						+ "). Update the mod to use it.");
			}
			if (version < 1) {
				throw new ProfileFormatException("Unknown schemaVersion " + version + ".");
			}
			String name = root.has("name") ? root.get("name").getAsString() : "Default";
			ProfileSettings settings = ProfileSettings.DEFAULTS;
			if (root.has("settings") && root.get("settings").isJsonObject()) {
				JsonObject s = root.getAsJsonObject("settings");
				if (s.has("showDistance")) {
					settings = settings.withShowDistance(s.get("showDistance").getAsBoolean());
				}
			}
			List<Rule> rules = new ArrayList<>();
			if (root.has("globalRules")) {
				JsonArray array = root.getAsJsonArray("globalRules");
				for (int i = 0; i < array.size(); i++) {
					rules.add(ruleFromJson(array.get(i).getAsJsonObject(), i + 1));
				}
			}
			return new Profile(name, settings, rules);
		} catch (IllegalStateException | ClassCastException | UnsupportedOperationException | NullPointerException e) {
			throw new ProfileFormatException("A value in the file has the wrong type: " + e.getMessage(), e);
		}
	}

	/** Plain words with a location, e.g. "The file is not valid JSON (line 1, column 44).", instead of parser jargon. */
	static String jsonProblem(JsonParseException e) {
		String detail = e.getMessage() == null ? "" : e.getMessage();
		Matcher where = LOCATION.matcher(detail);
		return where.find()
				? "The file is not valid JSON (line " + where.group(1) + ", column " + where.group(2) + ")."
				: "The file is not valid JSON.";
	}

	private static final Pattern LOCATION = Pattern.compile("line ([0-9]+) column ([0-9]+)");

	private static Rule ruleFromJson(JsonObject json, int number) throws ProfileFormatException {
		String where = "Rule " + number;
		if (!json.has("id") || !json.has("pattern")) {
			throw new ProfileFormatException(where + " needs an id and a pattern.");
		}
		SoundPattern pattern;
		try {
			pattern = SoundPattern.of(json.get("pattern").getAsString());
		} catch (InvalidPatternException e) {
			throw new ProfileFormatException(where + ", field pattern: " + e.getMessage(), e);
		}
		Boolean mute = json.has("mute") ? json.get("mute").getAsBoolean() : null;
		Integer volume = json.has("volume") ? json.get("volume").getAsInt() : null;
		Boolean cue = json.has("cue") ? json.get("cue").getAsBoolean() : null;
		Limit limit = null;
		boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
		try {
			if (json.has("limit")) {
				JsonObject l = json.getAsJsonObject("limit");
				limit = new Limit(l.get("count").getAsInt(), l.get("windowMs").getAsInt(),
						l.has("perSource") && l.get("perSource").getAsBoolean());
			}
			return new Rule(json.get("id").getAsString(), enabled, pattern, mute, volume, limit, cue);
		} catch (IllegalArgumentException e) {
			throw new ProfileFormatException(where + ": " + e.getMessage(), e);
		}
	}
}
