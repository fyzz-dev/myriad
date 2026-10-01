package dev.myriad.essentials.mixin;

import dev.myriad.essentials.modules.render.NoRender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
	/** No Render: hidden blocks have no model, which every chunk mesher (vanilla, Indigo) respects. */
	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void essentials$hideBlocks(CallbackInfoReturnable<BlockRenderType> cir) {
		if (NoRender.hides((BlockState) (Object) this)) cir.setReturnValue(BlockRenderType.INVISIBLE);
	}

	/** No Render: no random model offsets (they reveal block positions). */
	@Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
	private void essentials$noOffset(BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
		if (NoRender.hides(n -> n.textureRotations)) cir.setReturnValue(Vec3d.ZERO);
	}
}
