package soundsculptor.core.trace;

/**
 * Special cases stated plainly at the top of the trace panel (PRD FR-14.1, FR-14.3). Declared in display order:
 * an {@code EnumSet} of these iterates with the most important first.
 */
public enum TraceNotice {
	/** "Bypass is on: nothing is changed." */
	BYPASS("bypass"),
	/** "This sound has its own vanilla slider, so Sound Sculptor does not handle it." */
	VANILLA_COVERED("vanilla_covered"),
	/** "Not heard yet; zones depend on where it plays." */
	NOT_HEARD_YET("not_heard_yet"),
	/** "Non-positional: zones never apply." */
	NON_POSITIONAL("non_positional"),
	/** "Above 100%: boost applied." */
	BOOSTED("boosted"),
	/** Sets or zones changed since the last play, so the result shown differs from what was heard. */
	CHANGED_SINCE_PLAY("changed_since_play");

	private final String key;

	TraceNotice(String key) {
		this.key = key;
	}

	/** Key in {@code assets/soundsculptor/lang/en_us.json}. */
	public String translationKey() {
		return "soundsculptor.trace.notice." + key;
	}
}
