package audiozone.core.editor;

import java.util.EnumMap;
import java.util.List;

import audiozone.core.outline.ColorError;
import audiozone.core.outline.ZoneColors;
import audiozone.core.rules.Rule;
import audiozone.core.zones.AnchorType;
import audiozone.core.zones.BoxEdit;
import audiozone.core.zones.Point;
import audiozone.core.zones.Shape;
import audiozone.core.zones.Zone;
import audiozone.core.zones.ZoneEdge;
import audiozone.core.zones.ZoneMode;

/**
 * The zone editor's values (PRD 6.1): name, color, anchor type and coordinates, shape and sizes, mode, edge and fade
 * distance. The zone's own rule list is edited in its own rule editors and kept here as it is.
 *
 * <p>Only the fields in use are checked: coordinates for a fixed anchor, the sizes of the current shape, and the
 * fade distance with a fade edge. A personal bubble keeps its stored anchor, world and dimension, which it ignores.
 * The color is kept beside the zone (it belongs to the profile model): read it with {@link #color()}.
 */
public final class ZoneDraft extends Draft<ZoneDraft.Field, Zone> {
	/** The zone editor's fields, for mapping them to widgets. */
	public enum Field implements DraftField {
		NAME(true),
		ENABLED(false),
		/** Hex, e.g. {@code #E69F00}. */
		COLOR(true),
		ANCHOR_TYPE(false),
		X(true),
		Y(true),
		Z(true),
		/** Set with the anchor by {@link #setPosition}. */
		WORLD_KEY(false),
		DIMENSION(false),
		SHAPE(false),
		/** Sphere and cylinder. */
		RADIUS(true),
		/** Box. */
		WIDTH(true),
		/** Box and cylinder. */
		HEIGHT(true),
		/** Box. */
		DEPTH(true),
		MODE(false),
		EDGE(false),
		/** Blocks, with a fade edge. */
		FADE(true);

		private final boolean text;

		Field(boolean text) {
			this.text = text;
		}

		@Override
		public boolean isText() {
			return text;
		}
	}

	/** The zone's shape type, chosen in the editor. */
	public enum ShapeKind {
		SPHERE,
		BOX,
		CYLINDER
	}

	/** Size of a new zone, and what a size's reset button puts back: a 10-block box, sphere or cylinder. */
	public static final double DEFAULT_SIZE = 10;

	private final String id;
	private final List<Rule> rules;
	private final Point storedAnchor;
	/** The shape when the editor opened: a sphere stays on offer for a zone that already was one (FR-8.3). */
	private final ShapeKind openedShape;

	private ZoneDraft(String id, List<Rule> rules, Point storedAnchor, EnumMap<Field, String> values,
			EnumMap<Field, String> defaults) {
		super(Field.class, values, defaults);
		this.id = id;
		this.rules = List.copyOf(rules);
		this.storedAnchor = storedAnchor;
		this.openedShape = ShapeKind.valueOf(values.get(Field.SHAPE));
	}

	/**
	 * A new fixed Inside zone (FR-8.1a, FR-8.3): a {@value #DEFAULT_SIZE}-block box around {@code position} (normally
	 * "my position"), with the profile's default edge and no rules yet. The fields show the position as given; the
	 * saved box is snapped to the block grid like every box.
	 *
	 * @param rgb the next palette color ({@link ZoneColors#next}), also the color's reset value
	 */
	public static ZoneDraft blank(String id, String name, String worldKey, String dimension, Point position, int rgb) {
		Shape.Box box = new Shape.Box(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE);
		Zone zone = new Zone(id, name, true, worldKey, dimension, position, box, List.of());
		return of(zone, rgb, rgb);
	}

