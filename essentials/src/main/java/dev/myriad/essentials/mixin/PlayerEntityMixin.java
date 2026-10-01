package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Velocity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	private boolean essentials$isLocal() {
		return (Object) this == MinecraftClient.getInstance().player;
	}

	@ModifyReturnValue(method = "isPushedByFluids", at = @At("RETURN"))
	private boolean essentials$noLiquidPush(boolean original) {
		return original && !(essentials$isLocal() && Velocity.cancelsLiquidPush());
	}

	@ModifyReturnValue(method = "isClimbing", at = @At("RETURN"))
	private boolean essentials$noClimbSlow(boolean original) {
		return original && !(essentials$isLocal() && NoSlow.skipClimb());
	}
}
