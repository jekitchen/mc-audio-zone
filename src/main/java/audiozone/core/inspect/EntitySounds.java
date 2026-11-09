package audiozone.core.inspect;

import java.util.ArrayList;
import java.util.List;

/**
 * What the point-at-entity inspector knows (PRD FR-2.2 to FR-2.4): for each entity within vanilla hearing range, its
 * last {@value #SOUNDS_PER_ENTITY} sounds from the last 30 seconds, at most {@value #MAX_ENTITIES} entities, each
 * sound labeled Linked, Likely or Uncertain.
 *
 * <p>The sound hook calls {@link #recordLinked} for sounds tied to an entity and {@link #recordPositional} for plain
 * positional sounds. Both allocate nothing (PRD 7.4). The panel calls {@link #soundsOf} for the entity under the
 * crosshair; an empty list means "No sounds linked yet" (FR-2.4).
 *
 * <p>Fair play (section 8.2): only entities the caller marks as within hearing range ever get a sound, so the
 * inspector can never reveal a mob the player could not hear. Line-of-sight selection is the caller's job.
 *
 * <p>Not thread-safe: record and read from the client thread.
 */
public final class EntitySounds {
	public static final int SOUNDS_PER_ENTITY = 20;
	public static final long WINDOW_MS = 30_000;
	public static final int MAX_ENTITIES = 256;
	/** A positional sound links only to entities within this many blocks of it (FR-2.3). */
	public static final double LINK_DISTANCE = 1.0;
	/** Two candidates this close to each other make the link Uncertain (FR-2.3). */
	public static final double UNCERTAIN_DISTANCE = 0.25;

	private static final double LINK_DISTANCE_SQ = LINK_DISTANCE * LINK_DISTANCE;
	private static final double UNCERTAIN_DISTANCE_SQ = UNCERTAIN_DISTANCE * UNCERTAIN_DISTANCE;
	private static final String ENTITY_PREFIX = "entity.";
	private static final String MINECRAFT = "minecraft";

	private final int[] entityIds = new int[MAX_ENTITIES];
	private final boolean[] used = new boolean[MAX_ENTITIES];
	private final long[] lastHeard = new long[MAX_ENTITIES];
	private final int[] head = new int[MAX_ENTITIES];
	private final int[] count = new int[MAX_ENTITIES];
	private final String[] soundIds = new String[MAX_ENTITIES * SOUNDS_PER_ENTITY];
	private final LinkKind[] kinds = new LinkKind[MAX_ENTITIES * SOUNDS_PER_ENTITY];
	private final long[] times = new long[MAX_ENTITIES * SOUNDS_PER_ENTITY];
	private int entityCount;
	private int lastEntityId = -1;

	/** A sound attached to an entity, or sent by the server with the entity's ID. Ignored if out of hearing range. */
	public void recordLinked(int entityId, String soundId, boolean inRange, long nowMs) {
		lastEntityId = -1;
		if (!inRange) {
			return;
		}
		store(entityId, soundId, LinkKind.LINKED, nowMs);
		lastEntityId = entityId;
	}

	/**
	 * A plain positional sound: links it to the best candidate within 1 block, or to nothing.
	 *
	 * <p>Only in-range candidates count. If any of them matches the sound's type ({@code entity.villager.*} for a
	 * {@code minecraft:villager}), only those are considered. The nearest one wins; if another considered candidate
	 * stands within 0.25 blocks of it, the link is Uncertain.
	 *
	 * @return the link's label, or null if no entity was linked; {@link #lastEntityId()} then names the entity
	 */
	public LinkKind recordPositional(String soundId, double x, double y, double z, Candidates near, long nowMs) {
		lastEntityId = -1;
		boolean anyMatch = false;
		for (int i = 0; i < near.size; i++) {
			if (eligible(near, i, x, y, z) && typeMatches(soundId, near.types[i])) {
				anyMatch = true;
				break;
			}
		}
		int best = -1;
		double bestSq = Double.MAX_VALUE;
		for (int i = 0; i < near.size; i++) {
			if (considered(near, i, soundId, anyMatch, x, y, z)) {
				double d = distanceSq(near, i, x, y, z);
				if (d < bestSq) {
					bestSq = d;
					best = i;
				}
			}
		}
		if (best < 0) {
			return null;
		}
		LinkKind kind = LinkKind.LIKELY;
		for (int i = 0; i < near.size; i++) {
			if (i != best && considered(near, i, soundId, anyMatch, x, y, z)
					&& distanceSq(near, i, near.xs[best], near.ys[best], near.zs[best]) <= UNCERTAIN_DISTANCE_SQ) {
				kind = LinkKind.UNCERTAIN;
				break;
			}
		}
		store(near.ids[best], soundId, kind, nowMs);
		lastEntityId = near.ids[best];
		return kind;
	}

	/** The entity the last recorded sound was linked to, or -1. */
	public int lastEntityId() {
		return lastEntityId;
	}

