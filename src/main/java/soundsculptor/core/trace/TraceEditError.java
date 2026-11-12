package soundsculptor.core.trace;

import soundsculptor.core.Message;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;

/**
 * Why an edit from the trace panel was refused (PRD FR-14.4: validated like the full editors, FR-12.7). The panel
 * shows the message under the field and nothing changes.
 */
public enum TraceEditError {
	/** Volume must be 0 to 200 (AC: type 250 into a volume field, an error shows and nothing changes). */
	VOLUME_OUT_OF_RANGE("volume_out_of_range", SetEntry.MIN_VOLUME, SetEntry.MAX_VOLUME),
	/** Limit count must be 1 to 100. */
	LIMIT_COUNT_OUT_OF_RANGE("limit_count_out_of_range", Limit.MIN_COUNT, Limit.MAX_COUNT),
	/** Limit window must be 50 to 10,000 ms. */
	LIMIT_WINDOW_OUT_OF_RANGE("limit_window_out_of_range", Limit.MIN_WINDOW_MS, Limit.MAX_WINDOW_MS),
	/** The profile already holds the most rules it may. */
	TOO_MANY_RULES("too_many_rules", 0, TraceEditor.MAX_RULES),
	/** The rule was removed since the panel opened. */
	RULE_NOT_FOUND("rule_not_found", 0, 0),
	/** The zone was removed since the panel opened. */
	ZONE_NOT_FOUND("zone_not_found", 0, 0);

	private final String key;
	private final int min;
	private final int max;

	TraceEditError(String key, int min, int max) {
		this.key = key;
		this.min = min;
		this.max = max;
	}

	/**
	 * Key in {@code assets/soundsculptor/lang/en_us.json}. The range errors take {@link #min()} and {@link #max()} as
	 * arguments; {@link #TOO_MANY_RULES} takes {@link #max()}.
	 */
	public String translationKey() {
		return "soundsculptor.trace.edit.error." + key;
	}

	/** The text to show: {@link #translationKey()} with the arguments it takes. */
	public Message message() {
		return this == TOO_MANY_RULES ? Message.of(translationKey(), max) : Message.of(translationKey(), min, max);
	}

	/** The smallest allowed value, for the message. */
	public int min() {
		return min;
	}

	/** The largest allowed value, for the message. */
	public int max() {
		return max;
	}
}
