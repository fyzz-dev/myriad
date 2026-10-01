package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.render.Projection;
import dev.myriad.impl.render.WorldRenderQueue;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.myriad.api.event.events.CameraEvent;

/**
 * Runs Render3DEvent right after the world is drawn. WorldRenderer pushes and pops the camera rotation itself, so we
 * apply it once here, outside that push (doing it inside, e.g. from Fabric's LAST event, rotates twice).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void myriad$hand(Camera camera, float tickDelta, Matrix4f matrix, CallbackInfo ci) {
		if (Myriad.isReady() && Myriad.events().hasListeners(CameraEvent.Hand.class)
			&& Myriad.events().post(new CameraEvent.Hand()).isCancelled()) ci.cancel();
	}

	@WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
	private void myriad$afterWorld(WorldRenderer instance, ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
								   Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, Operation<Void> original) {
		original.call(instance, allocator, tickCounter, renderBlockOutline, camera, gameRenderer, positionMatrix, projectionMatrix);
		if (!Myriad.isReady()) return;
		Projection.update(positionMatrix, projectionMatrix, camera.getPos());
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(positionMatrix);
		var modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(positionMatrix);
		try {
			// Same tick delta the camera and entities were drawn with this frame.
			Myriad.events().post(new Render3DEvent(matrices, camera, tickCounter.getTickDelta(true)));
			WorldRenderQueue.flush(camera);
		} finally {
			modelView.popMatrix();
		}
	}
}
