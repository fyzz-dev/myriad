package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.CameraEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import dev.myriad.impl.render.WorldRenderQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void myriad$hand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(CameraEvent.Hand.class)
			&& Myriad.events().post(new CameraEvent.Hand()).isCancelled()) ci.cancel();
	}

	/** Vanilla multiplies the projection by the view bob; tracers undo it to start at the crosshair. */
	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", ordinal = 0))
	private Matrix4fc myriad$viewBob(Matrix4fc bob) {
		WorldRenderQueue.INSTANCE.viewBob(bob);
		return bob;
	}
}
