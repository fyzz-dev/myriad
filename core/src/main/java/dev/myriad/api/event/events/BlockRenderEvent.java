package dev.myriad.api.event.events;

import dev.myriad.api.event.Cancellable;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

/**
 * A block state is about to be drawn: cancel to leave it out of the chunk mesh (X-ray, hiding blocks). Asked once per
 * block when a chunk section is meshed, on the chunk builder threads, so handlers must be thread-safe and fast; the
 * answer is for every block of that state, not a position. Change what you hide, then make the world redraw
 * ({@code mc.levelRenderer.allChanged()}).
 */
public final class BlockRenderEvent extends Cancellable {
	private final BlockState state;

	@ApiStatus.Internal
	public BlockRenderEvent(BlockState state) {
		this.state = state;
	}

	public BlockState state() {
		return state;
	}
}
