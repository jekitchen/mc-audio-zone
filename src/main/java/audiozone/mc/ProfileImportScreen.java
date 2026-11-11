package audiozone.mc;

import audiozone.mc.ui.AzButton;
import audiozone.mc.ui.AzList;
import audiozone.mc.ui.AzScreen;
import audiozone.mc.ui.Tooltips;
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
 * Import a profile file (PRD FR-11.5): the {@code .json} files in {@code config/audiozone/imports/}, each with Import,
 * and "Open folder". Files can also be dropped on the Profiles tab. Choosing a file returns to the Profiles tab and
 * shows what the import will change (or what is wrong with the file) before anything changes.
 */
public final class ProfileImportScreen extends AzScreen {
	private static final int IMPORT_WIDTH = 50;
	private static final int DATE_WIDTH = 110;

	public AzList list;
	public AzButton openFolderButton;

	public ProfileImportScreen(Screen parent) {
		super(Component.translatable("audiozone.transfer.import_screen.title"), parent);
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
		footer.add(AzButton.text(Component.translatable("audiozone.ui.cancel"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		openFolderButton = Tooltips.describe(AzButton.text(Component.translatable("audiozone.transfer.open_folder"),
				() -> ProfileTransfer.openFolder(ProfileTransfer.importsDir())), Component.translatable("audiozone.transfer.open_imports.tip"));
		body.row(openFolderButton);
		body.label(() -> Component.translatable("audiozone.transfer.import_screen.hint"));
		list = new AzList(body.width(), 100, Component.translatable("audiozone.transfer.import_screen.list"));
		list.setEmptyText(() -> Component.translatable("audiozone.transfer.import_screen.empty"));
		body.fill(list);
		List<AzList.Row> rows = new ArrayList<>();
		for (Path file : ProfileTransfer.importFiles()) {
			String name = file.getFileName().toString();
			String modified = modified(file);
			AzButton button = Tooltips.describe(AzButton.text(Component.translatable("audiozone.transfer.import"), IMPORT_WIDTH,
					() -> choose(file)).narrationContext(() -> Component.literal(name)),
					Component.translatable("audiozone.transfer.import.tip"));
			rows.add(new AzList.Row(name, List.of(
					new AzList.Row.Column(() -> name, 0, 0),
					new AzList.Row.Column(() -> modified, DATE_WIDTH, 0)), List.of(button)));
		}
		list.setRows(rows);
	}

	private static String modified(Path file) {
		try {
			LocalDate date = LocalDate.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
			return Component.translatable("audiozone.transfer.modified", date.toString()).getString();
		} catch (IOException e) {
			return "";
		}
	}

	/** Back to the Profiles tab, then check the file and ask. */
	public void choose(Path file) {
		closeNow();
		if (parent instanceof AzScreen host) {
			ProfileTransfer.importWithDialog(host, file);
		}
	}
}
