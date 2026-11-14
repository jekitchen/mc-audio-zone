package soundsculptor.core.trace;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;

/**
 * One inline control in the trace panel (PRD FR-14.4): enable/disable, Mute, Volume, Limit or Cue. A {@code null}
 * value clears the setting, so the rule no longer sets it.
 */
public sealed interface EntryEdit {
	/** The reason this edit cannot be applied, or {@code null} if it is valid. */
	TraceEditError validate();

	/** The changed rule. Call only when {@link #validate()} returns {@code null}. */
	SetEntry apply(SetEntry rule);

	record Enabled(boolean value) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return rule.withEnabled(value);
		}
	}

	record Mute(Boolean value) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return rule.withMute(value);
		}
	}

	/** Volume in percent, 0 to 200. */
	record Volume(Integer value) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			boolean outside = value != null && (value < SetEntry.MIN_VOLUME || value > SetEntry.MAX_VOLUME);
			return outside ? TraceEditError.VOLUME_OUT_OF_RANGE : null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return rule.withVolume(value);
		}
	}

	/** Count, window and per source, taken as typed so an out-of-range value gives a message, not an exception. */
	record SetLimit(int count, int windowMs, boolean perSource) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			if (count < Limit.MIN_COUNT || count > Limit.MAX_COUNT) {
				return TraceEditError.LIMIT_COUNT_OUT_OF_RANGE;
			}
			if (windowMs < Limit.MIN_WINDOW_MS || windowMs > Limit.MAX_WINDOW_MS) {
				return TraceEditError.LIMIT_WINDOW_OUT_OF_RANGE;
			}
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return rule.withLimit(new Limit(count, windowMs, perSource));
		}
	}

	/** Removes the rule's limit. */
	enum ClearLimit implements EntryEdit {
		INSTANCE;

		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return rule.withLimit(null);
		}
	}

	record Cue(Boolean value) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return rule.withCue(value);
		}
	}

	/**
	 * Quick action Mute (FR-1.5, FR-7.6): mutes, and a rule that does not set Cue yet starts with the profile's Cue
	 * default ("new mute rules have Cue on by default").
	 */
	record MuteWithCue(boolean cueDefault) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			SetEntry muted = rule.withMute(true);
			return muted.cue() == null ? muted.withCue(cueDefault) : muted;
		}
	}

	/**
	 * Quick action Unmute: clears mute, and also the Cue that {@link MuteWithCue} added when the rule is left setting
	 * only that default Cue, so muting and then unmuting leaves the profile as it was. A Cue that differs from the
	 * default, or a rule that still sets something else, keeps its Cue.
	 */
	record UnmuteWithCue(boolean cueDefault) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			SetEntry unmuted = rule.withMute(null);
			boolean onlyDefaultCue = Boolean.valueOf(cueDefault).equals(unmuted.cue()) && !unmuted.setsLoudness()
					&& !unmuted.setsLimit();
			return onlyDefaultCue ? unmuted.withCue(null) : unmuted;
		}
	}

	/**
	 * Quick action Unmute right after a quick Mute ({@link soundsculptor.core.quick.MuteMemory}): puts back the
	 * sound's single-sound entry exactly as it was before that Mute, or removes it when there was none, so Mute then
	 * Unmute leaves the profile as it was, the entry's own Cue included. Only for Quick changes.
	 *
	 * @param entry the entry as it was, or {@code null} if the Mute created it
	 */
	record PutBack(SetEntry entry) implements EntryEdit {
		@Override
		public TraceEditError validate() {
			return null;
		}

		@Override
		public SetEntry apply(SetEntry rule) {
			return entry != null ? entry : rule.withMute(null).withVolume(null).withLimit(null).withCue(null);
		}
	}
}
