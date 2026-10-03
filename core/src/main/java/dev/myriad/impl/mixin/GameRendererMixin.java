package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.CameraEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void myriad$hand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(CameraEvent.Hand.class)
			&& Myriad.events().post(new CameraEvent.Hand()).isCancelled()) ci.cancel();
	}
}
