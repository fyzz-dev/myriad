package dev.myriad.impl.mixin;

import dev.myriad.impl.render.EntityRenderStateAccess;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	/** Every render state is built here; remember the entity it was built from (see RenderStates). */
	@Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
	private void myriad$rememberEntity(T entity, float tickDelta, CallbackInfoReturnable<S> cir) {
		((EntityRenderStateAccess) cir.getReturnValue()).myriad$setEntity(entity);
	}
}
