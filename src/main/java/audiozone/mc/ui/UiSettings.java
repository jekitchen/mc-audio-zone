package audiozone.mc.ui;

/**
 * UI settings the kit reads. Until the Settings tab exists (later milestone) these are set by code, e.g. the demo
 * screen's "Reduced motion" toggle.
 */
public final class UiSettings {
	private UiSettings() {
	}

	/** PRD A-6: no list animations and no toast slide-in. */
	public static volatile boolean reducedMotion;
}
