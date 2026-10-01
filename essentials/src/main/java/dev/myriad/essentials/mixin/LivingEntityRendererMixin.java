package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.render.OverlayTexture;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.myriad.essentials.modules.render.Chams;
import dev.myriad.essentials.util.ChamsLayers;
import dev.myriad.essentials.util.EntityHolder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void essentials$remember(T entity, S state, float tickDelta, CallbackInfo ci) {
		((EntityHolder) state).essentials$setEntity(entity);
	}

	/** Chams: draw the model again as a flat colour (optionally through walls), with or without the original. */
	@WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
	private void essentials$chams(EntityModel<?> model, MatrixStack matrices, VertexConsumer consumer, int light, int overlay, int color, Operation<Void> original,
								  @Local(argsOnly = true) S state, @Local(argsOnly = true) VertexConsumerProvider provider) {
		Chams chams = Modules.active(Chams.class);
		if (chams == null || !chams.appliesTo(((EntityHolder) state).essentials$getEntity())) {
			original.call(model, matrices, consumer, light, overlay, color);
			return;
		}
		if (chams.keepTexture()) original.call(model, matrices, consumer, light, overlay, color);
		if (chams.fill()) {
			VertexConsumer fill = provider.getBuffer(chams.throughWalls() ? ChamsLayers.FILL_THROUGH_WALLS : ChamsLayers.FILL);
			original.call(model, matrices, fill, light, overlay, chams.fillColor());
		}
	}

	/** No Render: drop the red hurt tint, keep the white creeper flash. */
	@ModifyReturnValue(method = "getOverlay", at = @At("RETURN"))
	private static int essentials$noDamageTint(int original, LivingEntityRenderState state, float whiteOverlayProgress) {
		if (!NoRender.hides(n -> n.damageTint)) return original;
		return OverlayTexture.packUv(OverlayTexture.getU(whiteOverlayProgress), OverlayTexture.getV(false));
	}
}
