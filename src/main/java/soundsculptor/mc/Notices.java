package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Messages shown as game toasts, outside the Sound Sculptor screen (e.g. a broken profile file, FR-11.8, or too many
 * errors, FR-13.2). Notices raised while the game is still starting are shown once it has started.
 */
public final class Notices {
	private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId(10_000L);
	private static final List<Component[]> PENDING = new ArrayList<>();
	private static final List<String> SHOWN = new ArrayList<>();
	private static final List<String> SHOWN_MESSAGES = new ArrayList<>();
	private static boolean started;

	private Notices() {
	}

	public static void register() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			synchronized (PENDING) {
				started = true;
				for (Component[] notice : PENDING) {
					display(client, notice[0], notice[1]);
				}
				PENDING.clear();
			}
		});
	}

	public static void show(Component title, Component message) {
		synchronized (PENDING) {
			if (!started) {
				PENDING.add(new Component[] {title, message});
				return;
			}
		}
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> display(minecraft, title, message));
	}

	private static void display(Minecraft minecraft, Component title, Component message) {
		synchronized (SHOWN) {
			SHOWN.add(title.getString());
			SHOWN_MESSAGES.add(message.getString());
		}
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), ID, title, message);
	}

	/** Messages of notices shown this session, oldest first, in step with {@link #shownTitles} (for tests). */
	public static List<String> shownMessages() {
		synchronized (SHOWN) {
			return List.copyOf(SHOWN_MESSAGES);
		}
	}

	/** Titles of notices shown this session, oldest first (for tests). */
	public static List<String> shownTitles() {
		synchronized (SHOWN) {
			return List.copyOf(SHOWN);
		}
	}
}
