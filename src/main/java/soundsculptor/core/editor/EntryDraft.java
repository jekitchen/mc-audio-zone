package soundsculptor.core.editor;

import java.util.EnumMap;
import java.util.Locale;

import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PatternError;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundPattern;

/**
 * The rule editor's values (PRD 6.1: pattern, Mute, Volume, Limit with Per source, Cue). Mute and Cue have three
 * states, {@code null} meaning "this rule does not set it"; an empty Volume or Limit count also means "not set".
 * The limit window counts only when a limit count is typed.
 */
public final class EntryDraft extends Draft<EntryDraft.Field, SetEntry> {
	/** The rule editor's fields, for mapping them to widgets. */
	public enum Field implements DraftField {
		PATTERN(true),
		ENABLED(false),
		MUTE(false),
		/** Percent; empty means not set. */
		VOLUME(true),
		/** Plays per window; empty means no limit. */
		LIMIT_COUNT(true),
		/** Window in milliseconds. */
		LIMIT_WINDOW(true),
		PER_SOURCE(false),
		CUE(false);

		private final boolean text;

		Field(boolean text) {
			this.text = text;
		}

		@Override
		public boolean isText() {
			return text;
		}
	}

	/** FR-6.1: the window is 1 second unless changed. */
	public static final int DEFAULT_WINDOW_MS = 1000;

	private final String id;
	private final int maxVolume;
	/** FR-7.6: the Cue a rule gets when it is set to mute with no Cue chosen, or null to leave Cue alone. */
	private Boolean cueDefault;

	private EntryDraft(String id, boolean boostAvailable, EnumMap<Field, String> original, EnumMap<Field, String> defaults) {
		super(Field.class, original, defaults);
		this.id = id;
		this.maxVolume = BoostWarning.sliderMax(boostAvailable);
	}

	/** A new rule. */
	public static EntryDraft blank(String id, boolean boostAvailable) {
		return of(SetEntry.of(id, "*"), boostAvailable, "");
	}

	/**
	 * Editing an existing rule.
	 *
	 * @param boostAvailable whether volumes above 100% are allowed (v1 with driver support, FR-5.4)
	 */
	public static EntryDraft of(SetEntry rule, boolean boostAvailable) {
		return of(rule, boostAvailable, rule.pattern().text());
	}

	private static EntryDraft of(SetEntry rule, boolean boostAvailable, String pattern) {
		EnumMap<Field, String> values = new EnumMap<>(Field.class);
		values.put(Field.PATTERN, pattern);
		values.put(Field.ENABLED, Boolean.toString(rule.enabled()));
		values.put(Field.MUTE, flag(rule.mute()));
		values.put(Field.VOLUME, rule.volume() == null ? "" : rule.volume().toString());
		Limit limit = rule.limit();
		values.put(Field.LIMIT_COUNT, limit == null ? "" : Integer.toString(limit.count()));
		values.put(Field.LIMIT_WINDOW, Integer.toString(limit == null ? DEFAULT_WINDOW_MS : limit.windowMs()));
		values.put(Field.PER_SOURCE, Boolean.toString(limit != null && limit.perSource()));
		values.put(Field.CUE, flag(rule.cue()));

		EnumMap<Field, String> defaults = new EnumMap<>(Field.class);
		defaults.put(Field.PATTERN, pattern);
		defaults.put(Field.ENABLED, "true");
		defaults.put(Field.MUTE, "");
		defaults.put(Field.VOLUME, "");
		defaults.put(Field.LIMIT_COUNT, "");
		defaults.put(Field.LIMIT_WINDOW, Integer.toString(DEFAULT_WINDOW_MS));
		defaults.put(Field.PER_SOURCE, "false");
		defaults.put(Field.CUE, "");
		return new EntryDraft(rule.id(), boostAvailable, values, defaults);
	}

	/** The ID the rule is saved under. */
	public String id() {
		return id;
	}

