package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.profile.WorldKey.Kind;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorldKeyTest {
	private static String server(String address) {
		return WorldKey.server(address).orElseThrow().key();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"play.example.net|play.example.net",
			"Play.Example.NET|play.example.net",
			"play.example.net:25565|play.example.net",
			"play.example.net:25566|play.example.net:25566",
			"play.example.net:025565|play.example.net",
			"'  play.example.net  '|play.example.net",
			"play.example.net.|play.example.net",
			"play.example.net.:25565|play.example.net",
			"play.example.net..:25570|play.example.net:25570",
			"localhost|localhost",
			"127.0.0.1:25565|127.0.0.1",
			"192.168.0.10:25600|192.168.0.10:25600",
			"[::1]|[::1]",
			"[::1]:25565|[::1]",
			"[::1]:25570|[::1]:25570",
			"[2001:DB8::1]:25565|[2001:db8::1]",
			"2001:db8::1|[2001:db8::1]",
			"::1|[::1]",
			"_minecraft._tcp.example.net|_minecraft._tcp.example.net",
			"mc-01.example.net|mc-01.example.net",
	})
	void serverKeysAreTheTypedAddressLowercasedWithoutTheDefaultPort(String address, String key) {
		assertEquals(key, server(address));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", ":25565", ".", "...", "host:", "host:0", "host:65536", "host:-1", "host:12a",
			"host:99999999999", "[::1", "[]", "[]:25565", "[::1]x", "[::1]:", "[::1]:0", "play example.net",
			"play.example.net/path", "user@play.example.net", "play\texample.net", "play\u0000.net"})
	void invalidAddressesGiveNoKey(String address) {
		assertEquals(Optional.empty(), WorldKey.server(address));
	}

	@Test
	void nullAddressGivesNoKey() {
		assertEquals(Optional.empty(), WorldKey.server(null));
	}

	@Test
	void serverKeyIsBasedOnTheTypedAddressNotWhereItResolves() {
		// SRV records and DNS are not looked up: the key is the address the player typed.
		assertEquals("example.net", server("example.net"));
		assertEquals("mc.example.net", server("mc.example.net"));
	}

	@Test
	void realmKeys() {
		assertEquals("realms:12345", WorldKey.realm(12345).key());
		assertEquals(Kind.REALM, WorldKey.realm(12345).kind());
		assertThrows(IllegalArgumentException.class, () -> WorldKey.realm(-1));
	}

	@Test
	void singleplayerKeyUsesTheWorldId() {
		WorldKey key = WorldKey.world("6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30");
		assertEquals("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30", key.key());
		assertEquals(Kind.WORLD, key.kind());
		assertThrows(IllegalArgumentException.class, () -> WorldKey.world("not an id"));
	}

	@Test
	void folderFallbackIsMarkedSoTheProfilesTabCanSaySo() {
		WorldKey key = WorldKey.folder("New World (1)");
		assertEquals("folder:New World (1)", key.key());
		assertEquals(Kind.FOLDER, key.kind());
		assertTrue(key.isFolderFallback());
		assertThrows(IllegalArgumentException.class, () -> WorldKey.folder(""));
		assertThrows(IllegalArgumentException.class, () -> WorldKey.folder("  "));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"play.example.net|SERVER|play.example.net",
			"[::1]:25570|SERVER|[::1]:25570",
			"default|SERVER|default",
			"realms:12345|REALM|12345",
			"world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30|WORLD|6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30",
			"folder:New World|FOLDER|New World",
	})
	void parseReadsAStoredKeyBack(String key, Kind kind, String value) {
		WorldKey parsed = WorldKey.parse(key);
		assertEquals(kind, parsed.kind());
		assertEquals(value, parsed.value());
		assertEquals(key, parsed.key());
	}

	@ParameterizedTest
	@ValueSource(strings = {"realms:", "realms:abc", "realms:-1", "world:", "world:not-an-id", "folder:", "folder: "})
	void parseNeverThrowsAndReadsAMalformedKeyAsAServerKey(String key) {
		WorldKey parsed = WorldKey.parse(key);
		assertEquals(Kind.SERVER, parsed.kind());
		assertEquals(key, parsed.key());
	}

	@Test
	void everyKindRoundTrips() {
		for (WorldKey key : new WorldKey[] {WorldKey.server("a.example.net:25570").orElseThrow(), WorldKey.realm(7),
				WorldKey.world("6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30"), WorldKey.folder("Survival")}) {
			assertEquals(key, WorldKey.parse(key.key()));
		}
	}

	@Test
	void keysWithDifferentKindsNeverCollide() {
		// A server cannot be called "realms:1" or "world:…": a colon followed by a non-port is not a valid address.
		assertEquals(Optional.empty(), WorldKey.server("realms:1x"));
		assertEquals(Optional.empty(), WorldKey.server("world:6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30"));
		assertEquals(Optional.empty(), WorldKey.server("folder:Survival"));
	}

	@Test
	void aServerNamedLikeAKindPrefixKeepsATrailingDotSoItCannotBeMistakenForOne() {
		// "realms." is the same host as "realms" in DNS, so the key still names the right server.
		assertEquals("realms.:1", server("realms:1"));
		assertEquals("world.:25570", server("WORLD:25570"));
		assertEquals("folder.:2", server("folder.:2"));
		assertEquals(Kind.SERVER, WorldKey.parse(server("realms:1")).kind());
		assertEquals("realms", server("realms:25565"));
		assertEquals(Kind.SERVER, WorldKey.parse("realms").kind());
	}
}
