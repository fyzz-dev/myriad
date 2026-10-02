package dev.myriad.api.util;

import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * What kind of block something is, with one answer shared by every addon: can it be broken, does it survive
 * explosions, does it hold items, does right-clicking it open it. Position overloads read the client world.
 */
public final class BlockInfo {
	/** Obsidian's blast resistance; anything at or above it survives crystal and bed explosions. */
	private static final float BLAST_RESISTANT = 600;

	private BlockInfo() {
	}

	private static BlockState state(BlockPos pos) {
		var world = MinecraftClient.getInstance().world;
		return world == null ? net.minecraft.block.Blocks.AIR.getDefaultState() : world.getBlockState(pos);
	}

	/** Bedrock, barriers, end portal frames and the like: nothing a survival player can mine. */
	public static boolean isUnbreakable(BlockState state) {
		return state.getBlock().getHardness() < 0;
	}

	public static boolean isUnbreakable(BlockPos pos) {
		return isUnbreakable(state(pos));
	}

	/** Not air or fluid, and minable. */
	public static boolean canBreak(BlockPos pos) {
		BlockState s = state(pos);
		return !s.isAir() && s.getFluidState().isEmpty() && !isUnbreakable(s);
	}

	/** Whether the held item (or the best hotbar tool) breaks it in a single tick. */
	public static boolean canInstaBreak(BlockPos pos, int toolSlot) {
		return canBreak(pos) && Mining.delta(state(pos), pos, toolSlot) >= 1;
	}

	/** Survives explosions: obsidian, crying obsidian, ender chests, anvils, netherite blocks, bedrock… */
	public static boolean isBlastResistant(BlockState state) {
		return state.getBlock().getBlastResistance() >= BLAST_RESISTANT;
	}

	public static boolean isBlastResistant(BlockPos pos) {
		return isBlastResistant(state(pos));
	}

	/** Holds items players put in and take out: chests, barrels, shulkers, ender chests, hoppers, furnaces… */
	public static boolean isStorage(BlockState state) {
		Block b = state.getBlock();
		return b instanceof AbstractChestBlock<?> || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof HopperBlock
			|| b instanceof DispenserBlock || b instanceof AbstractFurnaceBlock || b instanceof BrewingStandBlock || b instanceof CrafterBlock;
	}

	public static boolean isStorage(BlockPos pos) {
		return isStorage(state(pos));
	}

	/**
	 * Right-clicking it does something (opens, toggles, sits), so clicking it to place a block against it would
	 * interact instead unless the player sneaks.
	 */
	public static boolean isClickable(BlockState state) {
		Block b = state.getBlock();
		return b instanceof BlockWithEntity || b instanceof AbstractChestBlock<?> || b instanceof DoorBlock || b instanceof TrapdoorBlock
			|| b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof CraftingTableBlock
			|| b instanceof AnvilBlock || b instanceof BedBlock || b instanceof NoteBlock;
	}

	/** Air, water, grass and anything else a placed block replaces. */
	public static boolean isReplaceable(BlockPos pos) {
		return state(pos).isReplaceable();
	}
}
