package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
	@Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
	private static void essentials$fire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (NoRender.hides(n -> n.fire)) ci.cancel();
	}

	@Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
	private static void essentials$inWall(net.minecraft.client.texture.Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (NoRender.hides(n -> n.blockOverlay)) ci.cancel();
	}

	@Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
	private static void essentials$underwater(net.minecraft.client.MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (NoRender.hides(n -> n.liquidOverlay)) ci.cancel();
	}
}
