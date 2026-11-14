package soundsculptor.core.profile;

import soundsculptor.core.Message;

/**
 * The messages about profile files, imports and the Profiles tab (PRD FR-11.2 to FR-11.8), as translation keys
 * (PRD 9.3). A problem with a place in the file reads like "Zone 3, field radius: 900 is above the maximum 256.": the
 * place, the field as it is named in the file, and what is wrong, which translators can put in any order.
 */
final class ProfileText {
	// Places in a file.
	static final String PLACE_SETTINGS = "soundsculptor.profile.place.settings";
	static final String PLACE_EVERYWHERE = "soundsculptor.profile.place.everywhere";
	static final String PLACE_ZONE = "soundsculptor.profile.place.zone";
	static final String PLACE_ATTACHMENT = "soundsculptor.profile.place.attachment";
	static final String PLACE_QUICK = "soundsculptor.profile.place.quick";

	// A field's problem: "%1$s, field %2$s: %3$s." with a place, "Field %1$s: %2$s." at the top of the file.
	static final String FIELD = "soundsculptor.profile.problem.field";
	static final String TOP_FIELD = "soundsculptor.profile.problem.top_field";
	static final String ITEM_NOT_OBJECT = "soundsculptor.profile.problem.item_not_object";

	// What is wrong with a value.
	static final String MISSING = "soundsculptor.profile.problem.value.missing";
	static final String NOT_TEXT = "soundsculptor.profile.problem.value.not_text";
	static final String NOT_DATE = "soundsculptor.profile.problem.value.not_date";
	static final String NOT_BOOLEAN = "soundsculptor.profile.problem.value.not_boolean";
	static final String NOT_NUMBER = "soundsculptor.profile.problem.value.not_number";
	static final String ABOVE_MAX = "soundsculptor.profile.problem.value.above_max";
	static final String BELOW_MIN = "soundsculptor.profile.problem.value.below_min";
	static final String NOT_CHOICE = "soundsculptor.profile.problem.value.not_choice";
	/** A shape or anchor type that was dropped before release (owner decision 2026-11-13). */
	static final String UNSUPPORTED_SHAPE = "soundsculptor.profile.problem.value.unsupported_shape";
	static final String UNSUPPORTED_ANCHOR = "soundsculptor.profile.problem.value.unsupported_anchor";
	static final String NOT_COLOR = "soundsculptor.profile.problem.value.not_color";
	static final String NOT_OBJECT = "soundsculptor.profile.problem.value.not_object";
	static final String NOT_LIST = "soundsculptor.profile.problem.value.not_list";
	static final String ID_USED = "soundsculptor.profile.problem.value.id_used";
	static final String QUICK_NOT_FIRST = "soundsculptor.profile.problem.value.quick_not_first";
	static final String QUICK_UNLISTED = "soundsculptor.profile.problem.value.quick_unlisted";
	static final String SET_TWICE = "soundsculptor.profile.problem.value.set_twice";
	/** The Sound Set file's ID messages, for a place's reference to a library set. */
	static final String BAD_SET_ID = "soundsculptor.sets.file.problem.value.bad_id";
	static final String RESERVED_SET_ID = "soundsculptor.sets.file.problem.value.reserved_id";
	static final String TOO_MANY_SETS = "soundsculptor.profile.problem.value.too_many_sets";

	// The file as a whole.
	static final String NOT_JSON = "soundsculptor.profile.problem.not_json";
	static final String NOT_JSON_AT = "soundsculptor.profile.problem.not_json_at";
	static final String NOT_PROFILE = "soundsculptor.profile.problem.not_profile";
	static final String NOT_ALLOWED = "soundsculptor.profile.problem.not_allowed";
	static final String TOO_MANY_ZONES = "soundsculptor.profile.problem.too_many_zones";
	static final String TOO_LARGE = "soundsculptor.profile.problem.too_large";
	static final String UNREADABLE = "soundsculptor.profile.problem.unreadable";
	static final String NOT_UTF8 = "soundsculptor.profile.problem.not_utf8";
	static final String EMPTY = "soundsculptor.profile.problem.empty";

	// Schema versions (PRD 7.6).
	static final String NEWER = "soundsculptor.profile.problem.newer";
	static final String NEWER_IMPORT = "soundsculptor.profile.problem.newer_import";
	static final String BACKUP_FAILED = "soundsculptor.profile.problem.backup_failed";
	static final String NO_SCHEMA = "soundsculptor.profile.problem.no_schema";
	static final String UNKNOWN_SCHEMA = "soundsculptor.profile.problem.unknown_schema";

	// Unknown fields are ignored with a warning (FR-11.6).
	static final String UNKNOWN_FIELD = "soundsculptor.profile.warning.unknown_field";
	static final String UNKNOWN_FIELD_AT = "soundsculptor.profile.warning.unknown_field_at";

	// Import and rename (FR-11.3, FR-11.5).
	static final String IMPORT_TOO_MANY_ZONES = "soundsculptor.profile.problem.import_too_many_zones";
	static final String NAME_EMPTY = "soundsculptor.profile.problem.name_empty";
	static final String NAME_TOO_LONG = "soundsculptor.profile.problem.name_too_long";

	private ProfileText() {
	}

	static ProfileFormatException problem(String key, Object... args) {
		return new ProfileFormatException(Message.of(key, args));
	}
}
