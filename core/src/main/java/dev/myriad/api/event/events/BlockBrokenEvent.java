package dev.myriad.api.event.events;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * You finished breaking a block (the client removed it; the server confirms with a {@link BlockUpdateEvent}).
 * {@link #state()} is what was there.
 */
public final class BlockBrokenEvent {
	private final BlockPos pos;
	private final BlockState state;

	public BlockBrokenEvent(BlockPos pos, BlockState state) {
		this.pos = pos;
		this.state = state;
	}

	public BlockPos pos() {
		return pos;
	}

	public BlockState state() {
		return state;
	}
}
