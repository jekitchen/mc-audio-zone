package audiozone.mc.mixin;

import audiozone.mc.ZoneMarking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While marking a zone's corners (M8 review), left-click marks a corner instead of attacking: nothing is hit or broken,
 * also when the button is held. Only while marking; otherwise vanilla attacks as usual. Same methods in 26.2 and 26.3.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
	private void audiozone$markCornerInsteadOfAttack(CallbackInfoReturnable<Boolean> cir) {
		if (ZoneMarking.isActive()) {
			ZoneMarking.mark((Minecraft) (Object) this);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true)
	private void audiozone$noBreakingWhileMarking(boolean leftClickHeld, CallbackInfo ci) {
		if (ZoneMarking.isActive()) {
			ci.cancel();
		}
	}
}
