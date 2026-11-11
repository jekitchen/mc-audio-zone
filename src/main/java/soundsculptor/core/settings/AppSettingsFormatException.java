package soundsculptor.core.settings;

import java.io.IOException;

/** The settings file's text is not a settings object at all (not JSON, or not a JSON object). */
public final class AppSettingsFormatException extends IOException {
	public AppSettingsFormatException(String message) {
		super(message);
	}
}
