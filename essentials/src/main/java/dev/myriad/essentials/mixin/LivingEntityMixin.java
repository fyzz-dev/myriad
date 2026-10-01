package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.render.Swing;
import dev.myriad.essentials.modules.movement.ElytraBounce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Elytra Bounce's Silent mode (the local player's flight physics use the spoofed rotation) and Swing speed. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	private boolean essentials$spoofs() {
		return (Object) this == MinecraftClient.getInstance().player && ElytraBounce.spoofing();
	}

	@ModifyExpressionValue(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
	private Vec3d essentials$glideLook(Vec3d original) {
		return essentials$spoofs() ? Vec3d.fromPolar(ElytraBounce.spoofPitch(), ElytraBounce.spoofYaw()) : original;
	}

	@ModifyExpressionValue(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
	private float essentials$glidePitch(float original) {
		return essentials$spoofs() ? ElytraBounce.spoofPitch() : original;
	}

	@ModifyExpressionValue(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
	private float essentials$jumpYaw(float original) {
		return essentials$spoofs() ? ElytraBounce.spoofYaw() : original;
	}

	@ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
	private int essentials$swingSpeed(int original) {
		int custom = Swing.swingDuration((LivingEntity) (Object) this);
		return custom > 0 ? custom : original;
	}
}
