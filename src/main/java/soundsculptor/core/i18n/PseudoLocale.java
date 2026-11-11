package soundsculptor.core.i18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A pseudo-language for checking layouts (PRD 9.3): every letter becomes an accented look-alike and the text is
 * padded to at least 40% longer than English, inside brackets, so clipped or joined-up text is easy to spot.
 * "Mute" becomes "[Ṁúţé]".
 *
 * <p>Format placeholders ({@code %s}, {@code %1$s}, {@code %%}) and Minecraft formatting
 * codes ({@code §e}) are copied unchanged, so the pseudo text works wherever the English does. Dev tool only; the
 * mod never ships it.
 */
public final class PseudoLocale {
	/** How much longer pseudo text is than English, at least (PRD 9.3). */
	public static final double GROWTH = 1.4;

	/** A placeholder as Minecraft's translations read it: {@code %s}, {@code %2$s}, {@code %%}. */
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z%]");

	private static final String PLAIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String ACCENTED = "áƀçðéƒĝĥíĵķļɱñóþǫŕšţúṽŵẋýžÁƁÇÐÉƑĜĤÍĴĶĻṀÑÓÞǪŔŠŢÚṼŴẊÝŽ";

	private PseudoLocale() {
	}

	/** The pseudo version of one translation. Empty text stays empty. */
	public static String pseudo(String text) {
		if (text.isEmpty()) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length() * 2).append('[');
		Matcher placeholder = PLACEHOLDER.matcher(text);
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '%' && placeholder.find(i) && placeholder.start() == i) {
				out.append(placeholder.group());
				i = placeholder.end();
			} else if (c == '§' && i + 1 < text.length()) {
				out.append(c).append(text.charAt(i + 1));
				i += 2;
			} else {
				int plain = PLAIN.indexOf(c);
				out.append(plain >= 0 ? ACCENTED.charAt(plain) : c);
				i++;
			}
		}
		int target = (int) Math.ceil(text.length() * GROWTH);
		while (out.length() + 1 < target) {
			out.append('~');
		}
		return out.append(']').toString();
	}

	/** Every placeholder in the text, in order; pseudo text must keep the same list. */
	public static List<String> placeholders(String text) {
		List<String> found = new ArrayList<>();
		Matcher m = PLACEHOLDER.matcher(text);
		while (m.find()) {
			found.add(m.group());
		}
		return found;
	}

	/** The pseudo version of a whole language map, keeping its keys and their order. */
	public static Map<String, String> pseudoAll(Map<String, String> translations) {
		Map<String, String> out = new LinkedHashMap<>();
		translations.forEach((key, text) -> out.put(key, pseudo(text)));
		return out;
	}

	/** The pseudo version of a language file's JSON (an object of key to text), as pretty-printed JSON. */
	public static String pseudoJson(String json) {
		JsonObject in = JsonParser.parseString(json).getAsJsonObject();
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> e : in.entrySet()) {
			out.addProperty(e.getKey(), pseudo(e.getValue().getAsString()));
		}
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out) + "\n";
	}

	/** Reads a language file and writes its pseudo version, creating folders as needed. */
	public static void writeFile(Path english, Path target) throws IOException {
		String json = pseudoJson(Files.readString(english, StandardCharsets.UTF_8));
		if (target.getParent() != null) {
			Files.createDirectories(target.getParent());
		}
		Files.writeString(target, json, StandardCharsets.UTF_8);
	}
}
