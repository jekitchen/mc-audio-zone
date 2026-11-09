package audiozone;

import audiozone.mc.AudioZoneScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry point. Audio Zone is client-only (PRD section 1). */
public final class AudioZoneClient implements ClientModInitializer {
	public static final String MOD_ID = "audiozone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Default key for opening the Audio Zone screen (FR-12.2). K was the PRD's first choice but clashes
	 * with Iris ("Toggle shaders"); see the Decisions Log in docs/PRD.md.
	 */
	public static final int DEFAULT_OPEN_KEY = InputConstants.KEY_SEMICOLON;

	public static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	public static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		// The (name, key, category) constructor exists in 26.2 and 26.3; InputConstants.Type was renamed in 26.3.
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.audiozone.open", DEFAULT_OPEN_KEY, KEY_CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(AudioZoneClient::onEndTick);

		LOGGER.info("Audio Zone initialized");
	}

	private static void onEndTick(Minecraft minecraft) {
		while (openKey.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new AudioZoneScreen());
			}
		}
	}
}
