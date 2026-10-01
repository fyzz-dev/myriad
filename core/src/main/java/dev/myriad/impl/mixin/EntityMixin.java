package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.PlayerViewEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets modules move the local player's view ray (only the local player, and only when something listens). */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "getCameraPosVec", at = @At("RETURN"), cancellable = true)
	private void myriad$eyes(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		if ((Object) this != MinecraftClient.getInstance().player || !Myriad.isReady() || !Myriad.events().hasListeners(PlayerViewEvent.Eyes.class)) return;
		cir.setReturnValue(Myriad.events().post(new PlayerViewEvent.Eyes(cir.getReturnValue(), tickDelta)).value);
	}

	@Inject(method = "getRotationVec", at = @At("RETURN"), cancellable = true)
	private void myriad$look(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		if ((Object) this != MinecraftClient.getInstance().player || !Myriad.isReady() || !Myriad.events().hasListeners(PlayerViewEvent.Look.class)) return;
		cir.setReturnValue(Myriad.events().post(new PlayerViewEvent.Look(cir.getReturnValue(), tickDelta)).value);
	}
}
