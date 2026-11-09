package audiozone.core.profile;

/** One value on the profile settings page, for its reset-to-default button (PRD FR-12.6). */
public enum ProfileSetting {
	/** FR-1.4 "Show distance". */
	SHOW_DISTANCE,
	/** FR-7.6 the Cue setting new mute rules start with. */
	CUE_DEFAULT,
	/** FR-10.1 "Show zone outlines". */
	OUTLINES,
	/** FR-8.5 the edge zones use unless set to Hard or Fade themselves (mode and fade distance together). */
	DEFAULT_EDGE
}
