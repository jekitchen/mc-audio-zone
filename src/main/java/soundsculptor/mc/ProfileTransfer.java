package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.profile.ImportPlan;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileCodec;
import soundsculptor.core.profile.ProfileExport;
import soundsculptor.core.profile.ProfileFormatException;
import soundsculptor.core.profile.ProfileImport;
import soundsculptor.mc.compat.FolderCompat;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptScreen;
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
 *   <li>Export writes the whole profile, fixed zones included, to {@code config/soundsculptor/exports/<name>-<date>.json}
 *   and offers "Open folder".</li>
 *   <li>Import takes a file dropped on the Profiles tab or one placed in {@code config/soundsculptor/imports/}. The file is
 *   checked first (core {@link ProfileImport}); a dialog then says what will change ({@link ImportPlan}) before
 *   anything does. Imports always apply to the profile of the world you are in.</li>
 * </ul>
 */
public final class ProfileTransfer {
	private ProfileTransfer() {
	}

	/** {@code config/soundsculptor/exports/}. */
	public static Path exportsDir() {
		return ProfileManager.store().directory().resolveSibling("exports");
	}

	/** {@code config/soundsculptor/imports/}. */
	public static Path importsDir() {
		return ProfileManager.store().directory().resolveSibling("imports");
	}

	/** Opens a folder in the system's file browser, creating it first so there is something to open. */
	public static void openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			FolderCompat.open(dir);
		} catch (IOException | RuntimeException e) {
			SoundSculptorClient.LOGGER.warn("Sound Sculptor could not open {}", dir, e);
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
	public static void exportWithDialog(SculptScreen host, String key, Profile profile) {
		Path file;
		try {
			file = export(key, profile);
		} catch (IOException e) {
			SoundSculptorClient.LOGGER.error("Sound Sculptor could not export {}", key, e);
			host.showToast(Component.translatable("soundsculptor.transfer.export_failed", e.getMessage()), null);
			return;
		}
		Minecraft.getInstance().gui.setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.transfer.exported.title"),
				Component.translatable("soundsculptor.transfer.exported.message", file.getFileName().toString()),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.transfer.open_folder"), () -> openFolder(exportsDir())),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.ok"), () -> { })),
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
			SoundSculptorClient.LOGGER.warn("Sound Sculptor could not list {}", dir, e);
			return List.of();
		}
	}

	/**
	 * Checks the file and asks what to do: a dialog naming the problem (nothing changes), or one listing what the import
	 * will change, with Import and Cancel. {@code host} is the screen to return to.
	 */
	public static void importWithDialog(SculptScreen host, Path file) {
		String name = file.getFileName().toString();
		if (!ProfileManager.isLoaded()) {
			host.showToast(Component.translatable("soundsculptor.transfer.no_world"), null);
			return;
		}
		ProfileImport.Checked checked;
		try {
			checked = ProfileImport.check(file);
		} catch (ProfileFormatException e) {
			showProblem(host, name, ProfileManager.text(e.problem()));
			return;
		}
		ImportPlan plan = ImportPlan.of(ProfileManager.profile(), checked.profile(), ProfileManager.key());
		if (plan.problem() != null) {
			showProblem(host, name, ProfileManager.text(plan.problem()));
			return;
		}
		Minecraft.getInstance().gui.setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.transfer.import.title", name),
				planMessage(plan, checked.warnings().size()),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.transfer.import.confirm"),
						() -> ProfileListPanel.confirmBoostThen(host, plan.needsBoostConfirm(), () -> apply(host, name, checked))),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	/** What the import will do, in the dialog's words. */
	public static Component planMessage(ImportPlan plan, int warnings) {
		MutableComponent message = Component.translatable("soundsculptor.transfer.import.replace", plan.ruleCount(), plan.bubbleCount());
		if (plan.fixedImported() > 0) {
			message.append(" ").append(Component.translatable("soundsculptor.transfer.import.fixed", plan.fixedImported()));
		}
		if (plan.fixedSkipped() > 0) {
			message.append(" ").append(Component.translatable("soundsculptor.transfer.import.skipped", plan.fixedSkipped()));
		}
		if (warnings > 0) {
			message.append(" ").append(Component.translatable("soundsculptor.transfer.import.ignored", warnings));
		}
		return message;
	}

	private static void apply(SculptScreen host, String name, ProfileImport.Checked checked) {
		String key = ProfileManager.key();
		int[] skipped = {0};
		ProfileManager.update(current -> {
			ImportPlan plan = ImportPlan.of(current, checked.profile(), key);
			skipped[0] = plan.fixedSkipped();
			if (plan.problem() != null) {
				return current;
			}
			// Boosted rules were confirmed in the boost dialog (FR-5.4) before this runs.
			return plan.needsBoostConfirm() ? plan.result().withBoostConfirmed(true) : plan.result();
		});
		if (host instanceof SoundSculptorScreen main && main.profilesPanel() != null) {
			main.profilesPanel().refresh();
		}
		host.showToast(skipped[0] > 0
				? Component.translatable("soundsculptor.transfer.imported.skipped", name, skipped[0])
				: Component.translatable("soundsculptor.transfer.imported", name), null);
	}

	private static void showProblem(SculptScreen host, String name, String problem) {
		Minecraft.getInstance().gui.setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.transfer.problem.title", name),
				Component.translatable("soundsculptor.transfer.problem.message", problem),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.ui.ok"), () -> { }),
						new SculptDialog.Choice(Component.translatable("soundsculptor.transfer.open_folder"), () -> openFolder(importsDir()))),
				0, 0));
	}
}
