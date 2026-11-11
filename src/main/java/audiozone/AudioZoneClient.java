package audiozone;

import audiozone.mc.AppSettingsManager;
import audiozone.mc.AudioZoneScreen;
import audiozone.mc.AudioZoneSettings;
import audiozone.mc.BypassLabel;
import audiozone.mc.Notices;
import audiozone.mc.PlayingChannels;
import audiozone.mc.ProfileManager;
import audiozone.mc.RecentLog;
import audiozone.mc.SoundLogger;
import audiozone.mc.WorldSession;
import audiozone.mc.ZoneMarking;
import audiozone.mc.ZoneOutlines;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
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

	/** Default key for Bypass (FR-13.1); unused by vanilla and by the popular mods checked at M0. */
	public static final int DEFAULT_BYPASS_KEY = InputConstants.KEY_F10;

	public static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	public static KeyMapping openKey;
	public static KeyMapping bypassKey;
	/** FR-10.1: optional hotkey for "Show zone outlines", unbound by default. */
	public static KeyMapping outlinesKey;
	/** Cancels marking a zone's corners (Backspace by default; only acts while marking). */
	public static KeyMapping cancelMarkingKey;

	@Override
	public void onInitializeClient() {
		// The (name, key, category) constructor exists in 26.2 and 26.3; InputConstants.Type was renamed in 26.3.
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.audiozone.open", DEFAULT_OPEN_KEY, KEY_CATEGORY));
		bypassKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.audiozone.bypass", DEFAULT_BYPASS_KEY, KEY_CATEGORY));
		cancelMarkingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.audiozone.cancel_marking", InputConstants.KEY_BACKSPACE, KEY_CATEGORY));
		outlinesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.audiozone.outlines", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY));

		Notices.register();
		AppSettingsManager.init(FabricLoader.getInstance().getConfigDir());
		ProfileManager.init(FabricLoader.getInstance().getConfigDir());
		WorldSession.register();
		ZoneOutlines.register();
		ZoneMarking.register();
		ClientTickEvents.END_CLIENT_TICK.register(AudioZoneClient::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ProfileManager.flushSaves();
			AppSettingsManager.flushSaves();
		});
		SoundLogger.registerIfEnabled();
		RecentLog.register();
		BypassLabel.register();

		LOGGER.info("Audio Zone initialized");
	}

	private static void onEndTick(Minecraft minecraft) {
		ZoneMarking.tick(minecraft);
		while (openKey.consumeClick()) {
			if (ZoneMarking.isActive()) {
				// Marking a zone's corners: the key marks the next corner instead of opening the screen.
				ZoneMarking.mark(minecraft);
			} else if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new AudioZoneScreen());
			}
		}
		while (bypassKey.consumeClick()) {
			ProfileManager.toggleBypass();
		}
		while (outlinesKey.consumeClick()) {
			AudioZoneSettings.cycleOutlineMode();
		}
		// FR-8.8: re-decide playing sounds every tick (zones, moving sources, Bypass).
		PlayingChannels.get().tick(minecraft);
	}
}
