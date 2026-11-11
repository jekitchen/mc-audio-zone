package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** PRD FR-11.4: export file names are {@code <display name>-<date>.json}, safe on every system. */
class ProfileExportTest {
	private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

	@Test
	void nameAndDate() {
		assertEquals("My server-2026-11-09.json", ProfileExport.fileName("My server", DAY));
	}

	@Test
	void charactersNotAllowedInFileNamesBecomeUnderscores() {
		assertEquals("a_b_c_d_e_f_g_h_i_-2026-11-09.json", ProfileExport.fileName("a/b\\c:d*e?f\"g<h>i|", DAY));
		assertEquals("tab_x-2026-11-09.json", ProfileExport.fileName("tab\tx", DAY));
	}

	@Test
	void lettersFromOtherLanguagesAreKept() {
		assertEquals("Café Ünïcode 日本-2026-11-09.json", ProfileExport.fileName("Café Ünïcode 日本", DAY));
	}

	@Test
	void leadingAndTrailingSpacesAndDotsAreRemoved() {
		assertEquals("srv-2026-11-09.json", ProfileExport.fileName("  .srv. ", DAY));
	}

	@Test
	void emptyOrReservedNamesStillGiveAUsableFile() {
		assertEquals("profile-2026-11-09.json", ProfileExport.fileName("", DAY));
		assertEquals("profile-2026-11-09.json", ProfileExport.fileName(" .. ", DAY));
		assertEquals("_CON-2026-11-09.json", ProfileExport.fileName("CON", DAY));
		assertEquals("_com1-2026-11-09.json", ProfileExport.fileName("com1", DAY));
	}

	@Test
	void longNamesAreShortened() {
		String name = ProfileExport.fileName("x".repeat(300), DAY);
		assertEquals(ProfileExport.MAX_NAME_LENGTH + "-2026-11-09.json".length(), name.length());
	}
}
