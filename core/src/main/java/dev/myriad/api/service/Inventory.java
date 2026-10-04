package dev.myriad.api.service;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.item.ItemStack;

/** Hotbar helpers that track the slot the server thinks is selected. */
public interface Inventory {
	/** The hotbar slot (0-8) last sent to the server. */
	int serverSlot();

	/** Selects a hotbar slot (client and server). */
	void select(int hotbarSlot);

	/**
	 * Silently switches the server-side slot to {@code hotbarSlot}, runs {@code action}, then switches back to what
	 * the server held before (so a {@link #hold} survives it). The client's visible slot does not change. Sends
	 * nothing when the server already holds that slot.
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

	/**
	 * The item the server thinks is in your main hand: the held slot's item while a module {@link #hold holds} one,
	 * otherwise the selected item. Mining speed and tool checks use it, so holding a pickaxe mines at its speed.
	 */
	ItemStack serverItem();

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

	/**
	 * Whether an inventory click now would pass anti-cheats that refuse clicks while you move: Grim (2b2t) cancels a
	 * click sent with movement keys held, while sprinting or while sneaking. Moves that can wait (refills, tools,
	 * building material) should wait for this; urgent ones (a totem) go anyway.
	 */
	boolean safeToClick();

	/**
	 * For a click that shouldn't wait for you to stop moving: true if one is safe now ({@link #safeToClick()});
	 * otherwise your movement keys, sneak and sprint are released for the next tick (sprint comes back after) and it
	 * returns false, so asking again next tick gets a safe click. Costs a moment of slowing down, so moves that can
	 * wait (refills) should use {@link #safeToClick()} instead.
	 */
	boolean prepareClick();

	/**
	 * Brings the item at {@code inventoryIndex} (9-35) into the hotbar to be used, keeping out of the way of what you
	 * use: into an empty slot, else over an item {@code replaceable} accepts (they swap places, so it lands where this
	 * one was), else over ordinary building blocks. Never the selected slot or one a module holds. One click; while you
	 * move, it first releases your keys for a tick ({@link #prepareClick()}). Returns the hotbar slot it went to, or -1
	 * (ask again next tick).
	 */
	int pullToHotbar(int inventoryIndex, Predicate<ItemStack> replaceable);

	// The moves below click in your own inventory screen, which works without opening it (as vanilla's number keys
	// do). They need no container to be open, and return false otherwise, for bad slots, or when the packet budget
	// is spent (see PacketLimits; wrap must-happen moves in Myriad.limits().urgent). Indexes are inventory indexes
	// (see Slots): 0-8 hotbar, 9-35 main, 36-39 armour (feet..head), 40 off hand.

	/** Moves the stack at {@code from} to {@code to}, swapping with whatever is there (merging if they stack). */
	boolean move(int from, int to);

	/**
	 * Tops up the stack at {@code to} from the stack at {@code from} when they stack together: as much as fits moves
	 * over and the rest stays at {@code from}. Returns false (and does nothing) if they don't stack.
	 */
	boolean merge(int from, int to);

	/** Swaps {@code inventoryIndex} with the off hand (like pressing F over it), e.g. to put a totem there. */
	boolean swapWithOffhand(int inventoryIndex);

	/** Shift-clicks {@code inventoryIndex}: armour goes onto you, other items between the hotbar and main inventory. */
	boolean quickMove(int inventoryIndex);

	/** Throws out the stack at {@code inventoryIndex} (or one item). */
	boolean drop(int inventoryIndex, boolean wholeStack);
}
