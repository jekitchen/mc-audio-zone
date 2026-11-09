package audiozone.mc;

import audiozone.core.editor.FieldError;
import audiozone.core.editor.ZoneDraft;
import audiozone.core.editor.ZoneDraft.Field;
import audiozone.core.editor.ZoneDraft.ShapeKind;
import audiozone.core.outline.ZoneColors;
import audiozone.core.profile.Profile;
import audiozone.core.zones.Point;
import audiozone.core.zones.Zone;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzColorPicker;
import audiozone.mc.ui.AzDropdown;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.AzToggle;
import audiozone.mc.ui.Tooltips;
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
 * I'm looking at" (FR-8.1), shape (sphere or box) and size (FR-8.3). The zone's own rules are edited on their own
 * screen ("Zone rules", FR-8.6). Save and Cancel with the unsaved-changes dialog (FR-12.4), errors under the fields that
 * disable Save (FR-12.7). Outside mode, fade edges, cylinders and personal bubbles arrive in M13; a zone's existing
 * settings for them are kept.
 */
public final class ZoneEditorScreen extends AzScreen {
	private static final int NAME_WIDTH = 200;
	private static final int ENABLED_WIDTH = 100;
	private static final int COORD_WIDTH = 60;
	private static final int SIZE_WIDTH = 50;
	private static final int SHAPE_WIDTH = 130;
	private static final int POSITION_WIDTH = 90;

	private final ZoneDraft draft;
	private final String zoneId;
	private final boolean isNew;

	public AzTextField nameField;
	public AzToggle enabledToggle;
	public AzColorPicker colorPicker;
	public AzTextField xField;
	public AzTextField yField;
	public AzTextField zField;
	public AzButton myPositionButton;
	public AzButton lookingAtButton;
	public AzDropdown<ShapeKind> shapeDropdown;
	public AzTextField radiusField;
	public AzTextField widthField;
	public AzTextField heightField;
	public AzTextField depthField;
	public AzButton saveButton;
	public AzButton rulesButton;

