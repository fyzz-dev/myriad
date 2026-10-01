package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import dev.myriad.essentials.modules.player.AirPlace;
import dev.myriad.essentials.modules.render.Chams;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	/** Air Place already used the item this tick; don't let vanilla place a second block. */
	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void essentials$airPlace(CallbackInfo ci) {
		if (AirPlace.shouldCancelVanillaUse()) ci.cancel();
	}

	/** Chams glow uses the vanilla glowing outline, which sees through walls. */
	@Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
	private void essentials$chamsGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Modules.active(Chams.class) instanceof Chams chams && chams.glowsFor(entity)) cir.setReturnValue(true);
	}
}
