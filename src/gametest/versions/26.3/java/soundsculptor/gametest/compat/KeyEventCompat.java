package soundsculptor.gametest.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/**
 * Minecraft 26.3 version: builds key events the way the game does (used by tests to send key presses with Shift or
 * Ctrl). 26.3 uses SDL3 input: {@code key} is the physical key, and vanilla matches shortcuts (Tab, arrows) on the
 * separate layout keycode, so both are filled in. Keep the public API identical to the 26.2 copy.
 */
public final class KeyEventCompat {
	private KeyEventCompat() {
	}

	/** @param modifiers {@code InputConstants.MOD_*} bits */
	public static KeyEvent of(int key, int modifiers) {
		return new KeyEvent(key, keycode(key), modifiers);
	}

	private static int keycode(int key) {
		return switch (key) {
			case InputConstants.KEY_TAB -> InputConstants.KEYCODE_TAB;
			case InputConstants.KEY_LEFT -> InputConstants.KEYCODE_LEFT;
			case InputConstants.KEY_RIGHT -> InputConstants.KEYCODE_RIGHT;
			case InputConstants.KEY_HOME -> InputConstants.KEYCODE_HOME;
			case InputConstants.KEY_END -> InputConstants.KEYCODE_END;
			default -> throw new IllegalArgumentException("no keycode mapping for key " + key + "; add it here");
		};
	}
}
