package soundsculptor.core.sets;

import java.util.Locale;

import soundsculptor.core.Glob;

/**
 * A rule's target: a sound ID pattern (PRD FR-4.1, FR-4.2).
 *
 * <p>Format {@code namespace:path}. {@code *} matches any run of characters, including none and including dots.
 * A pattern without {@code :} matches any namespace ({@code block.*.step} is stored as {@code *:block.*.step}).
 * The pattern must match the whole ID. Input is trimmed and lowercased, and repeated stars collapse to one.
 *
 * <p>{@link #matches} allocates nothing, so it is safe on the sound hot path (PRD 7.4).
 */
public final class SoundPattern {
	private final String text;
	private final int starCount;

	private SoundPattern(String text) {
		this.text = text;
		int stars = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '*') {
				stars++;
			}
		}
		this.starCount = stars;
	}

	/**
	 * Parses a pattern typed by the player or read from a profile.
	 *
	 * @throws InvalidPatternException if {@link #validate} reports an error
	 */
	public static SoundPattern of(String raw) {
		PatternError error = validate(raw);
		if (error != null) {
			throw new InvalidPatternException(raw, error);
		}
		return new SoundPattern(normalize(raw));
	}

	/** Checks a pattern without building it. Returns {@code null} when it is valid. */
	public static PatternError validate(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			return PatternError.EMPTY;
		}
		int colons = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ':') {
				colons++;
			} else if (!isAllowed(c)) {
				return PatternError.INVALID_CHARACTER;
			}
		}
		if (colons > 1) {
			return PatternError.TOO_MANY_COLONS;
		}
		if (colons == 1 && (s.startsWith(":") || s.endsWith(":"))) {
			return PatternError.EMPTY_PART;
		}
		return null;
	}

	private static boolean isAllowed(char c) {
		return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/' || c == '*';
	}

	private static String normalize(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.indexOf(':') < 0) {
			s = "*:" + s;
		}
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '*' && out.length() > 0 && out.charAt(out.length() - 1) == '*') {
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	/** Whether this pattern matches the whole of {@code soundId} (e.g. {@code minecraft:entity.cow.ambient}). */
	public boolean matches(String soundId) {
		return Glob.matches(text, soundId);
	}

	/** The normalized pattern text, as saved in the profile. */
	public String text() {
		return text;
	}

	/** True when the pattern has no {@code *}: it names one sound ID. */
	public boolean isExact() {
		return starCount == 0;
	}

	/** Number of non-{@code *} characters. More literal characters means more specific (FR-4.5 step 2). */
	public int literalCount() {
		return text.length() - starCount;
	}

	/** Number of {@code *}. Fewer stars means more specific (FR-4.5 step 3). */
	public int starCount() {
		return starCount;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SoundPattern other && text.equals(other.text);
	}

	@Override
	public int hashCode() {
		return text.hashCode();
	}

	@Override
	public String toString() {
		return text;
	}
}
