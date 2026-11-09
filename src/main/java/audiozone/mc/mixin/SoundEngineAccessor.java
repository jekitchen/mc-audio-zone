package audiozone.mc.mixin;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The playing sounds and their channels, for the per-tick zone re-check (FR-8.8). Vanilla's own map; read on the game
 * thread only. Same field in 26.2 and 26.3.
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
	@Accessor("instanceToChannel")
	Map<SoundInstance, ChannelAccess.ChannelHandle> audiozone$instanceToChannel();
}
