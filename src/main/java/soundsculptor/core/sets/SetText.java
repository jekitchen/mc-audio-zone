package soundsculptor.core.sets;


/**
 * The messages about Sound Set files (PRD FR-11.6, FR-11.8), as translation keys (PRD 9.3). Values that are wrong in
 * the same way as in a profile file reuse the {@code soundsculptor.profile.problem.value.*} keys.
 */
final class SetText {
	// Places in a file: "Sound Set 'Farm animals', entry 3".
	static final String PLACE_SET = "soundsculptor.sets.file.place.set";
	static final String PLACE_ENTRY = "soundsculptor.sets.file.place.entry";
	static final String FIELD = "soundsculptor.sets.file.problem.field";

	// Fields, named as the player knows them rather than as they are spelled in the file.
	static final String F_ID = "soundsculptor.sets.file.field.id";
	static final String F_NAME = "soundsculptor.sets.file.field.name";
	static final String F_ENTRIES = "soundsculptor.sets.file.field.entries";
	static final String F_ENABLED = "soundsculptor.sets.file.field.enabled";
	static final String F_TARGET = "soundsculptor.sets.file.field.target";
	static final String F_MUTE = "soundsculptor.sets.file.field.mute";
	static final String F_LEVEL = "soundsculptor.sets.file.field.level";
	static final String F_LIMIT = "soundsculptor.sets.file.field.limit";
	static final String F_CUE = "soundsculptor.sets.file.field.cue";
	static final String F_LIMIT_COUNT = "soundsculptor.sets.file.field.limit_count";
	static final String F_LIMIT_WINDOW = "soundsculptor.sets.file.field.limit_window";
	static final String F_LIMIT_PER_SOURCE = "soundsculptor.sets.file.field.limit_per_source";

	// What is wrong with a value, beyond the profile ones.
	static final String BAD_ID = "soundsculptor.sets.file.problem.value.bad_id";
	static final String RESERVED_ID = "soundsculptor.sets.file.problem.value.reserved_id";
	static final String NOT_QUICK = "soundsculptor.sets.file.problem.value.not_quick";
	static final String ENTRY_ID_USED = "soundsculptor.sets.file.problem.value.entry_id_used";
	static final String EMPTY_TEXT = "soundsculptor.sets.file.problem.value.empty";

	// The file as a whole.
	static final String NOT_SET = "soundsculptor.sets.file.problem.not_set";
	static final String NEWER = "soundsculptor.sets.file.problem.newer";
	static final String NAME_TOO_LONG = "soundsculptor.sets.file.problem.name_too_long";
	static final String TOO_MANY_ENTRIES = "soundsculptor.sets.file.problem.too_many_entries";
	static final String ID_MISMATCH = "soundsculptor.sets.file.problem.id_mismatch";
	static final String NOT_ALLOWED_AT = "soundsculptor.sets.file.problem.not_allowed_at";

	// The folder.
	static final String FILE_PROBLEM = "soundsculptor.sets.store.problem";
	static final String FILE_UNREADABLE = "soundsculptor.sets.store.file_unreadable";
	static final String FOLDER_UNREADABLE = "soundsculptor.sets.store.folder_unreadable";
	static final String FOLDER_PARTLY_UNREADABLE = "soundsculptor.sets.store.folder_partly_unreadable";
	static final String TOO_MANY_SETS = "soundsculptor.sets.store.too_many_sets";

	// Shared with profile files.
	static final String MISSING = "soundsculptor.profile.problem.value.missing";
	static final String NOT_TEXT = "soundsculptor.profile.problem.value.not_text";
	static final String NOT_BOOLEAN = "soundsculptor.profile.problem.value.not_boolean";
	static final String NOT_NUMBER = "soundsculptor.profile.problem.value.not_number";
	static final String ABOVE_MAX = "soundsculptor.profile.problem.value.above_max";
	static final String BELOW_MIN = "soundsculptor.profile.problem.value.below_min";
	static final String NOT_WHOLE = "soundsculptor.profile.problem.value.not_whole";
	static final String NOT_OBJECT = "soundsculptor.profile.problem.value.not_object";
	static final String NOT_LIST = "soundsculptor.profile.problem.value.not_list";
	static final String ITEM_NOT_OBJECT = "soundsculptor.profile.problem.item_not_object";
	static final String NOT_JSON = "soundsculptor.profile.problem.not_json";
	static final String NOT_JSON_AT = "soundsculptor.profile.problem.not_json_at";
	static final String TOO_LARGE = "soundsculptor.profile.problem.too_large";
	static final String UNREADABLE = "soundsculptor.profile.problem.unreadable";
	static final String EMPTY = "soundsculptor.profile.problem.empty";
	static final String NO_SCHEMA = "soundsculptor.profile.problem.no_schema";
	static final String UNKNOWN_SCHEMA = "soundsculptor.profile.problem.unknown_schema";
	static final String UNKNOWN_FIELD_AT = "soundsculptor.profile.warning.unknown_field_at";

	private SetText() {
	}
}
