package audiozone.mc.compat;

import net.minecraft.client.input.KeyEvent;

/**
 * Minecraft 26.2 version: builds key events the way the game does (used by tests to send key presses with Shift or
 * Ctrl). 26.2 uses GLFW key codes; the second field is the platform scancode, which nothing here reads.
 * Keep the public API identical to the 26.3 copy.
 */
public final class KeyEventCompat {
	private KeyEventCompat() {
	}

	/** @param modifiers {@code InputConstants.MOD_*} bits */
	public static KeyEvent of(int key, int modifiers) {
		return new KeyEvent(key, 0, modifiers);
	}
}
