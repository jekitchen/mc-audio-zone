package audiozone.core.zones;

import java.util.List;

import audiozone.core.rules.Rule;

/**
 * A fixed zone (PRD FR-8.1, MVP): Inside mode with a hard edge. It changes positional sounds inside its shape, only in
 * its own server or world ({@code worldKey}) and dimension.
 *
 * <p>Color, display and edge settings belong to the profile model (issue #8). Personal bubbles, Outside mode and fade
 * (v1, M13) will add an anchor type, a mode and a fade weight here.
 *
 * @param rules the zone's own rule list, same format as the global list (FR-8.6)
 */
public record Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape,
		List<Rule> rules) {
	public Zone {
		if (id == null || worldKey == null || dimension == null || anchor == null || shape == null) {
			throw new IllegalArgumentException("zone id, world key, dimension, anchor and shape are required");
		}
		rules = List.copyOf(rules);
	}
}
