package audiozone.mc.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The sound engine behind the sound manager, for the per-tick zone re-check (FR-8.8). Same field in 26.2 and 26.3. */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
	@Accessor("soundEngine")
	SoundEngine audiozone$soundEngine();
}
