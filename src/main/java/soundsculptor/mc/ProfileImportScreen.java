package soundsculptor.mc;

import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.Tooltips;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Import a profile file (PRD FR-11.5): the {@code .json} files in {@code config/soundsculptor/imports/}, each with Import,
 * and "Open folder". Files can also be dropped on the Profiles tab. Choosing a file returns to the Profiles tab and
 * shows what the import will change (or what is wrong with the file) before anything changes.
 */
public final class ProfileImportScreen extends SculptScreen {
	private static final int IMPORT_WIDTH = 50;
	private static final int DATE_WIDTH = 110;

	public SculptList list;
	public SculptButton openFolderButton;

	public ProfileImportScreen(Screen parent) {
		super(Component.translatable("soundsculptor.transfer.import_screen.title"), parent);
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
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.cancel"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		openFolderButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.transfer.open_folder"),
				() -> ProfileTransfer.openFolder(ProfileTransfer.importsDir())), Component.translatable("soundsculptor.transfer.open_imports.tip"));
		body.row(openFolderButton);
		body.label(() -> Component.translatable("soundsculptor.transfer.import_screen.hint"));
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.transfer.import_screen.list"));
		list.setEmptyText(() -> Component.translatable("soundsculptor.transfer.import_screen.empty"));
		body.fill(list);
		List<SculptList.Row> rows = new ArrayList<>();
		for (Path file : ProfileTransfer.importFiles()) {
			String name = file.getFileName().toString();
			String modified = modified(file);
			SculptButton button = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.transfer.import"), IMPORT_WIDTH,
					() -> choose(file)),
					Component.translatable("soundsculptor.transfer.import.tip"));
			rows.add(new SculptList.Row(name, List.of(
					new SculptList.Row.Column(() -> name, 0, 0),
					new SculptList.Row.Column(() -> modified, DATE_WIDTH, 0)), List.of(button)));
		}
		list.setRows(rows);
	}

	private static String modified(Path file) {
		try {
			LocalDate date = LocalDate.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
			return Component.translatable("soundsculptor.transfer.modified", date.toString()).getString();
		} catch (IOException e) {
			return "";
		}
	}

	/** Back to the Profiles tab, then check the file and ask. */
	public void choose(Path file) {
		closeNow();
		if (parent instanceof SculptScreen host) {
			ProfileTransfer.importWithDialog(host, file);
		}
	}
}