	private ZoneEditorScreen(Screen parent, ZoneDraft draft, String zoneId, boolean isNew) {
		super(Component.translatable(isNew ? "audiozone.zone_editor.title.new" : "audiozone.zone_editor.title.edit"), parent);
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
		String name = Component.translatable("audiozone.zone_editor.default_name", profile.zones().size() + 1).getString();
		ZoneDraft draft = ZoneDraft.blank(id, name, ProfileManager.key(), WorldSession.dimension(minecraft), playerPosition(minecraft),
				ZoneColors.next(used));
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
		return Component.translatable("audiozone.header.profile", ProfileManager.profile().name());
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		rulesButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.zone_editor.rules"), this::openRules),
				Component.translatable("audiozone.zone_editor.rules.tip"));
		footer.add(rulesButton);
		footer.add(AzButton.text(Component.translatable("audiozone.ui.cancel"), this::onClose));
		saveButton = AzButton.text(Component.translatable("audiozone.ui.save"), this::save);
		footer.add(saveButton);
		updateButtons();
	}

	@Override
	protected void buildTab(int index, Body body) {
		nameField = textField(Field.NAME, NAME_WIDTH, "name");
		nameField.setMaxLength(64);
		enabledToggle = Tooltips.describe(new AzToggle(ENABLED_WIDTH, Component.translatable("audiozone.zone_editor.enabled"),
				draft.enabled(), value -> {
					draft.setEnabled(value);
					changed();
				}), Component.translatable("audiozone.zone_editor.enabled.tip"));
		body.row(nameField, enabledToggle);

		colorPicker = new AzColorPicker(font, Component.translatable("audiozone.zone_editor.color"), draft.color(),
				ZoneColors.parseHex(draft.defaultText(Field.COLOR)).rgb(), rgb -> {
					draft.set(Field.COLOR, ZoneColors.format(rgb));
					changed();
				});
		List<AbstractWidget> colorRow = new ArrayList<>(colorPicker.swatches());
		colorRow.add(colorPicker.hexField());
		colorRow.add(colorPicker.resetButton());
		body.row(colorRow.toArray(new AbstractWidget[0]));

		body.label(() -> Component.translatable("audiozone.zone_editor.center", dimensionName(draft.text(Field.DIMENSION))));
		xField = textField(Field.X, COORD_WIDTH, "x");
		yField = textField(Field.Y, COORD_WIDTH, "y");
		zField = textField(Field.Z, COORD_WIDTH, "z");
		myPositionButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.zone_editor.my_position"), POSITION_WIDTH,
				this::setToMyPosition), Component.translatable("audiozone.zone_editor.my_position.tip"));
		lookingAtButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.zone_editor.looking_at"), POSITION_WIDTH,
				this::setToLookedAtBlock), Component.translatable("audiozone.zone_editor.looking_at.tip"));
		body.row(xField, yField, zField, myPositionButton, lookingAtButton);

		// MVP shapes (FR-8.3); a cylinder from a later version stays a cylinder.
		List<ShapeKind> shapes = draft.shape() == ShapeKind.CYLINDER
				? List.of(ShapeKind.values()) : List.of(ShapeKind.SPHERE, ShapeKind.BOX);
		shapeDropdown = Tooltips.describe(new AzDropdown<>(SHAPE_WIDTH, Component.translatable("audiozone.zone_editor.shape"), shapes,
				shape -> Component.translatable("audiozone.zone_editor.shape." + shape.name().toLowerCase(Locale.ROOT)),
				draft.shape(), this::setShape), Component.translatable("audiozone.zone_editor.shape.tip"));
		radiusField = textField(Field.RADIUS, SIZE_WIDTH, "radius");
		widthField = textField(Field.WIDTH, SIZE_WIDTH, "width");
		heightField = textField(Field.HEIGHT, SIZE_WIDTH, "height");
		depthField = textField(Field.DEPTH, SIZE_WIDTH, "depth");
		List<AbstractWidget> shapeRow = new ArrayList<>();
		shapeRow.add(shapeDropdown);
		switch (draft.shape()) {
			case SPHERE -> shapeRow.add(radiusField);
			case BOX -> {
				shapeRow.add(widthField);
				shapeRow.add(heightField);
				shapeRow.add(depthField);
			}
			case CYLINDER -> {
				shapeRow.add(radiusField);
				shapeRow.add(heightField);
			}
		}
		body.row(shapeRow.toArray(new AbstractWidget[0]));
		body.label(() -> Component.translatable("audiozone.zone_editor.size_hint." + draft.shape().name().toLowerCase(Locale.ROOT)));

		// After Save was refused, keep every error visible.
		for (AzTextField field : List.of(nameField, xField, yField, zField, radiusField, widthField, heightField, depthField)) {
			if (field.error() != null && saveAttempted) {
				field.revealError();
			}
		}
	}

	private boolean saveAttempted;

	/** A text field bound to a draft field: its tooltip names the value, and its errors come from the draft. */
	private AzTextField textField(Field field, int width, String key) {
		AzTextField text = Tooltips.describe(new AzTextField(font, width, Component.translatable("audiozone.zone_editor." + key)),
				Component.translatable("audiozone.zone_editor." + key + ".tip"));
		text.placeholder(Component.translatable("audiozone.zone_editor." + key + ".hint"));
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
		String key = "audiozone.dimension." + dimension.replace(':', '.');
		return Component.translatableWithFallback(key, dimension);
	}

	private void changed() {
		updateButtons();
	}

	private void updateButtons() {
		if (saveButton != null) {
			saveButton.active = draft.canSave();
		}
		if (rulesButton != null) {
			rulesButton.active = draft.canSave();
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

	public void setShape(ShapeKind shape) {
		if (shape == draft.shape()) {
			return;
		}
		draft.setShape(shape);
		rebuildWidgets();
		setInitialFocus(shapeDropdown);
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
		for (AzTextField field : List.of(xField, yField, zField)) {
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

	// ---- Save, Cancel, Rules ----

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
			for (AzTextField field : List.of(nameField, xField, yField, zField, radiusField, widthField, heightField, depthField)) {
				field.revealError();
			}
			return false;
		}
		Zone built = draft.build();
		int color = draft.color();
		ProfileManager.update(profile -> {
			// Keep rules edited since this editor opened (on the zone rules screen).
			Zone saved = profile.zone(zoneId);
			Zone zone = saved == null ? built : built.withRules(saved.rules());
			return profile.putZone(zone).withZoneColor(zoneId, color);
		});
		return true;
	}

	/** "Zone rules": saves the zone (it must exist to hold rules), then opens its rule list. */
	public void openRules() {
		if (!saveChanges()) {
			return;
		}
		minecraft.gui.setScreen(new ZoneRulesScreen(parent, zoneId));
	}
}
