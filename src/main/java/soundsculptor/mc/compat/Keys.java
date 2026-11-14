package soundsculptor.mc.compat;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Key codes as the running game defines them. Minecraft 26.2 uses GLFW codes and 26.3 uses SDL3 codes, and
 * {@code InputConstants.KEY_*} are compile-time constants that javac copies into the class using them, so code naming
 * them directly gets the key codes of the version it was compiled against. Main code uses these instead and never
 * {@code InputConstants.KEY_*} or a number (docs/PORTING.md, "One jar for every version"). They are not constants, so
 * compare them in if/else chains: {@code switch} cannot use them.
 *
 * <p>Each value is read once, when this class loads; a name the running game does not have stops the game with a
 * message naming it.
 */
public final class Keys {
	public static final int TAB = key("KEY_TAB");
	public static final int ESCAPE = key("KEY_ESCAPE");
	public static final int BACKSPACE = key("KEY_BACKSPACE");
	public static final int UP = key("KEY_UP");
	public static final int DOWN = key("KEY_DOWN");
	public static final int LEFT = key("KEY_LEFT");
	public static final int RIGHT = key("KEY_RIGHT");
	public static final int PAGE_UP = key("KEY_PAGEUP");
	public static final int PAGE_DOWN = key("KEY_PAGEDOWN");
	public static final int P = key("KEY_P");
	public static final int Z = key("KEY_Z");
	public static final int SEMICOLON = key("KEY_SEMICOLON");
	public static final int F10 = key("KEY_F10");

	private Keys() {
	}

	private static int key(String name) {
		return RuntimeLookup.staticInt(InputConstants.class, name);
	}
}
