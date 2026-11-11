package audiozone.mc;

import audiozone.AudioZoneClient;
import audiozone.core.profile.ImportPlan;
import audiozone.core.profile.Profile;
import audiozone.core.profile.ProfileCodec;
import audiozone.core.profile.ProfileExport;
import audiozone.core.profile.ProfileFormatException;
import audiozone.core.profile.ProfileImport;
import audiozone.mc.compat.FolderCompat;
import audiozone.mc.ui.AzDialog;
import audiozone.mc.ui.AzScreen;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Export and import of profile files (PRD FR-11.4 to FR-11.7).
 *
 * <ul>
 *   <li>Export writes the whole profile, fixed zones included, to {@code config/audiozone/exports/<name>-<date>.json}
 *   and offers "Open folder".</li>
 *   <li>Import takes a file dropped on the Profiles tab or one placed in {@code config/audiozone/imports/}. The file is
 *   checked first (core {@link ProfileImport}); a dialog then says what will change ({@link ImportPlan}) before
 *   anything does. Imports always apply to the profile of the world you are in.</li>
 * </ul>
 */
public final class ProfileTransfer {
	private ProfileTransfer() {
	}

	/** {@code config/audiozone/exports/}. */
	public static Path exportsDir() {
		return ProfileManager.store().directory().resolveSibling("exports");
	}

	/** {@code config/audiozone/imports/}. */
	public static Path importsDir() {
		return ProfileManager.store().directory().resolveSibling("imports");
	}

	/** Opens a folder in the system's file browser, creating it first so there is something to open. */
	public static void openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			FolderCompat.open(dir);
		} catch (IOException | RuntimeException e) {
			AudioZoneClient.LOGGER.warn("Audio Zone could not open {}", dir, e);
		}
	}

	// ---- Export (FR-11.4) ----

	/**
	 * Writes the profile to the exports folder.
	 *
	 * @return the file written
	 */
	public static Path export(String key, Profile profile) throws IOException {
		Path dir = exportsDir();
		Files.createDirectories(dir);
		Path file = dir.resolve(ProfileExport.fileName(profile.name(), LocalDate.now()));
		Files.writeString(file, ProfileCodec.toJson(profile, key), StandardCharsets.UTF_8);
		return file;
	}

	/** Exports and shows where the file went, with "Open folder". */
	public static void exportWithDialog(AzScreen host, String key, Profile profile) {
		Path file;
		try {
			file = export(key, profile);
		} catch (IOException e) {
			AudioZoneClient.LOGGER.error("Audio Zone could not export {}", key, e);
			host.showToast(Component.translatable("audiozone.transfer.export_failed", e.getMessage()), null);
			return;
		}
		Minecraft.getInstance().gui.setScreen(new AzDialog(host,
				Component.translatable("audiozone.transfer.exported.title"),
				Component.translatable("audiozone.transfer.exported.message", file.getFileName().toString()),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.transfer.open_folder"), () -> openFolder(exportsDir())),
						new AzDialog.Choice(Component.translatable("audiozone.ui.ok"), () -> { })),
				1, 1));
	}

	// ---- Import (FR-11.5 to FR-11.7) ----

	/** The {@code .json} files in the imports folder, by name. */
	public static List<Path> importFiles() {
		Path dir = importsDir();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(dir)) {
			return new ArrayList<>(entries
					.filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json") && Files.isRegularFile(f))
					.sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
					.toList());
		} catch (IOException e) {
			AudioZoneClient.LOGGER.warn("Audio Zone could not list {}", dir, e);
			return List.of();
		}
	}

	/**
	 * Checks the file and asks what to do: a dialog naming the problem (nothing changes), or one listing what the import
	 * will change, with Import and Cancel. {@code host} is the screen to return to.
	 */
	public static void importWithDialog(AzScreen host, Path file) {
		String name = file.getFileName().toString();
		if (!ProfileManager.isLoaded()) {
			host.showToast(Component.translatable("audiozone.transfer.no_world"), null);
			return;
		}
		ProfileImport.Checked checked;
		try {
			checked = ProfileImport.check(file);
		} catch (ProfileFormatException e) {
			showProblem(host, name, e.getMessage());
			return;
		}
		ImportPlan plan = ImportPlan.of(ProfileManager.profile(), checked.profile(), ProfileManager.key());
		if (plan.problem() != null) {
			showProblem(host, name, plan.problem());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new AzDialog(host,
				Component.translatable("audiozone.transfer.import.title", name),
				planMessage(plan, checked.warnings().size()),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.transfer.import.confirm"), () -> apply(host, name, checked)),
						new AzDialog.Choice(Component.translatable("audiozone.ui.cancel"), () -> { })),
				1, 1));
	}

	/** What the import will do, in the dialog's words. */
	public static Component planMessage(ImportPlan plan, int warnings) {
		MutableComponent message = Component.translatable("audiozone.transfer.import.replace", plan.ruleCount(), plan.bubbleCount());
		if (plan.fixedImported() > 0) {
			message.append(" ").append(Component.translatable("audiozone.transfer.import.fixed", plan.fixedImported()));
		}
		if (plan.fixedSkipped() > 0) {
			message.append(" ").append(Component.translatable("audiozone.transfer.import.skipped", plan.fixedSkipped()));
		}
		if (warnings > 0) {
			message.append(" ").append(Component.translatable("audiozone.transfer.import.ignored", warnings));
		}
		return message;
	}

	private static void apply(AzScreen host, String name, ProfileImport.Checked checked) {
		String key = ProfileManager.key();
		int[] skipped = {0};
		ProfileManager.update(current -> {
			ImportPlan plan = ImportPlan.of(current, checked.profile(), key);
			skipped[0] = plan.fixedSkipped();
			return plan.problem() == null ? plan.result() : current;
		});
		if (host instanceof AudioZoneScreen main && main.profilesPanel() != null) {
			main.profilesPanel().refresh();
		}
		host.showToast(skipped[0] > 0
				? Component.translatable("audiozone.transfer.imported.skipped", name, skipped[0])
				: Component.translatable("audiozone.transfer.imported", name), null);
	}

	private static void showProblem(AzScreen host, String name, String problem) {
		Minecraft.getInstance().gui.setScreen(new AzDialog(host,
				Component.translatable("audiozone.transfer.problem.title", name),
				Component.translatable("audiozone.transfer.problem.message", problem),
				List.of(new AzDialog.Choice(Component.translatable("audiozone.ui.ok"), () -> { }),
						new AzDialog.Choice(Component.translatable("audiozone.transfer.open_folder"), () -> openFolder(importsDir()))),
				0, 0));
	}
}
