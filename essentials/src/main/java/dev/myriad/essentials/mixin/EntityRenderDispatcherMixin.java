package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private boolean essentials$hideEntities(boolean original, Entity entity) {
		return original && !NoRender.hides(entity);
	}

	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
	private void essentials$noBurning(MatrixStack matrices, VertexConsumerProvider vertexConsumers, EntityRenderState state, Quaternionf rotation, CallbackInfo ci) {
		if (NoRender.hides(n -> n.burning)) ci.cancel();
	}
}