	/**
	 * Editing an existing zone.
	 *
	 * @param rgb        the zone's color
	 * @param defaultRgb what the color's reset button puts back
	 */
	public static ZoneDraft of(Zone zone, int rgb, int defaultRgb) {
		EnumMap<Field, String> values = new EnumMap<>(Field.class);
		values.put(Field.NAME, zone.name() == null ? "" : zone.name());
		values.put(Field.ENABLED, Boolean.toString(zone.enabled()));
		values.put(Field.COLOR, ZoneColors.format(rgb));
		values.put(Field.ANCHOR_TYPE, zone.anchorType().name());
		values.put(Field.X, Numbers.format(zone.anchor().x()));
		values.put(Field.Y, Numbers.format(zone.anchor().y()));
		values.put(Field.Z, Numbers.format(zone.anchor().z()));
		values.put(Field.WORLD_KEY, zone.worldKey());
		values.put(Field.DIMENSION, zone.dimension());
		String size = Numbers.format(DEFAULT_SIZE);
		values.put(Field.RADIUS, size);
		values.put(Field.WIDTH, size);
		values.put(Field.HEIGHT, size);
		values.put(Field.DEPTH, size);
		switch (zone.shape()) {
			case Shape.Sphere s -> {
				values.put(Field.SHAPE, ShapeKind.SPHERE.name());
				values.put(Field.RADIUS, Numbers.format(s.radius()));
			}
			case Shape.Box b -> {
				values.put(Field.SHAPE, ShapeKind.BOX.name());
				values.put(Field.WIDTH, Numbers.format(b.width()));
				values.put(Field.HEIGHT, Numbers.format(b.height()));
				values.put(Field.DEPTH, Numbers.format(b.depth()));
			}
			case Shape.Cylinder c -> {
				values.put(Field.SHAPE, ShapeKind.CYLINDER.name());
				values.put(Field.RADIUS, Numbers.format(c.radius()));
				values.put(Field.HEIGHT, Numbers.format(c.height()));
			}
		}
		values.put(Field.MODE, zone.mode().name());
		values.put(Field.EDGE, zone.edge().mode().name());
		values.put(Field.FADE, Numbers.format(zone.edge().fadeDistance()));

		// Name, coordinates, world and dimension have no app-wide default: reset puts back the value at opening.
		EnumMap<Field, String> defaults = new EnumMap<>(values);
		defaults.put(Field.ENABLED, "true");
		defaults.put(Field.COLOR, ZoneColors.format(defaultRgb));
		defaults.put(Field.ANCHOR_TYPE, AnchorType.FIXED.name());
		defaults.put(Field.SHAPE, ShapeKind.BOX.name()); // see defaultText: a bubble's is a sphere
		for (Field f : List.of(Field.RADIUS, Field.WIDTH, Field.HEIGHT, Field.DEPTH)) {
			defaults.put(f, size);
		}
		defaults.put(Field.MODE, ZoneMode.INSIDE.name());
		defaults.put(Field.EDGE, ZoneEdge.Mode.DEFAULT.name());
		defaults.put(Field.FADE, Numbers.format(ZoneEdge.DEFAULT_FADE));
		return new ZoneDraft(zone.id(), zone.rules(), zone.anchor(), values, defaults);
	}

	public boolean enabled() {
		return Boolean.parseBoolean(text(Field.ENABLED));
	}

	public void setEnabled(boolean value) {
		put(Field.ENABLED, Boolean.toString(value));
	}

	public AnchorType anchorType() {
		return AnchorType.valueOf(text(Field.ANCHOR_TYPE));
	}

	/**
	 * Fixed zone or personal bubble. Bubbles are spheres (FR-8.3), so making a bubble turns the shape into a sphere,
	 * and making a fixed zone of a sphere that was not one when the editor opened turns it into a box.
	 */
	public void setAnchorType(AnchorType value) {
		put(Field.ANCHOR_TYPE, value.name());
		if (!shapeChoices().contains(shape())) {
			setShape(value == AnchorType.BUBBLE ? ShapeKind.SPHERE : ShapeKind.BOX);
		}
	}

	/**
	 * The shapes the editor offers (FR-8.3): a sphere for a personal bubble; a box or a cylinder for a fixed zone, and
	 * a sphere too if the zone was one when the editor opened (existing sphere zones keep working and can become boxes).
	 */
	public List<ShapeKind> shapeChoices() {
		if (anchorType() == AnchorType.BUBBLE) {
			return List.of(ShapeKind.SPHERE);
		}
		return openedShape == ShapeKind.SPHERE
				? List.of(ShapeKind.SPHERE, ShapeKind.BOX, ShapeKind.CYLINDER)
				: List.of(ShapeKind.BOX, ShapeKind.CYLINDER);
	}

	/** A bubble's shape resets to a sphere, a fixed zone's to a box (FR-8.3). */
	@Override
	public String defaultText(Field field) {
		if (field == Field.SHAPE) {
			return (anchorType() == AnchorType.BUBBLE ? ShapeKind.SPHERE : ShapeKind.BOX).name();
		}
		return super.defaultText(field);
	}

	@Override
	public void resetToDefault(Field field) {
		if (field == Field.ANCHOR_TYPE) {
			setAnchorType(AnchorType.valueOf(super.defaultText(field)));
		} else {
			super.resetToDefault(field);
		}
	}

	/** FR-8.1 "Set to my position" or "Set to the block I'm looking at": the anchor and where it is. */
	public void setPosition(Point position, String worldKey, String dimension) {
		put(Field.X, Numbers.format(position.x()));
		put(Field.Y, Numbers.format(position.y()));
		put(Field.Z, Numbers.format(position.z()));
		put(Field.WORLD_KEY, worldKey);
		put(Field.DIMENSION, dimension);
	}

	/** A box zone at {@code center} with {@code box}'s size, e.g. from marking corners or moving a side. */
	public void setBox(Point center, Shape.Box box, String worldKey, String dimension) {
		setShape(ShapeKind.BOX);
		setPosition(center, worldKey, dimension);
		put(Field.WIDTH, Numbers.format(box.width()));
		put(Field.HEIGHT, Numbers.format(box.height()));
		put(Field.DEPTH, Numbers.format(box.depth()));
	}

