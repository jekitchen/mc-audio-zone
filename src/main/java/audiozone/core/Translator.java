package audiozone.core;

/**
 * Turns a translation key and its arguments into text in the player's language. The Minecraft side backs this with
 * {@code I18n.get}; tests use a fixed English table. Keys live in {@code assets/audiozone/lang/en_us.json}.
 */
@FunctionalInterface
public interface Translator {
	String translate(String key, Object... args);
}
