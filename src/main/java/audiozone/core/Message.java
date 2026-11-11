package audiozone.core;

import java.util.List;
import java.util.Objects;

/**
 * Text for the player that core code works out but cannot translate: a key in
 * {@code assets/audiozone/lang/en_us.json} and its arguments (PRD 9.3). An argument may itself be a message, such as a
 * place in a file ("Zone 3"); it is translated first. Not for the hot path.
 */
public record Message(String key, List<Object> args) {
	public Message {
		Objects.requireNonNull(key, "key");
		args = List.copyOf(args);
	}

	public static Message of(String key, Object... args) {
		return new Message(key, List.of(args));
	}

	/** The text in the player's language. */
	public String text(Translator translator) {
		Object[] values = new Object[args.size()];
		for (int i = 0; i < values.length; i++) {
			Object arg = args.get(i);
			values[i] = arg instanceof Message message ? message.text(translator) : arg;
		}
		return translator.translate(key, values);
	}

	/** The key and arguments, for logs. */
	@Override
	public String toString() {
		return args.isEmpty() ? key : key + " " + args;
	}
}
