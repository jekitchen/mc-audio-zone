package soundsculptor.mc;

import soundsculptor.core.editor.FieldError;
import soundsculptor.core.editor.ZoneDraft;
import soundsculptor.core.editor.ZoneDraft.Field;
import soundsculptor.core.editor.ZoneDraft.ShapeKind;
import soundsculptor.core.outline.ZoneColors;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.BoxEdit;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptColorPicker;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The zone editor (PRD 6.1 Zones, FR-8 MVP, FR-10.4): name, on/off, color, the center with "My position" and "Block
 * I'm looking at" (FR-8.1), and the box's size (FR-8.3), drawn by marking two corners in the world or moved one side at
 * a time (M8 review). The zone's attached Sound Sets are edited on their own screen ("Sound Sets…", FR-8.6). Save and Cancel with the unsaved-changes dialog (FR-12.4), errors under the fields that
 * disable Save (FR-12.7). Outside mode, fade edges, cylinders and personal bubbles arrive in M13; a zone's existing
 * settings for them are kept.
 */
public final class ZoneEditorScreen extends SculptScreen {
	private static final int NAME_WIDTH = 200;
	private static final int ENABLED_WIDTH = 100;
	private static final int COORD_WIDTH = 60;
	private static final int SIZE_WIDTH = 50;
	private static final int SHAPE_WIDTH = 130;
	private static final int POSITION_WIDTH = 90;
	private static final int MARK_WIDTH = 110;
	private static final int SIDE_WIDTH = 140;
	private static final int STEP_WIDTH = 60;

	private final ZoneDraft draft;
	private final String zoneId;
	private final boolean isNew;

	public SculptTextField nameField;
	public SculptToggle enabledToggle;
	public SculptColorPicker colorPicker;
	public SculptTextField xField;
	public SculptTextField yField;
	public SculptTextField zField;
	public SculptButton myPositionButton;
	public SculptButton lookingAtButton;
	public SculptDropdown<ShapeKind> shapeDropdown;
	public SculptTextField radiusField;
	public SculptTextField widthField;
	public SculptTextField heightField;
	public SculptTextField depthField;
	public SculptButton saveButton;
	public SculptButton setsButton;
	public SculptButton markCornersButton;
	public SculptDropdown<BoxEdit.Side> sideDropdown;
	public SculptButton shrinkButton;
	public SculptButton growButton;

	private ZoneEditorScreen(Screen parent, ZoneDraft draft, String zoneId, boolean isNew) {
		super(Component.translatable(isNew ? "soundsculptor.zone_editor.title.new" : "soundsculptor.zone_editor.title.edit"), parent);
		this.draft = draft;
		this.zoneId = zoneId;
		this.isNew = isNew;
	}

	/** A new 10-block sphere at the player's position, with the next palette color (FR-10.4). */
	public static ZoneEditorScreen create(Screen parent) {
		Profile profile = ProfileManager.profile();
		Minecraft minecraft = Minecraft.getInstance();
		String id = profile.newZoneId();
		List<Integer> used = new ArrayList<>();
		for (Zone zone : profile.zones()) {
			Integer rgb = profile.colorOf(zone.id());
			if (rgb != null) {
				used.add(rgb);
			}
		}
		String name = Component.translatable("soundsculptor.zone_editor.default_name", profile.zones().size() + 1).getString();
		ZoneDraft draft = ZoneDraft.blank(id, name, ProfileManager.key(), WorldSession.dimension(minecraft), playerPosition(minecraft),
				ZoneColors.next(used));
		// New zones are boxes, drawn by marking corners and adjusted side by side (M8 review).
		draft.setShape(ShapeKind.BOX);
		return new ZoneEditorScreen(parent, draft, id, true);
	}

	public static ZoneEditorScreen edit(Screen parent, Zone zone) {
		Profile profile = ProfileManager.profile();
		Integer rgb = profile.colorOf(zone.id());
		int color = rgb != null ? rgb : ZoneColors.next(List.of());
		return new ZoneEditorScreen(parent, ZoneDraft.of(zone, color, color), zone.id(), false);
	}

