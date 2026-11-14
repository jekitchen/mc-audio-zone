package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.outline.Outline;
import soundsculptor.core.outline.OutlineGeometry;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.outline.OutlineVisibility;
import soundsculptor.core.outline.ZoneColors;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Draws zone outlines in the world (PRD FR-10 MVP): thin lines in the zone's color, solid for Inside zones, with the
 * zone's name at its anchor when the camera is within 32 blocks. Which zones are drawn follows the "Show zone
 * outlines" setting (Always, Only while editing, Never) and the 128-block draw distance; "Outlines visible through
 * blocks" (on by default, FR-10.5) draws them on top of the world.
 *
 * <p>Uses vanilla's gizmo lines and labels, emitted each frame when the level has been extracted (Fabric
 * {@code LevelRenderEvents.END_EXTRACTION}): vanilla collects the frame's gizmos until it submits features, so the
 * later {@code BEFORE_GIZMOS} event is too late for them to be drawn. The same API exists in 26.2 and 26.3. Outlines are built once per zone
 * and reused until the profile changes; the zone open in the editor is drawn from the editor's current values.
 */
public final class ZoneOutlines {
	/** Line width in pixels. */
	private static final float WIDTH = 2.0F;
	/** Label size, about three times vanilla's default (0.32), so names stay readable up to 32 blocks away. */
	private static final float LABEL_SCALE = 1.0F;

	private static List<Zone> cachedFor;
	private static final Map<Zone, Outline> CACHE = new IdentityHashMap<>();
	private static boolean failed;
	private static int linesLastFrame;
	private static int labelsLastFrame;

	private ZoneOutlines() {
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(context -> draw(Minecraft.getInstance()));
	}

	/** Line segments drawn in the last frame (for tests). */
	public static int linesLastFrame() {
		return linesLastFrame;
	}

	/** Name labels drawn in the last frame (for tests). */
	public static int labelsLastFrame() {
		return labelsLastFrame;
	}

	static void draw(Minecraft minecraft) {
		linesLastFrame = 0;
		labelsLastFrame = 0;
		if (failed || !ProfileManager.isLoaded() || minecraft.level == null) {
			return;
		}
		long started = System.nanoTime();
		try {
			Profile profile = ProfileManager.profile();
			OutlineMode mode = profile.settings().outlines();
			// The zone being edited, or the box being marked in the world, is always drawn (with its size).
			Zone marking = ZoneMarking.preview(minecraft);
			Zone editing = marking != null ? marking : ZoneEditorScreen.editingZone(minecraft);
			if (marking == null && (mode == OutlineMode.NEVER || (profile.zones().isEmpty() && editing == null))) {
				return;
			}
			if (profile.zones() != cachedFor) {
				CACHE.clear();
				cachedFor = profile.zones();
			}
			Vec3 camera = minecraft.gameRenderer.mainCamera().position();
			String key = ProfileManager.key();
			String dimension = WorldSession.dimension(minecraft);
			boolean onTop = AppSettingsManager.get().outlinesThroughBlocks();
			ZoneEdge edge = profile.settings().defaultEdge();
			String editingId = editing == null ? null : editing.id();
			for (Zone zone : profile.zones()) {
				if (zone.id().equals(editingId)) {
					continue; // drawn below from the editor's values
				}
				if (mode != OutlineMode.NEVER && OutlineVisibility.shouldDraw(zone, mode, editingId, key, dimension, camera.x, camera.y, camera.z)) {
					Outline outline = CACHE.computeIfAbsent(zone, z -> OutlineGeometry.of(z, edge));
					drawZone(zone, outline, colorOf(profile, zone), onTop, camera);
				}
			}
			if (editing != null && OutlineVisibility.shouldDraw(editing, OutlineMode.ALWAYS, editingId, key, dimension, camera.x, camera.y, camera.z)) {
				int color = marking != null ? ZoneMarking.color(minecraft) : ZoneEditorScreen.editingColor(minecraft);
				drawZone(editing, OutlineGeometry.of(editing, edge), color, onTop, camera);
			}
		} catch (RuntimeException | LinkageError e) {
			// Never break world rendering: stop drawing outlines for the rest of the session.
			failed = true;
			SoundSculptorClient.LOGGER.error("Sound Sculptor could not draw zone outlines; they are off until restart", e);
		} finally {
			Diagnostics.OUTLINES.add(System.nanoTime() - started, Util.getMillis());
		}
	}

	private static int colorOf(Profile profile, Zone zone) {
		Integer rgb = profile.colorOf(zone.id());
		return rgb != null ? rgb : ZoneColors.next(List.of());
	}

	private static void drawZone(Zone zone, Outline outline, int rgb, boolean onTop, Vec3 camera) {
		int argb = 0xFF000000 | rgb;
		drawOutline(outline, argb, onTop);
		if (outline.inner() != null) {
			// Fade zones (M13): the fainter line where the fade reaches full strength.
			drawOutline(outline.inner(), 0x80000000 | rgb, onTop);
		}
		if (OutlineVisibility.showLabel(zone, camera.x, camera.y, camera.z)) {
			String name = zone.name() == null || zone.name().isBlank() ? zone.id() : zone.name();
			Vec3 at = new Vec3(zone.anchor().x(), zone.anchor().y(), zone.anchor().z());
			GizmoProperties label = Gizmos.billboardText(name, at, TextGizmo.Style.whiteAndCentered().withScale(LABEL_SCALE));
			if (onTop) {
				label.setAlwaysOnTop();
			}
			labelsLastFrame++;
		}
	}

	/** "10 × 4 × 12": width (x) × height (y) × depth (z) in blocks. */
	static String size(Shape.Box box) {
		return Component.translatable("soundsculptor.outline.size", plain(box.width()), plain(box.height()), plain(box.depth())).getString();
	}

	private static String plain(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
	}

	private static void drawOutline(Outline outline, int argb, boolean onTop) {
		for (int i = 0; i < outline.segmentCount(); i++) {
			GizmoProperties line = Gizmos.line(new Vec3(outline.x1(i), outline.y1(i), outline.z1(i)),
					new Vec3(outline.x2(i), outline.y2(i), outline.z2(i)), argb, WIDTH);
			if (onTop) {
				line.setAlwaysOnTop();
			}
		}
		linesLastFrame += outline.segmentCount();
	}
}