	public boolean enabled() {
		return Boolean.parseBoolean(text(Field.ENABLED));
	}

	public void setEnabled(boolean value) {
		put(Field.ENABLED, Boolean.toString(value));
	}

	/** Mute on, off, or null for "not set". */
	public Boolean mute() {
		return flag(text(Field.MUTE));
	}

	public void setMute(Boolean value) {
		put(Field.MUTE, flag(value));
		if (Boolean.TRUE.equals(value) && cueDefault != null && cue() == null) {
			put(Field.CUE, flag(cueDefault));
		}
	}

	/**
	 * FR-7.6: from now on, setting Mute on while Cue is not set also sets Cue to the profile's Cue default ("new mute
	 * rules have Cue on by default"). For the editor once it shows Cue (M9); without it, Cue is never changed.
	 *
	 * @return this draft
	 */
	public EntryDraft useCueDefault(boolean value) {
		this.cueDefault = value;
		return this;
	}

	/** Cue on, off, or null for "not set". */
	public Boolean cue() {
		return flag(text(Field.CUE));
	}

	public void setCue(Boolean value) {
		put(Field.CUE, flag(value));
	}

	public boolean perSource() {
		return Boolean.parseBoolean(text(Field.PER_SOURCE));
	}

	public void setPerSource(boolean value) {
		put(Field.PER_SOURCE, Boolean.toString(value));
	}

	/** FR-5.4: the volume is valid and above 100%, so the slider shows "May distort. Protect your hearing." */
	public boolean showsBoostWarning() {
		return hasVolume() && error(Field.VOLUME) == null && BoostWarning.showWarning(Numbers.whole(text(Field.VOLUME)));
	}

	@Override
	protected FieldError validate(Field field, String text) {
		return switch (field) {
			case PATTERN -> {
				PatternError error = SoundPattern.validate(text);
				yield error == null ? null : FieldError.of(error.translationKey());
			}
			case VOLUME -> text.isBlank() ? null : Numbers.checkWhole(text, SetEntry.MIN_VOLUME, maxVolume);
			case LIMIT_COUNT -> text.isBlank() ? null : Numbers.checkWhole(text, Limit.MIN_COUNT, Limit.MAX_COUNT);
			case LIMIT_WINDOW -> Numbers.checkWhole(text, Limit.MIN_WINDOW_MS, Limit.MAX_WINDOW_MS);
			default -> null;
		};
	}

	@Override
	protected boolean inUse(Field field) {
		return switch (field) {
			case LIMIT_WINDOW, PER_SOURCE -> hasLimit();
			default -> true;
		};
	}

	@Override
	protected boolean same(Field field, String a, String b) {
		return switch (field) {
			case PATTERN -> SoundPattern.validate(a) == null && SoundPattern.validate(b) == null
					? SoundPattern.of(a).equals(SoundPattern.of(b))
					: a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
			case VOLUME, LIMIT_COUNT, LIMIT_WINDOW -> Numbers.same(a, b);
			default -> a.equals(b);
		};
	}

	@Override
	protected SetEntry create() {
		Limit limit = hasLimit()
				? new Limit(Numbers.whole(text(Field.LIMIT_COUNT)), Numbers.whole(text(Field.LIMIT_WINDOW)), perSource())
				: null;
		Integer volume = hasVolume() ? Numbers.whole(text(Field.VOLUME)) : null;
		return new SetEntry(id, enabled(), SoundPattern.of(text(Field.PATTERN)), mute(), volume, limit, cue());
	}

	private boolean hasVolume() {
		return !text(Field.VOLUME).isBlank();
	}

	private boolean hasLimit() {
		return !text(Field.LIMIT_COUNT).isBlank();
	}

	private static String flag(Boolean value) {
		return value == null ? "" : value.toString();
	}

	private static Boolean flag(String text) {
		return text.isEmpty() ? null : Boolean.valueOf(text);
	}
}
