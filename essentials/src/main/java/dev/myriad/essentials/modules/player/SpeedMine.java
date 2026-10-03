package dev.myriad.essentials.modules.player;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.BlockBreakEvent;
import dev.myriad.api.event.events.Render3DEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.render.Easing;
import dev.myriad.api.render.Renderer3D;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.EnumSetting;
import dev.myriad.api.setting.IntSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.Mining;
import dev.myriad.api.util.Packets;
import dev.myriad.essentials.util.Fade;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Packet mining. Hitting a block sends START and STOP straight away; the module then counts the break progress
 * itself (tool, enchantments, effects, water, air, optionally server TPS) and sends the final STOP the moment the
 * block is ready, swapping to the best tool for just that packet. Optionally:
 * <ul>
 * <li>Double Mine keeps a second block breaking server-side while you start another.</li>
 * <li>Queue lines up blocks you drag across while holding attack.</li>
 * <li>ReMine instantly breaks the primary block again whenever it's replaced.</li>
 * <li>2b2t mode paces START packets and sends a far-away decoy START, which keeps strict anticheats happy.</li>
 * </ul>
 * Other modules can mine through it with {@link #offerMine}.
 */
public class SpeedMine extends Module {
	public enum Swap {
		OFF, NORMAL, SILENT
	}

	public enum Remine {
		OFF, INSTANT, NORMAL, FAST
	}

	public enum Animation {
		EXPAND, SHRINK, RISE, FALL
	}

	public enum Goal {
		MANUAL, QUEUE, EXTERNAL
	}

	private static final int DECOY_Y = 999;
	private static final double GRIM_MIN_EYE = 0.4, GRIM_MAX_EYE = 1.62;
	private static final int SECONDARY_TIMEOUT = 10, SECONDARY_HOLD_TICKS = 40;
	private static final double BREAK_AHEAD_EDGE = 0.05;
	private static final long GRIM_STOP_DELAY_MS = 275;
	private static final double GRIM_BALANCE_LIMIT = 900;
	private static final long REMINE_DELAY_MS = 50;
	private static final int SAFE_TICKS = 2, START_DELAY_MS = 300, BREAK_DELAY_TICKS = 6;
	private static final int FADE_MS = 250;

	private final DoubleSetting range = sgGeneral.doubleSetting("Range").description("Max mining distance from your eyes.").defaultValue(5).range(1, 7).decimals(1).build();
	private final BoolSetting rangeAbort = sgGeneral.bool("Range Abort").description("Abort the second block when it goes out of range (the first always aborts).").defaultValue(true).build();
	private final DoubleSetting speed = sgGeneral.doubleSetting("Speed").description("Break progress needed before the final STOP. Lower is faster but riskier.").defaultValue(0.7).range(0.1, 1).decimals(2).build();
	private final EnumSetting<Swap> swap = sgGeneral.enumSetting("Auto Swap", Swap.SILENT).description("Swap to the fastest tool to break. Silent hides the swap.").build();
	private final BoolSetting decoy = sgGeneral.bool("2b2t").description("Grim-safe pacing: START a far decoy block with each mine and space out STARTs.").defaultValue(true).build();
	private final BoolSetting doubleMine = sgGeneral.bool("Double Mine").description("Keep the current block breaking server-side when you start another.").defaultValue(true).build();
	private final BoolSetting breakAhead = sgGeneral.bool("Break Ahead").description("Also mine the block behind a straight-on hit.").visible(doubleMine::get).build();
	private final BoolSetting queue = sgGeneral.bool("Queue").description("Queue blocks while holding attack and dragging across them.").defaultValue(true).build();
	private final IntSetting queueSize = sgGeneral.intSetting("Queue Size").defaultValue(12).range(1, 64).visible(queue::get).build();
	private final EnumSetting<Remine> remine = sgGeneral.enumSetting("ReMine", Remine.NORMAL).description("Break the first block again whenever it's replaced.").build();
	private final BoolSetting fast = sgGeneral.bool("Fast").description("Check for a replaced block every frame, not just every tick.").visible(() -> remine.get() != Remine.OFF).build();
	private final BoolSetting tpsSync = sgGeneral.bool("TPS Sync").description("Scale break speed to the server's TPS.").build();
	private final BoolSetting pauseWhileUsing = sgGeneral.bool("Pause While Using").description("Hold the final STOP while you eat or use an item.").defaultValue(true).build();

	private final SettingGroup sgRender = settings.group("Render");
	private final EnumSetting<Animation> animation = sgRender.enumSetting("Animation", Animation.EXPAND).build();
	private final EnumSetting<Easing> easing = sgRender.enumSetting("Easing", Easing.LINEAR).build();
	private final ColorSetting fill = sgRender.color("Fill").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 20)).build();
	private final ColorSetting line = sgRender.color("Line").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT)).build();
	private final DoubleSetting lineWidth = sgRender.doubleSetting("Line Width").defaultValue(1.5).range(0.5, 5).decimals(1).build();

	// Primary (instant) mine.
	private BlockPos pos;
	private Direction direction;
	private Goal primaryGoal = Goal.MANUAL;
	private int ticks;
	private double progress, lastProgress;
	private boolean started, finished;

	// Secondary (packet) mine.
	private BlockPos secondaryPos;
	private Direction secondaryDirection = Direction.UP;
	private int secondaryTicks;
	private boolean secondaryHolding;
	private double secondaryProgress, lastSecondaryProgress;

	private int stopCooldown;
	private long lastStartNanos, lastStopMs;
	private double delayBalance;
	private long lastRemineMs;
	private BlockPos lastQueuedCrosshair;

	private final ArrayDeque<Queued> queued = new ArrayDeque<>();
	private Shown primaryShown = Shown.NONE, secondaryShown = Shown.NONE;

	private record Queued(BlockPos pos, Direction direction) {
	}

	/** What the renderer shows for one mine; the fade outlives the mine so it can fade out. */
	private record Shown(@Nullable BlockPos pos, Fade fade) {
		static final Shown NONE = new Shown(null, new Fade(false, 200));
	}

	public SpeedMine() {
		super(Categories.PLAYER, "Speed Mine", "Mines blocks with packets, faster and from a distance.");
	}

	@Override
	public String hudInfo() {
		String extra = secondaryPos != null ? " +1" : "";
		String q = queued.isEmpty() ? "" : " q" + queued.size();
		if (pos == null) return secondaryPos != null ? "+1" + q : q.isEmpty() ? null : q.trim();
		if (!started) return "wait" + extra + q;
		if (finished) return "remine" + extra + q;
		return (int) (Math.min(progress / speed.get(), 1.0) * 100) + "%" + extra + q;
	}

	@Override
	protected void onDisable() {
		if (inGame() && pos != null && started && !finished) abort(pos);
		clearSecondary();
		endHold();
		clearMine();
		queued.clear();
		lastQueuedCrosshair = null;
		primaryShown = secondaryShown = Shown.NONE;
		lastStartNanos = lastStopMs = 0;
		delayBalance = 0;
		stopCooldown = 0;
	}

	// ---- events ---------------------------------------------------------------------------------------------------

	@Subscribe
	private void onAttack(BlockBreakEvent.Start e) {
		if (!inGame() || mc.player.isCreative() || mc.player.isSpectator()) return;
		e.cancel();
		if (isMining(e.pos())) return;
		if (!canBreak(e.pos(), mc.level.getBlockState(e.pos()))) return;
		if (queue.get() && pos != null) {
			enqueue(e.pos(), e.direction());
			return;
		}
		BlockPos ahead = breakAheadPos(e.pos());
		if (ahead != null) startMine(ahead, validFace(ahead), Goal.MANUAL);
		startMine(e.pos(), e.direction(), Goal.MANUAL);
	}

	@Subscribe
	private void onTick(TickEvent.Pre e) {
		if (!inGame()) return;
		captureQueuedCrosshair();
		if (stopCooldown > 0) stopCooldown--;
		tickSecondary();

		if (pos == null) {
			startNextQueued();
			return;
		}
		if (!inRange(pos)) {
			if (started && !finished) abort(pos);
			clearMine();
			startNextQueued();
			return;
		}
		BlockState state = mc.level.getBlockState(pos);
		if (!started) {
			if (!canBreak(pos, state)) {
				clearMine();
				startNextQueued();
				return;
			}
			if (stopCooldown > 0 || !canBegin() || !startDelayElapsed()) return;
			direction = validFace(pos);
			begin();
			return;
		}
		if (!finished) {
			if (state.isAir()) {
				clearMine();
				startNextQueued();
				return;
			}
			int slot = bestSlot(state);
			float delta = delta(state, pos, slot);
			if (delta <= 0) {
				abort(pos);
				clearMine();
				startNextQueued();
				return;
			}
			ticks++;
			lastProgress = progress;
			progress = Math.max(ticks - safeTicks(), 0) * delta;
			if (progress >= speed.get()) {
				if (shouldPause()) {
					ticks--;
					progress = lastProgress = speed.get();
				} else if (!tryPipelineQueued(slot)) {
					boolean queueWaiting = queue.get() && !queued.isEmpty();
					finished = stopBreak(slot, !queueWaiting);
					if (finished && queueWaiting) {
						clearMine();
						startNextQueued();
					}
				}
			} else {
				tryStartQueuedAsDouble();
			}
			return;
		}
		if (!queued.isEmpty()) {
			clearMine();
			startNextQueued();
			return;
		}
		if (remine.get() == Remine.OFF) {
			clearMine();
			return;
		}
		tryRemine();
	}

	@Subscribe
	private void onRender(Render3DEvent e) {
		if (!inGame() || mc.player.isCreative() || mc.player.isSpectator()) return;
		if (remine.get() != Remine.OFF && (fast.get() || remine.get() == Remine.FAST)) tryRemine();
		Renderer3D.lineWidth(lineWidth.getFloat());
		render(primaryShown, primaryDamage(), e.tickDelta(), remine.get() != Remine.OFF);
		if (doubleMine.get()) render(secondaryShown, new double[]{lastSecondaryProgress, secondaryProgress}, e.tickDelta(), false);
		if (queue.get()) renderQueue();
	}

	// ---- queue ----------------------------------------------------------------------------------------------------

	private void captureQueuedCrosshair() {
		if (!queue.get() || !mc.options.keyAttack.isDown()) {
			lastQueuedCrosshair = null;
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		BlockPos target = hit.getBlockPos();
		if (target.equals(lastQueuedCrosshair)) return;
		lastQueuedCrosshair = target.immutable();
		if (pos == null && queued.isEmpty()) return;
		enqueue(target, hit.getDirection());
	}

	private boolean enqueue(BlockPos target, Direction dir) {
		if (!queue.get() || target == null || isMining(target) || isQueued(target) || !inRange(target)) return false;
		if (!canBreak(target, mc.level.getBlockState(target))) return false;
		while (queued.size() >= queueSize.get()) queued.pollFirst();
		queued.addLast(new Queued(target.immutable(), dir != null ? dir : validFace(target)));
		tryStartQueuedAsDouble();
		return true;
	}

	private boolean startNextQueued() {
		while (!queued.isEmpty()) {
			Queued next = queued.pollFirst();
			if (!inRange(next.pos()) || !canBreak(next.pos(), mc.level.getBlockState(next.pos()))) continue;
			Direction face = next.direction();
			if (face == null || faceMargin(face, targetBox(next.pos())) <= 0) face = validFace(next.pos());
			startMine(next.pos(), face, Goal.QUEUE);
			return true;
		}
		return false;
	}

	private boolean tryStartQueuedAsDouble() {
		if (!queue.get() || !doubleMine.get() || queued.isEmpty() || secondaryPos != null) return false;
		if (pos == null || !started || finished) return false;
		return startNextQueued();
	}

	/** Finishes the primary as a secondary so the next queued block can start right away. */
	private boolean tryPipelineQueued(int slot) {
		if (!queue.get() || !doubleMine.get() || queued.isEmpty()) return false;
		if (secondaryPos != null || pos == null || !started || finished) return false;
		if (!stopBreak(slot, false)) return false;
		moveToSecondary();
		clearMine();
		if (!startNextQueued()) clearSecondary();
		return true;
	}

	private boolean isQueued(BlockPos target) {
		for (Queued q : queued) if (q.pos().equals(target)) return true;
		return false;
	}

	// ---- mining ---------------------------------------------------------------------------------------------------

	private void startMine(BlockPos target, Direction dir, Goal goal) {
		if (pos != null && started && !finished) {
			if (!doubleMine.get() || secondaryPos != null || !demote()) abort(pos);
		}
		pos = target.immutable();
		direction = dir != null ? dir : validFace(target);
		primaryGoal = goal;
		ticks = 0;
		progress = lastProgress = 0;
		started = finished = false;
		lastRemineMs = 0;
		primaryShown = new Shown(pos, new Fade(true, FADE_MS));
		if (stopCooldown == 0 && canBegin() && startDelayElapsed()) begin();
	}

	private void moveToSecondary() {
		secondaryPos = pos;
		secondaryDirection = direction != null ? direction : Direction.UP;
		secondaryTicks = ticks;
		secondaryProgress = lastSecondaryProgress = Math.min(progress, 1.0);
		secondaryShown = new Shown(secondaryPos, new Fade(true, FADE_MS));
	}

	/** Moves the running primary mine to the secondary slot (sending its STOP so the server keeps breaking it). */
	private boolean demote() {
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir()) return false;
		if (!stopBreak(bestSlot(state), false)) send(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, validFace(pos));
		moveToSecondary();
		return true;
	}

	private void begin() {
		if (!startDelayElapsed()) return;
		started = true;
		trackStarts(decoy.get() ? 2 : 1);
		if (faceMargin(direction, targetBox(pos)) <= 0) direction = validFace(pos);
		send(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction);
		if (decoy.get()) send(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, BlockPos.ZERO.atY(DECOY_Y), Direction.UP);
		send(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction);
		lastStartNanos = System.nanoTime();
	}

	private void tickSecondary() {
		if (secondaryPos == null) return;
		if (rangeAbort.get() && !inRange(secondaryPos)) {
			abort(secondaryPos);
			clearSecondary();
			return;
		}
		BlockState state = mc.level.getBlockState(secondaryPos);
		if (state.isAir()) {
			clearSecondary();
			return;
		}
		secondaryTicks++;
		int slot = bestSlot(state);
		float delta = delta(state, secondaryPos, slot);
		if (delta <= 0) {
			clearSecondary();
			return;
		}
		int expected = Mth.ceil(1.0 / delta) - 1;
		lastSecondaryProgress = secondaryProgress;
		secondaryProgress = Math.min(secondaryTicks * delta, 1.0);
		if (secondaryTicks > expected + SECONDARY_TIMEOUT) {
			clearSecondary();
			return;
		}
		if (pauseWhileUsing.get() && mc.player.isUsingItem()) return;
		// Hold the tool from one tick before the server finishes the block, so it breaks with the right tool.
		if (secondaryTicks >= expected - 1 && slot >= 0) {
			if (swap.get() == Swap.SILENT) secondaryHolding = Myriad.inventory().hold(this, slot, SECONDARY_HOLD_TICKS) || secondaryHolding;
			else if (swap.get() == Swap.NORMAL) Myriad.inventory().select(slot);
		}
	}

	private void clearSecondary() {
		endHold();
		secondaryPos = null;
		secondaryDirection = Direction.UP;
		secondaryTicks = 0;
		secondaryProgress = lastSecondaryProgress = 0;
		secondaryShown.fade().set(false);
	}

	private void endHold() {
		if (secondaryHolding) Myriad.inventory().release(this);
		secondaryHolding = false;
	}

	private boolean shouldPause() {
		if (pauseWhileUsing.get() && mc.player.isUsingItem()) return true;
		return Myriad.inventory().isHolding() && secondaryHolding;
	}

	private boolean stopBreak(int slot, boolean cooldown) {
		if (shouldPause()) return false;
		if (cooldown) stopCooldown = decoy.get() ? BREAK_DELAY_TICKS : 0;
		BlockPos target = pos;
		Direction face = validFace(target);
		Runnable finish = () -> send(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, target, face);
		if (slot >= 0 && slot < 9 && swap.get() == Swap.SILENT) {
			Myriad.inventory().silentSwap(slot, finish);
			return true;
		}
		if (slot >= 0 && slot < 9 && swap.get() == Swap.NORMAL) Myriad.inventory().select(slot);
		finish.run();
		return true;
	}

	private void abort(BlockPos target) {
		send(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, target, Direction.DOWN);
	}

	private void send(ServerboundPlayerActionPacket.Action action, BlockPos target, Direction face) {
		if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) lastStopMs = System.currentTimeMillis();
		Packets.sendSequenced(sequence -> new ServerboundPlayerActionPacket(action, target, face, sequence));
	}

	private void clearMine() {
		pos = null;
		direction = null;
		primaryGoal = Goal.MANUAL;
		ticks = 0;
		progress = lastProgress = 0;
		started = finished = false;
		lastRemineMs = 0;
		primaryShown.fade().set(false);
	}

	private void tryRemine() {
		if (pos == null || !started || !finished || remine.get() == Remine.OFF) return;
		BlockState state = mc.level.getBlockState(pos);
		if (!canBreak(pos, state)) {
			// The hole is open; re-arm so the next replacement goes straight away.
			lastRemineMs = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRemineMs < REMINE_DELAY_MS || shouldPause()) return;
		if (stopBreak(bestSlot(state), true)) lastRemineMs = now;
	}

	// ---- pacing (only active in 2b2t mode) --------------------------------------------------------------------------

	private int safeTicks() {
		return decoy.get() ? SAFE_TICKS : 0;
	}

	private boolean startDelayElapsed() {
		if (lastStartNanos == 0) return true;
		long delay = (decoy.get() ? START_DELAY_MS : 0) * 1_000_000L;
		return System.nanoTime() - lastStartNanos >= delay;
	}

	private boolean canBegin() {
		long delay = System.currentTimeMillis() - lastStopMs;
		if (delay >= GRIM_STOP_DELAY_MS) return true;
		double cost = (300.0 - delay) * (decoy.get() ? 2.0 : 1.0);
		return delayBalance + cost <= GRIM_BALANCE_LIMIT;
	}

	private void trackStarts(int starts) {
		long delay = System.currentTimeMillis() - lastStopMs;
		for (int i = 0; i < starts; i++) {
			if (delay >= GRIM_STOP_DELAY_MS) delayBalance *= 0.9;
			else delayBalance += 300.0 - delay;
		}
		delayBalance = Mth.clamp(delayBalance, -1000, 1000);
	}

	// ---- geometry -------------------------------------------------------------------------------------------------

	/** Traces the look ray through the hit block; returns the block behind it when the ray leaves cleanly. */
	private @Nullable BlockPos breakAheadPos(BlockPos target) {
		if (!breakAhead.get() || !doubleMine.get()) return null;
		if (secondaryPos != null || (pos != null && started && !finished)) return null;
		Vec3 eye = mc.player.getEyePosition();
		Vec3 dir = mc.player.getViewVector(1f);
		AABB box = new AABB(target);
		double tEnter = Double.NEGATIVE_INFINITY, tExit = Double.POSITIVE_INFINITY;
		Direction exitFace = null;
		for (Direction.Axis axis : Direction.Axis.values()) {
			double d = axis.choose(dir.x, dir.y, dir.z), o = axis.choose(eye.x, eye.y, eye.z);
			double min = axis.choose(box.minX, box.minY, box.minZ), max = axis.choose(box.maxX, box.maxY, box.maxZ);
			if (Math.abs(d) < 1e-7) {
				if (o < min || o > max) return null;
				continue;
			}
			double t1 = (min - o) / d, t2 = (max - o) / d;
			tEnter = Math.max(tEnter, Math.min(t1, t2));
			double far = Math.max(t1, t2);
			if (far < tExit) {
				tExit = far;
				exitFace = Direction.fromAxisAndDirection(axis, d > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
			}
		}
		if (exitFace == null || tEnter > tExit || tExit <= 0) return null;
		Vec3 exit = eye.add(dir.scale(tExit));
		for (Direction.Axis axis : Direction.Axis.values()) {
			if (axis == exitFace.getAxis()) continue;
			double p = axis.choose(exit.x, exit.y, exit.z) - axis.choose(target.getX(), target.getY(), target.getZ());
			if (p < BREAK_AHEAD_EDGE || p > 1 - BREAK_AHEAD_EDGE) return null;
		}
		BlockPos ahead = target.relative(exitFace);
		if (!inRange(ahead) || !canBreak(ahead, mc.level.getBlockState(ahead))) return null;
		return ahead;
	}

	/** The face the server will accept from where you stand (Grim checks the eye can see the face). */
	private Direction validFace(BlockPos target) {
		AABB box = targetBox(target);
		Direction best = Direction.UP;
		double bestMargin = -Double.MAX_VALUE;
		for (Direction dir : Direction.values()) {
			double margin = faceMargin(dir, box);
			if (margin > bestMargin) {
				bestMargin = margin;
				best = dir;
			}
		}
		return best;
	}

	private double faceMargin(Direction dir, AABB box) {
		Vec3 now = mc.player.position();
		Vec3 prev = new Vec3(mc.player.xo, mc.player.yo, mc.player.zo);
		return Math.min(feetMargin(dir, box, now), feetMargin(dir, box, prev));
	}

	private static double feetMargin(Direction dir, AABB box, Vec3 feet) {
		return switch (dir) {
			case UP -> feet.y + GRIM_MAX_EYE - box.maxY;
			case DOWN -> box.minY - (feet.y + GRIM_MIN_EYE);
			case EAST -> feet.x - box.maxX;
			case WEST -> box.minX - feet.x;
			case SOUTH -> feet.z - box.maxZ;
			case NORTH -> box.minZ - feet.z;
		};
	}

	private AABB targetBox(BlockPos target) {
		VoxelShape shape = mc.level.getBlockState(target).getShape(mc.level, target);
		return shape.isEmpty() ? new AABB(target) : shape.bounds().move(target);
	}

	private int bestSlot(BlockState state) {
		if (swap.get() == Swap.OFF) return mc.player.getInventory().getSelectedSlot();
		return Mining.fastestSlot(state, 0, 9);
	}

	private float delta(BlockState state, BlockPos target, int slot) {
		float d = Mining.delta(state, target, slot);
		if (tpsSync.get()) d *= Math.max(Myriad.server().tps() / 20f, 0f);
		return d;
	}

	private boolean canBreak(BlockPos target, BlockState state) {
		return !state.isAir() && state.getDestroySpeed(mc.level, target) != -1f && state.getFluidState().isEmpty();
	}

	private boolean inRange(BlockPos target) {
		return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= range.get();
	}

	// ---- rendering ------------------------------------------------------------------------------------------------

	private double[] primaryDamage() {
		double threshold = Math.max(0.001, speed.get());
		if (finished) return new double[]{1, 1};
		return new double[]{Mth.clamp(lastProgress / threshold, 0, 1), Mth.clamp(progress / threshold, 0, 1)};
	}

	private void render(Shown shown, double[] damage, float tickDelta, boolean instant) {
		float f = shown.fade().get();
		if (shown.pos() == null || f <= 0.01f) return;
		BlockPos at = shown.pos();
		BlockState state = mc.level.getBlockState(at);
		if (!instant && state.isAir()) return;
		Easing e = easing.get();
		double t = Mth.lerp(tickDelta, e.ease(damage[0]), e.ease(damage[1]));
		VoxelShape shape = state.getShape(mc.level, at);
		AABB base = (shape.isEmpty() || (instant && t >= 1) ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(at);
		double scale = (instant && t >= 1) || state.isAir() ? 1 : Math.max(0, t);
		AABB box = switch (animation.get()) {
			case EXPAND -> scaled(base, scale);
			case SHRINK -> scaled(base, Math.max(0.05, 1 - scale));
			case RISE -> new AABB(base.minX, base.minY, base.minZ, base.maxX, base.minY + (base.maxY - base.minY) * scale, base.maxZ);
			case FALL -> new AABB(base.minX, base.maxY - (base.maxY - base.minY) * scale, base.minZ, base.maxX, base.maxY, base.maxZ);
		};
		int fc = fill.argb(), lc = line.argb();
		Renderer3D.box(box, ColorUtil.withAlpha(fc, (int) (ColorUtil.alpha(fc) * f)), ColorUtil.withAlpha(lc, (int) (ColorUtil.alpha(lc) * f)), Renderer3D.ShapeMode.BOTH, true);
	}

	private void renderQueue() {
		int fc = ColorUtil.withAlpha(fill.argb(), Math.min(ColorUtil.alpha(fill.argb()), 14));
		int lc = ColorUtil.withAlpha(line.argb(), Math.min(ColorUtil.alpha(line.argb()), 120));
		int drawn = 0;
		for (Queued q : queued) {
			if (!inRange(q.pos()) || !canBreak(q.pos(), mc.level.getBlockState(q.pos()))) continue;
			Renderer3D.box(targetBox(q.pos()), fc, lc, Renderer3D.ShapeMode.BOTH, true);
			if (++drawn >= 16) break;
		}
	}

	private static AABB scaled(AABB box, double s) {
		Vec3 c = box.getCenter();
		double hx = (box.maxX - box.minX) * 0.5 * s, hy = (box.maxY - box.minY) * 0.5 * s, hz = (box.maxZ - box.minZ) * 0.5 * s;
		return new AABB(c.x - hx, c.y - hy, c.z - hz, c.x + hx, c.y + hy, c.z + hz);
	}

	// ---- API for other modules ------------------------------------------------------------------------------------

	/**
	 * Starts mining {@code target}, or queues it behind the current mines (even with Queue off). Returns true if
	 * the block is being mined or waiting. Used by Elytra Bounce to clear obstacles.
	 */
	public boolean offerMine(BlockPos target) {
		if (!isEnabled() || !inGame() || mc.player.isCreative() || mc.player.isSpectator() || target == null) return false;
		if (isMining(target) || isQueued(target)) return true;
		if (!canBreak(target, mc.level.getBlockState(target)) || !inRange(target)) return false;
		Direction face = validFace(target);
		if (pos == null || !started || finished || (doubleMine.get() && secondaryPos == null)) {
			startMine(target, face, Goal.EXTERNAL);
			return isMining(target);
		}
		while (queued.size() >= Math.max(8, queueSize.get())) queued.pollFirst();
		queued.addLast(new Queued(target.immutable(), face));
		return true;
	}

	public boolean doubleMine() {
		return doubleMine.get();
	}

	public boolean isMining() {
		return pos != null || secondaryPos != null;
	}

	public boolean isMining(BlockPos target) {
		return target.equals(pos) || target.equals(secondaryPos);
	}

	public @Nullable BlockPos primaryPos() {
		return pos;
	}

	public @Nullable BlockPos secondaryPos() {
		return secondaryPos;
	}

	/** True while blocks another module offered are still being mined or waiting. */
	public boolean hasExternalWork() {
		return (pos != null && primaryGoal == Goal.EXTERNAL) || !queued.isEmpty();
	}
}
