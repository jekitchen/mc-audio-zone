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
 * 26.2 and 26.3 (see docs/PORTING.md).
 *
 * <p>A muted sound returns {@code NOT_STARTED} here: no channel is created. This is the same point where vanilla
 * skips a sound whose volume is zero, so subtitles behave exactly as with a zero volume slider.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@Inject(
			method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"),
			cancellable = true)
	private void audiozone$onSoundStart(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (SoundHook.onSoundStart(instance)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}
}
