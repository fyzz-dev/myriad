package dev.myriad.essentials.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void essentials$hurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (NoRender.hides(n -> n.hurtCam)) ci.cancel();
	}

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void essentials$totem(ItemStack stack, CallbackInfo ci) {
		if (stack.is(Items.TOTEM_OF_UNDYING) && NoRender.hides(n -> n.totem)) ci.cancel();
	}
}
