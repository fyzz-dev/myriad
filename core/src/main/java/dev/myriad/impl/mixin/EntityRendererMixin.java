package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.EntityRenderEvent;
import dev.myriad.impl.render.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	/** Every render state is built here; remember the entity it was built from (see RenderStates). */
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void myriad$rememberEntity(T entity, float tickDelta, CallbackInfoReturnable<S> cir) {
		((EntityRenderStateAccess) cir.getReturnValue()).myriad$setEntity(entity);
	}

	/** EntityRenderEvent.Nametag: modules that draw their own tags hide vanilla's. */
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void myriad$nametag(T entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(EntityRenderEvent.Nametag.class)
			&& Myriad.events().post(new EntityRenderEvent.Nametag(entity)).isCancelled()) cir.setReturnValue(false);
	}
}
