package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Priority;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.MovementPacketsEvent;
import dev.myriad.api.event.events.TickEvent;
import dev.myriad.api.event.events.WorldEvent;
import dev.myriad.api.service.PacketLimits;
import dev.myriad.api.service.Placement;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.util.BlockInfo;
import dev.myriad.api.util.MathUtil;
import dev.myriad.api.util.Reach;
import dev.myriad.impl.network.BlockAckTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Places blocks and follows each one until the server acknowledges it (see {@link BlockAckTracker}).
 *
 * <p>Rotated placements are queued: each tick the rotation manager is asked to face the first one, and once that
 * rotation has gone out in the movement packet, every queued placement the sent rotation actually looks at is
 * clicked, at the point the look lands on. So the server always sees a placement it could have made by hand, and no
 * extra rotation packets are sent.
 */
public final class PlacementManager implements Placement {
	/**
	 * Placement rotations snap, and walk along the sent yaw while they're held: Grim simulates movement with the yaw it
	 * was sent, so without the fix walking while facing a placement is flagged.
	 */
	static final Rotations.Options MOVE_FIX = new Rotations.Options(0, true);

	/** Just above the first request's, so the refined aim wins it. */
	static final int REFINED_PRIORITY = Rotations.PRIORITY_HIGH + 1;

	/** How long a rotated placement may wait for its rotation before it's given up. */
	private static final int QUEUE_TICKS = 6;

	private record Queued(Object owner, BlockPos pos, BlockHitResult target, int slot, Options options, CompletableFuture<Boolean> result, int expires) {
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final BlockAckTracker acks;
	private final Map<BlockPos, CompletableFuture<Boolean>> pending = new HashMap<>();
	private final List<Queued> queue = new ArrayList<>();
	private int tick;

	public PlacementManager(BlockAckTracker acks) {
		this.acks = acks;
	}

	@Override
	public boolean isPending(BlockPos pos) {
		return pending.containsKey(pos);
	}

	@Override
	public Check check(BlockPos pos, Options o) {
		Check space = spaceCheck(pos, o);
		if (!space.ok()) return space;
		return target(pos, o) != null ? Check.OK : supportCheck(pos, o);
	}

	/** Why no face qualified: nothing to click, or only faces you can't see. */
	private Check supportCheck(BlockPos pos, Options o) {
		if (o.airPlace()) return Check.OK;
		return clickTargets(pos).isEmpty() ? Check.NO_SUPPORT : Check.NOT_VISIBLE;
	}

