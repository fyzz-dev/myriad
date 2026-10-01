package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.ViewClip;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	/** View Clip: the third-person distance (vanilla passes 4 times the entity's scale). */
	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
	private float essentials$distance(float desired) {
		return ViewClip.distance(desired);
	}

	@Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
	private void essentials$noClip(float desired, CallbackInfoReturnable<Float> cir) {
		if (ViewClip.noClip()) cir.setReturnValue(desired);
	}
}
