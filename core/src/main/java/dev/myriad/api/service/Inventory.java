package dev.myriad.api.service;

import net.minecraft.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Hotbar helpers that track the slot the server thinks is selected. */
public interface Inventory {
	/** The hotbar slot (0-8) last sent to the server. */
	int serverSlot();

	/** Selects a hotbar slot (client and server). */
	void select(int hotbarSlot);

	/**
	 * Silently switches the server-side slot to {@code hotbarSlot}, runs {@code action}, then switches back. The
	 * client's visible slot does not change.
	 */
	void silentSwap(int hotbarSlot, Runnable action);

	/**
	 * Holds the server-side slot on {@code hotbarSlot} across ticks without changing the visible slot, until
	 * {@link #release} or {@code maxTicks} pass. One module holds at a time; returns false if another owner holds.
	 * Calling it again as the same owner refreshes the timeout (and moves the hold to the new slot).
	 */
	boolean hold(Object owner, int hotbarSlot, int maxTicks);

	/** Ends {@code owner}'s hold and syncs the server back to the visible slot. */
	void release(Object owner);

	/** Whether any module is holding a slot. */
	boolean isHolding();

	/** First hotbar slot matching, or -1. */
	int findInHotbar(Predicate<ItemStack> predicate);

	/** First main-inventory slot (9-35) matching, or -1. */
	int findInInventory(Predicate<ItemStack> predicate);

	/** The hotbar slot whose item scores highest; items scoring 0 or less don't count. -1 if none. */
	int bestInHotbar(ToDoubleFunction<ItemStack> score);

	/** Like {@link #bestInHotbar} over the hotbar and main inventory (0-35). */
	int bestInInventory(ToDoubleFunction<ItemStack> score);

	/** Total item count matching across the hotbar, main inventory and off hand. */
	int count(Predicate<ItemStack> predicate);

	/**
	 * Swaps the item at {@code inventoryIndex} (0-35, see {@link dev.myriad.api.util.Slots}) into hotbar slot
	 * {@code hotbarSlot}, as pressing a number key over it would. Only works while no container is open; returns
	 * false otherwise.
	 */
	boolean moveToHotbar(int inventoryIndex, int hotbarSlot);

	/**
	 * The hotbar slot holding a matching item, moving one in from the main inventory if needed: into
	 * {@code preferredSlot}, or the first empty hotbar slot when that's -1 (falling back to the selected slot).
	 * Returns -1 if you have none or it couldn't be moved.
	 */
	int ensureInHotbar(Predicate<ItemStack> predicate, int preferredSlot);
}
