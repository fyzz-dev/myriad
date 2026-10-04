package dev.myriad.api.service;

import net.minecraft.core.BlockPos;

import java.util.concurrent.CompletableFuture;

/**
 * Shared block breaking for Nuker, tunnellers, speed mine, auto city and anything else that breaks blocks. Modules
 * ask for a position to be broken and get told when the server has broken it; the service mines one block at a time
 * (two with {@link Options#doubleBreak}) in priority order, breaks instant blocks in batches, picks and holds the
 * fastest tool, rotates if asked, shows the crack, and keeps placements and breaks from overlapping. Breaking runs
 * on its own packets, so it doesn't fight the player's own mining or the attack key.
 *
 * <pre>{@code
 * for (BlockPos pos : Positions.sphere(eyes, 4.5)) {
 *     if (shouldMine(pos)) Myriad.breaking().breakBlock(this, pos, 0, Breaking.Options.DEFAULT);
 * }
 * // Asking again for a position already queued returns the same attempt, so this can run every tick.
 * }</pre>
 *
 * A module's breaks are cancelled when it's disabled.
 */
public interface Breaking {
	enum Mode {
		/**
		 * Mines like holding the attack button: the tool held the whole time, a swing every tick, vanilla's full time,
		 * one instant break per tick and vanilla's pause between blocks.
		 */
		VANILLA,
		/**
		 * Mines with its own packets, your hand free: the tool is swapped in only to start and finish, swings are
		 * sent only then, and instant breaks go as fast as the packet budget allows. It still takes vanilla's full time and
		 * pause between blocks, which Grim (2b2t) insists on. A block put back where the last one was mined is broken
		 * again with a single packet once enough time has passed for it (rebreak).
		 */
		PACKET,
		/**
		 * Like {@link #PACKET}, but finishes the moment the vanilla server would accept it (70% of the time), with no
		 * pause between blocks and optional {@link Options#doubleBreak}. For servers that don't check break times; Grim
		 * refuses these finishes and the blocks come back.
		 */
		FAST,
		/**
		 * {@link #FAST} for Grim on servers running ViaVersion, such as 2b2t (the trick Lambda uses there). With each
		 * start it also sends a start for the same spot far above the world: the server ignores it as out of reach,
		 * but Grim takes it as the block being broken, and air breaks instantly, so the early finish passes its check.
		 * Breaks take at least 7 ticks, and just before finishing a burst of those starts clears Grim's record of
		 * starting too soon after the last finish, so the next block can begin at once. Experimental: it relies on how
		 * the server's Grim treats those packets, and can stop working when it's updated.
		 */
		FAST_GRIM
	}

	/**
	 * @param mode        how blocks are mined; see {@link Mode}
	 * @param rotate      face the block server-side when starting and finishing (servers that check break direction)
	 * @param swing       show the hand swinging while mining. Each break packet is sent with a swing either way, as
	 *                    vanilla does (Grim checks for it); this only decides whether you see it
	 * @param range       maximum distance from the eyes to the nearest point of the block, as the server and Grim
	 *                    measure (see {@link dev.myriad.api.util.Reach#canReach(BlockPos, double)})
	 * @param autoTool    mine with the fastest tool you have, held server-side only (the visible slot stays). One in
	 *                    the inventory is moved into the hotbar first, while you stand still (Grim refuses inventory
	 *                    clicks while you move); until then the best in the hotbar is used
	 * @param doubleBreak {@link Mode#FAST} and {@link Mode#FAST_GRIM} only: mine a second block while the server
	 *                    finishes the first on its own
	 */
	record Options(Mode mode, boolean rotate, boolean swing, double range, boolean autoTool, boolean doubleBreak) {
		public static final Options DEFAULT = new Options(Mode.VANILLA, false, true, 4.5, true, false);
		public static final Options STRICT = new Options(Mode.VANILLA, true, true, 4.5, true, false);
		/** Packet mining, Grim-safe: hand free, vanilla's time. */
		public static final Options PACKET = new Options(Mode.PACKET, false, true, 4.5, true, false);
		/** For servers that don't check break times. */
		public static final Options FAST = new Options(Mode.FAST, false, true, 4.5, true, true);
		/** Fast mining past Grim on servers with ViaVersion (2b2t); experimental. */
		public static final Options FAST_GRIM = new Options(Mode.FAST_GRIM, false, true, 4.5, true, true);
	}

	enum Check {
		OK,
		/** Not in a world. */
		UNAVAILABLE,
		/** Air, a fluid or anything else with nothing to break. */
		NOTHING_THERE,
		/** Bedrock and the like, or nothing in reach can break it. */
		UNBREAKABLE,
		/** Farther from the eyes than the range allows. */
		OUT_OF_RANGE,
		/** A placement there is still waiting for the server. */
		PENDING;

		public boolean ok() {
			return this == OK;
		}
	}

	/**
	 * One break. {@link #result()} completes with true once the server has broken the block (or it was already gone),
	 * and false if it refused, the block went out of range, or the break was cancelled. Completes on the render thread.
	 */
	record Attempt(BlockPos pos, Check check, CompletableFuture<Boolean> result) {
		public boolean accepted() {
			return check == Check.OK;
		}

		public static Attempt refused(BlockPos pos, Check check) {
			return new Attempt(pos, check, CompletableFuture.completedFuture(false));
		}
	}

	/** Whether {@code pos} could be broken now, or why not. */
	Check check(BlockPos pos, Options options);

	/**
	 * Queues {@code pos} to be broken. Higher {@code priority} goes first, then first come first served. Asking again
	 * for a position that's already queued returns its attempt (raising its priority if yours is higher).
	 */
	Attempt breakBlock(Object owner, BlockPos pos, int priority, Options options);

	/** Stops and forgets every break {@code owner} asked for. */
	void cancel(Object owner);

	/** Stops the break at {@code pos}, whoever asked for it. */
	void cancel(BlockPos pos);

	/** True while a break at {@code pos} is queued, being mined, or waiting for the server. */
	boolean isPending(BlockPos pos);

	/** How far mining {@code pos} has got, 0 to 1 (1 = the finish has been sent); 0 if it isn't being mined. */
	float progress(BlockPos pos);

	/** Whether a block at {@code pos} can be broken now by packet mining's rebreak (a single finish packet). */
	boolean canRebreak(BlockPos pos);
}
