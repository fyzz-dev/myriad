package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.EntityRenderEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	/** EntityRenderEvent.Visible: hiding entities. */
	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private boolean myriad$visible(boolean original, Entity entity) {
		if (!original || !Myriad.isReady() || !Myriad.events().hasListeners(EntityRenderEvent.Visible.class)) return original;
		return !Myriad.events().post(new EntityRenderEvent.Visible(entity)).isCancelled();
	}
}
