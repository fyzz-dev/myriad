package dev.myriad.api.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared block placement for modules like Scaffold, Surround or Auto Trap: finds a solid neighbour face to click,
 * optionally rotates for that exact placement, silently swaps to the block, and follows each placement until the
 * server has answered, so modules don't place twice while it catches up and learn whether the block stayed.
 *
 * <pre>{@code
 * Placement.Attempt a = Myriad.placement().place(pos, slot, strict.get() ? Placement.Options.STRICT : Placement.Options.DEFAULT);
 * if (!a.sent()) return; // a.check() says why: OUT_OF_RANGE, ENTITY_IN_WAY, PENDING, ...
 * a.result().thenAccept(placed -> { if (!placed) warn("The server refused the block at " + pos.toShortString()); });
 * }</pre>
 */
public interface Placement {
	/**
	 * @param rotate       face the clicked face server-side first (needed on servers that check placements). The block
	 *                     is placed at the start of the tick after the rotation went out in the movement packet,
	 *                     before that tick's movement, as vanilla clicks; placements that a single rotation can't
	 *                     cover wait their turn, one rotation per tick. While facing it, movement follows the sent yaw
	 *                     (rotation move fix), as Grim expects.
	 * @param airPlace     allow placing with no solid neighbour, through the off hand (swap, place, swing, swap back),
	 *                     clicking the face of the empty space that faces you. Any module or addon can ask for it; the
	 *                     Air Place module is just one user. This is how 2b2t clients air place there; current Grim
	 *                     builds (2.3.74 on the test server) refuse any placement against air (AirLiquidPlace)
	 * @param swing        swing the hand
	 * @param range        maximum distance from the eyes to the block centre
	 * @param visibleFaces only click faces that point towards you with nothing in the way; strict anti-cheats reject
	 *                     the rest. Visible faces are always preferred; this makes them required.
	 */
	record Options(boolean rotate, boolean airPlace, boolean swing, double range, boolean visibleFaces) {
		/** Lenient servers: no rotation, any face. */
		public static final Options DEFAULT = new Options(false, false, true, 4.5, false);
		/** Servers that check placements (Grim, NCP, 2b2t): rotate first and click only visible faces. */
		public static final Options STRICT = new Options(true, false, true, 4.5, true);
	}

	/** Whether a block could go somewhere now, and if not, why not. */
	enum Check {
		/** It can be placed. */
		OK,
		/** Not in a world, or the hotbar slot isn't 0-8. */
		UNAVAILABLE,
		/** Something that can't be replaced is already there. */
		OCCUPIED,
		/** Farther from the eyes than the range allows. */
		OUT_OF_RANGE,
		/** An entity (or you) stands in the way. */
		ENTITY_IN_WAY,
		/** Nothing solid touches the position to click against, and air placement is off. */
		NO_SUPPORT,
		/** There are faces to click against, but none you can see, and {@link Options#visibleFaces} requires one. */
		NOT_VISIBLE,
		/** A placement or break there is still waiting for its turn or the server's answer. */
		PENDING,
		/** The shared packet budget is spent for now ({@link PacketLimits}); try again next tick. */
		RATE_LIMITED;

		public boolean ok() {
			return this == OK;
		}
	}

	/**
	 * One placement. {@link #sent()} means it was accepted: sent now, or (with {@link Options#rotate}) as soon as the
	 * rotation is out. {@link #result()} completes with true once the server has kept a block at {@link #pos()}, and
	 * false if it refused it, never answered, or the rotation never got through. It completes on the render thread,
	 * and is already false when the placement wasn't accepted.
	 */
	record Attempt(BlockPos pos, Check check, CompletableFuture<Boolean> result) {
		public boolean sent() {
			return check == Check.OK;
		}

		public static Attempt refused(BlockPos pos, Check check) {
			return new Attempt(pos, check, CompletableFuture.completedFuture(false));
		}
	}

	/** Whether a block could go at {@code pos} now: {@link Check#OK}, or the reason it can't. */
	Check check(BlockPos pos, Options options);

	default boolean canPlace(BlockPos pos, Options options) {
		return check(pos, options).ok();
	}

	/** Places the block in {@code hotbarSlot} (0-8) at {@code pos}. The visible selected slot doesn't change. */
	default Attempt place(BlockPos pos, int hotbarSlot, Options options) {
		return place(null, pos, hotbarSlot, options);
	}

	/**
	 * Like {@link #place(BlockPos, int, Options)}, owned by {@code owner}: placements still waiting for their rotation
	 * are dropped by {@link #cancel(Object)} (a module's are when it's disabled).
	 */
	Attempt place(Object owner, BlockPos pos, int hotbarSlot, Options options);

	/**
	 * Places by clicking exactly {@code hit} (a face of an existing block), for when the face or the click position
	 * matters: stairs, slabs, logs and other blocks whose orientation follows where you click. Range, rotation, swing
	 * and pending checks follow {@code options}; the target position is {@code hit}'s block offset by its face.
	 */
	default Attempt place(BlockHitResult hit, int hotbarSlot, Options options) {
		return place(null, hit, hotbarSlot, options);
	}

	Attempt place(Object owner, BlockHitResult hit, int hotbarSlot, Options options);

	/** Drops {@code owner}'s placements that haven't been sent yet; their results complete with false. */
	void cancel(Object owner);

	/**
	 * Every neighbour face that could be clicked to put a block at {@code pos}: faces you can see first, then nearest
	 * to the eyes. Empty when nothing solid touches it (only air placement would work).
	 */
	List<BlockHitResult> clickTargets(BlockPos pos);

	/** True while a placement at {@code pos} is waiting to be sent or for the server's answer. */
	boolean isPending(BlockPos pos);
}
