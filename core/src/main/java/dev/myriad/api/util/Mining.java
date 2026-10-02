package dev.myriad.api.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/** Block breaking maths that mirrors the server, for mining with packets. */
public final class Mining {
	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	private Mining() {
	}

	public static int efficiency(ItemStack stack) {
		return ItemInfo.enchantmentLevel(stack, Enchantments.EFFICIENCY);
	}

	/** The inventory slot in [from, to) whose item mines {@code state} fastest, or -1. */
	public static int fastestSlot(BlockState state, int from, int to) {
		double best = -1;
		int slot = -1;
		for (int i = from; i < to; i++) {
			ItemStack stack = mc().player.getInventory().getStack(i);
			double score = stack.getMiningSpeedMultiplier(state);
			if (score > 1) {
				int eff = efficiency(stack);
				if (eff > 0) score += eff * eff + 1;
			}
			if (score > best) {
				best = score;
				slot = i;
			}
		}
		return slot;
	}

	public static boolean canHarvest(BlockState state, int toolSlot) {
		if (!state.isToolRequired()) return true;
		return toolSlot >= 0 && mc().player.getInventory().getStack(toolSlot).isSuitableFor(state);
	}

	/** Mining speed with the tool in {@code toolSlot} (or bare hands for -1), including effects, water and air. */
	public static float speed(BlockState state, int toolSlot) {
		float speed = 1f;
		if (toolSlot >= 0) {
			ItemStack stack = mc().player.getInventory().getStack(toolSlot);
			speed = stack.getMiningSpeedMultiplier(state);
			if (speed > 1f) {
				int eff = efficiency(stack);
				if (eff > 0 && !stack.isEmpty()) speed += eff * eff + 1;
			}
		}
		if (StatusEffectUtil.hasHaste(mc().player)) speed *= 1f + (StatusEffectUtil.getHasteAmplifier(mc().player) + 1) * 0.2f;
		var fatigue = mc().player.getStatusEffect(StatusEffects.MINING_FATIGUE);
		if (fatigue != null) {
			speed *= switch (fatigue.getAmplifier()) {
				case 0 -> 0.3f;
				case 1 -> 0.09f;
				case 2 -> 0.0027f;
				default -> 8.1e-4f;
			};
		}
		if (mc().player.isSubmergedIn(FluidTags.WATER)) speed *= (float) mc().player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
		if (!mc().player.isOnGround()) speed /= 5f;
		return speed;
	}

	/** Fraction of the block broken per tick; 0 for unbreakable blocks. */
	public static float delta(BlockState state, BlockPos pos, int toolSlot) {
		float hardness = state.getHardness(mc().world, pos);
		if (hardness == -1f) return 0f;
		int divisor = canHarvest(state, toolSlot) ? 30 : 100;
		return speed(state, toolSlot) / hardness / divisor;
	}
}
