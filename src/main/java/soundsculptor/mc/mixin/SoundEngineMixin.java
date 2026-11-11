package soundsculptor.mc.mixin;

import soundsculptor.mc.SoundHook;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound hooks (PRD 7.2). The same methods and calls exist in 26.2 and 26.3 (see docs/PORTING.md).
 *
 * <ul>
 *   <li><b>Start:</b> just before {@code play} calls {@code calculateVolume(float, SoundSource)}. By then the sound
 *   is resolved and the unknown/empty-sound early returns are behind us. A muted sound returns {@code NOT_STARTED}
 *   here: no channel is created. This is the same point where vanilla skips a sound whose volume is zero, so
 *   subtitles behave exactly as with a zero volume slider.</li>
 *   <li><b>Start volume:</b> the result of that same call is multiplied by the rule volume (FR-5.3). Vanilla then
 *   skips a sound whose volume became zero.</li>
 *   <li><b>Later volume:</b> {@code calculateVolume(SoundInstance)} is what vanilla calls to recalculate a playing
 *   sound (moving sounds every tick, all sounds of a category after a slider change); its result gets the same
 *   factor. {@code play} does not call it, so the factor is never applied twice.</li>
 * </ul>
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	private static final String PLAY =
			"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;";
	private static final String CALCULATE_VOLUME =
			"Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F";

	@Inject(method = PLAY, at = @At(value = "INVOKE", target = CALCULATE_VOLUME), cancellable = true)
	private void soundsculptor$onSoundStart(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (SoundHook.onSoundStart(instance)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@ModifyExpressionValue(method = PLAY, at = @At(value = "INVOKE", target = CALCULATE_VOLUME))
	private float soundsculptor$startVolume(float vanillaVolume) {
		return SoundHook.applyStartVolume(vanillaVolume);
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float soundsculptor$volume(float vanillaVolume, @Local(argsOnly = true) SoundInstance instance) {
		return SoundHook.applyVolume(instance, vanillaVolume);
	}
}
