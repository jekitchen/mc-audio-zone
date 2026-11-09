package audiozone.gametest;

import audiozone.mc.compat.KeyEventCompat;
import audiozone.mc.compat.NarrationCompat;
import audiozone.mc.ui.AzDialog;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.AzTextField;
import audiozone.mc.ui.demo.UiKitDemoScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Automated version of the M2 milestone test (PRD 11.1): on the UI kit demo screen, every component is reachable
 * with Tab in reading order and has narration; keyboard behaviors of slider, toggle, text field and dialogs work;
 * and at GUI scales 1–4 and Auto, at 854×480 and 1920×1080, nothing is off screen or overlapping (PRD A-1, A-3,
 * A-4, 6.1). Screenshots are saved for a visual check.
 */
public final class M2ClientGameTest implements FabricClientGameTest {
	private static final int SETTLE = 2;
	private static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
	private static final int MOD_CONTROL = InputConstants.MOD_CONTROL;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new UiKitDemoScreen(null));
		context.waitForScreen(UiKitDemoScreen.class);

		for (int tab = 0; tab < 3; tab++) {
			selectTab(context, tab);
			checkTabOrderAndNarration(context, tab);
		}
		checkCtrlTab(context);
		checkSliderKeys(context);
		checkToggleAndButtons(context);
		captureTooltips(context);
		checkTextFields(context);
		checkDialogs(context);
		checkLayouts(context);
		checkUnsavedChangesGuard(context);
	}

	// ---- Keyboard reachability, reading order, narration (A-1, A-4) ----

	private static void checkTabOrderAndNarration(ClientGameTestContext context, int tab) {
		List<AbstractWidget> focusable = context.computeOnClient(mc -> focusableWidgets(mc.gui.screen()));
		check(!focusable.isEmpty(), "tab " + tab + " has no focusable widgets");
		checkReadingOrder(focusable, "tab " + tab);

		// Press Tab once per widget (plus one to wrap) and record what gets focus.
		List<AbstractWidget> visited = new ArrayList<>();
		for (int i = 0; i <= focusable.size(); i++) {
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(SETTLE);
			GuiEventListener focused = context.computeOnClient(mc -> mc.gui.screen().getFocused());
			check(focused instanceof AbstractWidget, "tab " + tab + ": Tab focused something that is not a widget: " + focused);
			visited.add((AbstractWidget) focused);
		}
		check(visited.containsAll(focusable), "tab " + tab + ": not every widget was reached with Tab. Missing: "
				+ focusable.stream().filter(w -> !visited.contains(w)).map(M2ClientGameTest::describe).toList());
		// Consecutive Tab presses follow the list order (wrapping around).
		for (int i = 1; i < visited.size(); i++) {
			int prev = focusable.indexOf(visited.get(i - 1));
			int next = focusable.indexOf(visited.get(i));
			check(next == (prev + 1) % focusable.size(), "tab " + tab + ": Tab went from " + describe(visited.get(i - 1))
					+ " to " + describe(visited.get(i)) + ", not the next widget in reading order");
		}
		// Shift+Tab goes back.
		AbstractWidget before = visited.get(visited.size() - 1);
		pressWithModifiers(context, InputConstants.KEY_TAB, MOD_SHIFT);
		GuiEventListener afterShiftTab = context.computeOnClient(mc -> mc.gui.screen().getFocused());
		int expectedIndex = Math.floorMod(focusable.indexOf(before) - 1, focusable.size());
		check(afterShiftTab == focusable.get(expectedIndex), "tab " + tab + ": Shift+Tab did not go back one widget: from "
				+ describe(before) + " expected " + describe(focusable.get(expectedIndex)) + " but got "
				+ (afterShiftTab instanceof AbstractWidget w ? describe(w) : String.valueOf(afterShiftTab)));

		for (AbstractWidget widget : focusable) {
			String narration = context.computeOnClient(mc -> narrationOf(widget));
			check(!narration.isBlank(), "tab " + tab + ": " + describe(widget) + " has no narration");
			String label = context.computeOnClient(mc -> widget.getMessage().getString());
			if (!label.isBlank()) {
				check(narration.contains(label), "tab " + tab + ": narration '" + narration + "' does not contain the label '" + label + "'");
			}
		}
	}

	private static void checkReadingOrder(List<AbstractWidget> widgets, String where) {
		for (int i = 1; i < widgets.size(); i++) {
			AbstractWidget a = widgets.get(i - 1);
			AbstractWidget b = widgets.get(i);
			boolean sameRow = a.getY() == b.getY();
			boolean ok = sameRow ? b.getX() > a.getX() : b.getY() > a.getY();
			check(ok, where + ": " + describe(b) + " comes after " + describe(a) + " but is not right of or below it");
		}
	}

	private static void checkCtrlTab(ClientGameTestContext context) {
		selectTab(context, 0);
		pressWithModifiers(context, InputConstants.KEY_TAB, MOD_CONTROL);
		check(context.computeOnClient(mc -> demo(mc).currentTab()) == 1, "Ctrl+Tab did not switch to the next tab");
		pressWithModifiers(context, InputConstants.KEY_TAB, MOD_CONTROL | MOD_SHIFT);
		check(context.computeOnClient(mc -> demo(mc).currentTab()) == 0, "Ctrl+Shift+Tab did not switch back");
		check(context.computeOnClient(mc -> mc.gui.screen().isPauseScreen()), "the Buttons tab should pause the game");
		selectTab(context, 2);
		check(!context.computeOnClient(mc -> mc.gui.screen().isPauseScreen()), "the Dialogs tab should not pause the game");
	}

	// ---- Component behaviors ----

	private static void checkSliderKeys(ClientGameTestContext context) {
		selectTab(context, 1);
		context.runOnClient(mc -> {
			demo(mc).volumeSlider.setTypedValue(100);
			demo(mc).setFocused(demo(mc).volumeSlider);
		});
		check(!context.computeOnClient(mc -> demo(mc).volumeSlider.isWarning()), "100% should not be in the warning zone");
		press(context, InputConstants.KEY_RIGHT);
		checkVolume(context, 105, "Right");
		check(context.computeOnClient(mc -> demo(mc).volumeSlider.isWarning()), "105% should be in the warning zone");
		String narration = context.computeOnClient(mc -> narrationOf(demo(mc).volumeSlider));
		check(narration.contains("105%") && narration.contains("louder than vanilla"),
				"slider narration should read the value and the warning, was: " + narration);
		String visible = context.computeOnClient(mc -> demo(mc).volumeSlider.getMessage().getString());
		check(visible.equals("Volume: 105%"), "the visible label should be just 'Volume: 105%' (the tooltip explains), was: " + visible);
		pressWithModifiers(context, InputConstants.KEY_LEFT, MOD_SHIFT);
		checkVolume(context, 104, "Shift+Left");
		pressWithModifiers(context, InputConstants.KEY_RIGHT, MOD_SHIFT);
		checkVolume(context, 105, "Shift+Right");
		press(context, InputConstants.KEY_LEFT);
		press(context, InputConstants.KEY_LEFT);
		checkVolume(context, 95, "Left twice");
		check(context.computeOnClient(mc -> demo(mc).volumeField.getValue()).equals("95"),
				"the number field should follow the slider");
		// Home and End are not slider keys (they do nothing to the value).
		press(context, InputConstants.KEY_HOME);
		press(context, InputConstants.KEY_END);
		checkVolume(context, 95, "Home and End");

		// Typing a value moves the slider.
		context.runOnClient(mc -> {
			demo(mc).setFocused(demo(mc).volumeField);
			demo(mc).volumeField.setValue("");
		});
		context.getInput().typeChars("80");
		context.waitTicks(SETTLE);
		checkVolume(context, 80, "typing 80");
		check(!context.computeOnClient(mc -> demo(mc).volumeSlider.isWarning()), "80% should not be in the warning zone");
	}

	private static void checkVolume(ClientGameTestContext context, double expected, String after) {
		double actual = context.computeOnClient(mc -> demo(mc).volumeSlider.modelValue());
		check(actual == expected, "after " + after + " the slider should be " + expected + ", was " + actual);
	}

	private static void checkToggleAndButtons(ClientGameTestContext context) {
		selectTab(context, 0);
		context.runOnClient(mc -> demo(mc).setFocused(demo(mc).showCueToggle));
		check(context.computeOnClient(mc -> narrationOf(demo(mc).showCueToggle)).contains("Show cue: On"), "toggle should narrate 'Show cue: On'");
		press(context, InputConstants.KEY_SPACE);
		check(!context.computeOnClient(mc -> demo(mc).showCue), "Space should turn the toggle off");
		check(context.computeOnClient(mc -> narrationOf(demo(mc).showCueToggle)).contains("Show cue: Off"), "toggle should narrate 'Show cue: Off'");
		press(context, InputConstants.KEY_RETURN);
		check(context.computeOnClient(mc -> demo(mc).showCue), "Enter should turn the toggle back on");

		// Icon buttons narrate their text label, and Enter activates the focused button.
		AbstractWidget mute = context.computeOnClient(mc -> findByMessage(demo(mc), "Mute"));
		check(mute != null, "the Mute icon button is missing");
		check(context.computeOnClient(mc -> narrationOf(mute)).contains("Mute"), "icon button narration should contain its label");
		context.runOnClient(mc -> demo(mc).setFocused(mute));
		press(context, InputConstants.KEY_RETURN);
		check(context.computeOnClient(mc -> demo(mc).lastAction).equals("Mute"), "Enter on the Mute button should press it");
	}

	/**
	 * Tooltip screenshots: hover (after the 500 ms delay) and keyboard focus (immediate). Fabric's test input does
	 * not set the game's "last input" type, so the keyboard case sets it the way a real Tab press does.
	 */
	private static void captureTooltips(ClientGameTestContext context) {
		selectTab(context, 0);
		AbstractWidget plain = context.computeOnClient(mc -> findByMessage(demo(mc), "Plain button"));
		AbstractWidget mute = context.computeOnClient(mc -> findByMessage(demo(mc), "Mute"));
		double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		context.getInput().setCursorPos((plain.getX() + plain.getWidth() / 2.0) * scale, (plain.getY() + plain.getHeight() / 2.0) * scale);
		context.waitTicks(20); // longer than the 500 ms hover delay
		context.takeScreenshot("m2-tooltip-hover");
		context.getInput().setCursorPos(0, 0);
		context.runOnClient(mc -> {
			mc.setLastInputType(InputType.KEYBOARD_TAB);
			demo(mc).setFocused(mute);
		});
		context.waitTicks(1);
		context.takeScreenshot("m2-tooltip-keyboard-focus");
	}

	private static void checkTextFields(ClientGameTestContext context) {
		selectTab(context, 1);
		context.runOnClient(mc -> {
			demo(mc).setFocused(demo(mc).patternField);
			demo(mc).patternField.setValue("");
		});
		AzTextField field = context.computeOnClient(mc -> demo(mc).patternField);
		// A fresh, empty field is invalid (so Save could be disabled) but shows no error until it is edited.
		check(!context.computeOnClient(mc -> field.isValid()), "an empty pattern should be invalid");
		check(!context.computeOnClient(mc -> field.isErrorShown()), "a field the player has not edited should not show an error");
		check(!context.computeOnClient(mc -> narrationOf(field)).contains("Error"), "an unedited field should not narrate an error");
		context.takeScreenshot("m2-text-field-untouched");
		context.getInput().typeChars("minecraft:entity.cow.*");
		context.waitTicks(SETTLE);
		check(context.computeOnClient(mc -> field.isValid()), "a valid pattern was rejected: " + context.computeOnClient(mc -> field.getValue()));
		context.getInput().typeChars(" x");
		context.waitTicks(SETTLE);
		check(!context.computeOnClient(mc -> field.isValid()), "a pattern with a space should be invalid");
		check(context.computeOnClient(mc -> field.isErrorShown()), "after editing, the error should be shown");
		String narration = context.computeOnClient(mc -> narrationOf(field));
		check(narration.contains("Patterns cannot contain spaces"), "the error should be narrated with the field, was: " + narration);
		context.takeScreenshot("m2-text-field-error");
	}

	private static void checkDialogs(ClientGameTestContext context) {
		selectTab(context, 2);
		// Three buttons: default (Save) focused; Esc picks the safest choice (Keep editing).
		context.runOnClient(mc -> demo(mc).openThreeButtonDialog());
		context.waitForScreen(AzDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getFocused() == ((AzDialog) mc.gui.screen()).buttons().get(0)),
				"the default button (Save) should have focus when the dialog opens");
		String title = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		check(title.contains("Unsaved changes") && title.contains("not saved"), "dialog narration should read title and message, was: " + title);
		context.takeScreenshot("m2-dialog");
		press(context, InputConstants.KEY_ESCAPE);
		context.waitForScreen(UiKitDemoScreen.class);
		check(context.computeOnClient(mc -> demo(mc).lastAction).equals("Keep editing"), "Esc should pick 'Keep editing'");

		// Two buttons: Cancel is both default and safe; Enter on it cancels.
		context.runOnClient(mc -> demo(mc).openTwoButtonDialog());
		context.waitForScreen(AzDialog.class);
		press(context, InputConstants.KEY_RETURN);
		context.waitForScreen(UiKitDemoScreen.class);
		check(context.computeOnClient(mc -> demo(mc).lastAction).equals("Cancel"), "Enter on the default button should cancel");

		// Focus stays inside the dialog: Tab cycles only its buttons.
		context.runOnClient(mc -> demo(mc).openThreeButtonDialog());
		context.waitForScreen(AzDialog.class);
		for (int i = 0; i < 5; i++) {
			press(context, InputConstants.KEY_TAB);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof AzDialog dialog && dialog.buttons().contains(dialog.getFocused())),
					"Tab moved focus out of the dialog");
		}
		press(context, InputConstants.KEY_ESCAPE);
		context.waitForScreen(UiKitDemoScreen.class);
	}

	private static void checkUnsavedChangesGuard(ClientGameTestContext context) {
		selectTab(context, 2);
		context.runOnClient(mc -> demo(mc).pretendUnsaved = true);
		press(context, InputConstants.KEY_ESCAPE);
		context.waitForScreen(AzDialog.class);
		press(context, InputConstants.KEY_ESCAPE);
		context.waitForScreen(UiKitDemoScreen.class);
		check(context.computeOnClient(mc -> demo(mc).pretendUnsaved), "Esc in the guard should keep editing");

		UiKitDemoScreen screen = context.computeOnClient(M2ClientGameTest::demo);
		press(context, InputConstants.KEY_ESCAPE);
		context.waitForScreen(AzDialog.class);
		press(context, InputConstants.KEY_TAB); // Save -> Discard
		press(context, InputConstants.KEY_RETURN);
		context.waitFor(mc -> !(mc.gui.screen() instanceof UiKitDemoScreen) && !(mc.gui.screen() instanceof AzDialog));
		check(screen.lastAction.equals("Discarded"), "choosing Discard should discard and close, lastAction=" + screen.lastAction);
	}

	// ---- Layout at GUI scales 1–4 and Auto (A-3, 6.1) ----

	private static void checkLayouts(ClientGameTestContext context) {
		int[][] windows = {{854, 480}, {1920, 1080}};
		int[] scales = {1, 2, 3, 4, 0};
		int originalScale = context.computeOnClient(mc -> mc.options.guiScale().get());
		for (int[] window : windows) {
			context.getInput().resizeWindow(window[0], window[1]);
			context.waitTicks(SETTLE);
			for (int scale : scales) {
				context.runOnClient(mc -> {
					mc.options.guiScale().set(scale);
					mc.resizeGui();
				});
				context.waitTicks(SETTLE);
				int actual = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
				for (int tab = 0; tab < 3; tab++) {
					selectTab(context, tab);
					String where = window[0] + "x" + window[1] + " scale " + (scale == 0 ? "auto" : scale) + " (actual " + actual + "), tab " + tab;
					checkLayout(context, where);
					boolean minimum = window[0] == 854 && actual == 2;
					boolean largest = window[0] == 1920 && scale == 4;
					if ((minimum && scale == 2) || largest) {
						context.takeScreenshot("m2-" + window[0] + "x" + window[1] + "-scale" + actual + "-tab" + tab);
					}
				}
			}
		}
		context.runOnClient(mc -> {
			mc.options.guiScale().set(originalScale);
			mc.resizeGui();
		});
		context.getInput().resizeWindow(854, 480);
		selectTab(context, 0);
	}

	private static void checkLayout(ClientGameTestContext context, String where) {
		String problem = context.computeOnClient(mc -> {
			AzScreen screen = demo(mc);
			if (screen.body().overflows()) {
				return "body content is taller than the body";
			}
			List<int[]> rects = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (GuiEventListener child : screen.children()) {
				if (child instanceof AbstractWidget w && w.visible) {
					int extra = w instanceof AzTextField ? AzTextField.ERROR_SLOT_HEIGHT : 0;
					rects.add(new int[] {w.getX(), w.getY(), w.getWidth(), w.getHeight() + extra});
					names.add(describe(w));
				}
			}
			for (AzScreen.Body.Label label : screen.body().labels()) {
				rects.add(new int[] {label.x(), label.y(), Math.min(label.width(), mc.font.width(label.text().get())), mc.font.lineHeight});
				names.add("label '" + label.text().get().getString() + "'");
			}
			for (int i = 0; i < rects.size(); i++) {
				int[] r = rects.get(i);
				if (r[0] < 0 || r[1] < 0 || r[0] + r[2] > screen.width || r[1] + r[3] > screen.height) {
					return names.get(i) + " is outside the " + screen.width + "x" + screen.height + " screen";
				}
				for (int j = i + 1; j < rects.size(); j++) {
					int[] s = rects.get(j);
					boolean overlap = r[0] < s[0] + s[2] && s[0] < r[0] + r[2] && r[1] < s[1] + s[3] && s[1] < r[1] + r[3];
					if (overlap) {
						return names.get(i) + " overlaps " + names.get(j);
					}
				}
			}
			return null;
		});
		check(problem == null, where + ": " + problem);
	}

	// ---- Helpers ----

	private static void selectTab(ClientGameTestContext context, int tab) {
		context.runOnClient(mc -> demo(mc).selectTab(tab));
		context.waitTicks(SETTLE);
	}

	/**
	 * Key presses with Shift or Ctrl. Fabric's test input always sends modifier bits 0 (even with holdShift), but
	 * real keyboards send them and vanilla reads them from the key event, so the event is built here (per Minecraft
	 * version, see KeyEventCompat) and given to the screen directly.
	 */
	private static void pressWithModifiers(ClientGameTestContext context, int key, int modifiers) {
		context.runOnClient(mc -> mc.gui.screen().keyPressed(KeyEventCompat.of(key, modifiers)));
		context.waitTicks(SETTLE);
	}

	private static void press(ClientGameTestContext context, int key) {
		context.getInput().pressKey(key);
		context.waitTicks(SETTLE);
	}

	private static UiKitDemoScreen demo(Minecraft mc) {
		Screen screen = mc.gui.screen();
		check(screen instanceof UiKitDemoScreen, "expected the UI kit demo screen, found " + screen);
		return (UiKitDemoScreen) screen;
	}

	private static List<AbstractWidget> focusableWidgets(Screen screen) {
		List<AbstractWidget> widgets = new ArrayList<>();
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget w && w.visible && w.active) {
				widgets.add(w);
			}
		}
		return widgets;
	}

	private static AbstractWidget findByMessage(Screen screen, String message) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget w && w.getMessage().getString().equals(message)) {
				return w;
			}
		}
		return null;
	}

	private static String narrationOf(AbstractWidget widget) {
		return NarrationCompat.collect(widget::updateNarration);
	}

	private static String describe(AbstractWidget widget) {
		return widget.getClass().getSimpleName() + " '" + widget.getMessage().getString() + "' at " + widget.getX() + "," + widget.getY();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
