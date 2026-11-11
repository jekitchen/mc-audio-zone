package soundsculptor.core.zones;

import java.util.List;

import soundsculptor.core.rules.Rule;

/**
 * A zone (PRD FR-8): an area with its own rule list that changes positional sounds based on where they come from.
 *
 * <ul>
 *   <li>A <b>fixed</b> zone (FR-8.1) is active only in its own server or world ({@code worldKey}) and dimension.</li>
 *   <li>A <b>personal bubble</b> (FR-8.2, v1) is anchored at the local player's eye position, updated every tick,
 *       wherever the player is; its stored {@code anchor}, world key and dimension are not used.</li>
 *   <li>{@link ZoneMode#INSIDE} (default) affects sounds in the shape; {@link ZoneMode#OUTSIDE} (v1) affects sounds
 *       outside it in the same dimension.</li>
 *   <li>The edge (FR-8.5) is hard, or fades over a distance; {@link ZoneEdge#DEFAULT} uses the profile's setting.</li>
 * </ul>
 * Color and display settings belong to the profile model.
 *
 * @param rules the zone's own rule list, same format as the global list (FR-8.6)
 */
public record Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
		List<Rule> rules, ZoneMode mode, ZoneEdge edge, AnchorType anchorType) {
	public Zone {
		if (id == null || worldKey == null || dimension == null || anchor == null || shape == null || mode == null
				|| edge == null || anchorType == null) {
			throw new IllegalArgumentException("zone id, world key, dimension, anchor, shape, mode, edge and anchor type are required");
		}
		rules = List.copyOf(rules);
	}

	/** A fixed Inside zone with the profile's default edge: the MVP zone. */
	public Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
			List<Rule> rules) {
		this(id, name, enabled, worldKey, dimension, anchor, shape, rules, ZoneMode.INSIDE, ZoneEdge.DEFAULT, AnchorType.FIXED);
	}

	public Zone withMode(ZoneMode value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, rules, value, edge, anchorType);
	}

	public Zone withEdge(ZoneEdge value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, rules, mode, value, anchorType);
	}

	/** The same zone with another rule list (FR-8.6). */
	public Zone withRules(List<Rule> value) {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, value, mode, edge, anchorType);
	}

	/** The same zone turned on or off. */
	public Zone withEnabled(boolean value) {
		return new Zone(id, name, value, worldKey, dimension, anchor, shape, rules, mode, edge, anchorType);
	}

	/** The same zone in another world (used when a copied world gets its own profile, FR-11.1). */
	public Zone withWorldKey(String value) {
		return new Zone(id, name, enabled, value, dimension, anchor, shape, rules, mode, edge, anchorType);
	}

	/** The same zone as a personal bubble that follows the local player. */
	public Zone asBubble() {
		return new Zone(id, name, enabled, worldKey, dimension, anchor, shape, rules, mode, edge, AnchorType.BUBBLE);
	}

	public boolean isBubble() {
		return anchorType == AnchorType.BUBBLE;
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
