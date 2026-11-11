package soundsculptor.core.profile;

import java.util.Locale;
import java.util.Optional;

/**
 * Which profile a world uses (PRD FR-11.1). Each multiplayer server, Realm and singleplayer world has its own key:
 * <ul>
 *   <li>{@link Kind#SERVER}: the server address as typed, in lowercase, without the default port 25565
 *       ({@code play.example.net}, {@code play.example.net:25570}, {@code [::1]}). DNS and SRV records are not looked up,
 *       so the key stays the same wherever the address points.</li>
 *   <li>{@link Kind#REALM}: {@code realms:<realm id>}.</li>
 *   <li>{@link Kind#WORLD}: {@code world:<ID from soundsculptor_id.txt>} (see {@link WorldIds}).</li>
 *   <li>{@link Kind#FOLDER}: {@code folder:<save folder name>}, used when soundsculptor_id.txt cannot be written; the
 *       Profiles tab says so.</li>
 * </ul>
 *
 * <p>Keys of different kinds never collide: a server address cannot contain {@code realms:}, {@code world:} or
 * {@code folder:} followed by anything but a port, and a server whose host is literally {@code realms}, {@code world}
 * or {@code folder} keeps a trailing dot before its port ({@code realms.:1}), which names the same host.
 *
 * @param kind  where the key comes from
 * @param value the part after the kind's prefix (the whole key for servers)
 */
public record WorldKey(Kind kind, String value) {
	/** Minecraft's default server port, left out of server keys. */
	public static final int DEFAULT_PORT = 25565;

	public enum Kind {
		SERVER(""), REALM("realms:"), WORLD("world:"), FOLDER("folder:");

		private final String prefix;

		Kind(String prefix) {
			this.prefix = prefix;
		}

		public String prefix() {
			return prefix;
		}
	}

	public WorldKey {
		if (kind == null || value == null || value.isEmpty()) {
			throw new IllegalArgumentException("a world key needs a kind and a value");
		}
	}

	/** The key as stored: the profile file's name and {@code worldKey} field, and every fixed zone's world key. */
	public String key() {
		return kind.prefix + value;
	}

	/** Whether this world could not store its own ID, so renaming its folder will lose its profile. */
	public boolean isFolderFallback() {
		return kind == Kind.FOLDER;
	}

	@Override
	public String toString() {
		return key();
	}

	/** The key for a Realm. */
	public static WorldKey realm(long realmId) {
		if (realmId < 0) {
			throw new IllegalArgumentException("realm id " + realmId + " is negative");
		}
		return new WorldKey(Kind.REALM, Long.toString(realmId));
	}

	/** The key for a singleplayer world with the ID from its soundsculptor_id.txt. */
	public static WorldKey world(String id) {
		if (!WorldIds.isValidId(id)) {
			throw new IllegalArgumentException("not a world id: " + id);
		}
		return new WorldKey(Kind.WORLD, id);
	}

	/** The fallback key for a singleplayer world whose soundsculptor_id.txt cannot be written. */
	public static WorldKey folder(String folderName) {
		if (folderName == null || folderName.isBlank()) {
			throw new IllegalArgumentException("folder name is blank");
		}
		return new WorldKey(Kind.FOLDER, folderName);
	}

	/**
	 * The key for a multiplayer server, from the address the player typed in the server list or Direct Connect.
	 *
	 * @return empty if the address is not one Minecraft could connect to (blank, bad port, spaces, a path, …)
	 */
	public static Optional<WorldKey> server(String address) {
		if (address == null) {
			return Optional.empty();
		}
		String text = address.strip().toLowerCase(Locale.ROOT);
		String host;
		String portText = null;
		boolean bracketed = text.startsWith("[");
		if (bracketed) {
			int close = text.indexOf(']');
			if (close < 0) {
				return Optional.empty();
			}
			host = text.substring(1, close);
			String rest = text.substring(close + 1);
			if (!rest.isEmpty()) {
				if (!rest.startsWith(":")) {
					return Optional.empty();
				}
				portText = rest.substring(1);
			}
		} else {
			int first = text.indexOf(':');
			if (first >= 0 && first == text.lastIndexOf(':')) {
				host = text.substring(0, first);
				portText = text.substring(first + 1);
			} else {
				// No colon, or several: a bare IPv6 address, which cannot carry a port without brackets.
				host = text;
				bracketed = first >= 0;
			}
		}
		int port = DEFAULT_PORT;
		if (portText != null) {
			port = parsePort(portText);
			if (port < 0) {
				return Optional.empty();
			}
		}
		if (!bracketed) {
			host = stripTrailingDots(host);
		}
		if (!validHost(host, bracketed)) {
			return Optional.empty();
		}
		StringBuilder key = new StringBuilder(host.length() + 8);
		if (bracketed) {
			key.append('[').append(host).append(']');
		} else {
			key.append(host);
			if (port != DEFAULT_PORT && isKindName(host)) {
				key.append('.');
			}
		}
		if (port != DEFAULT_PORT) {
			key.append(':').append(port);
		}
		return Optional.of(new WorldKey(Kind.SERVER, key.toString()));
	}

	/**
	 * Reads a stored key back (from a profile file or a fixed zone). Never fails: anything that is not a well-formed
	 * Realm, world or folder key is read as a server key, which matches only itself.
	 */
	public static WorldKey parse(String key) {
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("world key is empty");
		}
		if (key.startsWith(Kind.REALM.prefix)) {
			String id = key.substring(Kind.REALM.prefix.length());
			if (!id.isEmpty() && id.length() <= 19 && id.chars().allMatch(c -> c >= '0' && c <= '9')) {
				return new WorldKey(Kind.REALM, id);
			}
		} else if (key.startsWith(Kind.WORLD.prefix)) {
			String id = key.substring(Kind.WORLD.prefix.length());
			if (WorldIds.isValidId(id)) {
				return new WorldKey(Kind.WORLD, id);
			}
		} else if (key.startsWith(Kind.FOLDER.prefix)) {
			String name = key.substring(Kind.FOLDER.prefix.length());
			if (!name.isBlank()) {
				return new WorldKey(Kind.FOLDER, name);
			}
		}
		return new WorldKey(Kind.SERVER, key);
	}

	private static int parsePort(String text) {
		if (text.isEmpty()) {
			return -1;
		}
		int port = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < '0' || c > '9') {
				return -1;
			}
			port = port * 10 + (c - '0');
			if (port > 65535) {
				return -1;
			}
		}
		return port >= 1 && port <= 65535 ? port : -1;
	}

	private static String stripTrailingDots(String host) {
		int end = host.length();
		while (end > 0 && host.charAt(end - 1) == '.') {
			end--;
		}
		return host.substring(0, end);
	}

	private static boolean validHost(String host, boolean ipv6) {
		if (host.isEmpty()) {
			return false;
		}
		for (int i = 0; i < host.length(); i++) {
			char c = host.charAt(i);
			boolean ok = ipv6
					? (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == ':' || c == '.' || c == '%'
					: c > ' ' && c != 0x7F && "/\\@?#[]:".indexOf(c) < 0 && !Character.isWhitespace(c);
			if (!ok) {
				return false;
			}
		}
		return !ipv6 || host.indexOf(':') >= 0;
	}

	private static boolean isKindName(String host) {
		for (Kind kind : Kind.values()) {
			if (kind != Kind.SERVER && kind.prefix.equals(host + ":")) {
				return true;
			}
		}
		return false;
	}
}
