package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	/** Nametags draws its own player tags. */
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void essentials$hideLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player && Nametags.hidesVanilla()) cir.setReturnValue(false);
	}
}