	/**
	 * The zone open in the zone editor, as currently edited (FR-10.1 "Only while editing"; outlines follow the editor
	 * live). Its saved version while the editor's values are invalid; {@code null} when no zone editor is open.
	 */
	public static Zone editingZone(Minecraft minecraft) {
		if (!(minecraft.gui.screen() instanceof ZoneEditorScreen editor)) {
			return null;
		}
		return editor.draft.canSave() ? editor.draft.build() : ProfileManager.profile().zone(editor.zoneId);
	}

	/** The color chosen in the open zone editor, as {@code 0xRRGGBB}. */
	public static int editingColor(Minecraft minecraft) {
		if (minecraft.gui.screen() instanceof ZoneEditorScreen editor && editor.draft.error(Field.COLOR) == null) {
			return editor.draft.color();
		}
		return ZoneColors.next(List.of());
	}

	public ZoneDraft draft() {
		return draft;
	}

	public String zoneId() {
		return zoneId;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.translatable("soundsculptor.header.profile", ProfileManager.profile().name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		setsButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zone_editor.sets"), this::openSets),
				Component.translatable("soundsculptor.zone_editor.sets.tip"));
		footer.add(setsButton);
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
		saveButton = SculptButton.text(Component.translatable("soundsculptor.ui.save"), this::save);
		footer.add(saveButton);
		updateButtons();
	}