	/** The entity's sounds from the last 30 seconds, newest first. Empty if none (FR-2.4). */
	public List<EntitySound> soundsOf(int entityId, long nowMs) {
		int slot = find(entityId);
		if (slot < 0) {
			return List.of();
		}
		List<EntitySound> out = new ArrayList<>(count[slot]);
		int base = slot * SOUNDS_PER_ENTITY;
		for (int k = 0; k < count[slot]; k++) {
			int index = base + Math.floorMod(head[slot] - 1 - k, SOUNDS_PER_ENTITY);
			if (nowMs - times[index] > WINDOW_MS) {
				break; // older ones are older still
			}
			out.add(new EntitySound(soundIds[index], kinds[index], times[index]));
		}
		return List.copyOf(out);
	}

	/** How many entities have a memory slot. */
	public int entityCount() {
		return entityCount;
	}

	/** Drops an entity, e.g. when it is removed from the world. */
	public void forget(int entityId) {
		int slot = find(entityId);
		if (slot >= 0) {
			free(slot);
		}
	}

	/** Drops everything, e.g. on disconnect or world change. */
	public void clear() {
		for (int slot = 0; slot < MAX_ENTITIES; slot++) {
			if (used[slot]) {
				free(slot);
			}
		}
	}

	/**
	 * Whether a sound ID belongs to an entity type: {@code ns:entity.<type>.<sound>} matches {@code ns:<type>}. A
	 * missing namespace means {@code minecraft}, as in Minecraft's own IDs.
	 */
	public static boolean typeMatches(String soundId, String typeId) {
		if (soundId == null || typeId == null) {
			return false;
		}
		int soundColon = soundId.indexOf(':');
		int typeColon = typeId.indexOf(':');
		if (!sameNamespace(soundId, soundColon, typeId, typeColon)) {
			return false;
		}
		int path = soundColon + 1;
		if (!soundId.startsWith(ENTITY_PREFIX, path)) {
			return false;
		}
		int typeStart = typeColon + 1;
		int typeLength = typeId.length() - typeStart;
		int start = path + ENTITY_PREFIX.length();
		int dot = start + typeLength;
		return typeLength > 0 && soundId.regionMatches(start, typeId, typeStart, typeLength)
				&& soundId.length() > dot + 1 && soundId.charAt(dot) == '.';
	}

	private static boolean sameNamespace(String a, int aColon, String b, int bColon) {
		if (aColon < 0 && bColon < 0) {
			return true;
		}
		if (aColon < 0) {
			return isMinecraft(b, bColon);
		}
		if (bColon < 0) {
			return isMinecraft(a, aColon);
		}
		return aColon == bColon && a.regionMatches(0, b, 0, aColon);
	}

	private static boolean isMinecraft(String id, int colon) {
		return colon == MINECRAFT.length() && id.startsWith(MINECRAFT);
	}

	private static boolean eligible(Candidates near, int i, double x, double y, double z) {
		return near.inRange[i] && distanceSq(near, i, x, y, z) <= LINK_DISTANCE_SQ;
	}

	private static boolean considered(Candidates near, int i, String soundId, boolean onlyMatches, double x, double y, double z) {
		return eligible(near, i, x, y, z) && (!onlyMatches || typeMatches(soundId, near.types[i]));
	}

	private static double distanceSq(Candidates near, int i, double x, double y, double z) {
		double dx = near.xs[i] - x;
		double dy = near.ys[i] - y;
		double dz = near.zs[i] - z;
		return dx * dx + dy * dy + dz * dz;
	}

	private void store(int entityId, String soundId, LinkKind kind, long nowMs) {
		int slot = find(entityId);
		if (slot < 0) {
			slot = claim(entityId);
		}
		int index = slot * SOUNDS_PER_ENTITY + head[slot];
		soundIds[index] = soundId;
		kinds[index] = kind;
		times[index] = nowMs;
		head[slot] = (head[slot] + 1) % SOUNDS_PER_ENTITY;
		if (count[slot] < SOUNDS_PER_ENTITY) {
			count[slot]++;
		}
		lastHeard[slot] = nowMs;
	}

	private int find(int entityId) {
		for (int slot = 0; slot < MAX_ENTITIES; slot++) {
			if (used[slot] && entityIds[slot] == entityId) {
				return slot;
			}
		}
		return -1;
	}

	/** A free slot, or the least recently heard entity's slot. */
	private int claim(int entityId) {
		int slot = -1;
		for (int s = 0; s < MAX_ENTITIES; s++) {
			if (!used[s]) {
				slot = s;
				break;
			}
		}
		if (slot < 0) {
			slot = 0;
			for (int s = 1; s < MAX_ENTITIES; s++) {
				if (lastHeard[s] < lastHeard[slot]) {
					slot = s;
				}
			}
			free(slot);
		}
		used[slot] = true;
		entityIds[slot] = entityId;
		entityCount++;
		return slot;
	}

	private void free(int slot) {
		used[slot] = false;
		head[slot] = 0;
		count[slot] = 0;
		int base = slot * SOUNDS_PER_ENTITY;
		for (int k = 0; k < SOUNDS_PER_ENTITY; k++) {
			soundIds[base + k] = null;
			kinds[base + k] = null;
		}
		entityCount--;
	}
}
