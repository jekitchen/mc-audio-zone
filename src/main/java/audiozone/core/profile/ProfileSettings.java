package audiozone.core.profile;

/**
 * Profile settings (PRD FR-11: cue defaults, outline mode, default edge, show distance). M4 has "show distance";
 * the others arrive with their features.
 *
 * @param showDistance FR-1.4: show the distance band in the recently played list (default on)
 */
public record ProfileSettings(boolean showDistance) {
	public static final ProfileSettings DEFAULTS = new ProfileSettings(true);

	public ProfileSettings withShowDistance(boolean value) {
		return new ProfileSettings(value);
	}
}
