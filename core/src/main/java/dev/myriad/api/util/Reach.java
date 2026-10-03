package dev.myriad.api.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the player can reach and see, measured from the eyes as the server does. Ranges are the player's attribute
 * values (4.5 for blocks and 3 for entities in survival), so they follow creative mode and attribute changes.
 */
public final class Reach {
	private Reach() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static double blockRange() {
		return mc().player == null ? 0 : mc().player.blockInteractionRange();
	}

	public static double entityRange() {
		return mc().player == null ? 0 : mc().player.entityInteractionRange();
	}

	public static Vec3 eyes() {
		return mc().player == null ? Vec3.ZERO : mc().player.getEyePosition();
	}

	/** Whether nothing solid lies between two points. */
	public static boolean canSee(Vec3 from, Vec3 to) {
		if (mc().level == null || mc().player == null) return false;
		return mc().level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc().player))
			.getType() == HitResult.Type.MISS;
	}

	/** Whether nothing solid lies between the eyes and {@code point}. */
	public static boolean canSee(Vec3 point) {
		return canSee(eyes(), point);
	}

	/** Whether {@code point} is within block reach of the eyes. */
	public static boolean canReach(Vec3 point) {
		return mc().player != null && eyes().distanceToSqr(point) <= blockRange() * blockRange();
	}

	/** Whether the server would accept hitting {@code entity} (its box within entity reach). */
	public static boolean canReach(Entity entity) {
		return mc().player != null && MathUtil.distanceTo(entity.getBoundingBox(), eyes()) <= entityRange();
	}

	/** Whether the server would accept interacting with the block at {@code pos}. */
	public static boolean canReach(BlockPos pos) {
		return mc().player != null && mc().player.isWithinBlockInteractionRange(pos, 0);
	}

	/** The point on {@code entity}'s box closest to the eyes, where a hit or rotation should aim. */
	public static Vec3 aimPoint(Entity entity) {
		return MathUtil.closestPoint(entity.getBoundingBox(), eyes());
	}

	/**
	 * A click on the block at {@code pos} for opening, breaking or using it: the nearest face within reach, preferring
	 * faces you can see. With {@code mustSee}, only visible faces count (strict anti-cheats check this). Null if no
	 * face qualifies.
	 */
	public static @Nullable BlockHitResult hitFor(BlockPos pos, boolean mustSee) {
		if (mc().level == null || mc().player == null) return null;
		Vec3 eyes = eyes();
		BlockHitResult best = null, bestHidden = null;
		double bestDist = Double.MAX_VALUE, bestHiddenDist = Double.MAX_VALUE;
		for (Direction face : Direction.values()) {
			Vec3 point = Positions.faceCenter(pos, face);
			double d = eyes.distanceToSqr(point);
			if (d > blockRange() * blockRange()) continue;
			// Seeing a face means the ray from the eyes lands on this block (not one in front of it).
			BlockHitResult ray = mc().level.clip(new ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc().player));
			boolean visible = ray.getType() == HitResult.Type.MISS || ray.getBlockPos().equals(pos);
			BlockHitResult hit = new BlockHitResult(point, face, pos, false);
			if (visible && d < bestDist) {
				best = hit;
				bestDist = d;
			} else if (!visible && d < bestHiddenDist) {
				bestHidden = hit;
				bestHiddenDist = d;
			}
		}
		return best != null || mustSee ? best : bestHidden;
	}
}
