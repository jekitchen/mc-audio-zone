package audiozone.core.profile;

import audiozone.core.Message;

/**
 * The messages about profile files, imports and the Profiles tab (PRD FR-11.2 to FR-11.8), as translation keys
 * (PRD 9.3). A problem with a place in the file reads like "Zone 3, field radius: 900 is above the maximum 256.": the
 * place, the field as it is named in the file, and what is wrong, which translators can put in any order.
 */
final class ProfileText {
	// Places in a file.
	static final String PLACE_SETTINGS = "audiozone.profile.place.settings";
	static final String PLACE_RULE = "audiozone.profile.place.rule";
	static final String PLACE_ZONE = "audiozone.profile.place.zone";
	static final String PLACE_ZONE_RULE = "audiozone.profile.place.zone_rule";

	// A field's problem: "%1$s, field %2$s: %3$s." with a place, "Field %1$s: %2$s." at the top of the file.
	static final String FIELD = "audiozone.profile.problem.field";
	static final String TOP_FIELD = "audiozone.profile.problem.top_field";
	static final String ITEM_NOT_OBJECT = "audiozone.profile.problem.item_not_object";

	// What is wrong with a value.
	static final String MISSING = "audiozone.profile.problem.value.missing";
	static final String NOT_TEXT = "audiozone.profile.problem.value.not_text";
	static final String NOT_DATE = "audiozone.profile.problem.value.not_date";
	static final String NOT_BOOLEAN = "audiozone.profile.problem.value.not_boolean";
	static final String NOT_NUMBER = "audiozone.profile.problem.value.not_number";
	static final String ABOVE_MAX = "audiozone.profile.problem.value.above_max";
	static final String BELOW_MIN = "audiozone.profile.problem.value.below_min";
	static final String NOT_WHOLE = "audiozone.profile.problem.value.not_whole";
	static final String NOT_CHOICE = "audiozone.profile.problem.value.not_choice";
	static final String NOT_COLOR = "audiozone.profile.problem.value.not_color";
	static final String NOT_OBJECT = "audiozone.profile.problem.value.not_object";
	static final String NOT_LIST = "audiozone.profile.problem.value.not_list";
	static final String ID_USED = "audiozone.profile.problem.value.id_used";

	// The file as a whole.
	static final String NOT_JSON = "audiozone.profile.problem.not_json";
	static final String NOT_JSON_AT = "audiozone.profile.problem.not_json_at";
	static final String NOT_PROFILE = "audiozone.profile.problem.not_profile";
	static final String NOT_ALLOWED = "audiozone.profile.problem.not_allowed";
	static final String TOO_MANY_ZONES = "audiozone.profile.problem.too_many_zones";
	static final String TOO_MANY_RULES = "audiozone.profile.problem.too_many_rules";
	static final String TOO_LARGE = "audiozone.profile.problem.too_large";
	static final String UNREADABLE = "audiozone.profile.problem.unreadable";
	static final String NOT_UTF8 = "audiozone.profile.problem.not_utf8";
	static final String EMPTY = "audiozone.profile.problem.empty";

	// Schema versions (PRD 7.6).
	static final String NEWER = "audiozone.profile.problem.newer";
	static final String NEWER_IMPORT = "audiozone.profile.problem.newer_import";
	static final String BACKUP_FAILED = "audiozone.profile.problem.backup_failed";
	static final String NO_SCHEMA = "audiozone.profile.problem.no_schema";
	static final String UNKNOWN_SCHEMA = "audiozone.profile.problem.unknown_schema";

	// Unknown fields are ignored with a warning (FR-11.6).
	static final String UNKNOWN_FIELD = "audiozone.profile.warning.unknown_field";
	static final String UNKNOWN_FIELD_AT = "audiozone.profile.warning.unknown_field_at";

	// Import, copy and rename (FR-11.2, FR-11.3, FR-11.5).
	static final String IMPORT_TOO_MANY_ZONES = "audiozone.profile.problem.import_too_many_zones";
	static final String COPY_TOO_MANY_ZONES = "audiozone.profile.problem.copy_too_many_zones";
	static final String NAME_EMPTY = "audiozone.profile.problem.name_empty";
	static final String NAME_TOO_LONG = "audiozone.profile.problem.name_too_long";

	private ProfileText() {
	}

	static ProfileFormatException problem(String key, Object... args) {
		return new ProfileFormatException(Message.of(key, args));
	}
}
