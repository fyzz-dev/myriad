package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.service.Placement;
import dev.myriad.api.service.Rotations;
import dev.myriad.api.util.BlockInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacementManager implements Placement {
	private static final long COOLDOWN_MS = 150;
	private final Minecraft mc = Minecraft.getInstance();
	private final Map<BlockPos, Long> cooldowns = new HashMap<>();

	@Override
	public boolean isOnCooldown(BlockPos pos) {
		Long t = cooldowns.get(pos);
		return t != null && System.currentTimeMillis() - t < COOLDOWN_MS;
	}

	@Override
	public boolean canPlace(BlockPos pos, Options o) {
		return spaceFree(pos, o) && (o.airPlace() || !clickTargets(pos).isEmpty());
	}

	/** Replaceable, in range, off cooldown, and no entity in the way. */
	private boolean spaceFree(BlockPos pos, Options o) {
		if (mc.player == null || mc.level == null || isOnCooldown(pos)) return false;
		BlockState state = mc.level.getBlockState(pos);
		if (!state.canBeReplaced()) return false;
		if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > o.range() * o.range()) return false;
		return mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), e -> e.isAlive() && e.isPickable() || e == mc.player && e.getBoundingBox().intersects(new AABB(pos))).isEmpty();
	}

	@Override
	public boolean place(BlockPos pos, int hotbarSlot, Options o) {
		if (hotbarSlot < 0 || hotbarSlot > 8 || !canPlace(pos, o)) return false;
		List<BlockHitResult> targets = clickTargets(pos);
		BlockHitResult hit = targets.isEmpty() ? new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false) : targets.getFirst();
		click(pos, hit, hotbarSlot, o);
		return true;
	}

	@Override
	public boolean place(BlockHitResult hit, int hotbarSlot, Options o) {
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
		if (hotbarSlot < 0 || hotbarSlot > 8 || !spaceFree(pos, o)) return false;
		click(pos, hit, hotbarSlot, o);
		return true;
	}

	private void click(BlockPos pos, BlockHitResult target, int hotbarSlot, Options o) {
		if (o.rotate()) {
			float[] angles = Myriad.rotations().anglesTo(target.getLocation());
			// Face the block now, so the server sees the right rotation for this placement packet.
			mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(angles[0], angles[1], mc.player.onGround(), mc.player.horizontalCollision));
			Myriad.rotations().request(this, angles[0], angles[1], Rotations.PRIORITY_HIGH);
		}
		Runnable action = () -> {
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, target);
			if (o.swing()) mc.player.swing(InteractionHand.MAIN_HAND);
		};
		Myriad.inventory().silentSwap(hotbarSlot, action);
		cooldowns.put(pos.immutable(), System.currentTimeMillis());
		if (cooldowns.size() > 256) cooldowns.values().removeIf(t -> System.currentTimeMillis() - t > COOLDOWN_MS);
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
		out.sort(Comparator.comparingDouble(h -> eyes.distanceToSqr(h.getLocation())));
		return out;
	}
}
