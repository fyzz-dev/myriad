package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.myriad.essentials.modules.movement.ElytraFly;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Elytra Fly's Silent mode: the local player's flight physics use the spoofed rotation. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	private boolean essentials$spoofs() {
		return (Object) this == Minecraft.getInstance().player && ElytraFly.spoofing();
	}

	@ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 essentials$glideLook(Vec3 original) {
		return essentials$spoofs() ? Vec3.directionFromRotation(ElytraFly.spoofPitch(), ElytraFly.spoofYaw()) : original;
	}

	@ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
	private float essentials$glidePitch(float original) {
		return essentials$spoofs() ? ElytraFly.spoofPitch() : original;
	}

	@ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
	private float essentials$jumpYaw(float original) {
		return essentials$spoofs() ? ElytraFly.spoofYaw() : original;
	}
}
