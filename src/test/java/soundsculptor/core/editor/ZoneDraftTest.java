package soundsculptor.core.editor;

import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.editor.ZoneDraft.Field;
import soundsculptor.core.editor.ZoneDraft.ShapeKind;
import soundsculptor.core.outline.ColorError;
import soundsculptor.core.outline.ZoneColor;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.core.zones.ZoneEdge;
import soundsculptor.core.zones.ZoneMode;

import org.junit.jupiter.api.Test;

/** PRD FR-12.4, FR-12.6, FR-12.7 and section 6.1 for the zone editor (FR-8.1 to FR-8.5, FR-10.4 limits). */
class ZoneDraftTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int ORANGE = ZoneColor.values()[0].rgb();
	private static final int SKY = ZoneColor.values()[1].rgb();
	private static final List<SetEntry> RULES = List.of(SetEntry.of("zr1", "*villager*").withVolume(50));

	@Test
	void aNewZoneIsATenBlockBoxAtThePlayerOnTheBlockGrid() {
		// FR-8.1a, FR-8.3: new zones are boxes, and every box edge is on a block boundary.
		ZoneDraft d = ZoneDraft.blank("z1", "Trading hall", WORLD, OVERWORLD, new Point(120.5, 64, -33.5), ORANGE);
		assertTrue(d.canSave());
		assertFalse(d.isDirty());
		assertEquals(ShapeKind.BOX, d.shape());
		assertEquals("120.5", d.text(Field.X), "the field shows the player's position");
		Zone z = d.build();
		assertEquals(new Shape.Box(10, 10, 10), z.shape());
		assertEquals(new Point(121, 64, -33), z.anchor());
		assertEquals(ZoneMode.INSIDE, z.mode());
		assertEquals(ZoneEdge.DEFAULT, z.edge());
		assertEquals(WORLD, z.worldKey());
		assertEquals(PlaceSets.EMPTY, z.sets());
		assertEquals(ORANGE, d.color());
	}

	@Test
	void aCylindersShapeResetsToABox() {
		// FR-8.3: boxes are the MVP shape; a (v1) cylinder keeps its shape and can become a box.
		ZoneDraft d = ZoneDraft.of(new Zone("z1", "Pen", true, WORLD, OVERWORLD, new Point(0, 64, 0), new Shape.Cylinder(12, 24),
				quick(RULES)), ORANGE, ORANGE);
		assertEquals(ShapeKind.CYLINDER, d.shape());
		assertFalse(d.isDefault(Field.SHAPE));
		d.resetToDefault(Field.SHAPE);
		assertEquals(ShapeKind.BOX, d.shape());
		assertTrue(d.isDefault(Field.SHAPE));
	}

	@Test
	void existingZonesRoundTrip() {
		Zone pen = new Zone("z1", "Pen", true, WORLD, OVERWORLD, new Point(1.25, 64, -3), new Shape.Cylinder(12.5, 9.5),
				quick(RULES));
		Zone outside = new Zone("z2", "Farm", false, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(20, 8, 30), quick(RULES))
				.withMode(ZoneMode.OUTSIDE).withEdge(ZoneEdge.fade(8));
		Zone cylinder = new Zone("z3", "Tower", true, WORLD, "minecraft:the_nether", new Point(5, 70, 5),
				new Shape.Cylinder(6, 40), PlaceSets.EMPTY).withEdge(ZoneEdge.hard());
		for (Zone z : List.of(pen, outside, cylinder)) {
			ZoneDraft d = ZoneDraft.of(z, SKY, ORANGE);
			assertTrue(d.canSave(), z.id());
			assertFalse(d.isDirty(), z.id());
			assertEquals(z, d.build(), z.id());
			assertEquals(SKY, d.color());
		}
	}

	@Test
	void sizesUseTheFr83Limits() {
		ZoneDraft d = draft();
		d.set(Field.RADIUS, "900");
		// The PRD's own example: "radius: 900 is above the maximum 256".
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("1", "256")), d.error(Field.RADIUS));
		d.setShape(ShapeKind.CYLINDER);
		d.set(Field.RADIUS, "6");
		d.set(Field.HEIGHT, "600");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("1", "512")), d.error(Field.HEIGHT));
		d.setShape(ShapeKind.BOX);
		d.set(Field.HEIGHT, "8");
		d.set(Field.WIDTH, "0");
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("1", "512")), d.error(Field.WIDTH));
		d.set(Field.WIDTH, "big");
		assertEquals(FieldError.NOT_A_NUMBER, d.error(Field.WIDTH).translationKey());
		// Boxes are whole blocks (M8 review).
		d.set(Field.WIDTH, "2.5");
		assertEquals(FieldError.NOT_WHOLE_NUMBER, d.error(Field.WIDTH).translationKey());
		d.set(Field.WIDTH, "3");
		assertEquals(new Shape.Box(3, 8, 10), d.build().shape());
	}

	@Test
	void onlyTheCurrentShapesSizesAreChecked() {
		ZoneDraft d = draft();
		d.setShape(ShapeKind.BOX);
		d.set(Field.WIDTH, "9999");
		d.setShape(ShapeKind.CYLINDER);
		assertNull(d.error(Field.WIDTH));
		assertTrue(d.canSave());
		assertEquals(new Shape.Cylinder(10, 20), d.build().shape());
	}

	@Test
	void coordinatesAreAlwaysChecked() {
		// Every zone has fixed coordinates (FR-8.1).
		ZoneDraft d = draft();
		d.set(Field.X, "not a number");
		assertFalse(d.canSave());
		assertEquals(FieldError.NOT_A_NUMBER, d.error(Field.X).translationKey());
		d.set(Field.X, "");
		assertEquals(FieldError.REQUIRED, d.error(Field.X).translationKey());
	}

	@Test
	void fadeDistanceIsCheckedOnlyForAFadeEdge() {
		ZoneDraft d = draft();
		d.set(Field.FADE, "40");
		assertNull(d.error(Field.FADE));
		d.setEdge(ZoneEdge.Mode.FADE);
		assertEquals(new FieldError(FieldError.OUT_OF_RANGE, List.of("0.5", "32")), d.error(Field.FADE));
		d.set(Field.FADE, "8");
		d.setMode(ZoneMode.OUTSIDE);
		Zone z = d.build();
		assertEquals(ZoneEdge.fade(8), z.edge());
		assertEquals(ZoneMode.OUTSIDE, z.mode());
		d.setEdge(ZoneEdge.Mode.HARD);
		assertEquals(ZoneEdge.hard(), d.build().edge());
	}

	@Test
	void colorUsesTheHexFieldRules() {
		// FR-10 AC: typing #XYZ shows an error; #56B4E9 is accepted.
		ZoneDraft d = draft();
		d.set(Field.COLOR, "#XYZ");
		assertEquals(ColorError.INVALID_HEX.translationKey(), d.shownError(Field.COLOR).translationKey());
		assertFalse(d.canSave());
		d.set(Field.COLOR, "#56b4e9");
		assertEquals(0x56B4E9, d.color());
		d.resetToDefault(Field.COLOR);
		assertEquals("#E69F00", d.text(Field.COLOR));
	}

	@Test
	void nameIsRequired() {
		ZoneDraft d = draft();
		d.set(Field.NAME, "   ");
		assertEquals(FieldError.REQUIRED, d.shownError(Field.NAME).translationKey());
		d.set(Field.NAME, "  Farm ");
		assertEquals("Farm", d.build().name());
	}

	@Test
	void dirtyIgnoresHowANumberOrColorIsWritten() {
		ZoneDraft d = ZoneDraft.of(new Zone("z1", "Pen", true, WORLD, OVERWORLD, new Point(120.5, 64, 0),
				new Shape.Cylinder(10, 20), quick(RULES)), ORANGE, ORANGE);
		d.set(Field.X, "120.50");
		d.set(Field.RADIUS, "10.0");
		d.set(Field.COLOR, "e69f00");
		assertFalse(d.isDirty());
		d.set(Field.RADIUS, "11");
		assertTrue(d.isDirty());
	}

	@Test
	void setToMyPositionMovesTheAnchorAndDimension() {
		// FR-8.1 "Set to my position", which can be in another dimension.
		ZoneDraft d = draft();
		d.setPosition(new Point(-8, 40.25, 3), WORLD, "minecraft:the_nether");
		assertEquals("40.25", d.text(Field.Y));
		assertTrue(d.isDirty());
		Zone z = d.build();
		assertEquals(new Point(-8, 40.25, 3), z.anchor());
		assertEquals("minecraft:the_nether", z.dimension());
	}

	@Test
	void resetToDefaultPerValue() {
		ZoneDraft d = ZoneDraft.of(new Zone("z1", "Pen", false, WORLD, OVERWORLD, new Point(1, 2, 3),
				new Shape.Cylinder(30, 60), quick(RULES)).withMode(ZoneMode.OUTSIDE).withEdge(ZoneEdge.fade(2)), SKY, ORANGE);
		for (Field f : List.of(Field.ENABLED, Field.COLOR, Field.SHAPE, Field.RADIUS, Field.HEIGHT, Field.MODE, Field.EDGE,
				Field.FADE)) {
			assertFalse(d.isDefault(f), f.name());
			d.resetToDefault(f);
			assertTrue(d.isDefault(f), f.name());
		}
		Zone z = d.build();
		assertEquals(new Shape.Box(10, 10, 10), z.shape());
		assertEquals(ZoneMode.INSIDE, z.mode());
		assertEquals(ZoneEdge.DEFAULT, z.edge());
		assertTrue(z.enabled());
		assertEquals(quick(RULES), z.sets(), "the zone's own sets are kept");
	}

	@Test
	void choicesAreSetThroughTheirOwnMethods() {
		ZoneDraft d = draft();
		assertThrows(IllegalArgumentException.class, () -> d.set(Field.SHAPE, "TORUS"));
		assertThrows(IllegalArgumentException.class, () -> d.set(Field.DIMENSION, "minecraft:the_end"));
	}

	@Test
	void saveShowsEveryError() {
		ZoneDraft d = draft();
		d.setShape(ShapeKind.BOX);
		d.setEdge(ZoneEdge.Mode.FADE);
		d.set(Field.FADE, "0");
		d.set(Field.WIDTH, "1000");
		ZoneDraft fresh = ZoneDraft.blank("z9", "", WORLD, OVERWORLD, new Point(0, 0, 0), ORANGE);
		assertNull(fresh.shownError(Field.NAME));
		assertFalse(fresh.attemptSave());
		assertEquals(FieldError.REQUIRED, fresh.shownError(Field.NAME).translationKey());
		assertThrows(IllegalStateException.class, d::build);
	}

	/** An unsaved-looking cylinder zone, so the field tests are not affected by box snapping. */
	private static ZoneDraft draft() {
		return ZoneDraft.of(new Zone("z1", "Trading hall", true, WORLD, OVERWORLD, new Point(120.5, 64, -33.5),
				new Shape.Cylinder(10, 20), PlaceSets.EMPTY), ORANGE, ORANGE);
	}
}
