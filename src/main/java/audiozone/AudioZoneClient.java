package audiozone;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry point. Audio Zone is client-only (PRD section 1). */
public final class AudioZoneClient implements ClientModInitializer {
	public static final String MOD_ID = "audiozone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Audio Zone initialized");
	}
}
