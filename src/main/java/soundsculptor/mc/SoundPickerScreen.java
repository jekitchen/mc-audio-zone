package soundsculptor.mc;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import soundsculptor.core.Message;
import soundsculptor.core.browser.PickerModel;
import soundsculptor.core.editor.SetDraft;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.mc.ui.Draw;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * The Sounds picker (PRD FR-1.7), opened by the set editor's Add sounds… (FR-4.3): the Sounds browser
 * ({@link SoundsPanel#picker}) with the same search and Recently played filter, a checkbox on each row and Like this…
 * for the sound's groups (FR-4.8). Sounds and groups the set already has are marked "In this set" and cannot be picked.
 * The footer says how many are picked ("3 picked") and has Cancel and Add.
 *
 * <p>Add puts one entry per pick, with no settings yet, into the set editor's draft, in the order picked, and returns to
 * the editor: the set editor's Save still decides whether anything is written (FR-12.4). Picks that would pass the
 * set's {@value SoundSet#MAX_ENTRIES} entries show the limit and the picker stays. Cancel and Esc return with no
 * change.
 */
public final class SoundPickerScreen extends SculptScreen {
	private final SetDraft draft;
	private final Component subtitle;
	private final PickerModel picker;
	private SoundsPanel panel;

	public SculptButton addButton;
	public SculptButton cancelButton;

	/**
	 * @param parent   the set editor, where Add and Cancel return to
	 * @param draft    the set editor's draft: what it holds now is "In this set", and Add adds to it
	 * @param subtitle the set as the set editor's header names it (its saved name, or "Quick changes in Pen")
	 */
	public SoundPickerScreen(Screen parent, SetDraft draft, Component subtitle) {
		super(Component.translatable("soundsculptor.picker.title"), parent);
		this.draft = draft;
		this.subtitle = subtitle;
		this.picker = new PickerModel(draft.build());
	}

	public PickerModel picker() {
		return picker;
	}

	public SoundsPanel sounds() {
		return panel;
	}

	/** "3 picked", as the footer shows it. */
	public String footerText() {
		int count = picker.pickedCount();
		return Component.translatable(count == 1 ? "soundsculptor.picker.picked.one" : "soundsculptor.picker.picked", count).getString();
	}

	@Override
	protected List<Tab> tabs() {
		// FR-1.6: like the Sounds tab, the picker does not pause a singleplayer game, so Recently played keeps filling.
		return List.of(new Tab(getTitle(), false));
	}

	/** The set as the header names it. */
	public Component subtitle() {
		return subtitle;
	}

	@Override
	protected Component headerSubtitle() {
		return subtitle;
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		cancelButton = SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose);
		addButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.picker.add"), this::add),
				Component.translatable("soundsculptor.picker.add.tip"));
		// The narrator reads the count with Add, since the footer text is not a control.
		addButton.narrationContext(() -> Component.literal(footerText()));
		footer.add(cancelButton);
		footer.add(addButton);
		updateAddButton();
	}

	@Override
	protected void buildTab(int index, Body body) {
		panel = SoundsPanel.picker(this, picker, this::updateAddButton);
		panel.build(body);
	}

	/** Add is off while nothing is picked. */
	private void updateAddButton() {
		if (addButton != null) {
			addButton.active = picker.pickedCount() > 0;
		}
	}

	/**
	 * Adds the picks to the set editor's draft and returns to it; with more picks than the set has room for, shows the
	 * limit and stays.
	 */
	public void add() {
		Message full = picker.canAdd();
		if (full != null) {
			showToast(ProfileManager.component(full), null);
			return;
		}
		SoundSet added = picker.addTo(draft.build());
		for (SetEntry entry : added.entries()) {
			if (draft.entry(entry.id()) == null && !draft.putEntry(entry)) {
				// The draft refused it (full): say why and stay, as when the picks pass the limit.
				showToast(ProfileManager.component(draft.canPut(entry)), null);
				return;
			}
		}
		closeNow();
	}

	@Override
	public void tick() {
		super.tick();
		if (panel != null) {
			panel.tick();
		}
	}

	/** The picked count at the left of the footer, cut short before the buttons. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		if (cancelButton == null) {
			return;
		}
		int y = height - FOOTER_HEIGHT + (FOOTER_HEIGHT - font.lineHeight) / 2 + 1;
		Draw.truncatedText(g, font, footerText(), Theme.PADDING, y, cancelButton.getX() - Theme.PADDING - Theme.GAP, Theme.TEXT);
	}
}
