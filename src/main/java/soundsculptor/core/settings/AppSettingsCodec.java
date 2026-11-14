package soundsculptor.core.settings;

import soundsculptor.core.ui.Palette;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Reads and writes the app-wide settings file. Human-readable JSON, one value per field.
 *
 * <p>Reading is forgiving, because these are only preferences: a missing value uses its default quietly (so files from
 * older versions load), and a wrong or unknown value uses the default (or the nearest allowed number) and adds a
 * problem in plain words, for the log. Good values are never lost because of one bad one. Only text that is not a
 * JSON object at all cannot be read.
 */
public final class AppSettingsCodec {
	public static final int SCHEMA_VERSION = 1;

	/** Tab names from before the Sounds tab (FR-12.1), read as the Sounds tab. */
	private static final Set<String> RETIRED_TABS = Set.of("recent", "search");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Set<String> FIELDS = Set.of("schemaVersion", "showCues", "cuesWithoutSubtitles", "cueColor",
			"cueTextScale", "cueOffsetX", "cueOffsetY", "outlinesThroughBlocks", "reducedMotion", "lastTab");

	private AppSettingsCodec() {
	}

	/** Settings read from a file, and what was wrong with it, in plain words (empty if nothing). */
	public record Read(AppSettings settings, List<String> problems) {
		public Read {
			problems = List.copyOf(problems);
		}
	}

	public static String toJson(AppSettings s) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("showCues", s.showCues());
		root.addProperty("cuesWithoutSubtitles", s.cuesWithoutSubtitles());
		root.addProperty("cueColor", Palette.formatHex(s.cueColor()));
		root.addProperty("cueTextScale", s.cueTextScale());
		root.addProperty("cueOffsetX", s.cueOffsetX());
		root.addProperty("cueOffsetY", s.cueOffsetY());
		root.addProperty("outlinesThroughBlocks", s.outlinesThroughBlocks());
		root.addProperty("reducedMotion", s.reducedMotion());
		root.addProperty("lastTab", s.lastTab());
		return GSON.toJson(root) + "\n";
	}

	/** @throws AppSettingsFormatException if {@code text} is not a JSON object */
	public static Read read(String text) throws AppSettingsFormatException {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(text);
		} catch (JsonParseException e) {
			throw new AppSettingsFormatException("The file is not valid JSON.");
		}
		if (!parsed.isJsonObject()) {
			throw new AppSettingsFormatException("The file does not hold a settings object.");
		}
		JsonObject root = parsed.getAsJsonObject();
		List<String> problems = new ArrayList<>();
		checkVersion(root, problems);
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!FIELDS.contains(entry.getKey())) {
				problems.add("Unknown setting \"" + entry.getKey() + "\" was ignored.");
			}
		}
		AppSettings d = AppSettings.DEFAULTS;
		boolean showCues = bool(root, "showCues", d.showCues(), problems);
		boolean withoutSubtitles = bool(root, "cuesWithoutSubtitles", d.cuesWithoutSubtitles(), problems);
		int color = color(root, "cueColor", d.cueColor(), problems);
		int scale = number(root, "cueTextScale", d.cueTextScale(), AppSettings.MIN_CUE_SCALE, AppSettings.MAX_CUE_SCALE,
				problems);
		int offsetX = number(root, "cueOffsetX", d.cueOffsetX(), -AppSettings.MAX_CUE_OFFSET, AppSettings.MAX_CUE_OFFSET,
				problems);
		int offsetY = number(root, "cueOffsetY", d.cueOffsetY(), -AppSettings.MAX_CUE_OFFSET, AppSettings.MAX_CUE_OFFSET,
				problems);
		boolean throughBlocks = bool(root, "outlinesThroughBlocks", d.outlinesThroughBlocks(), problems);
		boolean reducedMotion = bool(root, "reducedMotion", d.reducedMotion(), problems);
		String lastTab = tab(root, "lastTab", d.lastTab(), problems);
		return new Read(new AppSettings(showCues, withoutSubtitles, color, scale, offsetX, offsetY, throughBlocks,
				reducedMotion, lastTab), problems);
	}

	private static void checkVersion(JsonObject root, List<String> problems) {
		JsonElement value = root.get("schemaVersion");
		String asOne = "; the file is read as schema " + SCHEMA_VERSION + ".";
		if (value == null) {
			problems.add("schemaVersion is missing" + asOne);
			return;
		}
		BigDecimal version = wholeNumber(value);
		if (version == null) {
			problems.add("schemaVersion: " + value + " is not a whole number" + asOne);
		} else if (version.signum() <= 0) {
			problems.add("schemaVersion: " + value + " is not a schema version" + asOne);
		} else if (version.compareTo(BigDecimal.valueOf(SCHEMA_VERSION)) > 0) {
			problems.add("The file is from a newer Sound Sculptor (schema " + value
					+ "); settings this version does not know are ignored.");
		}
	}

	private static boolean bool(JsonObject root, String field, boolean fallback, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean();
		}
		problems.add(field + ": " + value + " is not true or false, so the default is used.");
		return fallback;
	}

	private static int number(JsonObject root, String field, int fallback, int min, int max, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		BigDecimal number = wholeNumber(value);
		if (number == null) {
			problems.add(field + ": " + value + " is not a whole number, so the default is used.");
			return fallback;
		}
		if (number.compareTo(BigDecimal.valueOf(min)) < 0) {
			problems.add(field + ": " + value + " is below the minimum " + min + ", so " + min + " is used.");
			return min;
		}
		if (number.compareTo(BigDecimal.valueOf(max)) > 0) {
			problems.add(field + ": " + value + " is above the maximum " + max + ", so " + max + " is used.");
			return max;
		}
		return number.intValueExact();
	}

	/** The value as a whole number, or {@code null} if it is not a JSON number without a fraction. */
	private static BigDecimal wholeNumber(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		BigDecimal number;
		try {
			number = value.getAsBigDecimal();
		} catch (NumberFormatException e) {
			return null;
		}
		return number.stripTrailingZeros().scale() <= 0 ? number : null;
	}

	private static int color(JsonObject root, String field, int fallback, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (value.isJsonPrimitive() && ((JsonPrimitive) value).isString()) {
			OptionalInt rgb = Palette.parseHex(value.getAsString());
			if (rgb.isPresent()) {
				return rgb.getAsInt();
			}
		}
		problems.add(field + ": " + value + " is not a color like #E69F00, so the default is used.");
		return fallback;
	}

	private static String tab(JsonObject root, String field, String fallback, List<String> problems) {
		JsonElement value = root.get(field);
		if (value == null) {
			return fallback;
		}
		if (value.isJsonPrimitive() && ((JsonPrimitive) value).isString() && AppSettings.isValidTab(value.getAsString())) {
			String tab = value.getAsString();
			// FR-12.1: the Sounds tab replaced Recently Played and Search, so a file saved before still opens it.
			return RETIRED_TABS.contains(tab) ? AppSettings.DEFAULT_TAB : tab;
		}
		problems.add(field + ": " + value + " is not a tab name, so the default is used.");
		return fallback;
	}
}
