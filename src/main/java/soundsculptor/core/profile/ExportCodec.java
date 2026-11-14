package soundsculptor.core.profile;

import soundsculptor.core.Message;
import soundsculptor.core.sets.Attachment;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetCodec;
import soundsculptor.core.sets.SetFormatException;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Zone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The export file (PRD FR-11.4, section 7.5): the profile file plus {@code librarySets}, a full copy of every library
 * Sound Set the profile attaches, each in the Sound Set file format ({@link SetCodec}). With the sets inside, the file
 * can be imported or restored on its own, even into a library that has never had them. Quick changes are not library
 * sets: they stay inline in their place, as in the profile file.
 *
 * <p>Reading checks the whole file before anything changes (FR-11.6): 1 MB or less, a known schema, every value of the
 * profile and of each set in range, and at most {@value SetLibrary#MAX_SETS} sets. A plain profile file reads as an
 * export with no sets.
 */
public final class ExportCodec {
	/** "The file has %1$s Sound Sets; the maximum is %2$s." */
	static final String TOO_MANY_SETS = "soundsculptor.profile.problem.too_many_library_sets";
	/** "Sound Set %s": a set in {@code librarySets} by its position, before its name is known. */
	static final String PLACE_SET = "soundsculptor.profile.place.library_set";
	/** "%1$s is already used by Sound Set %2$s". */
	static final String SET_ID_USED = "soundsculptor.profile.problem.value.set_id_used";

	private static final String LIBRARY_SETS = "librarySets";

	private ExportCodec() {
	}

	/**
	 * A checked export file: the profile as it is in the file, the library sets it carries in file order, and the unknown
	 * fields that were ignored. What the sets do to the library is worked out by {@link ImportPlan}.
	 */
	public record Read(Profile profile, List<SoundSet> sets, List<Message> warnings) {
		public Read {
			sets = List.copyOf(sets);
			warnings = List.copyOf(warnings);
		}
	}

	/**
	 * The export file's text: the profile file with its world key, and every library set attached anywhere in the
	 * profile, each once, in the order the places (Everywhere, then zones in list order) first attach it. A set the
	 * library lacks is left out; importing the file then skips the attachment as not found.
	 */
	public static String toJson(Profile p, String worldKey, SetLibrary lib) {
		JsonObject root = ProfileCodec.toJsonObject(p, worldKey);
		JsonArray sets = new JsonArray();
		for (SoundSet set : attachedSets(p, lib)) {
			sets.add(SetCodec.toJsonObject(set));
		}
		root.add(LIBRARY_SETS, sets);
		return ProfileCodec.write(root);
	}

	private static List<SoundSet> attachedSets(Profile p, SetLibrary lib) {
		Map<String, SoundSet> out = new LinkedHashMap<>();
		List<PlaceSets> places = new ArrayList<>();
		places.add(p.everywhere());
		for (Zone zone : p.zones()) {
			places.add(zone.sets());
		}
		for (PlaceSets place : places) {
			for (Attachment a : place.attached()) {
				SoundSet set = lib.get(a.setId());
				if (set != null) {
					out.putIfAbsent(set.id(), set);
				}
			}
		}
		return List.copyOf(out.values());
	}

	/**
	 * Whether a file with this text is within the import limit, 1 MB in UTF-8 (FR-11.6). An export holds the whole
	 * profile and every set it uses (FR-11.4), so it can pass the limit: it is still written, and Export warns.
	 */
	public static boolean fitsImportLimit(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length <= ProfileStore.MAX_FILE_BYTES;
	}

	/**
	 * Checks and reads an export (or plain profile) file's text. On any problem nothing is read, and the exception's
	 * message names it in plain words with its place, for example "Sound Set 'Farm animals', entry 3, level: 250 is
	 * above the maximum 200."
	 */
	public static Read read(String text) throws ProfileFormatException {
		if (!fitsImportLimit(text)) {
			throw ProfileText.problem(ProfileText.TOO_LARGE);
		}
		if (text.isBlank()) {
			throw ProfileText.problem(ProfileText.EMPTY);
		}
		JsonObject root = ProfileCodec.parse(text);
		if (ProfileMigrations.versionOf(root) > ProfileMigrations.CURRENT.target()) {
			throw ProfileText.problem(ProfileText.NEWER_IMPORT);
		}
		// The sets are read here; the rest is the profile file, which does not know the field.
		JsonElement librarySets = root.remove(LIBRARY_SETS);
		List<Message> warnings = new ArrayList<>();
		List<SoundSet> sets = librarySets == null || librarySets.isJsonNull() ? List.of() : readSets(librarySets, warnings);
		// An import file is not one of the stored profiles: there is nothing to back up before migrating it.
		ProfileCodec.Read profile = ProfileCodec.read(root, version -> { });
		List<Message> all = new ArrayList<>(profile.warnings());
		all.addAll(warnings);
		return new Read(profile.profile(), sets, all);
	}

	private static List<SoundSet> readSets(JsonElement value, List<Message> warnings) throws ProfileFormatException {
		if (!value.isJsonArray()) {
			throw ProfileText.problem(ProfileText.TOP_FIELD, LIBRARY_SETS,
					Message.of(ProfileText.NOT_LIST, ProfileCodec.describe(value)));
		}
		JsonArray array = value.getAsJsonArray();
		if (array.size() > SetLibrary.MAX_SETS) {
			throw ProfileText.problem(TOO_MANY_SETS, array.size(), SetLibrary.MAX_SETS);
		}
		List<SoundSet> sets = new ArrayList<>(array.size());
		Map<String, Integer> firstUse = new HashMap<>();
		for (int i = 0; i < array.size(); i++) {
			Message where = Message.of(PLACE_SET, i + 1);
			JsonElement item = array.get(i);
			if (!item.isJsonObject()) {
				throw ProfileText.problem(ProfileText.ITEM_NOT_OBJECT, where, ProfileCodec.describe(item));
			}
			SetCodec.Read read;
			try {
				read = SetCodec.readWithWarnings(item.getAsJsonObject(), null);
			} catch (SetFormatException e) {
				throw new ProfileFormatException(e.problem(), e);
			}
			SoundSet set = read.set();
			Integer earlier = firstUse.putIfAbsent(set.id(), i + 1);
			if (earlier != null) {
				throw ProfileText.problem(ProfileText.FIELD, where, "id",
						Message.of(SET_ID_USED, ProfileCodec.quote(set.id()), earlier));
			}
			warnings.addAll(read.warnings());
			sets.add(set);
		}
		return sets;
	}
}
