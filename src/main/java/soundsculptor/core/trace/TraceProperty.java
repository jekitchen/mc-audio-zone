package soundsculptor.core.trace;

import soundsculptor.core.rules.Rule;

/** The three settings that are decided separately (PRD FR-4.5, FR-9): loudness (mute + volume), limit and cue. */
public enum TraceProperty {
	LOUDNESS,
	LIMIT,
	CUE;

	/** Whether {@code rule} sets this property. */
	public boolean setBy(Rule rule) {
		return switch (this) {
			case LOUDNESS -> rule.setsLoudness();
			case LIMIT -> rule.setsLimit();
			case CUE -> rule.setsCue();
		};
	}
}
