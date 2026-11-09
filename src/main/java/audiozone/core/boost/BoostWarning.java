package audiozone.core.boost;

/**
 * Hearing-safety rules for volumes above 100% (PRD FR-5.4): the slider's warning, the confirmation the first time a
 * profile goes above 100%, and the slider's maximum.
 */
public final class BoostWarning {
	/** Volumes above this show the warning icon and "May distort. Protect your hearing." */
	public static final int WARNING_ABOVE = 100;

	/** Slider text; the narrator reads it too. */
	public static final String WARNING_KEY = "audiozone.boost.warning";
	/** First-boost dialog that explains clipping and hearing safety. */
	public static final String CONFIRM_TITLE_KEY = "audiozone.boost.confirm.title";
	public static final String CONFIRM_BODY_KEY = "audiozone.boost.confirm.body";
	/** Shown when the audio driver ignores the raised gain cap, so boost is turned off (section 7.2). */
	public static final String UNSUPPORTED_KEY = "audiozone.boost.unsupported";

	private BoostWarning() {
	}

	public static boolean showWarning(int percent) {
		return percent > WARNING_ABOVE;
	}

	/**
	 * @param confirmedForProfile whether this profile has already accepted the boost dialog (saved with the profile)
	 */
	public static boolean needsConfirmation(int percent, boolean confirmedForProfile) {
		return showWarning(percent) && !confirmedForProfile;
	}

	/** 200 with boost available; 100 in the MVP or when the driver does not support it. */
	public static int sliderMax(boolean boostAvailable) {
		return boostAvailable ? 200 : 100;
	}
}
