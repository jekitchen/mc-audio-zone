package audiozone.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import audiozone.core.rules.Rule;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldIdsTest {
	private static final String ID = "6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30";
	private static final String OTHER_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

	private static Supplier<UUID> ids(String... values) {
		Deque<UUID> queue = new ArrayDeque<>();
		for (String value : values) {
			queue.add(UUID.fromString(value));
		}
		return queue::remove;
	}

	@Test
	void newIdIsALowercaseUuid() {
		assertEquals(ID, WorldIds.newId(ids(ID.toUpperCase())));
	}

	@Test
	void newIdsDiffer() {
		assertNotEquals(WorldIds.newId(UUID::randomUUID), WorldIds.newId(UUID::randomUUID));
	}

	@Test
	void fileTextIsTheIdOnOneLine() {
		assertEquals(ID + "\n", WorldIds.fileText(ID));
		assertThrows(IllegalArgumentException.class, () -> WorldIds.fileText("nope"));
	}

	@Test
	void readIdRoundTripsTheFileText() {
		assertEquals(Optional.of(ID), WorldIds.readId(WorldIds.fileText(ID)));
	}

	@Test
	void readIdIsForgivingAboutCaseWhitespaceLineEndingsAndABom() {
		assertEquals(Optional.of(ID), WorldIds.readId(ID.toUpperCase()));
		assertEquals(Optional.of(ID), WorldIds.readId("  " + ID + "\r\n"));
		assertEquals(Optional.of(ID), WorldIds.readId("﻿" + ID + "\n\n"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  \n", "hello", "6f1c2a4e0b7d4f3a9c512d8e7f6a1b30", "6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b3",
			"6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30x", "6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b3g",
			"6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30\n0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", "{6f1c2a4e-0b7d-4f3a-9c51-2d8e7f6a1b30}"})
	void readIdRejectsAnythingElse(String text) {
		assertEquals(Optional.empty(), WorldIds.readId(text));
	}

	@Test
	void readIdOfNullIsEmpty() {
		assertEquals(Optional.empty(), WorldIds.readId(null));
	}

	@Test
	void isValidId() {
		assertEquals(true, WorldIds.isValidId(ID));
		assertFalse(WorldIds.isValidId(ID.toUpperCase()));
		assertFalse(WorldIds.isValidId(null));
	}

	// "Give this copy its own profile" (FR-11.1)

	private static Zone fixed(String id, String worldKey) {
		return new Zone(id, "Hall " + id, true, worldKey, "minecraft:overworld", new Point(1, 64, 2), new Shape.Sphere(8),
				List.of(Rule.of("r1", "*villager*").withVolume(50)));
	}

	@Test
	void ownProfileGetsANewIdAndKeepsEverythingWithFixedZonesMovedToTheNewKey() {
		String oldKey = WorldKey.world(ID).key();
		Zone bubble = fixed("z2", "").asBubble();
		Profile current = Profile.empty("My World")
				.withGlobalRules(List.of(Rule.of("r1", "minecraft:entity.villager.*").withMute(true)))
				.withZones(List.of(fixed("z1", oldKey), bubble, fixed("z3", "play.example.net")))
				.withZoneColor("z1", 0xE69F00);

		WorldIds.OwnProfile own = WorldIds.giveOwnProfile(current, oldKey, ids(OTHER_ID));

		String newKey = "world:" + OTHER_ID;
		assertEquals(OTHER_ID, own.id());
		assertEquals(newKey, own.worldKey().key());
		assertEquals(OTHER_ID + "\n", own.fileText());
		assertEquals("My World", own.profile().name());
		assertEquals(current.globalRules(), own.profile().globalRules());
		assertEquals(current.settings(), own.profile().settings());
		List<Zone> zones = own.profile().zones();
		assertEquals(newKey, zones.get(0).worldKey());
		assertEquals(fixed("z1", newKey), zones.get(0));
		assertSame(bubble, zones.get(1));
		// A zone made for another place stays inactive here, as before.
		assertEquals("play.example.net", zones.get(2).worldKey());
		assertEquals(0xE69F00, own.profile().colorOf("z1"));
	}

	@Test
	void ownProfileFromAFolderFallbackMovesThoseZonesToo() {
		String oldKey = WorldKey.folder("Survival").key();
		Profile current = Profile.empty("Survival").withZones(List.of(fixed("z1", oldKey)));
		WorldIds.OwnProfile own = WorldIds.giveOwnProfile(current, oldKey, ids(OTHER_ID));
		assertEquals("world:" + OTHER_ID, own.profile().zones().get(0).worldKey());
	}

	@Test
	void ownProfileDoesNotChangeTheOriginal() {
		String oldKey = WorldKey.world(ID).key();
		Profile current = Profile.empty("My World").withZones(List.of(fixed("z1", oldKey)));
		WorldIds.giveOwnProfile(current, oldKey, ids(OTHER_ID));
		assertEquals(oldKey, current.zones().get(0).worldKey());
	}

	@Test
	void ownProfileNeverReusesTheCurrentId() {
		String oldKey = WorldKey.world(ID).key();
		WorldIds.OwnProfile own = WorldIds.giveOwnProfile(Profile.empty("W"), oldKey, ids(ID, OTHER_ID));
		assertEquals(OTHER_ID, own.id());
	}
}
