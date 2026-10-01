package dev.myriad.essentials.mixin;

import dev.myriad.api.module.Modules;
import dev.myriad.essentials.modules.render.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
	/** Xray: blocks that stay visible draw every face, since their neighbours are hidden. */
	@Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
	private static void essentials$xraySides(BlockState state, BlockState otherState, Direction side, CallbackInfoReturnable<Boolean> cir) {
		if (Modules.isActive(Xray.class) && Xray.visible(state) && !state.isAir()) cir.setReturnValue(true);
	}
}
