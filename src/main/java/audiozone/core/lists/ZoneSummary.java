package audiozone.core.lists;

import java.util.ArrayList;
import java.util.List;

import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.core.zones.ZoneEdge;
import audiozone.core.zones.ZoneMode;

/**
 * The Zones tab row (PRD 6.1): shape, mode, "Personal bubble" for bubbles, and where the zone is. The color swatch
 * and name come straight from the zone.
 *
 * <p>These describe only the player's own zones, so the exact distance to a zone's anchor is fine (it is a point the
 * player chose).
 */
public final class ZoneSummary {
	public static final String SPHERE = "audiozone.summary.shape.sphere";
	public static final String BOX = "audiozone.summary.shape.box";
	public static final String CYLINDER = "audiozone.summary.shape.cylinder";
	public static final String INSIDE = "audiozone.summary.mode.inside";
	public static final String OUTSIDE = "audiozone.summary.mode.outside";
	public static final String BUBBLE = "audiozone.summary.zone.bubble";

	private ZoneSummary() {
	}

	/**
	 * @param worldKey    the world the player is in now
	 * @param dimension   the player's dimension now
	 * @param player      the player's eye position
	 * @param profileEdge the profile's default edge, for zones that use it
	 */
	public static List<SummaryPart> parts(Zone zone, String worldKey, String dimension, Point player, ZoneEdge profileEdge) {
		List<SummaryPart> parts = new ArrayList<>();
		parts.add(shapePart(zone.shape()));
		parts.add(modePart(zone.mode()));
		if (zone.isBubble()) {
			parts.add(SummaryPart.of(BUBBLE));
		}
		parts.add(location(zone, worldKey, dimension, player, profileEdge).part());
		return List.copyOf(parts);
	}

	public static SummaryPart shapePart(Shape shape) {
		return switch (shape) {
			case Shape.Sphere s -> SummaryPart.of(SPHERE, Numbers.plain(s.radius()));
			case Shape.Box b -> SummaryPart.of(BOX, Numbers.plain(b.width()), Numbers.plain(b.height()), Numbers.plain(b.depth()));
			case Shape.Cylinder c -> SummaryPart.of(CYLINDER, Numbers.plain(c.radius()), Numbers.plain(c.height()));
		};
	}

	public static SummaryPart modePart(ZoneMode mode) {
		return SummaryPart.of(mode == ZoneMode.OUTSIDE ? OUTSIDE : INSIDE);
	}

	/**
	 * Here if the zone affects the player's position (weight above 0, which covers Outside zones and fade edges), else
	 * the rounded distance to the anchor. A fixed zone in another world or dimension says so instead; a personal
	 * bubble is always here.
	 */
	public static ZoneLocation location(Zone zone, String worldKey, String dimension, Point player, ZoneEdge profileEdge) {
		if (zone.isBubble()) {
			return new ZoneLocation(LocationKind.HERE, 0);
		}
		if (!zone.worldKey().equals(worldKey)) {
			return new ZoneLocation(LocationKind.OTHER_WORLD, 0);
		}
		if (!zone.dimension().equals(dimension)) {
			return new ZoneLocation(LocationKind.OTHER_DIMENSION, 0);
		}
		double dx = player.x() - zone.anchor().x();
		double dy = player.y() - zone.anchor().y();
		double dz = player.z() - zone.anchor().z();
		if (zone.weight(dx, dy, dz, profileEdge) > 0) {
			return new ZoneLocation(LocationKind.HERE, 0);
		}
		return new ZoneLocation(LocationKind.AWAY, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
	}
}
