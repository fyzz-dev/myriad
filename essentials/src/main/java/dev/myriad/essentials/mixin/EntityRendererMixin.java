package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.Nametags;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	/** Nametags draws its own player tags. */
	@Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
	private void essentials$hideLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof PlayerEntity && Nametags.hidesVanilla()) cir.setReturnValue(false);
	}
}
