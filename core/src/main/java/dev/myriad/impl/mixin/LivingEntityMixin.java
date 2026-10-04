package dev.myriad.impl.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.ItemUseEvent;
import dev.myriad.impl.MyriadImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** The local player finished eating or drinking. */
	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void myriad$finishUsing(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self != Minecraft.getInstance().player || !Myriad.isReady() || self.getUseItem().isEmpty()) return;
		if (Myriad.events().hasListeners(ItemUseEvent.Finished.class)) Myriad.events().post(new ItemUseEvent.Finished(self.getUseItem().copy()));
	}

	/** Rotation move fix: a sprint jump pushes along the yaw being sent to the server. */
	@ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
	private float myriad$jumpYaw(float original) {
		if ((Object) this != Minecraft.getInstance().player || MyriadImpl.get() == null) return original;
		float yaw = MyriadImpl.get().rotationManager().movementYaw();
		return Float.isNaN(yaw) ? original : yaw;
	}
}