	/** Everything but support: replaceable, in range, not pending, no entity in the way, budget left. */
	private Check spaceCheck(BlockPos pos, Options o) {
		if (mc.player == null || mc.level == null) return Check.UNAVAILABLE;
		if (isPending(pos) || Myriad.breaking().isPending(pos)) return Check.PENDING;
		BlockState state = mc.level.getBlockState(pos);
		if (!state.canBeReplaced()) return Check.OCCUPIED;
		if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > o.range() * o.range()) return Check.OUT_OF_RANGE;
		boolean blocked = !mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos),
			e -> e.isAlive() && e.isPickable() || e == mc.player && e.getBoundingBox().intersects(new AABB(pos))).isEmpty();
		if (blocked) return Check.ENTITY_IN_WAY;
		if (!Myriad.limits().canSend(PacketLimits.Kind.INTERACT, 1)) return Check.RATE_LIMITED;
		return Check.OK;
	}

	/** The face to click for {@code pos}: the best visible one, any one unless visible faces are required, or air. */
	private BlockHitResult target(BlockPos pos, Options o) {
		List<BlockHitResult> targets = clickTargets(pos);
		if (!targets.isEmpty() && (!o.visibleFaces() || visible(targets.getFirst()))) return targets.getFirst();
		if (targets.isEmpty() && o.airPlace()) return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return null;
	}

	@Override
	public Attempt place(Object owner, BlockPos pos, int hotbarSlot, Options o) {
		if (hotbarSlot < 0 || hotbarSlot > 8) return Attempt.refused(pos, Check.UNAVAILABLE);
		Check space = spaceCheck(pos, o);
		if (!space.ok()) return Attempt.refused(pos, space);
		BlockHitResult hit = target(pos, o);
		if (hit == null) return Attempt.refused(pos, supportCheck(pos, o));
		return submit(owner, pos, hit, hotbarSlot, o);
	}

	@Override
	public Attempt place(Object owner, BlockHitResult hit, int hotbarSlot, Options o) {
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
		if (hotbarSlot < 0 || hotbarSlot > 8) return Attempt.refused(pos, Check.UNAVAILABLE);
		Check check = spaceCheck(pos, o);
		if (!check.ok()) return Attempt.refused(pos, check);
		if (o.visibleFaces() && !visible(hit)) return Attempt.refused(pos, Check.NOT_VISIBLE);
		return submit(owner, pos, hit, hotbarSlot, o);
	}

	@Override
	public void cancel(Object owner) {
		if (owner == null) return;
		for (Queued q : new ArrayList<>(queue)) {
			if (q.owner != owner) continue;
			queue.remove(q);
			q.result.complete(false);
		}
	}

	private Attempt submit(Object owner, BlockPos pos, BlockHitResult hit, int slot, Options o) {
		BlockPos key = pos.immutable();
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		pending.put(key, result);
		result.whenComplete((placed, t) -> pending.remove(key, result));
		if (o.rotate()) queue.add(new Queued(owner, key, hit, slot, o, result, tick + QUEUE_TICKS));
		else click(key, hit, slot, o, result);
		return new Attempt(key, Check.OK, result);
	}

	/** Sends the placement now and completes {@code result} with the server's answer. */
	private void click(BlockPos pos, BlockHitResult target, int slot, Options o, CompletableFuture<Boolean> result) {
		int before = acks.currentSequence();
		// Clicking the empty space itself: an air placement.
		boolean air = target.getBlockPos().equals(pos);
		Myriad.inventory().silentSwap(slot, () -> {
			if (air) {
				// Grim refuses blocks placed against air from the main hand, but not from the off hand: swap the block
				// over, place it with the off hand, and swap back (how 2b2t clients air place).
				swapHands();
				mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, target);
				swapHands();
			} else {
				mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, target);
			}
			if (o.swing()) mc.player.swing(InteractionHand.MAIN_HAND);
		});
		int sequence = acks.currentSequence();
		// useItemOn gives up before sending anything outside the world border.
		if (sequence == before) {
			result.complete(false);
			return;
		}
		acks.track(sequence, pos).whenComplete((state, t) -> result.complete(t == null && !state.canBeReplaced()));
	}

	/**
	 * The rotation to look at {@code point} from about where your eyes will be when it's sent (the movement packet goes
	 * out after you move this tick). Good enough to settle the yaw; the pitch is refined just before sending.
	 */
	static float[] anglesFromNextPosition(Vec3 point) {
		Minecraft mc = Minecraft.getInstance();
		return MathUtil.anglesTo(mc.player.getEyePosition().add(mc.player.getDeltaMovement()), point);
	}

	/** Swaps the main and off hand, on the client and the server, as the swap key does. */
	private void swapHands() {
		ItemStack off = mc.player.getOffhandItem();
		mc.player.setItemInHand(InteractionHand.OFF_HAND, mc.player.getMainHandItem());
		mc.player.setItemInHand(InteractionHand.MAIN_HAND, off);
		mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
	}

	/** Asks to face the first queued placement; it's clicked once that rotation has been sent. */
	@Subscribe
	private void onTickStart(TickEvent.Pre e) {
		if (queue.isEmpty() || mc.player == null) return;
		float[] r = anglesFromNextPosition(queue.getFirst().target.getLocation());
		Myriad.rotations().request(this, r[0], r[1], Rotations.PRIORITY_HIGH, MOVE_FIX, null);
	}

	/**
	 * Aims again just before the rotation goes out, now that you've moved this tick: the yaw is already fixed (walking
	 * follows it), but the pitch can still be exact. Looking down at the side of the block you stand on, a tenth of a
	 * block of error in where your eyes are swings the look by most of a block.
	 */
	@Subscribe(priority = Priority.HIGH)
	private void onBeforeRotationSent(MovementPacketsEvent e) {
		if (queue.isEmpty() || mc.player == null) return;
		float[] r = MathUtil.anglesTo(mc.player.getEyePosition(), queue.getFirst().target.getLocation());
		Myriad.rotations().request(this, r[0], r[1], REFINED_PRIORITY, MOVE_FIX, null);
	}

	/** Clicks every queued placement the rotation just sent looks at, where the look lands. */
	@Subscribe(priority = Priority.HIGH)
	private void onRotationSent(MovementPacketsEvent.Post e) {
		if (queue.isEmpty() || mc.player == null) return;
		float yaw = Myriad.rotations().serverYaw(), pitch = Myriad.rotations().serverPitch();
		Iterator<Queued> it = queue.iterator();
		while (it.hasNext()) {
			Queued q = it.next();
			if (q.result.isDone()) {
				it.remove();
				continue;
			}
			BlockHitResult look = Reach.rayHit(yaw, pitch, q.target.getBlockPos(), q.options.range() + 1);
			if (look == null || look.getDirection() != q.target.getDirection()) continue;
			it.remove();
			// The world may have changed while it waited.
			if (!mc.level.getBlockState(q.pos).canBeReplaced() || mc.level.getBlockState(q.target.getBlockPos()).canBeReplaced()
				|| !Myriad.limits().canSend(PacketLimits.Kind.INTERACT, 1)) {
				q.result.complete(false);
				continue;
			}
			click(q.pos, new BlockHitResult(look.getLocation(), look.getDirection(), q.target.getBlockPos(), false), q.slot, q.options, q.result);
		}
	}

	@Subscribe
	private void onTickEnd(TickEvent.Post e) {
		tick++;
		if (queue.isEmpty()) return;
		queue.removeIf(q -> {
			if (q.result.isDone()) return true;
			if (tick < q.expires && mc.player != null) return false;
			q.result.complete(false);
			return true;
		});
	}

	@Subscribe
	private void onLeave(WorldEvent.Leave e) {
		for (Queued q : new ArrayList<>(queue)) q.result.complete(false);
		queue.clear();
		pending.clear();
	}

	@Override
	public List<BlockHitResult> clickTargets(BlockPos pos) {
		List<BlockHitResult> out = new ArrayList<>();
		if (mc.player == null || mc.level == null) return out;
		Vec3 eyes = mc.player.getEyePosition();
		for (Direction dir : Direction.values()) {
			BlockPos neighbour = pos.relative(dir);
			BlockState state = mc.level.getBlockState(neighbour);
			if (state.canBeReplaced() || (BlockInfo.isClickable(state) && !mc.player.isShiftKeyDown())) continue;
			Direction face = dir.getOpposite();
			Vec3 hitVec = Vec3.atCenterOf(neighbour).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
			out.add(new BlockHitResult(hitVec, face, neighbour, false));
		}
		out.sort(Comparator.<BlockHitResult, Boolean>comparing(h -> !visible(h)).thenComparingDouble(h -> eyes.distanceToSqr(h.getLocation())));
		return out;
	}

	/** The clicked face points towards the eyes and nothing stands between them and the click point. */
	private boolean visible(BlockHitResult hit) {
		if (!Reach.faceExposed(hit.getBlockPos(), hit.getDirection())) return false;
		BlockHitResult ray = mc.level.clip(new ClipContext(mc.player.getEyePosition(), hit.getLocation(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
		return ray.getType() == HitResult.Type.MISS || ray.getBlockPos().equals(hit.getBlockPos());
	}
}