	public ShapeKind shape() {
		return ShapeKind.valueOf(text(Field.SHAPE));
	}

	public void setShape(ShapeKind value) {
		put(Field.SHAPE, value.name());
	}

	public ZoneMode mode() {
		return ZoneMode.valueOf(text(Field.MODE));
	}

	public void setMode(ZoneMode value) {
		put(Field.MODE, value.name());
	}

	public ZoneEdge.Mode edge() {
		return ZoneEdge.Mode.valueOf(text(Field.EDGE));
	}

	public void setEdge(ZoneEdge.Mode value) {
		put(Field.EDGE, value.name());
	}

	/** The color as {@code 0xRRGGBB}. Valid only while the color field has no error. */
	public int color() {
		return ZoneColors.parseHex(text(Field.COLOR)).rgb();
	}

	@Override
	protected FieldError validate(Field field, String text) {
		return switch (field) {
			case NAME -> text.isBlank() ? FieldError.of(FieldError.REQUIRED) : null;
			case COLOR -> {
				ColorError error = ZoneColors.parseHex(text).error();
				yield error == null ? null : FieldError.of(error.translationKey());
			}
			case X, Y, Z -> Numbers.checkNumber(text);
			case RADIUS -> shape() == ShapeKind.SPHERE
					? Numbers.checkDecimal(text, Shape.Sphere.MIN_RADIUS, Shape.Sphere.MAX_RADIUS)
					: Numbers.checkDecimal(text, Shape.Cylinder.MIN_RADIUS, Shape.Cylinder.MAX_RADIUS);
			// Boxes line up with the block grid: whole-block sizes (M8 review).
			case HEIGHT -> shape() == ShapeKind.BOX
					? Numbers.checkWhole(text, (int) Shape.Box.MIN_SIDE, (int) Shape.Box.MAX_SIDE)
					: Numbers.checkDecimal(text, Shape.Cylinder.MIN_HEIGHT, Shape.Cylinder.MAX_HEIGHT);
			case WIDTH, DEPTH -> Numbers.checkWhole(text, (int) Shape.Box.MIN_SIDE, (int) Shape.Box.MAX_SIDE);
			case FADE -> Numbers.checkDecimal(text, ZoneEdge.MIN_FADE, ZoneEdge.MAX_FADE);
			default -> null;
		};
	}

	@Override
	protected boolean inUse(Field field) {
		return switch (field) {
			case X, Y, Z, WORLD_KEY, DIMENSION -> anchorType() == AnchorType.FIXED;
			case RADIUS -> shape() != ShapeKind.BOX;
			case WIDTH, DEPTH -> shape() == ShapeKind.BOX;
			case HEIGHT -> shape() != ShapeKind.SPHERE;
			case FADE -> edge() == ZoneEdge.Mode.FADE;
			default -> true;
		};
	}

	@Override
	protected boolean same(Field field, String a, String b) {
		return switch (field) {
			case NAME -> a.trim().equals(b.trim());
			case COLOR -> {
				ZoneColors.Parsed pa = ZoneColors.parseHex(a);
				ZoneColors.Parsed pb = ZoneColors.parseHex(b);
				yield pa.error() == null && pb.error() == null ? pa.rgb() == pb.rgb() : a.trim().equals(b.trim());
			}
			case X, Y, Z, RADIUS, WIDTH, HEIGHT, DEPTH, FADE -> Numbers.same(a, b);
			default -> a.equals(b);
		};
	}

	@Override
	protected Zone create() {
		Shape shape = switch (shape()) {
			case SPHERE -> new Shape.Sphere(number(Field.RADIUS));
			case BOX -> new Shape.Box(number(Field.WIDTH), number(Field.HEIGHT), number(Field.DEPTH));
			case CYLINDER -> new Shape.Cylinder(number(Field.RADIUS), number(Field.HEIGHT));
		};
		ZoneEdge edge = switch (edge()) {
			case DEFAULT -> ZoneEdge.DEFAULT;
			case HARD -> ZoneEdge.hard();
			case FADE -> ZoneEdge.fade(number(Field.FADE));
		};
		AnchorType anchorType = anchorType();
		Point anchor = anchorType == AnchorType.FIXED
				? new Point(number(Field.X), number(Field.Y), number(Field.Z))
				: storedAnchor;
		if (shape instanceof Shape.Box box && anchorType == AnchorType.FIXED) {
			// Every edge on a block boundary, however the center was set (typed, My position, Looking at).
			anchor = BoxEdit.snap(anchor, box).center();
		}
		return new Zone(id, text(Field.NAME).trim(), enabled(), text(Field.WORLD_KEY), text(Field.DIMENSION), anchor, shape,
				rules, mode(), edge, anchorType);
	}

	private double number(Field field) {
		return Numbers.decimal(text(field));
	}
}
