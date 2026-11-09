package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.profile.WorldIds;
import audiozone.core.profile.WorldKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Picks the profile when the player joins a server or world, and drops it on leaving (PRD FR-11.1).
 *
 * <ul>
 *   <li>Multiplayer: the server address, lowercase, without the default port ({@code play.example.net}). A Realm's
 *   address is used the same way, because the Realm ID is not available to the client.</li>
 *   <li>Singleplayer: {@code world:<id>}, where the ID is kept in {@code audiozone_id.txt} in the world's save folder, so
 *   the profile survives folder renames and backups. If that file cannot be written (a read-only save), the folder
 *   name is used instead.</li>
 * </ul>
 */
public final class WorldSession {
	private static Object dimensionLevel;
	private static String dimension = "";
	private static boolean folderFallback;

	private WorldSession() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> join(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> leave());
	}

	static void join(Minecraft minecraft) {
		Optional<Joined> joined = worldKey(minecraft);
		if (joined.isEmpty()) {
			AudioZoneClient.LOGGER.warn("Audio Zone could not tell which server or world this is; sounds play as in vanilla");
			ProfileManager.leave();
			return;
		}
		folderFallback = joined.get().key().isFolderFallback();
		ProfileManager.join(joined.get().key().key(), joined.get().displayName());
		PlayingChannels.get().clear();
	}

	static void leave() {
		ProfileManager.leave();
		PlayingChannels.get().clear();
		dimensionLevel = null;
		dimension = "";
	}

	/** Whether this singleplayer world uses its folder name because its ID file could not be written. */
	public static boolean usesFolderFallback() {
		return folderFallback;
	}

	/**
	 * The current dimension's ID, e.g. {@code minecraft:overworld}; one string per dimension change, so the sound path
	 * does not build it per sound. Empty outside a world.
	 */
	public static String dimension(Minecraft minecraft) {
		if (minecraft.level == null) {
			return "";
		}
		// A dimension change gives the client a new level object.
		if (minecraft.level != dimensionLevel) {
			dimensionLevel = minecraft.level;
			dimension = minecraft.level.dimension().identifier().toString();
		}
		return dimension;
	}

	private record Joined(WorldKey key, String displayName) {
	}

	private static Optional<Joined> worldKey(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			String levelName = server.getWorldData().getLevelName();
			return Optional.of(new Joined(singleplayerKey(root), levelName));
		}
		ServerData data = minecraft.getCurrentServer();
		if (data == null) {
			return Optional.empty();
		}
		return WorldKey.server(data.ip).map(key -> new Joined(key, key.value()));
	}

	/** Reads the world's ID file, writing a new one on first join; the folder name if that fails. */
	public static WorldKey singleplayerKey(Path root) {
		Path file = root.resolve(WorldIds.FILE_NAME);
		try {
			if (Files.exists(file)) {
				Optional<String> id = WorldIds.readId(Files.readString(file, StandardCharsets.UTF_8));
				if (id.isPresent()) {
					return WorldKey.world(id.get());
				}
			}
			String id = WorldIds.newId(UUID::randomUUID);
			Files.writeString(file, WorldIds.fileText(id), StandardCharsets.UTF_8);
			return WorldKey.world(id);
		} catch (IOException | RuntimeException e) {
			AudioZoneClient.LOGGER.warn("Audio Zone could not read or write {}; using the folder name for this world's profile", file, e);
			Path name = root.getFileName();
			return WorldKey.folder(name == null ? "world" : name.toString());
		}
	}
}