	@Override
	protected void buildTab(int index, Body body) {
		nameField = textField(Field.NAME, NAME_WIDTH, "name");
		nameField.setMaxLength(64);
		enabledToggle = Tooltips.describe(new SculptToggle(ENABLED_WIDTH, Component.translatable("soundsculptor.zone_editor.enabled"),
				draft.enabled(), value -> {
					draft.setEnabled(value);
					changed();
				}), Component.translatable("soundsculptor.zone_editor.enabled.tip"));
		body.row(nameField, enabledToggle);

		colorPicker = new SculptColorPicker(font, Component.translatable("soundsculptor.zone_editor.color"), draft.color(),
				ZoneColors.parseHex(draft.defaultText(Field.COLOR)).rgb(), rgb -> {
					draft.set(Field.COLOR, ZoneColors.format(rgb));
					changed();
				});
		List<AbstractWidget> colorRow = new ArrayList<>(colorPicker.swatches());
		colorRow.add(colorPicker.hexField());
		colorRow.add(colorPicker.resetButton());
		body.row(colorRow.toArray(new AbstractWidget[0]));

		body.label(() -> Component.translatable("soundsculptor.zone_editor.center", dimensionName(draft.text(Field.DIMENSION))));
		xField = textField(Field.X, COORD_WIDTH, "x");
		yField = textField(Field.Y, COORD_WIDTH, "y");
		zField = textField(Field.Z, COORD_WIDTH, "z");
		myPositionButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zone_editor.my_position"), POSITION_WIDTH,
				this::setToMyPosition), Component.translatable("soundsculptor.zone_editor.my_position.tip"));
		lookingAtButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zone_editor.looking_at"), POSITION_WIDTH,
				this::setToLookedAtBlock), Component.translatable("soundsculptor.zone_editor.looking_at.tip"));
		body.row(xField, yField, zField, myPositionButton, lookingAtButton);

		shapeDropdown = null;
		markCornersButton = null;
		sideDropdown = null;
		radiusField = textField(Field.RADIUS, SIZE_WIDTH, "radius");
		widthField = textField(Field.WIDTH, SIZE_WIDTH, "width");
		heightField = textField(Field.HEIGHT, SIZE_WIDTH, "height");
		depthField = textField(Field.DEPTH, SIZE_WIDTH, "depth");
		if (draft.shape() == ShapeKind.BOX) {
			// Size, then the drawing tools: mark two corners in the world, or move one side at a time.
			markCornersButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zone_editor.mark_corners"), MARK_WIDTH,
					this::markCorners), Component.translatable("soundsculptor.zone_editor.mark_corners.tip", soundsculptor.SoundSculptorClient.openKey.getTranslatedKeyMessage()));
			body.label(() -> Component.translatable("soundsculptor.zone_editor.size_label"));
			body.row(widthField, heightField, depthField, markCornersButton);
			sideDropdown = Tooltips.describe(new SculptDropdown<>(SIDE_WIDTH, Component.translatable("soundsculptor.zone_editor.side"),
					List.of(BoxEdit.Side.values()),
					side -> Component.translatable("soundsculptor.zone_editor.side." + side.name().toLowerCase(Locale.ROOT)),
					selectedSide, side -> selectedSide = side), Component.translatable("soundsculptor.zone_editor.side.tip"));
			shrinkButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zone_editor.shrink"), STEP_WIDTH,
					() -> moveSide(-1)), Component.translatable("soundsculptor.zone_editor.shrink.tip"));
			growButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.zone_editor.grow"), STEP_WIDTH,
					() -> moveSide(1)), Component.translatable("soundsculptor.zone_editor.grow.tip"));
			body.row(sideDropdown, shrinkButton, growButton);
		} else {
			// A sphere or cylinder from an older profile or a later version keeps its shape, and can become a box.
			shapeDropdown = Tooltips.describe(new SculptDropdown<>(SHAPE_WIDTH, Component.translatable("soundsculptor.zone_editor.shape"),
					List.of(draft.shape(), ShapeKind.BOX),
					shape -> Component.translatable("soundsculptor.zone_editor.shape." + shape.name().toLowerCase(Locale.ROOT)),
					draft.shape(), this::setShape), Component.translatable("soundsculptor.zone_editor.shape.tip"));
			List<AbstractWidget> shapeRow = new ArrayList<>();
			shapeRow.add(shapeDropdown);
			shapeRow.add(radiusField);
			if (draft.shape() == ShapeKind.CYLINDER) {
				shapeRow.add(heightField);
			}
			body.row(shapeRow.toArray(new AbstractWidget[0]));
		}

		// After Save was refused, keep every error visible.
		for (SculptTextField field : List.of(nameField, xField, yField, zField, radiusField, widthField, heightField, depthField)) {
			if (field.error() != null && saveAttempted) {
				field.revealError();
			}
		}
	}

	private boolean saveAttempted;
	/** The side Grow and Shrink move; remembered while the game runs. */
	private static BoxEdit.Side selectedSide = BoxEdit.Side.NORTH;

	/** A text field bound to a draft field: its tooltip names the value, and its errors come from the draft. */
	private SculptTextField textField(Field field, int width, String key) {
		SculptTextField text = Tooltips.describe(new SculptTextField(font, width, Component.translatable("soundsculptor.zone_editor." + key)),
				Component.translatable("soundsculptor.zone_editor." + key + ".tip"));
		text.placeholder(Component.translatable("soundsculptor.zone_editor." + key + ".hint"));
		text.setMaxLength(16);
		text.setValue(draft.text(field));
		text.moveCursorToStart(false); // a long number shows its start, not its last digits
		text.validator(value -> {
			if (!value.equals(draft.text(field))) {
				draft.set(field, value);
			}
			return message(draft.error(field));
		});
		text.onChange(value -> changed());
		return text;
	}

	private static Component message(FieldError error) {
		return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
	}

	private static Component dimensionName(String dimension) {
		String key = "soundsculptor.dimension." + dimension.replace(':', '.');
		return Component.translatableWithFallback(key, dimension);
	}

	private void changed() {
		updateButtons();
	}

	private void updateButtons() {
		if (saveButton != null) {
			saveButton.active = draft.canSave();
		}
		if (setsButton != null) {
			setsButton.active = draft.canSave();
		}
		if (lookingAtButton != null) {
			lookingAtButton.active = lookedAtBlock(minecraft).isPresent();
		}
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	/** "Mark corners": the editor closes while the player marks two corners in the world (see {@link ZoneMarking}). */
	public void markCorners() {
		ZoneMarking.start(this);
	}

	/**
	 * Grow ({@code direction} 1) or Shrink (-1) the chosen side by one block, or five with Shift. The opposite side stays
	 * where it is.
	 */
	public void moveSide(int direction) {
		BoxEdit.Placed current = currentBox();
		if (current == null) {
			return;
		}
		int blocks = minecraft.hasShiftDown() ? 5 : 1;
		applyBox(BoxEdit.moveSide(current.center(), current.box(), selectedSide, direction * blocks));
	}

	public BoxEdit.Side selectedSide() {
		return selectedSide;
	}

	public void selectSide(BoxEdit.Side side) {
		selectedSide = side;
		if (sideDropdown != null) {
			sideDropdown.setValue(side);
		}
	}

	/** The box as the editor has it now, or null while a center or size field is invalid. */
	private BoxEdit.Placed currentBox() {
		for (Field field : List.of(Field.X, Field.Y, Field.Z, Field.WIDTH, Field.HEIGHT, Field.DEPTH)) {
			if (draft.error(field) != null) {
				return null;
			}
		}
		Zone zone = draft.build();
		return zone.shape() instanceof Shape.Box box ? new BoxEdit.Placed(zone.anchor(), box) : null;
	}

	/** Puts a box's center and size into the editor (from marking corners or moving a side). */
	public void applyBox(BoxEdit.Placed placed) {
		draft.setBox(placed.center(), placed.box(), ProfileManager.key(), WorldSession.dimension(Minecraft.getInstance()));
		if (Minecraft.getInstance().gui.screen() == this) {
			rebuildWidgets();
		}
	}

	/** The zone as currently edited, or its saved version while a value is invalid; null for a new zone not saved yet. */
	Zone draftZone() {
		return draft.canSave() ? draft.build() : ProfileManager.profile().zone(zoneId);
	}

	/** The chosen color, as {@code 0xRRGGBB}. */
	int currentColor() {
		return draft.error(Field.COLOR) == null ? draft.color() : ZoneColors.next(List.of());
	}

	public void setShape(ShapeKind shape) {
		if (shape == draft.shape()) {
			return;
		}
		draft.setShape(shape);
		rebuildWidgets();
		// A box has no shape choice (it is the only shape new zones get), so focus moves to its width.
		setInitialFocus(shapeDropdown != null ? shapeDropdown : widthField);
	}

	/** FR-8.1 "Set to my position": the player's feet, in this world and dimension. */
	public void setToMyPosition() {
		setPosition(playerPosition(minecraft));
	}

	/** FR-8.1 "Set to the block I'm looking at": the center of that block. */
	public void setToLookedAtBlock() {
		lookedAtBlock(minecraft).ifPresent(pos -> setPosition(new Point(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
	}

	private void setPosition(Point point) {
		draft.setPosition(point, ProfileManager.key(), WorldSession.dimension(minecraft));
		xField.setValue(draft.text(Field.X));
		yField.setValue(draft.text(Field.Y));
		zField.setValue(draft.text(Field.Z));
		for (SculptTextField field : List.of(xField, yField, zField)) {
			field.moveCursorToStart(false);
		}
		changed();
	}

	private static Point playerPosition(Minecraft minecraft) {
		if (minecraft.player == null) {
			return new Point(0, 64, 0);
		}
		Vec3 feet = minecraft.player.position();
		return new Point(Math.round(feet.x * 10) / 10.0, Math.round(feet.y * 10) / 10.0, Math.round(feet.z * 10) / 10.0);
	}

	private static Optional<BlockPos> lookedAtBlock(Minecraft minecraft) {
		HitResult hit = minecraft == null ? null : minecraft.hitResult;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return Optional.of(block.getBlockPos());
		}
		return Optional.empty();
	}

	// ---- Save, Cancel, Sound Sets ----

	public void save() {
		if (saveChanges()) {
			closeNow();
		}
	}

	@Override
	protected boolean hasUnsavedChanges() {
		return isNew || draft.isDirty();
	}

	@Override
	protected boolean saveChanges() {
		saveAttempted = true;
		if (!draft.attemptSave()) {
			for (SculptTextField field : List.of(nameField, xField, yField, zField, radiusField, widthField, heightField, depthField)) {
				field.revealError();
			}
			return false;
		}
		Zone built = draft.build();
		int color = draft.color();
		ProfileManager.update(profile -> {
			// Keep sets changed since this editor opened (on the zone's Sound Sets screen, or by a quick action).
			Zone saved = profile.zone(zoneId);
			Zone zone = saved == null ? built : built.withSets(saved.sets());
			return profile.putZone(zone).withZoneColor(zoneId, color);
		});
		return true;
	}

	/** "Sound Sets…": saves the zone (it must exist to have sets attached), then opens its attached sets (FR-8.6). */
	public void openSets() {
		if (!saveChanges()) {
			return;
		}
		minecraft.gui.setScreen(new PlaceSetsScreen(parent, PlaceRef.zone(zoneId)));
	}
}
