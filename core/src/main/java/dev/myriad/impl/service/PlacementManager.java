package dev.myriad.impl.service;

import dev.myriad.api.Myriad;
import dev.myriad.api.service.Placement;
import dev.myriad.api.service.Rotations;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlacementManager implements Placement {
	private static final long COOLDOWN_MS = 150;
	private final MinecraftClient mc = MinecraftClient.getInstance();
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
		if (mc.player == null || mc.world == null || isOnCooldown(pos)) return false;
		BlockState state = mc.world.getBlockState(pos);
		if (!state.isReplaceable()) return false;
		if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > o.range() * o.range()) return false;
		return mc.world.getOtherEntities(null, new Box(pos), e -> e.isAlive() && e.canHit() || e == mc.player && e.getBoundingBox().intersects(new Box(pos))).isEmpty();
	}

	@Override
	public boolean place(BlockPos pos, int hotbarSlot, Options o) {
		if (hotbarSlot < 0 || hotbarSlot > 8 || !canPlace(pos, o)) return false;
		List<BlockHitResult> targets = clickTargets(pos);
		BlockHitResult hit = targets.isEmpty() ? new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false) : targets.getFirst();
		click(pos, hit, hotbarSlot, o);
		return true;
	}

	@Override
	public boolean place(BlockHitResult hit, int hotbarSlot, Options o) {
		BlockPos pos = hit.getBlockPos().offset(hit.getSide());
		if (hotbarSlot < 0 || hotbarSlot > 8 || !spaceFree(pos, o)) return false;
		click(pos, hit, hotbarSlot, o);
		return true;
	}

	private void click(BlockPos pos, BlockHitResult target, int hotbarSlot, Options o) {
		if (o.rotate()) {
			float[] angles = Myriad.rotations().anglesTo(target.getPos());
			// Face the block now, so the server sees the right rotation for this placement packet.
			mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angles[0], angles[1], mc.player.isOnGround(), mc.player.horizontalCollision));
			Myriad.rotations().request(this, angles[0], angles[1], Rotations.PRIORITY_HIGH);
		}
		Runnable action = () -> {
			mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target);
			if (o.swing()) mc.player.swingHand(Hand.MAIN_HAND);
		};
		Myriad.inventory().silentSwap(hotbarSlot, action);
		cooldowns.put(pos.toImmutable(), System.currentTimeMillis());
		if (cooldowns.size() > 256) cooldowns.values().removeIf(t -> System.currentTimeMillis() - t > COOLDOWN_MS);
	}

	@Override
	public List<BlockHitResult> clickTargets(BlockPos pos) {
		List<BlockHitResult> out = new ArrayList<>();
		if (mc.player == null || mc.world == null) return out;
		Vec3d eyes = mc.player.getEyePos();
		for (Direction dir : Direction.values()) {
			BlockPos neighbour = pos.offset(dir);
			BlockState state = mc.world.getBlockState(neighbour);
			if (state.isReplaceable() || (isClickable(state.getBlock()) && !mc.player.isSneaking())) continue;
			Direction face = dir.getOpposite();
			Vec3d hitVec = Vec3d.ofCenter(neighbour).add(Vec3d.of(face.getVector()).multiply(0.5));
			out.add(new BlockHitResult(hitVec, face, neighbour, false));
		}
		out.sort(Comparator.comparingDouble(h -> eyes.squaredDistanceTo(h.getPos())));
		return out;
	}

	/** Blocks that open or react when right-clicked; clicking them would interact instead of place. */
	private static boolean isClickable(Block b) {
		return b instanceof BlockWithEntity || b instanceof AbstractChestBlock<?> || b instanceof DoorBlock || b instanceof TrapdoorBlock
			|| b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof CraftingTableBlock
			|| b instanceof AnvilBlock || b instanceof BedBlock || b instanceof NoteBlock;
	}
}
