package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Velocity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {
	private boolean essentials$isLocal() {
		return (Object) this == Minecraft.getInstance().player;
	}

	@ModifyReturnValue(method = "isPushedByFluid", at = @At("RETURN"))
	private boolean essentials$noLiquidPush(boolean original) {
		return original && !(essentials$isLocal() && Velocity.cancelsLiquidPush());
	}

	@ModifyReturnValue(method = "onClimbable", at = @At("RETURN"))
	private boolean essentials$noClimbSlow(boolean original) {
		return original && !(essentials$isLocal() && NoSlow.skipClimb());
	}
}
