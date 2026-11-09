package audiozone.core.profile;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks a file before it is imported (PRD FR-11.6): valid JSON, 1 MB or less, a known schema version (older ones are
 * migrated), every value in range and every pattern valid. On any problem nothing is imported, and the exception's
 * message names it in plain words with its place. Unknown fields are ignored and listed as warnings.
 *
 * <p>What the checked profile then changes is worked out by {@link ImportPlan}.
 */
public final class ProfileImport {
	/** A profile that passed every check, and the unknown fields that were ignored. */
	public record Checked(Profile profile, List<String> warnings) {
		public Checked {
			warnings = List.copyOf(warnings);
		}
	}

	private ProfileImport() {
	}

	/** Checks a file on disk; its size is checked before it is read. */
	public static Checked check(Path file) throws ProfileFormatException {
		byte[] bytes;
		try {
			if (Files.size(file) > ProfileStore.MAX_FILE_BYTES) {
				throw tooLarge();
			}
			bytes = Files.readAllBytes(file);
		} catch (IOException e) {
			throw new ProfileFormatException("The file could not be read: " + e.getMessage(), e);
		}
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			throw new ProfileFormatException("The file is not UTF-8 text.", e);
		}
		return check(text);
	}

	/** Checks the text of a file. */
	public static Checked check(String text) throws ProfileFormatException {
		if (text.getBytes(StandardCharsets.UTF_8).length > ProfileStore.MAX_FILE_BYTES) {
			throw tooLarge();
		}
		if (text.isBlank()) {
			throw new ProfileFormatException("The file is empty.");
		}
		JsonObject root = ProfileCodec.parse(text);
		if (ProfileMigrations.versionOf(root) > ProfileMigrations.CURRENT.target()) {
			throw new ProfileFormatException("This file was made with a newer Audio Zone. Update the mod to import it.");
		}
		// An import file is not one of the stored profiles: there is nothing to back up before migrating it.
		ProfileCodec.Read read = ProfileCodec.read(root, version -> { });
		return new Checked(read.profile(), read.warnings());
	}

	private static ProfileFormatException tooLarge() {
		return new ProfileFormatException("The file is larger than 1 MB.");
	}
}
