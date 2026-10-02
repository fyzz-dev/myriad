package dev.myriad.impl.mixin;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.events.BlockBreakEvent;
import dev.myriad.api.event.events.BlockBrokenEvent;
import dev.myriad.api.event.events.InteractEvent;
import dev.myriad.api.event.events.ItemUseEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
	@Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
	private void myriad$attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(BlockBreakEvent.Start.class)
			&& Myriad.events().post(new BlockBreakEvent.Start(pos, direction)).isCancelled()) cir.setReturnValue(false);
	}

	@Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
	private void myriad$interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(InteractEvent.Block.class)
			&& Myriad.events().post(new InteractEvent.Block(hand, hit)).isCancelled()) cir.setReturnValue(ActionResult.FAIL);
	}

	@Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
	private void myriad$interactItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(InteractEvent.Item.class)
			&& Myriad.events().post(new InteractEvent.Item(hand)).isCancelled()) cir.setReturnValue(ActionResult.FAIL);
	}

	@Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
	private void myriad$interactEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(InteractEvent.EntityTarget.class)
			&& Myriad.events().post(new InteractEvent.EntityTarget(hand, entity)).isCancelled()) cir.setReturnValue(ActionResult.FAIL);
	}

	@Unique
	private BlockState myriad$breaking;

	@Inject(method = "breakBlock", at = @At("HEAD"))
	private void myriad$beforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient mc = MinecraftClient.getInstance();
		myriad$breaking = mc.world == null ? null : mc.world.getBlockState(pos);
	}

	@Inject(method = "breakBlock", at = @At("RETURN"))
	private void myriad$afterBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockState state = myriad$breaking;
		myriad$breaking = null;
		if (cir.getReturnValueZ() && state != null && Myriad.isReady()) Myriad.events().post(new BlockBrokenEvent(pos.toImmutable(), state));
	}

	@Inject(method = "stopUsingItem", at = @At("HEAD"))
	private void myriad$stopUsing(PlayerEntity player, CallbackInfo ci) {
		if (Myriad.isReady() && player.isUsingItem() && Myriad.events().hasListeners(ItemUseEvent.Stopped.class)) {
			Myriad.events().post(new ItemUseEvent.Stopped(player.getActiveItem().copy()));
		}
	}

	@Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
	private void myriad$progress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (Myriad.isReady() && Myriad.events().hasListeners(BlockBreakEvent.Progress.class)
			&& Myriad.events().post(new BlockBreakEvent.Progress(pos, direction)).isCancelled()) cir.setReturnValue(false);
	}
}
