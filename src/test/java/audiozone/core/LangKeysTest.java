package audiozone.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import audiozone.core.boost.BoostWarning;
import audiozone.core.inspect.LinkKind;
import audiozone.core.outline.ColorError;
import audiozone.core.outline.ZoneColor;
import audiozone.core.recent.DistanceBand;
import audiozone.core.recent.PlayStatus;
import audiozone.core.rules.PatternError;
import audiozone.core.trace.RuleWinReason;
import audiozone.core.trace.TraceNotice;
import audiozone.core.trace.ZoneWinReason;
import audiozone.core.ui.Palette;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** PRD 9.3: every visible string the core code names has English text in {@code en_us.json}. */
class LangKeysTest {
	private static final String LANG = "/assets/audiozone/lang/en_us.json";
	private static final Path CORE_SOURCES = Path.of("src/main/java/audiozone/core");
	/** A translation key written out in core source code, e.g. "audiozone.boost.warning" or "audiozone.color.". */
	private static final Pattern KEY_LITERAL = Pattern.compile("\"(audiozone\\.[a-z0-9_.]+)\"");

	/** Keys built from a prefix plus an enum or palette ID, by prefix. */
	private static Map<String, List<String>> builtKeys() {
		return Map.of(
				"audiozone.color.", Stream.concat(keys(ZoneColor.values(), ZoneColor::translationKey).stream(),
						Palette.OKABE_ITO.stream().map(s -> "audiozone.color." + s.id())).distinct().toList(),
				"audiozone.color.error.", keys(ColorError.values(), ColorError::translationKey),
				"audiozone.inspect.link.", keys(LinkKind.values(), LinkKind::translationKey),
				"audiozone.pattern.error.", keys(PatternError.values(), PatternError::translationKey),
				"audiozone.recent.distance.", keys(DistanceBand.values(), DistanceBand::translationKey),
				"audiozone.recent.status.", keys(PlayStatus.values(), PlayStatus::translationKey),
				"audiozone.trace.notice.", keys(TraceNotice.values(), TraceNotice::translationKey),
				"audiozone.trace.rule_reason.", keys(RuleWinReason.values(), RuleWinReason::translationKey),
				"audiozone.trace.zone_reason.", keys(ZoneWinReason.values(), ZoneWinReason::translationKey));
	}

	private static <T> List<String> keys(T[] values, Function<T, String> key) {
		return Stream.of(values).map(key).toList();
	}

	@Test
	void everyCoreKeyHasEnglishText() throws IOException {
		JsonObject lang = lang();
		Set<String> needed = new TreeSet<>(List.of(BoostWarning.WARNING_KEY, BoostWarning.CONFIRM_TITLE_KEY,
				BoostWarning.CONFIRM_BODY_KEY, BoostWarning.UNSUPPORTED_KEY));
		builtKeys().values().forEach(needed::addAll);
		Set<String> literals = coreKeyLiterals();
		literals.stream().filter(k -> !k.endsWith(".")).forEach(needed::add);

		List<String> missing = needed.stream().filter(k -> !lang.has(k)).toList();
		assertEquals(List.of(), missing, "keys with no text in en_us.json");
	}

	@Test
	void everyKeyPrefixInCoreIsCoveredByThisTest() throws IOException {
		// A new enum with a translationKey() prefix must be added to builtKeys(), or its keys go unchecked.
		Set<String> prefixes = new TreeSet<>();
		coreKeyLiterals().stream().filter(k -> k.endsWith(".")).forEach(prefixes::add);
		prefixes.removeAll(builtKeys().keySet());
		assertEquals(Set.of(), prefixes, "key prefixes in core code that LangKeysTest does not expand");
	}

	@Test
	void noDuplicateKeysOrEmptyText() throws IOException {
		String text = langText();
		Matcher m = Pattern.compile("(?m)^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:").matcher(text);
		Set<String> seen = new HashSet<>();
		Set<String> duplicates = new LinkedHashSet<>();
		while (m.find()) {
			if (!seen.add(m.group(1))) {
				duplicates.add(m.group(1));
			}
		}
		assertEquals(Set.of(), duplicates, "duplicate keys");
		JsonObject lang = lang();
		assertEquals(seen.size(), lang.size(), "every entry should be on its own line");
		List<String> empty = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
			if (!e.getValue().isJsonPrimitive() || e.getValue().getAsString().isBlank()) {
				empty.add(e.getKey());
			}
		}
		assertEquals(List.of(), empty, "keys with empty text");
	}

	@Test
	void percentSignsAreEscapedOrPlaceholders() throws IOException {
		// Minecraft formats every entry: a lone % must be written %%, and values go in as %s or %1$s.
		Pattern stray = Pattern.compile("%(?!%|s|[0-9]+\\$s)");
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : lang().entrySet()) {
			String value = e.getValue().getAsString().replace("%%", "");
			if (stray.matcher(value).find()) {
				bad.add(e.getKey());
			}
		}
		assertEquals(List.of(), bad, "entries with a % that is neither %% nor a placeholder");
	}

	private static Set<String> coreKeyLiterals() throws IOException {
		assertTrue(Files.isDirectory(CORE_SOURCES), "run from the project folder");
		Set<String> found = new TreeSet<>();
		try (Stream<Path> files = Files.walk(CORE_SOURCES)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				Matcher m = KEY_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
				while (m.find()) {
					found.add(m.group(1));
				}
			}
		}
		return found;
	}

	private static String langText() throws IOException {
		try (InputStream in = LangKeysTest.class.getResourceAsStream(LANG)) {
			assertNotNull(in, LANG + " on the classpath");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static JsonObject lang() throws IOException {
		return JsonParser.parseString(langText()).getAsJsonObject();
	}
}
