package audiozone.gametest;

import audiozone.mc.compat.KeyEventCompat;
import audiozone.mc.compat.NarrationCompat;
import audiozone.mc.ui.AzColorPicker;
import audiozone.mc.ui.AzDropdown;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.UiSettings;
import audiozone.mc.ui.demo.UiKitDemoScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Automated test for the second half of the M2 UI kit (PRD 6.2, 6.3): scrolling list and rows, dropdown, color
 * picker and toast, on the UI kit demo screen.
 */
public final class M2bClientGameTest implements FabricClientGameTest {
	private static final int SETTLE = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new UiKitDemoScreen(null));
		context.waitForScreen(UiKitDemoScreen.class);
		checkList(context);
		checkDropdown(context);
		checkColorPicker(context);
		checkToasts(context);
		context.runOnClient(mc -> UiSettings.reducedMotion = false);
		context.setScreen(() -> null);
	}

	// ---- Scrolling list ----

	private static void checkList(ClientGameTestContext context) {
		selectTab(context, UiKitDemoScreen.TAB_LISTS);
		AzList list = context.computeOnClient(mc -> demo(mc).soundList);
		check(context.computeOnClient(mc -> list.children().size()) == 40, "the sample list should have 40 rows");

		// Only visible rows are drawn.
		context.waitTicks(SETTLE);
		int drawn = context.computeOnClient(mc -> list.rowsDrawnLastFrame());
		check(drawn > 0 && drawn <= list.visibleRowCount() + 1, "only visible rows should be drawn, drew " + drawn);

		// Rest the mouse over row 2: narration must still follow keyboard focus, not the mouse.
		double hoverScale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		context.getInput().setCursorPos((list.getX() + 20) * hoverScale, (list.getY() + AzList.ROW_HEIGHT * 1.5) * hoverScale);
		context.waitTicks(SETTLE);

		// Tab from the Refresh button enters the list on row 1, first button.
		context.runOnClient(mc -> demo(mc).setFocused(findByMessage(demo(mc), "Refresh")));
		press(context, InputConstants.KEY_TAB);
		check(focusedRowIndex(context, list) == 0, "Tab should enter the list on the first row");
		String narration = context.computeOnClient(mc -> NarrationCompat.collect(list::updateNarration));
		check(narration.contains("Villager mumbles #1") && narration.contains("Mute") && narration.contains("row 1 out of 40"),
				"list narration should read the row, the button and the position, was: " + narration);

		// Down moves to the next row; Page Down by a page.
		press(context, InputConstants.KEY_DOWN);
		check(focusedRowIndex(context, list) == 1, "Down should move to row 2, was row " + (focusedRowIndex(context, list) + 1));
		int page = context.computeOnClient(mc -> list.visibleRowCount());
		press(context, InputConstants.KEY_PAGEDOWN);
		check(focusedRowIndex(context, list) == 1 + page, "Page Down should move " + page + " rows, now at row " + (focusedRowIndex(context, list) + 1));
		check(context.computeOnClient(mc -> demo(mc).getFocused()) == list, "after Page Down the screen's focus should still be the list");
		press(context, InputConstants.KEY_PAGEUP);
		check(focusedRowIndex(context, list) == 1, "Page Up should move back to row 2, now at row " + (focusedRowIndex(context, list) + 1) + " (page " + page + ")");

		// Tab leaves the list (one Tab stop); Shift+Tab comes back to the same row.
		press(context, InputConstants.KEY_TAB);
		check(context.computeOnClient(mc -> demo(mc).getFocused()) != list, "Tab should leave the list");
		pressWithModifiers(context, InputConstants.KEY_TAB, InputConstants.MOD_SHIFT);
		check(focusedRowIndex(context, list) == 1, "Shift+Tab should come back to the remembered row");

		// Refresh keeps the focused row and the scroll position.
		press(context, InputConstants.KEY_PAGEDOWN);
		press(context, InputConstants.KEY_PAGEDOWN);
		int rowBefore = focusedRowIndex(context, list);
		double scrollBefore = context.computeOnClient(mc -> list.scrollAmount());
		check(scrollBefore > 0, "the list should have scrolled to keep the focused row visible");
		context.runOnClient(mc -> demo(mc).refreshList());
		context.waitTicks(SETTLE);
		check(focusedRowIndex(context, list) == rowBefore, "refresh should keep the focused row");
		check(context.computeOnClient(mc -> list.scrollAmount()) == scrollBefore, "refresh should keep the scroll position");

		// Mouse wheel: one row per notch.
		context.runOnClient(mc -> list.setScrollAmount(0));
		double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		context.getInput().setCursorPos((list.getX() + list.getWidth() / 2.0) * scale, (list.getY() + list.getHeight() / 2.0) * scale);
		context.getInput().scroll(-1);
		context.waitTicks(SETTLE);
		check(context.computeOnClient(mc -> list.scrollAmount()) == AzList.ROW_HEIGHT, "one wheel notch should scroll exactly one row");
		context.getInput().setCursorPos(0, 0);
		context.takeScreenshot("m2b-list");
	}

	private static int focusedRowIndex(ClientGameTestContext context, AzList list) {
		return context.computeOnClient(mc -> list.getFocused() == null ? -1 : list.indexOf(list.getFocused()));
	}

	// ---- Dropdown ----

	@SuppressWarnings("unchecked")
	private static void checkDropdown(ClientGameTestContext context) {
		selectTab(context, UiKitDemoScreen.TAB_PICKERS);
		AzDropdown<String> sort = context.computeOnClient(mc -> demo(mc).sortDropdown);
		check(context.computeOnClient(mc -> NarrationCompat.collect(sort::updateNarration)).contains("Sort: Newest"),
				"the dropdown should narrate its selected option");

		// Enter opens the list with the current option focused; Down then Enter picks the next one.
		context.runOnClient(mc -> demo(mc).setFocused(sort));
		press(context, InputConstants.KEY_RETURN);
		check(context.computeOnClient(mc -> demo(mc).hasOverlay()), "Enter should open the dropdown");
		check(context.computeOnClient(mc -> ((AzDropdown<String>.Option) demo(mc).getFocused()).option()).equals("Newest"),
				"the open list should focus the current option");
		context.takeScreenshot("m2b-dropdown-open");
		press(context, InputConstants.KEY_DOWN);
		AzDropdown<String>.Option focused = context.computeOnClient(mc -> (AzDropdown<String>.Option) demo(mc).getFocused());
		check(focused.option().equals("Oldest"), "Down should move to the next option, was " + focused.option());
		String optionNarration = context.computeOnClient(mc -> NarrationCompat.collect(focused::updateNarration));
		check(optionNarration.contains("Oldest") && optionNarration.contains("2 of 4"), "options should narrate their position, was: " + optionNarration);

		// Focus is trapped in the open list.
		for (int i = 0; i < 6; i++) {
			press(context, InputConstants.KEY_TAB);
			check(context.computeOnClient(mc -> demo(mc).getFocused() instanceof AzDropdown<?>.Option), "Tab left the open dropdown");
		}
		context.runOnClient(mc -> demo(mc).setFocused(demo(mc).overlayWidgets().get(1)));
		press(context, InputConstants.KEY_RETURN);
		check(!context.computeOnClient(mc -> demo(mc).hasOverlay()), "Enter on an option should close the list");
		check(context.computeOnClient(mc -> demo(mc).sort).equals("Oldest"), "Enter should pick Oldest");
		check(context.computeOnClient(mc -> demo(mc).getFocused()) == sort, "focus should return to the dropdown");

		// Esc cancels without changing the value.
		press(context, InputConstants.KEY_RETURN);
		press(context, InputConstants.KEY_DOWN);
		press(context, InputConstants.KEY_ESCAPE);
		check(!context.computeOnClient(mc -> demo(mc).hasOverlay()), "Esc should close the list");
		check(context.computeOnClient(mc -> demo(mc).sort).equals("Oldest"), "Esc should not change the value");
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof UiKitDemoScreen), "Esc in a dropdown should not close the screen");
	}

	// ---- Color picker ----

	private static void checkColorPicker(ClientGameTestContext context) {
		selectTab(context, UiKitDemoScreen.TAB_PICKERS);
		AzColorPicker picker = context.computeOnClient(mc -> demo(mc).colorPicker);
		AzColorPicker.Swatch skyBlue = context.computeOnClient(mc -> picker.swatches().get(1));
		check(context.computeOnClient(mc -> NarrationCompat.collect(skyBlue::updateNarration)).contains("Sky blue"), "swatches should narrate their name");

		context.runOnClient(mc -> demo(mc).setFocused(skyBlue));
		press(context, InputConstants.KEY_RETURN);
		check(context.computeOnClient(mc -> demo(mc).zoneColor) == 0x56B4E9, "choosing Sky blue should set the color");
		check(context.computeOnClient(mc -> picker.hexField().getValue()).equals("#56B4E9"), "the hex field should follow the swatch");
		check(context.computeOnClient(mc -> NarrationCompat.collect(skyBlue::updateNarration)).contains("selected"), "the chosen swatch should narrate 'selected'");
		context.takeScreenshot("m2b-color-picker");

		// Typing a hex color.
		context.runOnClient(mc -> {
			demo(mc).setFocused(picker.hexField());
			picker.hexField().setValue("");
		});
		context.getInput().typeChars("#123456");
		context.waitTicks(SETTLE);
		check(context.computeOnClient(mc -> demo(mc).zoneColor) == 0x123456, "a typed hex color should apply");
		context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		context.waitTicks(SETTLE);
		check(context.computeOnClient(mc -> picker.hexField().isErrorShown()), "an incomplete hex color should show an error");
		check(context.computeOnClient(mc -> demo(mc).zoneColor) == 0x123456, "an invalid hex color should not change the color");

		// Reset.
		context.runOnClient(mc -> demo(mc).setFocused(picker.resetButton()));
		press(context, InputConstants.KEY_RETURN);
		check(context.computeOnClient(mc -> demo(mc).zoneColor) == 0xE69F00, "Reset should restore the default color");
		check(context.computeOnClient(mc -> picker.hexField().getValue()).equals("#E69F00"), "Reset should update the hex field");
	}

	// ---- Toasts ----

	private static void checkToasts(ClientGameTestContext context) {
		selectTab(context, UiKitDemoScreen.TAB_PICKERS);

		// Slides in normally; appears in place with reduced motion (A-6).
		context.runOnClient(mc -> {
			UiSettings.reducedMotion = false;
			demo(mc).showPlainToast();
		});
		check(context.computeOnClient(mc -> demo(mc).toast().slideOffset()) > 0, "a new toast should start off to the side (slide-in)");
		context.runOnClient(mc -> {
			UiSettings.reducedMotion = true;
			demo(mc).showPlainToast();
		});
		check(context.computeOnClient(mc -> demo(mc).toast().slideOffset()) == 0, "with reduced motion the toast should not slide");

		// Narrated once, then gone after 5 seconds.
		context.waitTicks(10);
		check(context.computeOnClient(mc -> demo(mc).toast().announcements()) == 1, "a toast should be narrated exactly once");
		context.takeScreenshot("m2b-toast");
		context.waitTicks(100); // 5 s at 20 ticks per second, plus margin
		check(context.computeOnClient(mc -> demo(mc).toast()) == null, "the toast should disappear after 5 seconds");

		// Undo with the keyboard.
		context.runOnClient(mc -> demo(mc).showUndoToast());
		check(context.computeOnClient(mc -> demo(mc).toast().undoButton()) != null, "an Undo toast should have an Undo button");
		context.runOnClient(mc -> demo(mc).setFocused(demo(mc).toast().undoButton()));
		context.takeScreenshot("m2b-toast-undo");
		press(context, InputConstants.KEY_RETURN);
		check(context.computeOnClient(mc -> demo(mc).lastAction).equals("Undone"), "Enter on Undo should run the undo action");
		check(context.computeOnClient(mc -> demo(mc).toast()) == null, "Undo should close the toast");
	}

	// ---- Helpers ----

	private static void selectTab(ClientGameTestContext context, int tab) {
		context.runOnClient(mc -> demo(mc).selectTab(tab));
		context.waitTicks(SETTLE);
	}

	private static void press(ClientGameTestContext context, int key) {
		context.getInput().pressKey(key);
		context.waitTicks(SETTLE);
	}

	/** See M2ClientGameTest: Fabric's test input never sends modifier bits. */
	private static void pressWithModifiers(ClientGameTestContext context, int key, int modifiers) {
		context.runOnClient(mc -> mc.gui.screen().keyPressed(KeyEventCompat.of(key, modifiers)));
		context.waitTicks(SETTLE);
	}

	private static UiKitDemoScreen demo(Minecraft mc) {
		Screen screen = mc.gui.screen();
		check(screen instanceof UiKitDemoScreen, "expected the UI kit demo screen, found " + screen);
		return (UiKitDemoScreen) screen;
	}

	private static AbstractWidget findByMessage(Screen screen, String message) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget w && w.getMessage().getString().equals(message)) {
				return w;
			}
		}
		throw new AssertionError("no widget labelled " + message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
