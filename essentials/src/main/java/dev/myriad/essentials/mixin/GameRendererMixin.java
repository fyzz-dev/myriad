package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.render.AspectRatio;
import dev.myriad.essentials.modules.render.NoRender;
import dev.myriad.essentials.modules.render.Zoom;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void essentials$hurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (NoRender.hides(n -> n.hurtCam)) ci.cancel();
	}

	@Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
	private void essentials$totem(ItemStack stack, CallbackInfo ci) {
		if (stack.isOf(Items.TOTEM_OF_UNDYING) && NoRender.hides(n -> n.totem)) ci.cancel();
	}

	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float essentials$zoom(float fov) {
		return Zoom.apply(fov);
	}

	@ModifyArg(method = "getBasicProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"), index = 1)
	private float essentials$aspectRatio(float aspect) {
		AspectRatio m = Modules.active(AspectRatio.class);
		return m != null ? m.ratio.getFloat() : aspect;
	}
}
