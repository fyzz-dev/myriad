package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.CollisionShapeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	/** CollisionShapeEvent: the local player's view of block collisions. */
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void myriad$collisionShape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext ec) || ec.getEntity() == null || ec.getEntity() != Minecraft.getInstance().player) return;
		if (!Myriad.isReady() || !Myriad.events().hasListeners(CollisionShapeEvent.class)) return;
		CollisionShapeEvent event = Myriad.events().post(new CollisionShapeEvent((BlockState) (Object) this, pos, cir.getReturnValue()));
		if (event.shape() != cir.getReturnValue()) cir.setReturnValue(event.shape());
	}
}
