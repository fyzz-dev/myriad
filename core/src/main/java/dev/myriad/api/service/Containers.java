package dev.myriad.api.service;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Containers (chests, barrels, shulkers, furnaces, …): opening one at a position, reading it once its contents have
 * arrived, and moving items. Pair it with {@code Myriad.tasks()} for multi-step jobs, and listen to
 * {@code ContainerEvent} to see containers the player opens by hand.
 *
 * <pre>{@code
 * Myriad.containers().open(chestPos, 20).thenAccept(view -> {
 *     int slot = view.find(s -> s.isOf(Items.DIAMOND));
 *     if (slot != -1) view.quickMove(slot);
 *     view.close();
 * });
 * }</pre>
 *
 * Futures complete on the render thread, so it's safe to touch the game from them.
 */
@ApiStatus.NonExtendable
public interface Containers {
	/** The open container, if one is open (the player's own inventory doesn't count). */
	Optional<View> current();

	/**
	 * Right-clicks the block at {@code pos} (on the nearest face in reach) and completes once the container's
	 * contents have arrived. Fails with a {@link java.util.concurrent.TimeoutException} after {@code timeoutTicks}, or
	 * an {@link IllegalStateException} if the block is out of reach. Opening another replaces a pending one.
	 */
	CompletableFuture<View> open(BlockPos pos, int timeoutTicks);

	/** Closes the open container (and its screen), if any. */
	void close();

	/** An open container. Slot numbers are the container's own (0 = its first slot), unless noted. */
	@ApiStatus.NonExtendable
	interface View {
		AbstractContainerMenu handler();

		int syncId();

		/** The block it was opened from, when known (it was opened by clicking a block just before). */
		@Nullable BlockPos pos();

		/** Whether the contents have arrived. Before that, slots read as empty. */
		boolean isLoaded();

		/** Whether this container is still the one open. */
		boolean isOpen();

		/** Number of the container's own slots (the player's inventory below them isn't counted). */
		int size();

		ItemStack stack(int slot);

		/** The container's own slots. */
		List<ItemStack> stacks();

		/** First container slot matching, or -1. */
		int find(Predicate<ItemStack> predicate);

		/** First empty container slot, or -1 if it's full. */
		int emptySlot();

		/** Total count of matching items in the container. */
		int count(Predicate<ItemStack> predicate);

		/** The screen slot id for an inventory index (0-35) in this container's screen. */
		int playerSlot(int inventoryIndex);

		/** Shift-clicks a container slot: moves its stack into your inventory. */
		void quickMove(int slot);

		/** Shift-clicks your inventory index (0-35): moves that stack into the container. */
		void quickMoveFromPlayer(int inventoryIndex);

		/** Swaps a container slot with a hotbar slot (0-8), like pressing a number key over it. */
		void swapWithHotbar(int slot, int hotbarSlot);

		/** Drops a container slot's stack (or one item). */
		void drop(int slot, boolean wholeStack);

		/** Any click, with a raw screen slot id. */
		void click(int screenSlot, int button, ContainerInput action);

		void close();
	}
}
