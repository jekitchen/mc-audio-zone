package audiozone.core.outline;

import audiozone.core.zones.Point;
import audiozone.core.zones.Zone;

/**
 * Which zone outlines and name labels to draw this frame (PRD FR-10.1 to FR-10.3). Allocation-free, so the renderer
 * can call it for every zone every frame.
 */
public final class OutlineVisibility {
	/** Zones whose nearest part is farther than this from the camera are not drawn (FR-10.2). */
	public static final double DRAW_DISTANCE = 128;
	/** The zone's name label shows when the camera is this close to its anchor (FR-10.3). */
	public static final double LABEL_DISTANCE = 32;

	private OutlineVisibility() {
	}

	/**
	 * @param editingZoneId the ID of the zone open in the zone editor, or {@code null}
	 * @param worldKey      the current server or world
	 * @param dimension     the current dimension
	 */
	public static boolean shouldDraw(Zone zone, OutlineMode mode, String editingZoneId, String worldKey, String dimension,
			double camX, double camY, double camZ) {
		boolean editing = zone.id().equals(editingZoneId);
		switch (mode) {
			case NEVER:
				return false;
			case EDITING:
				if (!editing) {
					return false;
				}
				break;
			case ALWAYS:
				if (!zone.enabled() && !editing) {
					return false;
				}
				break;
		}
		if (!worldKey.equals(zone.worldKey()) || !dimension.equals(zone.dimension())) {
			return false;
		}
		return distanceToShape(zone, camX, camY, camZ) <= DRAW_DISTANCE;
	}

	/** Whether the zone's name label shows at its anchor. */
	public static boolean showLabel(Zone zone, double camX, double camY, double camZ) {
		Point a = zone.anchor();
		double dx = camX - a.x();
		double dy = camY - a.y();
		double dz = camZ - a.z();
		return dx * dx + dy * dy + dz * dz <= LABEL_DISTANCE * LABEL_DISTANCE;
	}

	/** Distance from the camera to the nearest point of the zone; 0 when the camera is inside. */
	static double distanceToShape(Zone zone, double camX, double camY, double camZ) {
		Point a = zone.anchor();
		double dx = camX - a.x();
		double dy = camY - a.y();
		double dz = camZ - a.z();
		return zone.shape().distanceOutside(dx, dy, dz);
	}
}
