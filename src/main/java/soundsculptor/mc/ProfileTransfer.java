package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.Message;
import soundsculptor.core.profile.ExportCodec;
import soundsculptor.core.profile.ImportPlan;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.ProfileExport;
import soundsculptor.core.profile.ProfileFormatException;
import soundsculptor.core.profile.ProfileImport;
import soundsculptor.core.profile.ProfileStore;
import soundsculptor.core.sets.SetImportPlan;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Export and import of profile files (PRD FR-11.4 to FR-11.7).
 *
 * <ul>
 *   <li>Export writes the whole profile, fixed zones included, and a copy of every library Sound Set it attaches
 *   ({@link ExportCodec}) to {@code config/soundsculptor/exports/<name>-<date>.json} and offers "Open folder".</li>
 *   <li>Import takes a file dropped on the Profiles tab or one placed in {@code config/soundsculptor/imports/}. The file is
 *   checked first (core {@link ProfileImport}). Its Sound Sets are compared with the library (FR-11.5a,
 *   {@link SetImportPlan}): a set with exactly the entries of a library set under another ID cancels the import with a
 *   dialog naming that set; each set whose name a library set with other entries has is given a new name, one at a
 *   time ({@link SetRenameOnImportScreen}). A dialog then says what will change ({@link ImportPlan}) before anything
 *   does. Imports always apply to the profile of the world you are in, and the profile and the library change
 *   together.</li>
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
	 * Writes the profile, with every library set it attaches, to the exports folder.
	 *
	 * @return the file written
	 */
	public static Path export(String key, Profile profile) throws IOException {
		Path dir = exportsDir();
		Files.createDirectories(dir);
		Path file = dir.resolve(ProfileExport.fileName(profile.name(), LocalDate.now()));
		Files.writeString(file, ExportCodec.toJson(profile, key, ProfileManager.library()), StandardCharsets.UTF_8);
		return file;
	}

	/**
	 * Exports and shows where the file went, with "Open folder". A file larger than the import limit (FR-11.6, 1 MB) is
	 * still written, and the dialog warns that it can't be imported again as it is.
	 */
	public static void exportWithDialog(SculptScreen host, String key, Profile profile) {
		Path file;
		boolean tooLarge;
		try {
			file = export(key, profile);
			tooLarge = Files.size(file) > ProfileStore.MAX_FILE_BYTES;
		} catch (IOException e) {
			SoundSculptorClient.LOGGER.error("Sound Sculptor could not export {}", key, e);
			host.showToast(Component.translatable("soundsculptor.transfer.export_failed", e.getMessage()), null);
			return;
		}
		Minecraft.getInstance().gui.setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.transfer.exported.title"),
				Component.translatable(tooLarge ? "soundsculptor.profiles.export.too_large" : "soundsculptor.transfer.exported.message",
						file.getFileName().toString()),
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
	 * Checks the file and asks what to do: a dialog naming the problem (nothing changes); for a set in the file with
	 * exactly the entries of a library set under another ID, a dialog naming that set, with only OK; otherwise a new name
	 * for each set whose name clashes with a library set, then a dialog listing what the import will change, with Import
	 * and Cancel. {@code host} is the screen to return to; Cancel anywhere changes nothing.
	 */
	public static void importWithDialog(SculptScreen host, Path file) {
		String name = file.getFileName().toString();
		if (!ProfileManager.isLoaded()) {
			host.showToast(Component.translatable("soundsculptor.transfer.no_world"), null);
			return;
		}
		ExportCodec.Read read;
		try {
			read = ProfileImport.check(file);
		} catch (ProfileFormatException e) {
			showProblem(host, name, ProfileManager.text(e.problem()));
			return;
		}
		SetLibrary lib = ProfileManager.library();
		SetImportPlan sets = ImportPlan.of(ProfileManager.profile(), read, lib, ProfileManager.key(), Map.of()).sets();
		Message exact = sets.exactMatchProblem(lib);
		if (exact != null) {
			Minecraft.getInstance().gui.setScreen(exactMatchDialog(host, name, exact));
			return;
		}
		// Every other problem (too many zones, a full library) is shown before any name is asked for: with the names the
		// rename screen would fill in, only those are left.
		ImportPlan check = ImportPlan.of(ProfileManager.profile(), read, lib, ProfileManager.key(), sets.suggestedNames(lib));
		if (check.problem() != null) {
			showProblem(host, name, ProfileManager.text(check.problem()));
			return;
		}
		askNames(host, name, read, sets, Map.of());
	}

	/**
	 * Asks for the next name clash's new name ({@link SetRenameOnImportScreen}), filled in with "name (imported)", or,
	 * once every one has a name, shows what the import will do.
	 */
	private static void askNames(SculptScreen host, String name, ExportCodec.Read read, SetImportPlan sets, Map<String, String> chosen) {
		List<SoundSet> clashes = sets.nameClashes();
		if (chosen.size() == clashes.size()) {
			confirm(host, name, read, chosen);
			return;
		}
		SoundSet next = clashes.get(chosen.size());
		Minecraft.getInstance().gui.setScreen(new SetRenameOnImportScreen(host, next, chosen.size() + 1, clashes.size(),
				sets.suggestedName(next.id(), ProfileManager.library(), chosen),
				typed -> sets.nameProblem(next.id(), typed, ProfileManager.library(), chosen),
				picked -> {
					Map<String, String> more = new LinkedHashMap<>(chosen);
					more.put(next.id(), picked);
					askNames(host, name, read, sets, more);
				}));
	}

	/** The dialog listing what the import will do, with Import and Cancel. */
	private static void confirm(SculptScreen host, String name, ExportCodec.Read read, Map<String, String> names) {
		ImportPlan plan = ImportPlan.of(ProfileManager.profile(), read, ProfileManager.library(), ProfileManager.key(), names);
		if (plan.problem() != null) {
			showProblem(host, name, ProfileManager.text(plan.problem()));
			return;
		}
		Minecraft.getInstance().gui.setScreen(new SculptDialog(host,
				Component.translatable("soundsculptor.transfer.import.title", name),
				planMessage(plan, read.warnings().size()),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.transfer.import.confirm"),
						() -> ProfileListPanel.confirmBoostThen(host, plan.needsBoostConfirm(), () -> apply(host, name, read, names))),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), () -> { })),
				1, 1));
	}

	/** What the import will do, in the dialog's words. */
	public static Component planMessage(ImportPlan plan, int warnings) {
		MutableComponent message = Component.translatable("soundsculptor.transfer.import.replace",
				ProfileListPanel.setsOnEverywhere(plan.setCount()));
		if (plan.setsAdded() > 0) {
			message.append(" ").append(plan.setsAdded() == 1 ? Component.translatable("soundsculptor.transfer.import.sets_added.one")
					: Component.translatable("soundsculptor.transfer.import.sets_added", plan.setsAdded()));
		}
		Message missing = plan.missingNote();
		if (missing != null) {
			message.append(" ").append(Component.translatable("soundsculptor.transfer.import.note", ProfileManager.text(missing)));
		}
		if (plan.fixedImported() > 0) {
			message.append(" ").append(ProfileListPanel.plural("soundsculptor.transfer.import.fixed", plan.fixedImported()));
		}
		if (plan.fixedSkipped() > 0) {
			message.append(" ").append(ProfileListPanel.plural("soundsculptor.transfer.import.skipped", plan.fixedSkipped()));
		}
		if (warnings > 0) {
			message.append(" ").append(ProfileListPanel.plural("soundsculptor.transfer.import.ignored", warnings));
		}
		return message;
	}

	/**
	 * Imports: the plan is worked out again against the live profile and library, and both are swapped in together
	 * ({@link ProfileManager#change}), the sets saved before the profile. If the library has changed since the dialog
	 * opened so that the import no longer fits or a chosen name is now in use, nothing changes and the problem is shown.
	 */
	private static void apply(SculptScreen host, String name, ExportCodec.Read read, Map<String, String> names) {
		String key = ProfileManager.key();
		ImportPlan[] done = {null};
		ProfileManager.change(ws -> {
			ImportPlan plan = ImportPlan.of(ws.profile(), read, ws.library(), key, names);
			done[0] = plan;
			if (plan.problem() != null) {
				return ws;
			}
			// Boosted levels were confirmed in the boost dialog (FR-5.4) before this runs.
			Profile result = plan.needsBoostConfirm() ? plan.result().withBoostConfirmed(true) : plan.result();
			return new Workspace(result, plan.library());
		});
		if (done[0].problem() != null) {
			showProblem(host, name, ProfileManager.text(done[0].problem()));
			return;
		}
		int skipped = done[0].fixedSkipped();
		if (host instanceof SoundSculptorScreen main && main.profilesPanel() != null) {
			main.profilesPanel().refresh();
		}
		host.showToast(skipped == 0 ? Component.translatable("soundsculptor.transfer.imported", name)
				: skipped == 1 ? Component.translatable("soundsculptor.transfer.imported.skipped.one", name)
				: Component.translatable("soundsculptor.transfer.imported.skipped", name, skipped), null);
	}

	/**
	 * A set in the file has exactly the entries of a library set under another ID (FR-11.5a): the import is cancelled,
	 * and the dialog names the library set. Only OK: there is nothing to choose.
	 */
	public static SculptDialog exactMatchDialog(Screen host, String name, Message problem) {
		return new SculptDialog(host,
				Component.translatable("soundsculptor.import.exact_match.title", name),
				Component.translatable("soundsculptor.import.exact_match.message", ProfileManager.component(problem)),
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.ui.ok"), () -> { })),
				0, 0);
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
