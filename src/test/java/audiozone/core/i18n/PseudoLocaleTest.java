package audiozone.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** PRD 9.3: a pseudo-language about 40% longer than English, for checking layouts for clipping. */
class PseudoLocaleTest {
	private static final Path EN_US = Path.of("src/main/resources/assets/audiozone/lang/en_us.json");

	@Test
	void accentsLettersAndWrapsInBrackets() {
		String out = PseudoLocale.pseudo("Mute");
		assertTrue(out.startsWith("[") && out.endsWith("]"), out);
		assertTrue(out.contains("Ṁúţé"), out);
	}

	@Test
	void isAtLeastFortyPercentLonger() {
		for (String text : List.of("On", "Off", "Mute", "Bypass Audio Zone", "a", "Recently Played",
				"This file was made with a newer Audio Zone. Update the mod to import it.")) {
			String out = PseudoLocale.pseudo(text);
			assertTrue(out.length() >= Math.ceil(text.length() * 1.4), text + " -> " + out);
		}
	}

	@Test
	void keepsPlaceholdersExactlyAndInOrder() {
		String text = "%2$s of %1$s at %s%% (%d blocks)";
		String out = PseudoLocale.pseudo(text);
		assertEquals(PseudoLocale.placeholders(text), PseudoLocale.placeholders(out));
		assertEquals(List.of("%2$s", "%1$s", "%s", "%%", "%d"), PseudoLocale.placeholders(out));
		assertTrue(out.contains("%2$s") && out.contains("%d"), out);
	}

	@Test
	void keepsMinecraftFormattingCodes() {
		String out = PseudoLocale.pseudo("§eWarning§r: loud");
		assertTrue(out.contains("§e") && out.contains("§r"), out);
	}

	@Test
	void leavesALonePercentSignAlone() {
		String out = PseudoLocale.pseudo("100% sure");
		assertTrue(out.contains("100%"), out);
	}

	@Test
	void emptyTextStaysEmpty() {
		assertEquals("", PseudoLocale.pseudo(""));
	}

	@Test
	void sameTextGivesSameResult() {
		assertEquals(PseudoLocale.pseudo("Zone color"), PseudoLocale.pseudo("Zone color"));
		assertNotEquals("Zone color", PseudoLocale.pseudo("Zone color"));
	}

	@Test
	void pseudoAllKeepsKeysAndOrder() {
		Map<String, String> in = new LinkedHashMap<>();
		in.put("b.key", "Bee");
		in.put("a.key", "Ay %s");
		Map<String, String> out = PseudoLocale.pseudoAll(in);
		assertEquals(List.of("b.key", "a.key"), List.copyOf(out.keySet()));
		assertEquals(PseudoLocale.pseudo("Ay %s"), out.get("a.key"));
	}

	@Test
	void wholeLanguageFileGrowsAndKeepsEveryPlaceholder() throws IOException {
		String english = Files.readString(EN_US, StandardCharsets.UTF_8);
		JsonObject in = JsonParser.parseString(english).getAsJsonObject();
		JsonObject out = JsonParser.parseString(PseudoLocale.pseudoJson(english)).getAsJsonObject();

		assertEquals(in.keySet(), out.keySet());
		assertTrue(in.size() > 0);
		for (Map.Entry<String, JsonElement> e : in.entrySet()) {
			String en = e.getValue().getAsString();
			String ps = out.get(e.getKey()).getAsString();
			assertTrue(ps.length() >= Math.ceil(en.length() * 1.4), e.getKey() + ": " + ps);
			assertEquals(PseudoLocale.placeholders(en), PseudoLocale.placeholders(ps), e.getKey());
		}
	}

	@Test
	void writesThePseudoFileForDevTesting() throws IOException {
		// See docs/DEV_ENVIRONMENT.md: copy this file over en_us.json locally to try the layouts in game.
		Path target = Path.of("build/pseudo/en_pseudo.json");
		PseudoLocale.writeFile(EN_US, target);
		JsonObject written = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(JsonParser.parseString(Files.readString(EN_US, StandardCharsets.UTF_8)).getAsJsonObject().keySet(),
				written.keySet());
	}
}
