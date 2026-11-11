package soundsculptor.core.profile;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/** Export file names (PRD FR-11.4): {@code config/soundsculptor/exports/<display name>-<date>.json}. */
public final class ProfileExport {
	/** Display names longer than this are shortened in the file name. */
	public static final int MAX_NAME_LENGTH = 64;

	private static final String NOT_ALLOWED = "<>:\"/\\|?*";
	private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5",
			"com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private ProfileExport() {
	}

	/**
	 * A file name that is safe on Windows, macOS and Linux: characters those systems refuse become {@code _}, leading
	 * and trailing spaces and dots are removed, and letters in any language are kept.
	 */
	public static String fileName(String displayName, LocalDate date) {
		StringBuilder out = new StringBuilder();
		displayName.codePoints().forEach(c -> {
			if (c < 0x20 || c == 0x7F || NOT_ALLOWED.indexOf(c) >= 0) {
				out.append('_');
			} else {
				out.appendCodePoint(c);
			}
		});
		String name = trim(out.toString());
		if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
			name = trim(name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH)));
		}
		if (name.isEmpty()) {
			name = "profile";
		} else if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
			name = "_" + name;
		}
		return name + "-" + date + ".json";
	}

	private static String trim(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && (s.charAt(start) == ' ' || s.charAt(start) == '.')) {
			start++;
		}
		while (end > start && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '.')) {
			end--;
		}
		return s.substring(start, end);
	}
}
