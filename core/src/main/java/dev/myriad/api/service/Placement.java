package dev.myriad.api.service;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Shared block placement for modules like Scaffold, Surround or Auto Trap: finds a solid neighbour face to click,
 * optionally rotates for that exact placement, silently swaps to the block, and keeps a short per-position cooldown
 * so modules don't double-place while the server catches up.
 */
public interface Placement {
	/**
	 * @param rotate   face the clicked face server-side first (needed on servers that check placements)
	 * @param airPlace allow placing with no solid neighbour (only works on lenient servers)
	 * @param swing    swing the hand
	 * @param range    maximum distance from the eyes to the block centre
	 */
	record Options(boolean rotate, boolean airPlace, boolean swing, double range) {
		public static final Options DEFAULT = new Options(true, false, true, 4.5);
	}

	/** Whether a block could go at {@code pos} now: replaceable, in range, no entity in the way, something to click. */
	boolean canPlace(BlockPos pos, Options options);

	/**
	 * Places the block in {@code hotbarSlot} (0-8) at {@code pos}. The visible selected slot doesn't change.
	 * Returns true if the placement was sent.
	 */
	boolean place(BlockPos pos, int hotbarSlot, Options options);

	/**
	 * Places by clicking exactly {@code hit} (a face of an existing block), for when the face or the click position
	 * matters: stairs, slabs, logs and other blocks whose orientation follows where you click. Range, rotation, swing
	 * and cooldowns follow {@code options}; the target position is {@code hit}'s block offset by its face.
	 */
	boolean place(BlockHitResult hit, int hotbarSlot, Options options);

	/**
	 * Every neighbour face that could be clicked to put a block at {@code pos}, nearest to the eyes first. Empty
	 * when nothing solid touches it (only air placement would work).
	 */
	List<BlockHitResult> clickTargets(BlockPos pos);

	/** True for a short while after {@link #place} targeted {@code pos}. */
	boolean isOnCooldown(BlockPos pos);
}
