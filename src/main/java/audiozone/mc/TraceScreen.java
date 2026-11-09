package audiozone.mc;

import audiozone.core.Translator;
import audiozone.core.search.SoundSearch;
import audiozone.core.trace.SoundTrace;
import audiozone.core.trace.SoundTracer;
import audiozone.core.trace.TraceText;
import audiozone.core.zones.Precedence;
import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.Tooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "Why does this sound like this?" panel (PRD FR-14, read-only MVP). Built when it opens from the sound's last
 * play (FR-14.6); the lines on screen are the same text "Copy trace" puts on the clipboard (FR-14.5), so the two
 * never disagree. Zones are included since M7.
 */
public final class TraceScreen extends AzScreen {
	private static final Translator TRANSLATOR = (key, args) -> Component.translatable(key, args).getString();

	private final String soundId;
	private final SoundTrace trace;
	private final String text;
	public AzList lines;
	public AzButton copyButton;

	public TraceScreen(Screen parent, String soundId) {
		super(Component.translatable("audiozone.trace.title"), parent);
		this.soundId = soundId;
		Precedence precedence = ProfileManager.precedence();
		this.trace = SoundTracer.trace(precedence, soundId, SoundHook.lastPlays().get(soundId), ProfileManager.isBypassed(),
				SoundSearch.isVanillaCovered(soundId) || KnownSounds.seenExcluded().contains(soundId));
		this.text = TraceText.of(trace, TRANSLATOR);
	}

	public SoundTrace trace() {
		return trace;
	}

	/** The plain text that "Copy trace" copies. */
	public String text() {
		return text;
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(new Tab(getTitle(), true));
	}

	@Override
	protected Component headerSubtitle() {
		return Component.literal(soundId);
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		copyButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.trace.copy"), this::copy),
				Component.translatable("audiozone.trace.copy.tip"));
		footer.add(copyButton);
		footer.add(AzButton.text(Component.translatable("audiozone.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		lines = new AzList(body.width(), AzList.ROW_HEIGHT, Component.translatable("audiozone.trace.lines"));
		body.fill(lines);
		List<AzList.Row> rows = new ArrayList<>();
		String[] split = text.split("\n");
		for (int i = 0; i < split.length; i++) {
			String line = split[i];
			rows.add(new AzList.Row(i, List.of(new AzList.Row.Column(() -> line, 0)), List.of()));
		}
		lines.setRows(rows);
	}

	public void copy() {
		minecraft.keyboardHandler.setClipboard(text);
		showToast(Component.translatable("audiozone.trace.copied"), null);
	}
}
