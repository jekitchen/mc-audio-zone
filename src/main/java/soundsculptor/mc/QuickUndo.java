package soundsculptor.mc;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.undo.UndoSlot;
import soundsculptor.mc.ui.SculptScreen;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Undo for quick actions in lists (PRD FR-12.5): the change is saved at once and a 5-second toast offers Undo. Only
 * the latest action can be undone, and only while nothing else has changed the profile or the library since (core
 * {@link UndoSlot}); otherwise the toast says so and nothing changes. The snapshot is the whole {@link Workspace}, so
 * Undo puts back the profile, the library Sound Sets and their files together. UI thread only.
 */
public final class QuickUndo {
	private static final UndoSlot<Workspace> SLOT = new UndoSlot<>();
	/** The world the offered action was made in: undo never applies to another world's profile. */
	private static String slotWorld;

	private QuickUndo() {
	}

	/**
	 * Applies {@code change} to this world's profile and, if it changed anything, shows the toast with Undo.
	 *
	 * @param refresh   rebuilds the host's lists after Undo
	 * @param toastKey  the toast's translation key
	 * @param toastArgs its arguments
	 * @return whether the profile changed
	 */
	public static boolean apply(SculptScreen host, Runnable refresh, UnaryOperator<Profile> change, String toastKey, String... toastArgs) {
		return applyWorkspace(host, refresh, ws -> new Workspace(change.apply(ws.profile()), ws.library()), toastKey, toastArgs);
	}

	/**
	 * Applies {@code change} to the profile and the library and, if it changed anything, shows the toast with Undo.
	 *
	 * (Named apart from {@link #apply} because both take a {@code UnaryOperator} and would clash after erasure.)
	 *
	 * @return whether the profile or the library changed
	 */
	public static boolean applyWorkspace(SculptScreen host, Runnable refresh, UnaryOperator<Workspace> change, String toastKey, String... toastArgs) {
		Workspace before = ProfileManager.workspace();
		ProfileManager.change(change);
		Workspace after = ProfileManager.workspace();
		if (after.equals(before)) {
			return false;
		}
		offer(host, refresh, before, after, toastKey, List.of(toastArgs));
		return true;
	}

	/** Offers Undo for a profile change already saved; the library is taken as it is now. */
	public static void offer(SculptScreen host, Runnable refresh, Profile before, Profile after, String toastKey, List<String> toastArgs) {
		offer(host, refresh, new Workspace(before, ProfileManager.library()), new Workspace(after, ProfileManager.library()), toastKey, toastArgs);
	}

	/** Offers Undo for a change already saved, from {@code before} to {@code after}. */
	public static void offer(SculptScreen host, Runnable refresh, Workspace before, Workspace after, String toastKey, List<String> toastArgs) {
		SLOT.offer(before, after, toastKey, toastArgs, Util.getMillis());
		slotWorld = ProfileManager.key();
		host.showToast(Component.translatable(toastKey, toastArgs.toArray()), () -> undo(host, refresh),
				held -> {
					if (held) {
						SLOT.hold(Util.getMillis());
					} else {
						SLOT.release(Util.getMillis());
					}
				});
	}

	/** The Undo button: puts the profile and the library back as they were before the action. */
	static void undo(SculptScreen host, Runnable refresh) {
		Optional<Workspace> before = Objects.equals(slotWorld, ProfileManager.key())
				? SLOT.undo(ProfileManager.workspace(), Util.getMillis())
				: Optional.empty();
		SLOT.clear();
		if (before.isEmpty()) {
			host.showToast(Component.translatable("soundsculptor.undo.stale"), null);
			return;
		}
		Workspace restored = before.get();
		ProfileManager.change(current -> restored);
		refresh.run();
		host.showToast(Component.translatable("soundsculptor.undo.done"), null);
	}

	/** Forgets the offered action, e.g. when the player leaves the world. */
	public static void clear() {
		SLOT.clear();
		slotWorld = null;
	}
}
