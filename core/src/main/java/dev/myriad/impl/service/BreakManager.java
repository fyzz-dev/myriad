package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.Breaking;
import dev.myriad.api.service.PacketLimits;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.util.BlockInfo;
import dev.myriad.api.util.Mining;
import dev.myriad.api.util.Packets;
import dev.myriad.api.util.Reach;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Breaks blocks with its own action packets, mirroring how the server times a break:
 *
 * <ul>
 *   <li>A start that would finish the block at once (the held tool's progress per tick is 1 or more) breaks it on the
 *   spot. Instant breaks are batched by tool, so a whole tick's worth needs one swap.</li>
 *   <li>Otherwise the server remembers when the break started and, on the finish packet, accepts it once
 *   {@code progress per tick * (ticks since start + 1)} reaches 0.7, using the tool held at that moment.</li>
 *   <li>Grim (2b2t) is stricter: it expects vanilla's full time, {@code ceil(1 / progress per tick)} ticks from the
 *   start, judged by the tool held at the start (or a better one seen on later swings), and about 300 ms between
 *   finishing one block and starting the next. Early finishes build up an allowance of about a second; past it, the
 *   finish is refused and the block comes back. So vanilla and packet modes start with the tool in hand, finish at
 *   full time and keep that gap; only {@link Mode#FAST} finishes at 0.7.</li>
 *   <li>A finish sent too early isn't wasted on the server: it keeps mining that block by itself until it reaches 1.
 *   Fast mode's double break uses that to start a second block while the first finishes server-side.</li>
 *   <li>The server keeps the start of the last block it was told to start until another start arrives, so a finish
 *   alone breaks whatever appears there again (rebreak), once enough time has passed for that block.</li>
 * </ul>
 *
 * Everything happens at the start of a tick, before the movement packet, where vanilla sends break packets; with
 * {@code rotate} the server has been told where you look in the tick before. Results come from the server's acknowledgements (see
 * {@link dev.myriad.impl.network.BlockAckTracker}).
 */
public final class BreakManager implements Breaking {
	static final float PACKET_THRESHOLD = 0.7f;
	/**
	 * Ticks between finishing one block and starting another: vanilla's own gap (its 5-tick delay, then the next tick),
	 * which Grim checks for (about 300 ms).
	 */
	private static final int FINISH_GAP = 6;
	/** {@link Mode#FAST_GRIM}: how far above the block the decoy starts go (out of the world, so the server ignores them). */
	private static final int DECOY_HEIGHT = 955;
	/** {@link Mode#FAST_GRIM}: decoy starts sent before finishing; each one shrinks Grim's start-too-soon record by 10%. */
	private static final int DECOY_BURST = 22;
	/** {@link Mode#FAST_GRIM}: a break lasts more than this many ticks, so the burst counts as well clear of the last finish. */
	private static final int GRIM_MIN_TICKS = 6;
	/** How long to wait for the server's word on a finished break, beyond when it's due. */
	private static final int GRACE_TICKS = 40;
	/** Breaker ids for the crack overlay, apart from real entities (and the player's own mining). */
	private static final int CRACK_PRIMARY = Integer.MIN_VALUE + 17, CRACK_DELAYED = Integer.MIN_VALUE + 18;

	private enum Phase {
		/** Waiting its turn. */
		QUEUED,
		/** The server is mining it as its current break. */
		MINING,
		/** Finished early; the server finishes it by itself (double break). */
		DELAYED,
		/** The finishing packet is out; waiting for the server. */
		AWAITING
	}

	private final class Job {
		final Object owner;
		final BlockPos pos;
		final Options options;
		final Block block;
		final long order;
		final CompletableFuture<Boolean> result = new CompletableFuture<>();
		final Attempt attempt;
		int priority;
		Phase phase = Phase.QUEUED;
		Direction face = Direction.UP;
		Vec3 aim;
		int started, due, deadline, lastSent;
		/** When the start went out, in wall-clock ms: Grim times breaks in real time. */
		long startedMs;
		float progress;
		/** The best progress per tick seen since the start: what Grim times the break by. */
		float maxRate;
		/** {@link Mode#FAST_GRIM}: the decoy burst for this break has been sent. */
		boolean decoyed;
		boolean acked, finishLater;

		Job(Object owner, BlockPos pos, int priority, Options options, Block block) {
			this.owner = owner;
			this.pos = pos;
			this.priority = priority;
			this.options = options;
			this.block = block;
			this.order = counter++;
			this.attempt = new Attempt(pos, Check.OK, result);
		}

		/** Mined with its own packets (hand free), rather than as if holding the attack button. */
		boolean packet() {
			return options.mode() != Mode.VANILLA;
		}

		/** Finished at 70% (servers that don't check break times, or with Grim's decoy starts). */
		boolean fast() {
			return options.mode() == Mode.FAST || grim();
		}

		/** Fast, with decoy starts that hide the speed from Grim. */
		boolean grim() {
			return options.mode() == Mode.FAST_GRIM;
		}

		/** Waits vanilla's gap after a finish before any start (everything but plain fast mode). */
		boolean keepsGap() {
			return options.mode() != Mode.FAST;
		}
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final List<Job> jobs = new ArrayList<>();
	private Job primary, delayed;
	/** The server's remembered break: where it was last told to start, and when (client ticks). */
	private BlockPos rebreakPos;
	private int rebreakStart;
	private boolean rebreakReady, holding;
	/** The last finish came after a decoy burst, so Grim lets the next break start without the gap. */
	private boolean gapCleared;
	/**
	 * The block the server is finishing by itself, if any. It has room for one: while it's taken, an early finish is
	 * ignored, so double break waits for it to clear (the block there changes) before using it again.
	 */
	private BlockPos serverDelayedPos;
	private Block serverDelayedBlock;
	private long counter;
	private int tick, finishCooldown, lastStop = Integer.MIN_VALUE / 2, swungTick = -1;
	/**
	 * When the last finish went out, in wall-clock ms. Grim measures breaks and the pause between them in real time, so
	 * tick counts alone aren't enough: a client that falls behind runs several ticks back to back.
	 */
	private long lastStopMs;

	// ---- API ----------------------------------------------------------------------------------------------------

	@Override
	public Check check(BlockPos pos, Options o) {
		if (mc.player == null || mc.level == null) return Check.UNAVAILABLE;
		BlockState state = mc.level.getBlockState(pos);
		if (nothingThere(state)) return Check.NOTHING_THERE;
		if (BlockInfo.isUnbreakable(state) || Mining.delta(state, pos, toolSlot(o, state, pos)) <= 0) return Check.UNBREAKABLE;
		if (!inRange(pos, o)) return Check.OUT_OF_RANGE;
		if (Myriad.placement().isPending(pos)) return Check.PENDING;
		return Check.OK;
	}

	@Override
	public Attempt breakBlock(Object owner, BlockPos pos, int priority, Options options) {
		Job existing = find(pos);
		if (existing != null) {
			if (priority > existing.priority) {
				existing.priority = priority;
				sort();
			}
			return existing.attempt;
		}
		Check check = check(pos, options);
		if (!check.ok()) return Attempt.refused(pos, check);
		Job job = new Job(owner, pos.immutable(), priority, options, mc.level.getBlockState(pos).getBlock());
		jobs.add(job);
		sort();
		return job.attempt;
	}

	@Override
	public void cancel(Object owner) {
		for (Job j : new ArrayList<>(jobs)) if (j.owner == owner) finish(j, false);
	}

	@Override
	public void cancel(BlockPos pos) {
		Job j = find(pos);
		if (j != null) finish(j, false);
	}

	@Override
	public boolean isPending(BlockPos pos) {
		return find(pos) != null;
	}

	@Override
	public float progress(BlockPos pos) {
		Job j = find(pos);
		if (j == null) return 0;
		return switch (j.phase) {
			case QUEUED -> 0;
			case MINING -> Math.min(1, j.progress);
			case DELAYED, AWAITING -> 1;
		};
	}

	@Override
	public boolean canRebreak(BlockPos pos) {
		return rebreakable(pos, false);
	}

	/**
	 * Whether a finish alone breaks {@code pos} again. The server measures from the start it remembers, with the block
	 * there now: it needs 70% of that block's time to have passed. Grim measures from the last finish and wants the
	 * block's full time, unless {@code fast} (servers that don't check).
	 */
	private boolean rebreakable(BlockPos pos, boolean fast) {
		if (!rebreakReady || !pos.equals(rebreakPos) || mc.player == null || mc.level == null) return false;
		BlockState state = mc.level.getBlockState(pos);
		if (nothingThere(state)) return false;
		float rate = Mining.delta(state, pos, toolSlot(Options.PACKET, state, pos));
		if (rate <= 0 || rate * ((tick - rebreakStart) * tpsFactor() + 1) < PACKET_THRESHOLD) return false;
		int needed = (int) Math.ceil(1 / rate - 1e-4);
		return fast || tick - lastStop >= needed && msSince(lastStopMs) >= needed * 50L;
	}

	// ---- ticking ------------------------------------------------------------------------------------------------

	/** Faces whatever is about to need it: the block being mined, or the first one waiting. */
	@Subscribe
	private void onTickStart(TickEvent.Pre e) {
		if (jobs.isEmpty() || mc.player == null) return;
		Job focus = primary != null && primary.options.rotate() ? primary : null;
		if (focus == null) {
			for (Job j : jobs) {
				if (j.phase == Phase.QUEUED && j.options.rotate()) {
					focus = j;
					break;
				}
			}
		}
		if (focus == null) return;
		aim(focus);
		if (focus.aim == null) return;
		float[] r = PlacementManager.anglesFromNextPosition(focus.aim);
		Myriad.rotations().request(this, r[0], r[1], Rotations.PRIORITY_HIGH, PlacementManager.MOVE_FIX, null);
	}

	/** Aims again from where you are now, just before the rotation goes out (see PlacementManager). */
	@Subscribe(priority = Priority.HIGH)
	private void onBeforeRotationSent(MovementPacketsEvent e) {
		Job focus = primary != null && primary.options.rotate() ? primary : null;
		if (focus == null) for (Job j : jobs) if (j.phase == Phase.QUEUED && j.options.rotate()) {
			focus = j;
			break;
		}
		if (focus == null || focus.aim == null || mc.player == null) return;
		float[] r = dev.myriad.api.util.MathUtil.anglesTo(mc.player.getEyePosition(), focus.aim);
		Myriad.rotations().request(this, r[0], r[1], PlacementManager.REFINED_PRIORITY, PlacementManager.MOVE_FIX, null);
	}

	/**
	 * Breaks at the start of the tick, before this tick's movement: the server already has the rotation sent last tick,
	 * and it's where vanilla sends break packets (Grim flags them after a movement packet).
	 */
	@Subscribe(priority = RotationManager.ACT_PRIORITY - 20)
	private void onBreakTime(TickEvent.Pre e) {
		step();
	}

	@Subscribe
	private void onTickEnd(TickEvent.Post e) {
		tick++;
		if (finishCooldown > 0) finishCooldown--;
	}

	@Subscribe
	private void onLeave(WorldEvent.Leave e) {
		for (Job j : new ArrayList<>(jobs)) j.result.complete(false);
		jobs.clear();
		primary = delayed = null;
		rebreakReady = holding = false;
		rebreakPos = serverDelayedPos = null;
	}

	private void step() {
		if (jobs.isEmpty() && !holding) return;
		if (mc.player == null || mc.level == null || mc.gameMode == null) return;
		if (serverDelayedPos != null && !mc.level.getBlockState(serverDelayedPos).is(serverDelayedBlock)) serverDelayedPos = null;
		validate();
		fetchTool();
		rebreak();
		instants();
		mine();
		updateHold();
	}

	/** Drops finished and impossible jobs, and completes the ones the server has dealt with. */
	private void validate() {
		for (Job j : new ArrayList<>(jobs)) {
			if (j.result.isDone()) {
				finish(j, j.result.getNow(false));
				continue;
			}
			BlockState state = mc.level.getBlockState(j.pos);
			boolean gone = !state.is(j.block);
			switch (j.phase) {
				case QUEUED, MINING -> {
					if (gone) finish(j, true);
					else if (!inRange(j.pos, j.options)) finish(j, false);
				}
				case DELAYED -> {
					if (gone) finish(j, true);
					else if (tick > j.deadline) finish(j, false);
				}
				case AWAITING -> {
					if (!j.acked) continue;
					if (gone) finish(j, true);
					else if (tick > j.deadline) finish(j, false);
					else if (j.fast() && j.pos.equals(rebreakPos) && rebreakReady && !j.pos.equals(serverDelayedPos) && tick - j.lastSent >= 2 && budget(1)) {
						// The finish arrived early while the server was already finishing another block by itself, so
						// it was ignored; the server still remembers this start, so finishing again soon works.
						j.lastSent = tick;
						withTool(toolSlot(j.options, state, j.pos), () -> Packets.sendSequenced(j.pos,
							seq -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, j.pos, j.face, seq)));
						swing(j);
					}
				}
			}
		}
	}

	/** Packet mode: a block back where the server's remembered break was is broken instantly. */
	private void rebreak() {
		if (!rebreakReady) return;
		Job j = find(rebreakPos);
		if (j == null || j.phase != Phase.QUEUED || !j.packet() || !rebreakable(j.pos, j.fast()) || !canAct(j) || !budget(1)) return;
		BlockState state = mc.level.getBlockState(j.pos);
		withTool(toolSlot(j.options, state, j.pos), () -> finishWith(j, Action.STOP_DESTROY_BLOCK, true, false));
	}

	/** Breaks everything waiting that the right tool breaks in one hit, grouped by tool to save swaps. */
	private void instants() {
		List<Job> instant = new ArrayList<>();
		List<Integer> slots = new ArrayList<>();
		boolean vanillaUsed = false;
		for (Job j : jobs) {
			if (j.phase != Phase.QUEUED) continue;
			BlockState state = mc.level.getBlockState(j.pos);
			int slot = toolSlot(j.options, state, j.pos);
			if (Mining.delta(state, j.pos, slot) < 1) continue;
			// Vanilla can't break more than one block a tick.
			if (!j.packet() && vanillaUsed) continue;
			// Starting anything straight after a finish is too fast for vanilla, and for Grim.
			if (j.keepsGap() && !gapOver()) continue;
			// Any start restarts the server's clock for the block being mined, so only more urgent blocks cut in.
			if (primary != null && primary.phase == Phase.MINING && j.priority <= primary.priority) continue;
			if (!canAct(j) || !budget(instant.size() + 1)) continue;
			if (!j.packet()) vanillaUsed = true;
			instant.add(j);
			slots.add(slot);
		}
		while (!instant.isEmpty()) {
			int slot = slots.getFirst();
			List<Job> group = new ArrayList<>();
			for (int i = instant.size() - 1; i >= 0; i--) {
				if (slots.get(i) != slot) continue;
				group.addFirst(instant.remove(i));
				slots.remove(i);
			}
			withTool(slot, () -> {
				for (Job j : group) finishWith(j, Action.START_DESTROY_BLOCK, true, false);
			});
			// An instant start also resets when the server thinks the current break started: start counting again.
			rebreakReady = false;
			rebreakStart = tick;
			if (primary != null && primary.phase == Phase.MINING) {
				primary.started = tick;
				primary.startedMs = System.currentTimeMillis();
				primary.progress = 0;
				primary.maxRate = 0;
			}
		}
	}

	/** The block being mined: progress, finishing, double break, and starting the next one. */
	private void mine() {
		if (primary != null && primary.phase == Phase.MINING) {
			Job p = primary;
			BlockState state = mc.level.getBlockState(p.pos);
			int slot = toolSlot(p.options, state, p.pos);
			float rate = Mining.delta(state, p.pos, slot);
			boolean done;
			if (p.fast()) {
				float elapsed = (tick - p.started) * tpsFactor();
				p.progress = rate * (elapsed + 1) / PACKET_THRESHOLD;
				done = rate * (elapsed + 1) >= PACKET_THRESHOLD;
				if (p.grim()) {
					int ticks = tick - p.started;
					// Past the minimum, clear Grim's start-too-soon record before finishing, then finish.
					if (ticks > GRIM_MIN_TICKS && !p.decoyed && budget(DECOY_BURST)) {
						for (int i = 0; i < DECOY_BURST; i++) decoyStart(p);
						swing(p);
						p.decoyed = true;
					}
					done &= ticks > GRIM_MIN_TICKS;
				}
			} else if (p.packet()) {
				// Vanilla's full time, as Grim times it: from the start, at the best speed seen since with what the
				// server sees held between packets (the tool is only swapped in to start and finish). Wall-clock
				// ticks (Grim uses real time); if the server lags, it finishes the block by itself.
				p.maxRate = Math.max(p.maxRate, Mining.delta(state, p.pos, Myriad.inventory().serverSlot()));
				int needed = (int) Math.ceil(1 / p.maxRate - 1e-4);
				p.progress = (float) (tick - p.started) / needed;
				done = tick - p.started >= needed && msSince(p.startedMs) >= needed * 50L;
			} else {
				p.progress += rate;
				done = p.progress >= 1;
			}
			mc.level.destroyBlockProgress(CRACK_PRIMARY, p.pos, Math.min(9, (int) (p.progress * 10)));
			// Holding the attack button swings every tick; packet mining only swings to start and finish.
			if (p.options.swing() && !p.packet()) mc.player.swing(InteractionHand.MAIN_HAND);
			if (done && budget(1) && canAct(p)) {
				withTool(slot, () -> finishWith(p, Action.STOP_DESTROY_BLOCK, true, p.packet()));
				if (p.packet()) rebreakReady = true;
			} else if (!done && p.fast() && p.options.doubleBreak() && serverDelayedPos == null && (!p.grim() || p.decoyed)) {
				Job next = nextToStart(true);
				if (next != null && budget(2)) {
					// Finish early: the server keeps mining this one by itself, and the next one starts now.
					primary = null;
					mc.level.destroyBlockProgress(CRACK_PRIMARY, p.pos, -1);
					becomeDelayed(p, rate);
					Packets.sendSequenced(p.pos, seq -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, p.pos, p.face, seq));
					swing(p);
				}
			}
		}
		if (delayed != null) {
			int stage = (int) Math.min(9, 10f * (tick - delayed.started) / Math.max(1, delayed.due - delayed.started));
			mc.level.destroyBlockProgress(CRACK_DELAYED, delayed.pos, stage);
		}
		if (primary == null && !mc.gameMode.isDestroying()) {
			Job next = nextToStart(false);
			if (next != null && (gapOver() || !next.keepsGap() || next.grim() && gapCleared) && budget(next.grim() ? 2 : 1)) start(next);
		}
	}

	/** The first waiting block that takes more than one hit and can be started now; double break wants packet mode. */
	private Job nextToStart(boolean forDoubleBreak) {
		for (Job j : jobs) {
			if (j.phase != Phase.QUEUED) continue;
			if (forDoubleBreak && (!j.fast() || !j.options.doubleBreak())) continue;
			BlockState state = mc.level.getBlockState(j.pos);
			float rate = Mining.delta(state, j.pos, toolSlot(j.options, state, j.pos));
			if (rate >= 1 || rate <= 0) continue;
			if (canAct(j)) return j;
		}
		return null;
	}

	private void start(Job j) {
		aim(j);
		BlockState state = mc.level.getBlockState(j.pos);
		int slot = toolSlot(j.options, state, j.pos);
		// Start with the tool in hand: the server and Grim judge the break's speed by what you hold as it starts.
		withTool(slot, () -> {
			Packets.sendSequenced(j.pos, seq -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, j.pos, j.face, seq));
			// Grim now times the break as the decoy's: air, which breaks at once.
			if (j.grim()) decoyStart(j);
			swing(j);
		});
		j.decoyed = false;
		gapCleared = false;
		j.phase = Phase.MINING;
		j.started = tick;
		j.startedMs = System.currentTimeMillis();
		j.progress = 0;
		j.maxRate = Mining.delta(state, j.pos, slot);
		primary = j;
		rebreakPos = j.pos;
		rebreakStart = tick;
		rebreakReady = false;
	}

	/**
	 * A start for the spot {@link #DECOY_HEIGHT} blocks above {@code j}'s block, out of the world: the server ignores
	 * it as out of reach, while Grim (on servers with ViaVersion) takes it as the block now being broken.
	 */
	private void decoyStart(Job j) {
		BlockPos decoy = j.pos.above(DECOY_HEIGHT);
		Packets.sendSequenced(seq -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, decoy, j.face, seq));
	}

	/** {@code j} was finished early: the server now finishes it by itself once its progress reaches 1. */
	private void becomeDelayed(Job j, float rate) {
		j.due = j.started + (int) Math.ceil(serverTicksToReach(rate, 1f) / tpsFactor());
		j.deadline = j.due + GRACE_TICKS;
		j.phase = Phase.DELAYED;
		delayed = j;
		serverDelayedPos = j.pos;
		serverDelayedBlock = j.block;
	}

	/**
	 * Sends the packet that should break {@code j}, predicting the break client-side if {@code predict}, and settles
	 * the job from the server's acknowledgement. With {@code finishLater}, a refusal may still turn into a break the
	 * server finishes by itself, so the job waits for that a while.
	 */
	private void finishWith(Job j, Action action, boolean predict, boolean finishLater) {
		aim(j);
		if (j == primary) {
			primary = null;
			mc.level.destroyBlockProgress(CRACK_PRIMARY, j.pos, -1);
		}
		j.phase = Phase.AWAITING;
		j.finishLater = finishLater;
		j.deadline = Integer.MAX_VALUE;
		j.lastSent = tick;
		if (action == Action.STOP_DESTROY_BLOCK) {
			lastStop = tick;
			lastStopMs = System.currentTimeMillis();
			if (j.keepsGap()) finishCooldown = FINISH_GAP;
			gapCleared = j.grim() && j.decoyed;
		}
		CompletableFuture<BlockState> ack = Packets.sendSequenced(j.pos, seq -> {
			if (predict) mc.gameMode.destroyBlock(j.pos);
			return new ServerboundPlayerActionPacket(action, j.pos, j.face, seq);
		});
		swing(j);
		ack.whenComplete((state, t) -> {
			if (j.result.isDone()) return;
			if (t != null) {
				finish(j, false);
			} else if (!state.is(j.block)) {
				finish(j, true);
			} else if (finishLater && serverDelayedPos == null && delayed == null) {
				// Finished a little early (the server lagged): it keeps mining this one by itself.
				BlockState now = mc.level.getBlockState(j.pos);
				becomeDelayed(j, Mining.delta(now, j.pos, toolSlot(j.options, now, j.pos)));
			} else if (finishLater) {
				// Early while the server was already finishing another: ignored, so it's finished again shortly.
				j.acked = true;
				j.deadline = tick + GRACE_TICKS;
			} else {
				finish(j, false);
			}
		});
	}

	private void finish(Job j, boolean broken) {
		if (j == primary) {
			if (j.phase == Phase.MINING && mc.getConnection() != null) {
				mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, j.pos, j.face));
			}
			if (mc.level != null) mc.level.destroyBlockProgress(CRACK_PRIMARY, j.pos, -1);
			primary = null;
		}
		if (j == delayed) {
			if (mc.level != null) mc.level.destroyBlockProgress(CRACK_DELAYED, j.pos, -1);
			delayed = null;
		}
		jobs.remove(j);
		j.result.complete(broken);
	}

	/**
	 * Holds the tool a break needs at the server between packets: the vanilla-mode block being mined, or the
	 * double-broken block around when the server finishes it (it checks the held tool every tick).
	 */
	private void updateHold() {
		int slot = -1;
		if (delayed != null && tick >= delayed.due - 1) {
			BlockState state = mc.level.getBlockState(delayed.pos);
			slot = toolSlot(delayed.options, state, delayed.pos);
		} else if (primary != null && primary.phase == Phase.MINING && !primary.packet()) {
			BlockState state = mc.level.getBlockState(primary.pos);
			slot = toolSlot(primary.options, state, primary.pos);
		}
		if (slot >= 0 && slot != mc.player.getInventory().getSelectedSlot()) {
			holding = Myriad.inventory().hold(this, slot, 3);
		} else if (holding) {
			Myriad.inventory().release(this);
			holding = false;
		}
	}

	// ---- helpers ------------------------------------------------------------------------------------------------

	/**
	 * Server ticks from the start until {@code progress per tick * (ticks + 1)} reaches {@code threshold}: how the
	 * server measures a break.
	 */
	static int serverTicksToReach(float ratePerTick, float threshold) {
		if (ratePerTick <= 0) return Integer.MAX_VALUE;
		return Math.max(0, (int) Math.ceil(threshold / ratePerTick - 1e-4) - 1);
	}

	/** Server ticks per client tick: under 1 while the server lags, so timed finishes wait for it. */
	private static float tpsFactor() {
		return Math.clamp(Myriad.server().tps() / 20f, 0.1f, 1f);
	}

	private void aim(Job j) {
		BlockHitResult hit = Reach.hitFor(j.pos, false, j.options.range());
		if (hit != null) {
			j.face = hit.getDirection();
			j.aim = hit.getLocation();
		} else {
			j.aim = Vec3.atCenterOf(j.pos);
		}
	}

	/** With rotate, only once the rotation just sent looks at the block; the face it lands on is the one used. */
	private boolean canAct(Job j) {
		if (!j.options.rotate()) return true;
		BlockHitResult look = Reach.rayHit(Myriad.rotations().serverYaw(), Myriad.rotations().serverPitch(), j.pos, j.options.range());
		if (look == null) return false;
		j.face = look.getDirection();
		return true;
	}

	/**
	 * Swings with a break packet, as vanilla does with each one (Grim notices break packets without a swing): shown
	 * if the options want the hand to swing, otherwise only sent, once a tick.
	 */
	private void swing(Job j) {
		if (j.options.swing()) mc.player.swing(InteractionHand.MAIN_HAND);
		else if (swungTick != tick && mc.getConnection() != null) mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
		swungTick = tick;
	}

	/**
	 * Auto tool from the inventory: when the best tool for the block being mined, or the next one, is outside the
	 * hotbar, it's moved in (see {@link dev.myriad.api.service.Inventory#pullToHotbar}, which waits while you move,
	 * since Grim cancels clicks then); until then the best hotbar tool does. A packet-mode break already started is
	 * timed by the tool it started with, so then it's fetched for the next block instead.
	 */
	private void fetchTool() {
		Job j = primary != null && primary.phase == Phase.MINING && (!primary.packet() || primary.fast()) ? primary : null;
		if (j == null) for (Job q : jobs) if (q.phase == Phase.QUEUED) {
			j = q;
			break;
		}
		if (j == null || !j.options.autoTool()) return;
		BlockState state = mc.level.getBlockState(j.pos);
		int from = Mining.fastestSlot(state, 9, 36);
		if (from < 0 || Mining.delta(state, j.pos, from) <= Mining.delta(state, j.pos, toolSlot(j.options, state, j.pos))) return;
		// A tool that's worse for this block makes room for it.
		Myriad.inventory().pullToHotbar(from, s -> s.getDestroySpeed(state) > 1);
	}

	private int toolSlot(Options o, BlockState state, BlockPos pos) {
		int selected = mc.player.getInventory().getSelectedSlot();
		if (!o.autoTool()) return selected;
		int best = Mining.fastestSlot(state, 0, 9);
		return best < 0 || Mining.delta(state, pos, selected) >= Mining.delta(state, pos, best) ? selected : best;
	}

	/** Runs {@code action} with {@code slot} held at the server, swapping only if it isn't already. */
	private void withTool(int slot, Runnable action) {
		if (Myriad.inventory().serverSlot() == slot) action.run();
		else Myriad.inventory().silentSwap(slot, action);
	}

	/** Vanilla's pause after a finish is over, in ticks and in real time. */
	private boolean gapOver() {
		return finishCooldown == 0 && msSince(lastStopMs) >= FINISH_GAP * 50L;
	}

	private static long msSince(long ms) {
		return System.currentTimeMillis() - ms;
	}

	private static boolean budget(int packets) {
		return Myriad.limits().canSend(PacketLimits.Kind.BLOCK_ACTION, packets);
	}

	/** Measured to the nearest point of the block, as the server and Grim do. */
	private boolean inRange(BlockPos pos, Options o) {
		return Reach.canReach(pos, o.range());
	}

	private static boolean nothingThere(BlockState state) {
		return state.isAir() || state.getBlock() instanceof LiquidBlock;
	}

	private Job find(BlockPos pos) {
		if (pos == null) return null;
		for (Job j : jobs) if (j.pos.equals(pos)) return j;
		return null;
	}

	private void sort() {
		jobs.sort(Comparator.<Job>comparingInt(j -> -j.priority).thenComparingLong(j -> j.order));
	}
}
