package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Sprint;
import dev.myriad.essentials.modules.movement.Velocity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
	private boolean essentials$noSlowItems(boolean original) {
		return original && !NoSlow.skipItemSlow();
	}

	@ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldSlowDown()Z"))
	private boolean essentials$noSlowSneak(boolean original) {
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		return original && !NoSlow.skipSneakSlow(self.isSneaking(), self.isCrawling());
	}

	@ModifyReturnValue(method = "shouldStopSprinting", at = @At("RETURN"))
	private boolean essentials$keepSprinting(boolean original) {
		return original && !Sprint.keepSprinting();
	}

	@Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
	private void essentials$noBlockPush(double x, double z, CallbackInfo ci) {
		if (Velocity.cancelsBlockPush()) ci.cancel();
	}
}
