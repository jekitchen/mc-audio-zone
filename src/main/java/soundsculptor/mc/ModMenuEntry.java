package soundsculptor.mc;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu's config button for Sound Sculptor (PRD FR-12.3): opens the same screen as the ; hotkey, returning to Mod Menu
 * when closed. Mod Menu loads this class only when it is installed; nothing else refers to it, so without Mod Menu the
 * mod works the same.
 */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SoundSculptorScreen::new;
	}
}
