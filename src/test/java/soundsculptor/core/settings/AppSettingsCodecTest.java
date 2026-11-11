package soundsculptor.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The app-wide settings file: human-readable JSON that never loses good values because of one bad one. */
class AppSettingsCodecTest {
	private static final AppSettings D = AppSettings.DEFAULTS;

	private static AppSettingsCodec.Read read(String json) throws AppSettingsFormatException {
		return AppSettingsCodec.read(json);
	}

	private static String only(AppSettingsCodec.Read read) {
		assertEquals(1, read.problems().size(), read.problems().toString());
		return read.problems().get(0);
	}

	@Test
	void defaultsAreWrittenAsReadableJson() {
		assertEquals("""
				{
				  "schemaVersion": 1,
				  "showCues": true,
				  "cuesWithoutSubtitles": false,
				  "cueColor": "%s",
				  "cueTextScale": 100,
				  "cueOffsetX": 0,
				  "cueOffsetY": 0,
				  "outlinesThroughBlocks": true,
				  "reducedMotion": false,
				  "lastTab": "recent"
				}
				""".formatted(soundsculptor.core.ui.Palette.formatHex(AppSettings.DEFAULT_CUE_COLOR)), AppSettingsCodec.toJson(D));
	}

	@Test
	void roundTrip() throws AppSettingsFormatException {
		AppSettings s = D.withShowCues(false).withCuesWithoutSubtitles(true).withCueColor(0xABCDEF).withCueTextScale(175)
				.withCueOffset(-12, 40).withOutlinesThroughBlocks(false).withReducedMotion(true).withLastTab("zones");
		AppSettingsCodec.Read read = read(AppSettingsCodec.toJson(s));
		assertEquals(s, read.settings());
		assertEquals(List.of(), read.problems());
	}

	@Test
	void missingValuesUseDefaultsQuietly() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"reducedMotion\": true}");
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals(List.of(), read.problems());
	}

	@Test
	void unknownFieldsAreIgnoredWithAProblem() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"reducedMotion\": true, \"sparkles\": 3}");
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals("Unknown setting \"sparkles\" was ignored.", only(read));
	}

	@Test
	void aWrongTypeUsesTheDefaultForThatValueOnly() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"showCues\": \"no\", \"cueTextScale\": 150}");
		assertEquals(D.withCueTextScale(150), read.settings());
		assertEquals("showCues: \"no\" is not true or false, so the default is used.", only(read));
	}

	@Test
	void numbersMustBeWholeNumbers() throws AppSettingsFormatException {
		assertEquals("cueTextScale: 1.5 is not a whole number, so the default is used.",
				only(read("{\"schemaVersion\": 1, \"cueTextScale\": 1.5}")));
		assertEquals("cueOffsetX: \"left\" is not a whole number, so the default is used.",
				only(read("{\"schemaVersion\": 1, \"cueOffsetX\": \"left\"}")));
		assertEquals("cueTextScale: true is not a whole number, so the default is used.",
				only(read("{\"schemaVersion\": 1, \"cueTextScale\": true}")));
	}

	@Test
	void outOfRangeNumbersAreClampedWithAProblem() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"cueTextScale\": 300}");
		assertEquals(200, read.settings().cueTextScale());
		assertEquals("cueTextScale: 300 is above the maximum 200, so 200 is used.", only(read));
		read = read("{\"schemaVersion\": 1, \"cueOffsetY\": -99999}");
		assertEquals(-AppSettings.MAX_CUE_OFFSET, read.settings().cueOffsetY());
		assertEquals("cueOffsetY: -99999 is below the minimum -" + AppSettings.MAX_CUE_OFFSET + ", so -"
				+ AppSettings.MAX_CUE_OFFSET + " is used.", only(read));
		assertEquals(D, read("{\"schemaVersion\": 1, \"cueTextScale\": 100}").settings());
	}

	@Test
	void hugeNumbersDoNotOverflow() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"cueOffsetX\": 99999999999999}");
		assertEquals(AppSettings.MAX_CUE_OFFSET, read.settings().cueOffsetX());
		assertEquals(1, read.problems().size());
	}

	@Test
	void colorsAreHexText() throws AppSettingsFormatException {
		assertEquals(0xABCDEF, read("{\"schemaVersion\": 1, \"cueColor\": \"#abcdef\"}").settings().cueColor());
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"cueColor\": \"#GG0000\"}");
		assertEquals(D, read.settings());
		assertEquals("cueColor: \"#GG0000\" is not a color like #E69F00, so the default is used.", only(read));
		read = read("{\"schemaVersion\": 1, \"cueColor\": 255}");
		assertEquals(D, read.settings());
		assertEquals("cueColor: 255 is not a color like #E69F00, so the default is used.", only(read));
	}

	@Test
	void anUnknownLastTabFallsBackToTheFirstTab() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"lastTab\": \"../../etc\"}");
		assertEquals(D, read.settings());
		assertEquals("lastTab: \"../../etc\" is not a tab name, so the default is used.", only(read));
	}

	@Test
	void aNewerFileIsReadAsFarAsPossible() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 2, \"reducedMotion\": true}");
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals("The file is from a newer Sound Sculptor (schema 2); settings this version does not know are ignored.",
				only(read));
	}

	@Test
	void aMissingOrBadSchemaVersionIsReported() throws AppSettingsFormatException {
		assertEquals("schemaVersion is missing; the file is read as schema 1.", only(read("{\"reducedMotion\": true}")));
		assertEquals("schemaVersion: \"one\" is not a whole number; the file is read as schema 1.",
				only(read("{\"schemaVersion\": \"one\"}")));
		assertEquals("schemaVersion: 0 is not a schema version; the file is read as schema 1.",
				only(read("{\"schemaVersion\": 0}")));
	}

	@Test
	void nullValuesUseTheDefault() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"reducedMotion\": null}");
		assertEquals(D, read.settings());
		assertEquals("reducedMotion: null is not true or false, so the default is used.", only(read));
	}

	@Test
	void everyProblemIsReportedAtOnce() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read(
				"{\"schemaVersion\": 1, \"showCues\": 1, \"cueTextScale\": 0, \"cueColor\": \"x\", \"what\": 1}");
		assertEquals(D, read.settings());
		assertEquals(4, read.problems().size());
	}

	@Test
	void textThatIsNotASettingsObjectCannotBeRead() {
		for (String bad : new String[] {"", "   ", "not json {", "[1, 2]", "\"text\"", "42", "{\"a\": "}) {
			AppSettingsFormatException e = assertThrows(AppSettingsFormatException.class, () -> read(bad), bad);
			assertTrue(e.getMessage().endsWith("."), e.getMessage());
		}
	}

	@Test
	void problemsListIsReadOnly() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"x\": 1}");
		assertThrows(UnsupportedOperationException.class, () -> read.problems().add("y"));
	}
}
