package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	/** Nametags draws its own tags for players, mobs and items. */
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void essentials$hideLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
		if (Nametags.hidesVanilla(entity)) cir.setReturnValue(false);
	}
}
