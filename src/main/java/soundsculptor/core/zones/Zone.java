package soundsculptor.core.zones;

import java.util.Objects;

import soundsculptor.core.sets.PlaceSets;

/**
 * A zone (PRD FR-8): an area with its own attached Sound Sets that change positional sounds based on where they come
 * from.
 *
 * <ul>
 *   <li>Every zone is fixed (FR-8.1): its {@code anchor} is the shape's geometric center, and it is active only in its
 *       own server or world ({@code worldKey}) and dimension.</li>
 *   <li>{@link ZoneMode#INSIDE} (default) affects sounds in the shape; {@link ZoneMode#OUTSIDE} (v1) affects sounds
 *       outside it in the same dimension.</li>
 *   <li>The edge (FR-8.5) is hard, or fades over a distance; {@link ZoneEdge#DEFAULT} uses the profile's setting.</li>
 * </ul>
 * Color and display settings belong to the profile model.
 *
 * @param sets the Sound Sets attached to the zone, and its Quick changes, as for Everywhere (FR-8.6)
 */
public record Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
		PlaceSets sets, ZoneMode mode, ZoneEdge edge) {
	public Zone {
		if (id == null || worldKey == null || dimension == null || anchor == null || shape == null || mode == null
				|| edge == null) {
			throw new IllegalArgumentException("zone id, world key, dimension, anchor, shape, mode and edge are required");
		}
		Objects.requireNonNull(sets, "sets");
	}

	/** An Inside zone with the profile's default edge: the MVP zone. */
	public Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
			PlaceSets sets) {
		this(id, name, enabled, worldKey, dimension, anchor, shape, sets, ZoneMode.INSIDE, ZoneEdge.DEFAULT);
	}

	public Zone withMode(ZoneMode value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, sets, value, edge);
	}

	public Zone withEdge(ZoneEdge value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, sets, mode, value);
	}

	/** The same zone with other attached sets (FR-8.6). */
	public Zone withSets(PlaceSets value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, value, mode, edge);
	}

	/** The same zone turned on or off. */
	public Zone withEnabled(boolean value) {
		return new Zone(id, name, value, worldKey, dimension, anchor, shape, sets, mode, edge);
	}

	/** The same zone in another world (used when a copied world gets its own profile, FR-11.1). */
	public Zone withWorldKey(String value) {
		return new Zone(id, name, enabled, value, dimension, anchor, shape, sets, mode, edge);
	}

	/**
	 * How strongly this zone applies at the offset {@code (dx, dy, dz)} from its anchor (FR-8.5): 1 on the affected
	 * side with a hard edge; with a fade, rising from 0 at the boundary to 1 at the fade distance into the affected
	 * side (inward for Inside zones, outward for Outside zones); 0 on the other side. Allocation-free.
	 *
	 * @param profileEdge the profile's default edge, used when this zone's edge is {@link ZoneEdge#DEFAULT}
	 */
	public double weight(double dx, double dy, double dz, ZoneEdge profileEdge) {
		ZoneEdge e = edge.mode() == ZoneEdge.Mode.DEFAULT ? profileEdge : edge;
		boolean inside = shape.contains(dx, dy, dz);
		if (inside != (mode == ZoneMode.INSIDE)) {
			return 0;
		}
		if (e.mode() != ZoneEdge.Mode.FADE) {
			return 1;
		}
		double into = mode == ZoneMode.INSIDE ? shape.depthInside(dx, dy, dz) : shape.distanceOutside(dx, dy, dz);
		return Math.min(1, into / e.fadeDistance());
	}
}
