package soundsculptor.core.rules;

/**
 * The winning rule for each property of one sound ID within one rule list (PRD FR-4.5). A {@code null} winner
 * means no rule in the list sets that property, so vanilla behavior applies (FR-9 step 7).
 *
 * <p>Zones and the global list are combined later by the precedence step (FR-9, issue #7).
 */
public record RuleResolution(Rule loudness, Rule limit, Rule cue) {
	/** Shared result for a sound no rule sets anything for. */
	public static final RuleResolution NONE = new RuleResolution(null, null, null);

	public boolean muted() {
		return loudness != null && Boolean.TRUE.equals(loudness.mute());
	}

	/** The rule's volume setting, kept while muted so unmuting restores it (FR-5.1). 100 when unset. */
	public int volumePercent() {
		return loudness == null || loudness.volume() == null ? 100 : loudness.volume();
	}

	/** What the sound actually plays at: 0 when muted, otherwise {@link #volumePercent()}. */
	public int effectiveVolumePercent() {
		return muted() ? 0 : volumePercent();
	}

	public boolean cueOn() {
		return cue != null && cue.cue();
	}
}
