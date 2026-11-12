package soundsculptor.core.profile;

import soundsculptor.core.Message;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks a file before it is imported (PRD FR-11.6): valid JSON, 1 MB or less, a known schema version (older ones are
 * migrated), every value in range and every pattern valid, in the profile and in each Sound Set the file carries
 * ({@link ExportCodec}). On any problem nothing is imported, and the exception's message names it in plain words with its
 * place. Unknown fields are ignored and listed as warnings.
 *
 * <p>What the checked file then changes is worked out by {@link ImportPlan}.
 */
public final class ProfileImport {
	private ProfileImport() {
	}

	/** Checks a file on disk; its size is checked before it is read. */
	public static ExportCodec.Read check(Path file) throws ProfileFormatException {
		byte[] bytes;
		try {
			if (Files.size(file) > ProfileStore.MAX_FILE_BYTES) {
				throw ProfileText.problem(ProfileText.TOO_LARGE);
			}
			bytes = Files.readAllBytes(file);
		} catch (IOException e) {
			throw new ProfileFormatException(Message.of(ProfileText.UNREADABLE, String.valueOf(e.getMessage())), e);
		}
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			throw new ProfileFormatException(Message.of(ProfileText.NOT_UTF8), e);
		}
		return check(text);
	}

	/** Checks the text of a file: {@link ExportCodec#read}. */
	public static ExportCodec.Read check(String text) throws ProfileFormatException {
		return ExportCodec.read(text);
	}
}
