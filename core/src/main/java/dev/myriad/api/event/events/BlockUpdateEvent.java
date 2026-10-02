package dev.myriad.api.event.events;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * The server changed a block: single updates and multi-block updates alike, including confirmations of blocks you
 * placed or broke. Posted on the render thread just before the change is applied, so the world still holds
 * {@link #oldState()}.
 */
public final class BlockUpdateEvent {
	private final BlockPos pos;
	private final BlockState oldState, newState;

	public BlockUpdateEvent(BlockPos pos, BlockState oldState, BlockState newState) {
		this.pos = pos;
		this.oldState = oldState;
		this.newState = newState;
	}

	public BlockPos pos() {
		return pos;
	}

	public BlockState oldState() {
		return oldState;
	}

	public BlockState newState() {
		return newState;
	}
}
