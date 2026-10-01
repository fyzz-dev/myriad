package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.BlockBreakEvent;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
	@Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
	private void myriad$attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(BlockBreakEvent.Start.class)
			&& Myriad.events().post(new BlockBreakEvent.Start(pos, direction)).isCancelled()) cir.setReturnValue(false);
	}

	@Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
	private void myriad$progress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(BlockBreakEvent.Progress.class)
			&& Myriad.events().post(new BlockBreakEvent.Progress(pos, direction)).isCancelled()) cir.setReturnValue(false);
	}
}
