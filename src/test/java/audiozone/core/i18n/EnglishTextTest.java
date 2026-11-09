package audiozone.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audiozone.core.TestEnglish;
import audiozone.core.editor.FieldError;
import audiozone.core.rules.PatternError;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The game's English text (en_us.json) agrees with the tests' English table and covers every error the editors show. */
class EnglishTextTest {
	private static final Path EN_US = Path.of("src/main/resources/assets/audiozone/lang/en_us.json");

	private static JsonObject english() throws IOException {
		return JsonParser.parseString(Files.readString(EN_US, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void sharedKeysHaveTheSameText() throws IOException {
		JsonObject english = english();
		for (Map.Entry<String, String> entry : TestEnglish.TEXT.entrySet()) {
			if (english.has(entry.getKey())) {
				assertEquals(entry.getValue(), english.get(entry.getKey()).getAsString(), entry.getKey());
			}
		}
	}

	@Test
	void ruleSummariesAndTraceTextAreTranslated() throws IOException {
		JsonObject english = english();
		for (String key : TestEnglish.TEXT.keySet()) {
			if (key.startsWith("audiozone.summary.rule.") || key.equals("audiozone.summary.separator")
					|| key.startsWith("audiozone.trace.")) {
				assertTrue(english.has(key), "en_us.json is missing " + key);
			}
		}
	}

	@Test
	void everyEditorErrorIsTranslated() throws IOException {
		JsonObject english = english();
		for (PatternError error : PatternError.values()) {
			assertTrue(english.has(error.translationKey()), "en_us.json is missing " + error.translationKey());
		}
		for (String key : List.of(FieldError.REQUIRED, FieldError.NOT_A_NUMBER, FieldError.NOT_WHOLE_NUMBER, FieldError.OUT_OF_RANGE)) {
			assertTrue(english.has(key), "en_us.json is missing " + key);
		}
	}
}
