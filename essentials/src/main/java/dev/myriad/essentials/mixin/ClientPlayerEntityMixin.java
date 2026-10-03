package dev.myriad.essentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.myriad.essentials.modules.movement.NoSlow;
import dev.myriad.essentials.modules.movement.Sprint;
import dev.myriad.essentials.modules.movement.Velocity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin {
	@ModifyExpressionValue(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
	private boolean essentials$noSlowItems(boolean original) {
		return original && !NoSlow.skipItemSlow();
	}

	@ModifyExpressionValue(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
	private boolean essentials$noSlowSneak(boolean original) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		return original && !NoSlow.skipSneakSlow(self.isShiftKeyDown(), self.isVisuallyCrawling());
	}

	@ModifyReturnValue(method = "shouldStopRunSprinting", at = @At("RETURN"))
	private boolean essentials$keepSprinting(boolean original) {
		return original && !Sprint.keepSprinting();
	}

	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void essentials$noBlockPush(double x, double z, CallbackInfo ci) {
		if (Velocity.cancelsBlockPush()) ci.cancel();
	}
}
