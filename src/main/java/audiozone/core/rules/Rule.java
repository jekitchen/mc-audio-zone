package audiozone.core.rules;

/**
 * One rule in the global list or a zone's list (PRD FR-4, section 7.5). Immutable; the {@code with...} methods
 * return a changed copy.
 *
 * <p>Each property is optional, and {@code null} means "this rule does not set it". Mute and volume together form
 * the <em>loudness</em> property: a rule that sets either one competes for both (FR-4.5).
 *
 * @param id      stable ID used by the profile file and the sound trace
 * @param enabled disabled rules are kept but ignored (FR-4.7)
 * @param pattern which sound IDs the rule targets
 * @param mute    mute on or off, or {@code null}
 * @param volume  volume in percent, 0 to 200, or {@code null}
 * @param limit   repeat limit, or {@code null}
 * @param cue     show a cue when muted, or {@code null}
 */
public record Rule(String id, boolean enabled, SoundPattern pattern, Boolean mute, Integer volume, Limit limit, Boolean cue) {
	public static final int MIN_VOLUME = 0;
	public static final int MAX_VOLUME = 200;

	public Rule {
		if (id == null || pattern == null) {
			throw new IllegalArgumentException("rule id and pattern are required");
		}
		if (volume != null && (volume < MIN_VOLUME || volume > MAX_VOLUME)) {
			throw new IllegalArgumentException("volume " + volume + " is outside " + MIN_VOLUME + ".." + MAX_VOLUME);
		}
	}

	/** An enabled rule that sets nothing yet. */
	public static Rule of(String id, String pattern) {
		return new Rule(id, true, SoundPattern.of(pattern), null, null, null, null);
	}

	/** The same rule under another ID, e.g. a copy in another list. */
	public Rule withId(String value) {
		return new Rule(value, enabled, pattern, mute, volume, limit, cue);
	}

	public Rule withEnabled(boolean value) {
		return new Rule(id, value, pattern, mute, volume, limit, cue);
	}

	public Rule withMute(Boolean value) {
		return new Rule(id, enabled, pattern, value, volume, limit, cue);
	}

	public Rule withVolume(Integer value) {
		return new Rule(id, enabled, pattern, mute, value, limit, cue);
	}

	public Rule withLimit(Limit value) {
		return new Rule(id, enabled, pattern, mute, volume, value, cue);
	}

	public Rule withCue(Boolean value) {
		return new Rule(id, enabled, pattern, mute, volume, limit, value);
	}

	public boolean setsLoudness() {
		return mute != null || volume != null;
	}

	public boolean setsLimit() {
		return limit != null;
	}

	public boolean setsCue() {
		return cue != null;
	}
}
