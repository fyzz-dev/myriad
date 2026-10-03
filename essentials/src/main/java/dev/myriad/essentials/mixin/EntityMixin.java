package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.ElytraFly;
import dev.myriad.essentials.modules.movement.Velocity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Velocity No Push: other entities don't shove the local player. */
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void essentials$noEntityPush(Entity entity, CallbackInfo ci) {
		Object self = this;
		if (self == Minecraft.getInstance().player && Velocity.cancelsEntityPush()) ci.cancel();
	}

	/** Elytra Fly Silent: walking between bounces follows the lane, not the camera. */
	@ModifyExpressionValue(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
	private float essentials$moveYaw(float original) {
		Object self = this;
		return self == Minecraft.getInstance().player && ElytraFly.spoofing() ? ElytraFly.spoofYaw() : original;
	}

	/** Elytra Fly Packet: keep the standing pose (and hitbox) while gliding. */
	@ModifyReturnValue(method = "getPose", at = @At("RETURN"))
	private Pose essentials$standingPose(Pose original) {
		Object self = this;
		return original == Pose.FALL_FLYING && self == Minecraft.getInstance().player && ElytraFly.holdStandingPose() ? Pose.STANDING : original;
	}
}
