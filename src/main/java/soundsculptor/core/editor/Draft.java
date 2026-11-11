package soundsculptor.core.editor;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * The values an editor holds while the player changes them (PRD FR-12.4, FR-12.6, FR-12.7, section 6.2), before
 * Save turns them into a rule or zone. Every value is kept as text, as typed, so a half-typed number is never lost.
 *
 * <ul>
 *   <li>{@link #error} is checked live; {@link #canSave} is false while any field has one, from the start.</li>
 *   <li>{@link #shownError} is what the UI shows under a field: only once the field has been edited, or after Save
 *       was pressed ({@link #attemptSave}).</li>
 *   <li>{@link #isDirty} drives the Save / Discard / Keep editing dialog. A value counts as changed only if it means
 *       something different ("10" and "10.0" are the same), and only fields that are in use count (a box's width does
 *       not matter while the shape is a sphere).</li>
 *   <li>{@link #resetToDefault} is the reset button next to each value.</li>
 * </ul>
 *
 * <p>Not thread-safe; used from the UI thread only.
 *
 * @param <F> the editor's field enum
 * @param <T> what Save builds
 */
public abstract class Draft<F extends Enum<F> & DraftField, T> {
	private final Class<F> type;
	private final EnumMap<F, String> values;
	private final EnumMap<F, String> original;
	private final EnumMap<F, String> defaults;
	private final EnumSet<F> touched;
	private boolean saveAttempted;

	protected Draft(Class<F> type, Map<F, String> original, Map<F, String> defaults) {
		this.type = type;
		this.values = new EnumMap<>(original);
		this.original = new EnumMap<>(original);
		this.defaults = new EnumMap<>(defaults);
		this.touched = EnumSet.noneOf(type);
		for (F f : type.getEnumConstants()) {
			if (!original.containsKey(f) || !defaults.containsKey(f)) {
				throw new IllegalArgumentException("no value or default for " + f);
			}
		}
	}

	/** The field's current text. */
	public String text(F field) {
		return values.get(field);
	}

	/** The player typed into a text field. */
	public void set(F field, String text) {
		if (!field.isText()) {
			throw new IllegalArgumentException(field + " is not a text field; use its own setter");
		}
		put(field, text);
	}

	/** For subclasses' toggle and choice setters. */
	protected void put(F field, String text) {
		values.put(field, text == null ? "" : text);
		touched.add(field);
	}

	/** The value the reset button puts back. Subclasses may make it depend on other values. */
	public String defaultText(F field) {
		return defaults.get(field);
	}

	/** FR-12.6: put the field back to its default. */
	public void resetToDefault(F field) {
		put(field, defaultText(field));
	}

	/** Whether the field already holds its default (the reset button can be disabled). */
	public boolean isDefault(F field) {
		return same(field, values.get(field), defaultText(field));
	}

	/** Why the field's value is refused, or null. Fields not in use have no error. */
	public FieldError error(F field) {
		return inUse(field) ? validate(field, values.get(field)) : null;
	}

	/** The error to show under the field (6.2): only once it has been edited, or after Save was pressed. */
	public FieldError shownError(F field) {
		return touched.contains(field) || saveAttempted ? error(field) : null;
	}

	public boolean canSave() {
		for (F f : type.getEnumConstants()) {
			if (error(f) != null) {
				return false;
			}
		}
		return true;
	}

	/** Save was pressed: from now on every error shows. Returns whether the draft can be saved. */
	public boolean attemptSave() {
		saveAttempted = true;
		return canSave();
	}

	/** Whether any value in use means something different from when the editor opened (FR-12.4). */
	public boolean isDirty() {
		for (F f : type.getEnumConstants()) {
			if (inUse(f) && !same(f, values.get(f), original.get(f))) {
				return true;
			}
		}
		return false;
	}

	/** Builds the result. Call only when {@link #canSave} is true. */
	public T build() {
		if (!canSave()) {
			throw new IllegalStateException("the draft has errors");
		}
		return create();
	}

	/** Why {@code text} is refused for {@code field}, or null. Called only for fields in use. */
	protected abstract FieldError validate(F field, String text);

	/** Whether the field matters with the draft's other values (for example the fade distance only with a fade edge). */
	protected boolean inUse(F field) {
		return true;
	}

	/** Whether two texts mean the same value for this field. */
	protected boolean same(F field, String a, String b) {
		return a.trim().equals(b.trim());
	}

	/** Builds the result from valid values. */
	protected abstract T create();
}
