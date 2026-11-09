package audiozone.core.undo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The last quick action, kept so its toast can offer Undo (PRD FR-12.5, FR-14.4). A quick action applies at once and
 * calls {@link #offer} with the state before and after it. For {@link #UNDO_MS} after that, {@link #undo} gives back
 * the state before. A newer action replaces the older one, so only the latest can be undone.
 *
 * <p>The toast stays while its Undo button has keyboard focus (decisions log, A-6): {@link #hold} pauses the
 * countdown and {@link #release} resumes it.
 *
 * <p>Undo is refused if the state has changed since the action (for example an editor saved in between), because
 * going back to the old state would also throw away that later change.
 *
 * <p>{@code T} is the whole state a quick action changes, such as the profile. It must be immutable and compare by
 * value. Not thread-safe: use it on the UI thread only.
 *
 * @param <T> the state type
 */
public final class UndoSlot<T> {
	/** How long the toast offers Undo. */
	public static final long UNDO_MS = 5000;

	/** What the toast shows: a translation key and its arguments, so translators can reorder words (PRD 9.3). */
	public record Toast(String messageKey, List<String> args) {
		public Toast {
			Objects.requireNonNull(messageKey, "messageKey");
			args = List.copyOf(args);
		}
	}

	private T before;
	private T after;
	private Toast toast;
	/** When the offer runs out, while not held. */
	private long expiresAtMs;
	/** Time left when the hold began, or -1 when not held. */
	private long heldRemainingMs = -1;

	/** Records a quick action that changed {@code before} into {@code after}, replacing any older one. */
	public void offer(T before, T after, String messageKey, List<String> args, long nowMs) {
		Objects.requireNonNull(before, "before");
		Objects.requireNonNull(after, "after");
		Toast t = new Toast(messageKey, args);
		this.before = before;
		this.after = after;
		this.toast = t;
		this.expiresAtMs = nowMs + UNDO_MS;
		this.heldRemainingMs = -1;
	}

	/** Whether there is an action that can still be undone, so the toast shows. */
	public boolean isActive(long nowMs) {
		return remainingMs(nowMs) > 0;
	}

	/** Time left on the toast, in milliseconds (0 when nothing can be undone). */
	public long remainingMs(long nowMs) {
		if (before == null) {
			return 0;
		}
		if (heldRemainingMs >= 0) {
			return heldRemainingMs;
		}
		return Math.max(0, expiresAtMs - nowMs);
	}

	/** The toast for the current action, if one is active. */
	public Optional<Toast> toast(long nowMs) {
		return isActive(nowMs) ? Optional.of(toast) : Optional.empty();
	}

	/**
	 * Undoes the action: returns the state before it, if the offer is still open and {@code current} is still the
	 * state the action produced. Either way the offer is used up, so the toast closes.
	 */
	public Optional<T> undo(T current, long nowMs) {
		if (!isActive(nowMs)) {
			clear();
			return Optional.empty();
		}
		T result = after.equals(current) ? before : null;
		clear();
		return Optional.ofNullable(result);
	}

	/** The Undo button got keyboard focus: pause the countdown. Does nothing if no offer is active. */
	public void hold(long nowMs) {
		if (heldRemainingMs < 0 && isActive(nowMs)) {
			heldRemainingMs = remainingMs(nowMs);
		}
	}

	/** The Undo button lost focus: resume the countdown with the time that was left. */
	public void release(long nowMs) {
		if (heldRemainingMs >= 0) {
			expiresAtMs = nowMs + heldRemainingMs;
			heldRemainingMs = -1;
		}
	}

	/** Drops the action, for example when the player leaves the server the change was made on. */
	public void clear() {
		before = null;
		after = null;
		toast = null;
		heldRemainingMs = -1;
	}
}
