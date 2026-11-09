package audiozone.mc.mixin;

import audiozone.mc.SoundHook;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound start hook (PRD 7.2). Injects just before {@code play} calls {@code calculateVolume(float, SoundSource)}:
 * by then the sound is resolved and the unknown/empty-sound early returns are behind us. The same call exists in
 * 26.2 and 26.3 (see docs/PORTING.md). Observation only; vanilla behavior is unchanged.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@Inject(
			method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
	private void audiozone$onSoundStart(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		SoundHook.onSoundStart(instance);
	}
}
